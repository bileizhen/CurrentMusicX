package io.github.currencortex.music

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.currencortex.music.core.config.AppMetadata
import io.github.currencortex.music.core.logging.AppLogger
import io.github.currencortex.music.core.update.GitHubUpdateChecker
import io.github.currencortex.music.core.update.UpdateService
import io.github.currencortex.music.data.settings.SettingsRepository
import io.github.currencortex.music.data.update.UpdateSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.security.SecureTokenStore
import io.github.currencortex.music.data.auth.*
import io.github.currencortex.music.data.song.MusicRepository
import io.github.currencortex.music.data.local.MusicDatabase
import io.github.currencortex.music.data.settings.MusicSettingsRepository
import io.github.currencortex.music.data.settings.AudioProvider
import androidx.room.Room
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import io.github.currencortex.music.core.netease.*

class AppContainer(context: Context, storageNamespace: String = "", externalPlayer: ExternalPlayer? = null,
    ttmlProvider: io.github.currencortex.music.feature.lyrics.data.LyricsProvider? = null,
    useNativeNetease: Boolean = storageNamespace.isEmpty(),
    neteaseEndpoints: NeteaseEndpoints = NeteaseEndpoints()) : java.io.Closeable {
    private val storageSuffix = if (storageNamespace.isEmpty()) "" else ".$storageNamespace".also {
        require(storageNamespace.matches(Regex("[a-zA-Z0-9-]+")))
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settingsStore = PreferenceDataStoreFactory.create(
        scope = appScope,
        produceFile = { context.preferencesDataStoreFile("app$storageSuffix.preferences_pb") },
    )

    val updateTransfer = io.github.currencortex.music.core.update.UpdateTransfer(
        io.github.currencortex.music.core.update.UpdateDownloader(okhttp3.OkHttpClient(), java.io.File(context.cacheDir, "updates")),
        io.github.currencortex.music.core.update.AndroidUpdateInstaller(context.applicationContext), playerScope,
        if (storageNamespace.isEmpty()) io.github.currencortex.music.core.update.WorkManagerUpdateBackground(context) else null)
    val logger = AppLogger(context)
    val musicSettings = MusicSettingsRepository(settingsStore, appScope)
    val soundEffectNotices = io.github.currencortex.music.core.media.SoundEffectNoticeCenter(playerScope)
    val errorNotices = io.github.currencortex.music.core.media.IslandErrorNoticeCenter(playerScope)
    val desktopLyrics = io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsPreferences(settingsStore, appScope)
    val appForeground = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val updateIslandNotice = io.github.currencortex.music.core.update.UpdateIslandNotice(
        updateTransfer.state.map { it.download }, kotlinx.coroutines.flow.combine(desktopLyrics.state, appForeground) { options, foreground -> options.enabled || foreground }, playerScope)
    val islandNotices = io.github.currencortex.music.core.update.prioritizedIslandNotices(
        updateIslandNotice.state, soundEffectNotices.state, playerScope, errors = errorNotices.state)
    val desktopLyricsStatus = kotlinx.coroutines.flow.MutableStateFlow(io.github.currencortex.music.feature.lyrics.desktop.DesktopWindowStatus())
    val desktopLyricsSettingsVisible = kotlinx.coroutines.flow.MutableStateFlow(false)
    val desktopIslandPageHidden = kotlinx.coroutines.flow.MutableStateFlow(false)
    val equalizerSettings = io.github.currencortex.music.data.settings.EqualizerSettingsRepository(settingsStore, appScope)
    val equalizer = PlaybackEqualizer { logger.warn("Equalizer", it) }
    val soundEffectsSettings = io.github.currencortex.music.data.settings.SoundEffectsSettingsRepository(settingsStore, appScope)
    val originalEffectsSettings = io.github.currencortex.music.data.settings.OriginalSoundEffectsSettingsRepository(settingsStore, appScope)
    val originalSmartSettings = io.github.currencortex.music.data.settings.OriginalSmartSettingsRepository(settingsStore, appScope)
    val originalMoodSettings = io.github.currencortex.music.data.settings.OriginalMoodSettingsRepository(settingsStore, appScope)
    val originalAiMasterSettings = io.github.currencortex.music.data.settings.OriginalAiMasterSettingsRepository(settingsStore, appScope)
    private val originalEngineContext = context.applicationContext
    init { NeteaseNativeEngine.configure(originalEngineContext) }
    suspend fun importOriginalApk(uri: android.net.Uri) = importOriginal { NeteaseNativeEngine.installFromApk(originalEngineContext, uri) }
    suspend fun importInstalledOriginal() = importOriginal { NeteaseNativeEngine.installFromInstalled(originalEngineContext) }
    private suspend fun importOriginal(action: suspend () -> Unit) {
        try { action() } finally {
            // APK extraction may finish after the settings page is dismissed.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) {
                refreshOriginalEngine()
            }
        }
    }
    val audioSettings = io.github.currencortex.music.data.settings.AudioSourceSettings(settingsStore,
        SecureTokenStore(context, ".leiz$storageSuffix"), appScope)
    val accountVault = EncryptedAccountVault.create(context, storageSuffix, settingsStore, appScope)
    val accountRepository = AccountRepository(SecureTokenStore(context, storageSuffix), appScope, accountVault)
    val neteaseSessions = NeteaseSessionStore(SecureTokenStore(context, ".netease$storageSuffix"), appScope)
    private val neteaseTransport = NeteaseTransport(neteaseSessions, neteaseEndpoints,
        log = { logger.info("Network", it) })
    val nativeNetease = if (useNativeNetease) NeteaseGateway(neteaseTransport) else null
    val neteaseSoundEffects = io.github.currencortex.music.data.effects.NeteaseSoundEffectsRepository(neteaseTransport)
    val originalMoodModels = io.github.currencortex.music.data.effects.OriginalMoodModelRepository(originalEngineContext, neteaseTransport)
    val originalAiMasterRepository = io.github.currencortex.music.data.effects.OriginalAiMasterRepository(originalEngineContext, neteaseTransport)
    val originalSpeakerRepository = io.github.currencortex.music.data.effects.OriginalSpeakerEffectsRepository(neteaseTransport)
    internal val originalEffectsPageCache by lazy {
        originalEngineContext.getSharedPreferences("original-effects-page$storageSuffix", Context.MODE_PRIVATE)
    }
    internal fun originalEffectsPageAudioManager(): android.media.AudioManager? =
        originalEngineContext.getSystemService(android.media.AudioManager::class.java)
    internal suspend fun requestOriginalEffectsPage(path: String, params: kotlinx.serialization.json.JsonObject) =
        neteaseTransport.request(path, params, clientProfile = NeteaseClientProfile.ANDROID).body
    fun musicSession(authenticated: Boolean = true) = RequestSession(
        accountRepository.server.ifBlank { musicSettings.state.value.server },
        accountRepository.token.takeIf { authenticated }, nativeNetease?.sessions?.state?.value?.revision)
    fun audioSession() = if (audioSettings.access().provider == AudioProvider.NETEASE)
        RequestSession("https://music.163.com/", null, neteaseSessions.state.value.revision)
        else RequestSession(accountRepository.server.ifBlank { musicSettings.state.value.server }, accountRepository.token)
    val apiClient = ApiClient(
        server = { accountRepository.server.ifBlank { musicSettings.state.value.server } },
        token = { accountRepository.token }, onUnauthorized = accountRepository::expired,
        log = { logger.info("Network", it) },
        netease = nativeNetease,
        nativeMedia = { audioSettings.access().provider == AudioProvider.NETEASE },
    )
    val authRepository = AuthRepository(apiClient, accountRepository, "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        persistServer = musicSettings::setServer, persistAccount = musicSettings::setAccount)
    val navidrome = io.github.currencortex.music.data.navidrome.NavidromeRepository(SecureTokenStore(context, ".navidrome$storageSuffix"), appScope)
    val navidromeMedia = okhttp3.OkHttpClient.Builder().addInterceptor(navidrome.client.mediaInterceptor()).build()
    val musicRepository = MusicRepository(apiClient, audioSettings::access, navidrome = navidrome)
    fun audioRequest(song: io.github.currencortex.music.data.song.Song, quality: AudioQuality): AudioRequest {
        if (song.musicSource == io.github.currencortex.music.data.song.MusicSource.NAVIDROME)
            return AudioRequest(song.id, quality, 0L, navidrome.session(), "navidrome:" + navidrome.state.value.identity, song)
        val access = audioSettings.access()
        if (access.provider == AudioProvider.NETEASE) {
            // Snapshot UID and revision together so a concurrent account switch cannot mix cache owners.
            val owner = neteaseSessions.state.value
            return AudioRequest(song.id, quality, owner.profile?.uid ?: 0L,
                RequestSession("https://music.163.com/", null, owner.revision), access.identity, song)
        }
        return AudioRequest(song.id, quality, accountRepository.state.value.account?.id ?: 0L, audioSession(), access.identity, song)
    }
    val announcements = io.github.currencortex.music.data.announcement.AnnouncementRepository(apiClient)
    val announcementPreferences = io.github.currencortex.music.data.announcement.AnnouncementPreferences(settingsStore)
    val songDownloads = io.github.currencortex.music.core.download.SongDownloadManager(context.applicationContext, storageSuffix)
    val mvDownloads = io.github.currencortex.music.core.download.MvDownloadManager(context.applicationContext, songDownloads)
    val musicStyles = io.github.currencortex.music.data.style.MusicStyleRepository(apiClient) {
        musicSession(authenticated = false)
    }
    val neteaseSongActions = io.github.currencortex.music.data.song.NeteaseSongActionsRepository(apiClient) {
        musicSession()
    }
    val playlistLikeImporter by lazy {
        io.github.currencortex.music.data.library.PlaylistLikeImporter(playerScope, { musicSession() },
            { accountRepository.state.value.account?.id ?: 0 },
            { libraryRepository.playlist(it, fresh = true) },
            { expected ->
                val uid = neteaseSongActions.boundUid(expected)
                val liked = neteaseSongActions.likedIds(fresh = true)
                if (neteaseSongActions.boundUid(expected) != uid)
                    throw ApiException(ErrorKind.NeteaseSessionChanged)
                io.github.currencortex.music.data.library.PlaylistLikeTarget(uid, liked)
            },
            { song, expected, uid ->
                val id = io.github.currencortex.music.data.song.NeteaseSongActionsRepository.songId(song)
                    ?: throw ApiException(ErrorKind.NotFound)
                neteaseSongActions.setLiked(id, true, expected, song, expectedUid = uid)
            })
    }
    val audioCache = AudioCache(context.applicationContext, java.io.File(context.cacheDir, "audio$storageSuffix"), navidromeMedia)
    val storage = io.github.currencortex.music.core.storage.StorageStore(context.applicationContext, audioCache)
    val audioSources = AudioSourceResolver(audioCache,
        { audioSession() },
        currentProvider = { audioSettings.access().identity },
        validateRequest = { request -> request.song?.takeIf { it.musicSource == io.github.currencortex.music.data.song.MusicSource.NAVIDROME }?.let {
            navidrome.accepts(it) && request.session == navidrome.session() && request.providerIdentity == "navidrome:" + navidrome.state.value.identity
        } },
        loadTrack = { request -> request.song?.let { musicRepository.source(it, request.quality, request.session) }
            ?: musicRepository.source(request.songId, request.quality, request.session) }, load = musicRepository::source)
    val libraryRepository = io.github.currencortex.music.data.library.LibraryRepository(apiClient,
        { accountRepository.state.value.account?.id ?: 0L }, { musicSession() })
    val listeningReports = run {
        val listened = mutableMapOf<Pair<Long, RequestSession>, Long>()
        val submitted = mutableSetOf<Pair<Long, RequestSession>>()
        ListeningReportQueue(appScope,
            currentSession = { song -> if (song.musicSource == io.github.currencortex.music.data.song.MusicSource.NAVIDROME)
                navidrome.session() else musicSession() },
            currentMusic = { song, ms, owner ->
                if (ms == null) libraryRepository.recordPlay(song, owner) else libraryRepository.listen(song, ms, owner)
            },
            netease = nativeNetease?.let { gateway -> { song, ms, owner ->
                gateway.recordPlay(song.id, ms, owner.neteaseRevision)
                if (ms == null) libraryRepository.invalidate()
            } },
            navidrome = { song, ms, owner ->
                val key = song.id to owner
                if (ms == null) { listened[key] = 0; submitted.remove(key); navidrome.scrobble(song, false) }
                else {
                    val total = (listened[key] ?: 0) + ms; listened[key] = total
                    val threshold = minOf(song.durationMs.takeIf { it > 0 }?.div(2) ?: 240000, 240000).coerceAtLeast(30000)
                    if (total >= threshold && key !in submitted) { navidrome.scrobble(song, true); submitted.add(key) }
                }
                if (listened.size > 100) { listened.keys.firstOrNull()?.let { listened.remove(it); submitted.remove(it) } }
            },
            onFailure = { platform, kind -> logger.warn("Player", "$platform listening report failed: $kind") })
    }
    val profileRepository = io.github.currencortex.music.data.profile.ProfileRepository(apiClient,
        AvatarImageCache(context, storageSuffix)) { RequestSession(accountRepository.server, accountRepository.token) }
    val bindingRepository: io.github.currencortex.music.data.binding.BindingRepository = io.github.currencortex.music.data.binding.BindingRepository(apiClient,
        { musicSession() }) { libraryRepository.invalidate(); neteaseLibrary.invalidate() }
    val neteaseLibrary = io.github.currencortex.music.data.library.NeteaseLibraryRepository(apiClient, bindingRepository,
        neteaseSongActions) { musicSession() }
    val primaryLibrary = io.github.currencortex.music.data.library.PrimaryMusicLibrary(libraryRepository,
        neteaseLibrary, bindingRepository, musicSettings, appScope)
    val database = Room.databaseBuilder(context.applicationContext, MusicDatabase::class.java, "music$storageSuffix.db")
        .addMigrations(MusicDatabase.MIGRATION_1_2).build()
    private val nativeLyricsProvider = io.github.currencortex.music.feature.lyrics.data.CurrentMusicLyricsProvider(
        musicRepository, database.music(), server = { accountRepository.server }) { logger.debug("Lyrics", it) }
    private val projectLyricsProvider = io.github.currencortex.music.feature.lyrics.data.CurrentMusicLyricsProvider(
        musicRepository, database.music(), io.github.currencortex.music.data.settings.LyricsSource.CURRENT_MUSIC,
        { accountRepository.server }) { logger.debug("Lyrics", it) }
    // Isolated containers use only explicitly injected external providers, never live GitHub.
    val lyricsRepository = io.github.currencortex.music.feature.lyrics.data.LyricsRepository(
        ttmlProvider ?: if (storageNamespace.isEmpty()) io.github.currencortex.music.feature.lyrics.data.AmllLyricsProvider()
        else io.github.currencortex.music.feature.lyrics.data.LyricsProvider {
            io.github.currencortex.music.feature.lyrics.data.LyricsProviderResult.Failure(io.github.currencortex.music.feature.lyrics.model.LyricsErrorCode.NO_LYRICS, "Isolated external provider")
        },
        nativeLyricsProvider,
        io.github.currencortex.music.feature.lyrics.data.LyricsCache(java.io.File(context.cacheDir, "lyrics$storageSuffix")),
        { logger.debug("Lyrics", it) }, selection = if (ttmlProvider != null) null else {
            {
                val source = musicSettings.state.value.lyricsSource
                io.github.currencortex.music.feature.lyrics.data.LyricsSelection(
                    listOf(source, accountRepository.server, accountRepository.sessionRevision.value,
                        neteaseSessions.state.value.revision, navidrome.state.value.revision),
                    if (source == io.github.currencortex.music.data.settings.LyricsSource.CURRENT_MUSIC)
                        projectLyricsProvider else nativeLyricsProvider)
            }
        })
    val playbackQueue = PlaybackQueue()
    val dockAudioLevel = DockAudioLevel()
    val playerController = PlayerController(context.applicationContext, playbackQueue, playerScope)
    val personalRoaming = io.github.currencortex.music.feature.roaming.PersonalRoamingController(
        io.github.currencortex.music.data.roaming.PersonalRoamingRepository(neteaseTransport), neteaseSessions, playerController,
        io.github.currencortex.music.data.roaming.RoamingPreferences(settingsStore), playerScope,
        audioSettings.state.map { audioSettings.access().identity }.distinctUntilChanged()
            .stateIn(playerScope, SharingStarted.Eagerly, audioSettings.access().identity))
    val smartTuning = SmartTuningController(equalizerSettings.state, soundEffectsSettings.state,
        playerController.state.map { it.song }, neteaseSoundEffects::analyze, playerScope)
    val originalSoundEffects: OriginalSoundEffectsController = OriginalSoundEffectsController(neteaseSoundEffects, neteaseSessions, originalEffectsSettings, playerScope) {
        disableOriginalSmart()
        disableOriginalMood()
        disableOriginalAdditional()
        soundEffectsSettings.setSmartEnabled(false)
        smartTuning.clearManualPreview()
    }
    private val originalEngineStatus = kotlinx.coroutines.flow.MutableStateFlow(NeteaseNativeEngine.available())
    val originalSmartEffects: OriginalSmartEffectsController = OriginalSmartEffectsController(neteaseSoundEffects, neteaseSessions,
        originalSmartSettings, playerController.state.map { it.song }, originalEngineStatus, playerScope,
        beforeEnable = {
            originalSoundEffects.clear()
            disableOriginalMood()
            disableOriginalAdditional()
            soundEffectsSettings.setSmartEnabled(false)
            smartTuning.clearManualPreview()
        }, definition = NeteaseNativeEngine::smartDefinition)
    private fun disableOriginalSmart() = originalSmartEffects.disable()
    private fun disableOriginalMood() = originalMoodEffects.disable()
    private val originalMoodEngineStatus = kotlinx.coroutines.flow.MutableStateFlow(NeteaseEngineLibraries.moodAvailable())
    val originalMoodEffects: OriginalMoodEffectsController = OriginalMoodEffectsController(originalMoodModels, neteaseSessions,
        originalMoodSettings, playerController.state.map { it.song }, originalMoodEngineStatus, playerScope,
        beforeEnable = {
            originalSoundEffects.clear()
            originalSmartEffects.disable()
            disableOriginalAdditional()
            soundEffectsSettings.setSmartEnabled(false)
            smartTuning.clearManualPreview()
        }, definition = NeteaseNativeEngine::moodDefinition)
    private fun disableOriginalAdditional() {
        originalAiMasterEffects.disable()
        originalSpeakerEffects.disable()
    }
    val originalAiMasterEffects: OriginalAiMasterEffectsController = OriginalAiMasterEffectsController(
        originalAiMasterRepository, neteaseSessions, originalAiMasterSettings, playerController.state.map { it.song },
        originalEngineStatus, playerScope, beforeEnable = {
            originalSoundEffects.clear()
            originalSmartEffects.disable()
            originalMoodEffects.disable()
            originalSpeakerEffects.disable()
            soundEffectsSettings.setSmartEnabled(false)
            smartTuning.clearManualPreview()
        }, definition = { originalEngineContext.assets.open("netease-ai-master-codec.js").use { it.readBytes() } })
    val originalSpeakerEffects: OriginalSpeakerEffectsController = OriginalSpeakerEffectsController(
        originalSpeakerRepository, neteaseSessions, OriginalSpeakerEnvironmentObserver(originalEngineContext).state,
        originalEngineStatus, playerScope, beforeEnable = {
            originalSoundEffects.clear()
            originalSmartEffects.disable()
            originalMoodEffects.disable()
            originalAiMasterEffects.disable()
            soundEffectsSettings.setSmartEnabled(false)
            smartTuning.clearManualPreview()
        })
    val originalProcessingPreset = combine(originalSoundEffects.selection, originalSmartEffects.selection,
        originalMoodEffects.selection, originalAiMasterEffects.selection, originalSpeakerEffects.selection) { single, smart, mood, master, speaker ->
        speaker ?: master ?: mood ?: smart ?: single
    }.stateIn(playerScope, SharingStarted.Eagerly, null)
    internal fun createEffectsAudition(scope: CoroutineScope) = EffectsAuditionController(originalEngineContext, this, scope)
    fun refreshOriginalEngine() {
        originalEngineStatus.value = NeteaseNativeEngine.available()
        originalMoodEngineStatus.value = originalEngineStatus.value && NeteaseEngineLibraries.moodAvailable()
        originalSoundEffects.refreshAvailability()
        originalSmartEffects.refresh()
        originalMoodEffects.refresh()
        originalAiMasterEffects.refresh()
        originalSpeakerEffects.refresh()
    }
    val playbackLyrics = io.github.currencortex.music.feature.lyrics.data.PlaybackLyrics(
        kotlinx.coroutines.flow.combine(playbackQueue.state.map { it.current }.distinctUntilChanged(),
            accountRepository.sessionRevision, neteaseSessions.state.map { it.revision }.distinctUntilChanged(),
            navidrome.state.map { it.revision }.distinctUntilChanged(),
            musicSettings.state.map { it.server to it.lyricsSource }.distinctUntilChanged()) { song, account, netease, navidrome, lyricsSettings ->
            io.github.currencortex.music.feature.lyrics.data.LyricsRequest(song, listOf(account, netease, navidrome, lyricsSettings))
        }, lyricsRepository::load, playerScope)
    val sleepTimer = io.github.currencortex.music.core.media.SleepTimer(playerController, playerScope)
    val roomRepository = io.github.currencortex.music.data.room.RoomRepository(apiClient) { RequestSession(accountRepository.server, accountRepository.token) }
    val roomSession = io.github.currencortex.music.core.room.RoomSession(roomRepository,
        io.github.currencortex.music.core.room.RoomSseClient(), externalPlayer ?: playerController, playerScope,
        { accountRepository.state.value.account?.id ?: 0 }, accountRepository::expired)
    internal val listenTogetherHistory = io.github.currencortex.music.data.netease.listen.NeteaseListenTogetherHistoryRepository(neteaseTransport)
    internal val listenTogetherFate = io.github.currencortex.music.data.netease.listen.NeteaseListenTogetherFateRepository(neteaseTransport)
    val listenTogether = io.github.currencortex.music.core.netease.listen.NeteaseListenTogetherSession(
        io.github.currencortex.music.data.netease.listen.NeteaseListenTogetherRepository(neteaseTransport,
            trace = { if (BuildConfig.DEBUG) android.util.Log.d("ListenTogetherSync", it) }),
        externalPlayer ?: playerController, playerScope, musicRepository::detail,
        { song -> musicRepository.source(song, AudioQuality.from(musicSettings.state.value.quality.value), audioSession()).url },
        details = musicRepository::details,
        trace = { if (BuildConfig.DEBUG) android.util.Log.d("ListenTogetherSync", it) })
    private val dlnaSoap = io.github.currencortex.music.core.dlna.SoapClient(networkSocketFactory = {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        manager.allNetworks.firstOrNull { manager.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true }?.socketFactory
    })
    val dlnaDiscovery = io.github.currencortex.music.core.dlna.DlnaDiscovery(context, soap = dlnaSoap)
    val dlnaController = io.github.currencortex.music.core.dlna.DlnaController(externalPlayer ?: playerController, musicRepository,
        { audioSession() }, playerScope, dlnaSoap)
    val ready = CompletableDeferred<Unit>()
    val sessionRestored = CompletableDeferred<Unit>()
    init {
        if (storageNamespace.isEmpty() && externalPlayer == null) playerScope.launch {
            ready.await()
            sessionRestored.await()
            neteaseSessions.ready.await()
            val account = neteaseSessions.state.value
            if (account.loggedIn && !account.stale) {
                val commands = playerController.commandRevision
                val localQueue = playbackQueue.state.value
                listenTogether.restoreAutomatically {
                    playerController.commandRevision == commands && playbackQueue.state.value == localQueue &&
                        !playerController.state.value.showPause && roomSession.state.value.detail == null
                }
            }
        }
        playerScope.launch {
            neteaseSessions.ready.await()
            var previous = neteaseSessions.state.value.revision
            neteaseSessions.state.map { it.revision }.distinctUntilChanged().collect { revision ->
                if (revision != previous) {
                    listenTogether.disconnect(if (listenTogether.state.value.active) "网易云账号已变更，请重新进入一起听" else null)
                    previous = revision
                }
            }
        }
        playerScope.launch {
            var previous = navidrome.state.value.revision
            navidrome.state.collect { account ->
                if (previous != account.revision) {
                    audioSources.invalidate()
                    if (playbackQueue.state.value.current?.musicSource == io.github.currencortex.music.data.song.MusicSource.NAVIDROME) playerController.pause()
                    previous = account.revision
                }
            }
        }
        if (nativeNetease != null) appScope.launch {
            neteaseSessions.ready.await()
            neteaseSessions.state.collect {
                bindingRepository.clearSession()
                // Cloud binding reads can fail offline; a background refresh must not
                // terminate the app or prevent the other libraries from being invalidated.
                appResult { bindingRepository.status() }
                libraryRepository.clearSession(); neteaseLibrary.invalidate(); neteaseSongActions.clearSession()
            }
        }
        playerScope.launch {
            accountRepository.sessionRevision.collect {
                audioSources.invalidate(); roomSession.disconnect(); listenTogether.disconnect(); dlnaController.stop()
                bindingRepository.clearSession(); neteaseLibrary.invalidate(); neteaseSongActions.clearSession()
            }
        }
        appScope.launch { audioSettings.state.collect { audioSources.invalidate() } }
        appScope.launch { audioSettings.state.map { it.provider }.distinctUntilChanged().collect {
            bindingRepository.clearSession(); neteaseLibrary.invalidate(); neteaseSongActions.clearSession()
            libraryRepository.clearReads()
        } }
        appScope.launch {
            bindingRepository.state.map { it?.profile?.uid to it?.bound }.distinctUntilChanged().collect {
                neteaseSongActions.clearSession(); neteaseLibrary.invalidate()
            }
        }
        appScope.launch {
            kotlinx.coroutines.flow.combine(accountRepository.state, musicSettings.state) { account, preferences ->
                account.account?.id to preferences.server
            }.distinctUntilChanged().collect {
                libraryRepository.clearSession(); bindingRepository.clearSession()
            }
        }
        appScope.launch {
            try {
                val initial = musicSettings.snapshot()
                audioSettings.ready.await()
                navidrome.ready.await()
                if (nativeNetease != null) neteaseSessions.ready.await()
                accountRepository.server = initial.server
                if (initial.restoreQueue) database.music().queue()?.let {
                    playbackQueue.restore(ApiJson.decodeFromString<QueueSnapshot>(it.payload))
                }
                ready.complete(Unit)
                if (nativeNetease != null) {
                    // Restore cached CurrentMusic identity locally. Its slow validation must
                    // not block the independent NetEase library or launch animation.
                    accountRepository.restoreToken()
                    sessionRestored.complete(Unit)
                    appScope.launch { authRepository.validateRestored() }
                } else authRepository.restore(initial.server)
            } catch (_: Exception) {
                ready.complete(Unit)
                accountRepository.state.value = AccountState(error = "本地会话恢复失败，请重新登录")
            } finally {
                sessionRestored.complete(Unit)
            }
        }
    }
    val settings = SettingsRepository(settingsStore, appScope)
    val updateSettings = UpdateSettingsRepository(settingsStore, appScope)
    val updates = UpdateService(
        checker = GitHubUpdateChecker(
            owner = AppMetadata.RELEASES_OWNER,
            repository = AppMetadata.RELEASES_REPO,
            installedVersion = BuildConfig.VERSION_NAME,
            userAgent = "${AppMetadata.APP_NAME}/${BuildConfig.VERSION_NAME}",
            nativeAssetsOnly = true,
        ),
        settings = updateSettings,
        logger = logger,
    )
    override fun close() {
        originalAiMasterEffects.close()
        originalSpeakerEffects.close()
        originalMoodEffects.close()
        originalSmartEffects.close()
        originalSoundEffects.close()
        smartTuning.close()
        equalizer.close()
        roomSession.disconnect()
        listenTogether.disconnect()
        appScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        playerScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        database.close()
        audioCache.close()
    }
}
