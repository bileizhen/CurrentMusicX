package io.github.currencortex.music

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.currencortex.music.core.config.AppMetadata
import io.github.currencortex.music.core.logging.AppLogger
import io.github.currencortex.music.core.update.GitHubUpdateChecker
import io.github.currencortex.music.core.update.UpdateService
import io.github.currencortex.music.data.settings.SettingsRepository
import io.github.currencortex.music.data.update.UpdateSettingsRepository
import io.github.currencortex.music.data.settings.StorageSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.security.SecureTokenStore
import io.github.currencortex.music.data.auth.*
import io.github.currencortex.music.data.effects.NeteaseEffectsAssetRepository
import io.github.currencortex.music.data.effects.NeteaseEffectsInstallState
import io.github.currencortex.music.data.song.MusicRepository
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.ServerAudioSourceOwner
import io.github.currencortex.music.data.song.ServerAudioSources
import io.github.currencortex.music.data.local.MusicDatabase
import io.github.currencortex.music.data.settings.MusicSettingsRepository
import io.github.currencortex.music.data.settings.AudioProvider
import io.github.currencortex.music.data.settings.OriginalEffectPreferenceKeys
import io.github.currencortex.music.data.settings.AudioSourceAccess
import androidx.room.Room
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import io.github.currencortex.music.core.netease.*

class AppContainer(context: Context, storageNamespace: String = "", externalPlayer: ExternalPlayer? = null,
    ttmlProvider: io.github.currencortex.music.feature.lyrics.data.LyricsProvider? = null,
    useNativeNetease: Boolean = storageNamespace.isEmpty(),
    neteaseEndpoints: NeteaseEndpoints = NeteaseEndpoints(),
    serverCheck: ServerCheck = CurrentMusicServerProbe(),
    automaticLyricsProvider: (suspend (io.github.currencortex.music.data.song.Song) ->
        io.github.currencortex.music.feature.lyrics.data.SavedLyrics?)? = null,
    apiHttpClient: okhttp3.OkHttpClient? = null,
    releaseChecker: io.github.currencortex.music.core.update.ReleaseChecker? = null) : java.io.Closeable {
    private val storageSuffix = if (storageNamespace.isEmpty()) "" else ".$storageNamespace".also {
        require(storageNamespace.matches(Regex("[a-zA-Z0-9-]+")))
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val feedbackStore = io.github.currencortex.music.data.feedback.FeedbackSubmissionStore(context, storageSuffix)
    internal val feedbackMediaStore = io.github.currencortex.music.data.feedback.FeedbackMediaStore(context, storageSuffix)
    private val settingsStore = PreferenceDataStoreFactory.create(
        scope = appScope,
        produceFile = { context.preferencesDataStoreFile("app$storageSuffix.preferences_pb") },
    )
    val playbackNetworkStatus = preloadNetwork(context.applicationContext)
        .stateIn(appScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, PreloadNetworkStatus())

    val updateTransfer = io.github.currencortex.music.core.update.UpdateTransfer(
        io.github.currencortex.music.core.update.UpdateDownloader(okhttp3.OkHttpClient(), java.io.File(context.cacheDir, "updates"),
            urlFor = ::updateAssetUrl, cache = io.github.currencortex.music.core.update.UpdateArtifactCache(java.io.File(context.cacheDir, "updates"))),
        io.github.currencortex.music.core.update.AndroidUpdateInstaller(context.applicationContext), playerScope,
        if (storageNamespace.isEmpty()) io.github.currencortex.music.core.update.WorkManagerUpdateBackground(context) else null)
    val logger = AppLogger(context)
    val musicSettings = MusicSettingsRepository(settingsStore, appScope)
    internal val localMusic = io.github.currencortex.music.feature.library.LocalMusicRepository(
        java.io.File(context.filesDir, "local-music$storageSuffix.json"))
    val storageSettings = StorageSettingsRepository(settingsStore, appScope)
    val serverRouter = CurrentMusicServerRouter(serverCheck)
    fun updateAssetUrl(release: io.github.currencortex.music.core.update.AppRelease,
        source: io.github.currencortex.music.core.update.UpdateSource): String = source.url(release,
        io.github.currencortex.music.core.update.UpdateProxy.forServer(serverRouter.resolve(musicSettings.state.value.server)))
    val updateSourceCheck = io.github.currencortex.music.core.update.UpdateSourceProbe(urlFor = ::updateAssetUrl)

    val audioOutput = io.github.currencortex.music.data.settings.AudioOutputSettingsRepository(settingsStore, appScope)
    val audioDiagnostics = io.github.currencortex.music.core.media.engine.AudioDiagnostics()
    val audioDevices by lazy { io.github.currencortex.music.core.media.engine.AudioDeviceMonitor(context, appScope) }
    val audioOutputRetry = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
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
    val adaptiveAudio = io.github.currencortex.music.data.settings.AdaptiveAudioSettingsRepository(settingsStore, appScope)
    val adaptiveAudioRuntime = kotlinx.coroutines.flow.MutableStateFlow(io.github.currencortex.music.core.media.AdaptiveAudioRuntime())
    val musicHapticsSupported by lazy {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        vibrator?.hasVibrator() == true
    }
    val musicHapticsAmplitudeControl by lazy {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        vibrator?.hasAmplitudeControl() == true
    }
    val originalEffectsSettings = io.github.currencortex.music.data.settings.OriginalSoundEffectsSettingsRepository(settingsStore, appScope)
    val originalSmartSettings = io.github.currencortex.music.data.settings.OriginalSmartSettingsRepository(settingsStore, appScope)
    val originalMoodSettings = io.github.currencortex.music.data.settings.OriginalMoodSettingsRepository(settingsStore, appScope)
    val originalAiMasterSettings = io.github.currencortex.music.data.settings.OriginalAiMasterSettingsRepository(settingsStore, appScope)
    private val originalEngineContext = context.applicationContext
    private val originalEngineHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .followSslRedirects(false)
        .build()
    val originalEffectsAssets = NeteaseEffectsAssetRepository(originalEngineContext, originalEngineHttpClient)
    init { NeteaseNativeEngine.configure(originalEngineContext) }
    private val effectsInstall = MutableStateFlow(NeteaseEffectsInstallState())
    internal val originalEffectsInstall = effectsInstall.asStateFlow()

    fun importOriginalApk(uri: android.net.Uri) = installOriginal("正在校验音效引擎…") {
        NeteaseNativeEngine.installFromApk(originalEngineContext, uri)
    }

    fun importInstalledOriginal() = installOriginal("正在校验音效引擎…") {
        NeteaseNativeEngine.installFromInstalled(originalEngineContext)
    }

    fun downloadOriginalEffects() = installOriginal("正在准备下载…") {
        check("arm64-v8a" in android.os.Build.SUPPORTED_ABIS) { "此设备暂不支持网易云音效" }
        val source = updateSettings.snapshot().effectiveDownloadSource
        val engineBytes = if (originalEffectsEngineReady.value) 0L else NeteaseEngineLibraries.DOWNLOAD_SIZE
        val assetBytes = if (originalEffectsAssets.state.value.available) 0L else NeteaseEffectsAssetRepository.PACKAGE_SIZE
        val total = engineBytes + assetBytes
        fun progress(message: String, received: Long) {
            effectsInstall.value = NeteaseEffectsInstallState(loading = true, message = message,
                progress = (received * 100 / total).toInt().coerceIn(0, 100))
        }
        if (engineBytes > 0) {
            progress("正在下载音效引擎…", 0)
            NeteaseNativeEngine.installFromRepository(originalEngineContext, originalEngineHttpClient, source) { received, _ ->
                progress("正在下载音效引擎…", received)
            }
        }
        if (assetBytes > 0) {
            progress("正在下载界面素材…", engineBytes)
            originalEffectsAssets.install(source) { received, assetTotal ->
                progress(if (received == assetTotal) "正在校验并解压素材…" else "正在下载界面素材…",
                    engineBytes + received)
            }
        }
    }

    fun removeOriginalEffects() = installOriginal("正在移除音效包…", removing = true) {
        effectsReady.value = false
        originalSoundEffects.clear()
        originalSmartEffects.disable()
        disableOriginalMood()
        disableOriginalAdditional()
        settingsStore.edit { preferences ->
            OriginalEffectPreferenceKeys.clearSingle(preferences)
            OriginalEffectPreferenceKeys.clearSmart(preferences)
            OriginalEffectPreferenceKeys.clearMood(preferences)
            OriginalEffectPreferenceKeys.clearMaster(preferences)
        }
        try {
            originalEffectsAssets.remove()
        } finally {
            NeteaseEngineLibraries.remove(originalEngineContext)
        }
    }

    private fun installOriginal(message: String, removing: Boolean = false, action: suspend () -> Unit) {
        if (effectsInstall.value.loading) return
        effectsInstall.value = NeteaseEffectsInstallState(loading = true, message = message, removing = removing)
        playerScope.launch {
            var result = NeteaseEffectsInstallState()
            try {
                action()
                if (removing) result = NeteaseEffectsInstallState(message = "网易云音效包已移除")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val fallback = if (removing) "移除失败，请重试" else "安装失败，请重试"
                result = NeteaseEffectsInstallState(message = failure.message?.take(160) ?: fallback, failed = true)
            } finally {
                refreshOriginalEngine()
                effectsInstall.value = result
            }
        }
    }
    val accountVault = EncryptedAccountVault.create(context, storageSuffix, settingsStore, appScope)
    val accountRepository = AccountRepository(SecureTokenStore(context, storageSuffix), appScope, accountVault)
    val audioSettings = io.github.currencortex.music.data.settings.AudioSourceSettings(settingsStore,
        SecureTokenStore(context, ".leiz$storageSuffix"), appScope, ::serverAudioSourceOwner)
    val lxSources = io.github.currencortex.music.data.lx.LxSourceStore(context,
        SecureTokenStore(context, ".lx-sources$storageSuffix"), appScope)
    val neteaseSessions = NeteaseSessionStore(SecureTokenStore(context, ".netease$storageSuffix"), appScope)
    private val neteaseTransport = NeteaseTransport(neteaseSessions, neteaseEndpoints,
        log = { logger.info("Network", it) })
    val nativeNetease = if (useNativeNetease) NeteaseGateway(neteaseTransport) else null
    val neteaseTasks = io.github.currencortex.music.data.netease.tasks.NeteaseTaskRepository(neteaseTransport)
    internal val neteaseListening = io.github.currencortex.music.data.netease.listening.NeteaseListeningRepository(neteaseTransport)
    val playlistImporter = io.github.currencortex.music.data.netease.playlistimport.NeteasePlaylistImportRepository(neteaseTransport)
    val neteaseTaskPreferences = io.github.currencortex.music.data.netease.tasks.NeteaseTaskPreferences(settingsStore)
    val neteaseTasksEnabled = neteaseTaskPreferences.enabled.stateIn(appScope, SharingStarted.Eagerly, null)
    val neteaseTaskAutomation = io.github.currencortex.music.data.netease.tasks.NeteaseTaskAutomation(neteaseTasks,
        neteaseTaskPreferences, appForeground, appScope, enabled = neteaseTaskPreferences.enabled)
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
    /** Community features belong to the project account, independently of the audio provider. */
    fun projectSession() = RequestSession(
        accountRepository.server.ifBlank { musicSettings.state.value.server }, accountRepository.token)
    internal fun serverAudioSourceOwner(): ServerAudioSourceOwner? {
        val account = accountRepository.state.value.account ?: return null
        val session = projectSession()
        return if (session.token.isNullOrBlank()) null else ServerAudioSourceOwner(session.server, account.id)
    }
    private fun directNeteaseSession() =
        RequestSession("https://music.163.com/", null, neteaseSessions.state.value.revision)
    fun audioSession() = if (audioSettings.access().provider == AudioProvider.NETEASE)
        directNeteaseSession()
        else RequestSession(accountRepository.server.ifBlank { musicSettings.state.value.server }, accountRepository.token)
    val apiClient = ApiClient(
        server = { accountRepository.server.ifBlank { musicSettings.state.value.server } },
        token = { accountRepository.token }, onUnauthorized = accountRepository::expired,
        log = { logger.info("Network", it) },
        client = apiHttpClient ?: ApiClient.defaultHttpClient(),
        netease = nativeNetease,
        nativeMedia = {
            val session = neteaseSessions.state.value
            val provider = audioSettings.access().provider
            (provider == AudioProvider.NETEASE || provider == AudioProvider.SERVER) && session.loggedIn && !session.stale
        },
        currentMusicAccountReady = { accountRepository.state.value.account != null && !accountRepository.token.isNullOrBlank() },
        currentMusicSessionRevision = { accountRepository.sessionRevision.value },
        routeServer = serverRouter::resolve,
    )
    val authRepository = AuthRepository(apiClient, accountRepository, "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        persistServer = musicSettings::setServer, persistAccount = musicSettings::setAccount)
    val navidrome = io.github.currencortex.music.data.navidrome.NavidromeRepository(SecureTokenStore(context, ".navidrome$storageSuffix"), appScope)
    val emby = io.github.currencortex.music.data.emby.EmbyRepository(SecureTokenStore(context, ".emby$storageSuffix"), appScope)
    val navidromeMedia = okhttp3.OkHttpClient.Builder().addInterceptor(navidrome.client.mediaInterceptor()).addInterceptor(emby.client.mediaInterceptor()).build()
    val qqMusic = io.github.currencortex.music.data.qqmusic.QqMusicRepository(apiClient, ::projectSession,
        SecureTokenStore(context, ".qqmusic$storageSuffix"),
        io.github.currencortex.music.data.qqmusic.QqDirectMusicClient(
            apiHttpClient ?: ApiClient.defaultHttpClient(),
            log = { logger.info("Network", it) },
        ))
    val serverAudioSources = ServerAudioSources(apiClient)
    val musicRepository = MusicRepository(apiClient, audioSettings::access, navidrome = navidrome,
        lx = lxSources, emby = emby, qqMusic = qqMusic, serverAudioSources = serverAudioSources)
    internal fun qqAudioIdentity(access: AudioSourceAccess = audioSettings.access()): String =
        qqAudioIdentity(qqMusic.audioIdentity, access)

    private fun qqAudioIdentity(accountIdentity: String, access: AudioSourceAccess): String =
        if (access.provider == AudioProvider.LX) "$accountIdentity:${access.identity}" else accountIdentity

    fun audioRequest(song: io.github.currencortex.music.data.song.Song, quality: AudioQuality, supportsDolby: Boolean = false): AudioRequest {
        if (song.musicSource == io.github.currencortex.music.data.song.MusicSource.QQ_MUSIC) {
            qqMusic.selectOwner()
            return AudioRequest(song.id, quality, accountRepository.state.value.account?.id ?: 0L,
                qqMusic.session(), qqAudioIdentity(), song)
        }
        if (song.musicSource == io.github.currencortex.music.data.song.MusicSource.NAVIDROME)
            return AudioRequest(song.id, quality, 0L, navidrome.session(), "navidrome:" + navidrome.state.value.identity, song)
        if (song.musicSource == io.github.currencortex.music.data.song.MusicSource.EMBY)
            return AudioRequest(song.id, quality, 0L, emby.session(), "emby:" + emby.state.value.identity, song)
        if (song.neteaseCloud) {
            val owner = neteaseSessions.state.value
            return AudioRequest(song.id, quality, owner.profile?.uid ?: 0L,
                RequestSession("https://music.163.com/", null, owner.revision), "netease-cloud", song,
                supportsDolby = supportsDolby && quality == AudioQuality.AUTO)
        }
        val access = audioSettings.access()
        val directNetease = access.provider == AudioProvider.NETEASE
        val preferDolby = supportsDolby && quality == AudioQuality.AUTO &&
            song.musicSource != io.github.currencortex.music.data.song.MusicSource.LOCAL &&
            (directNetease || access.provider == AudioProvider.SERVER)
        if (directNetease) {
            // Snapshot UID and revision together so a concurrent account switch cannot mix cache owners.
            val owner = neteaseSessions.state.value
            return AudioRequest(song.id, quality, owner.profile?.uid ?: 0L,
                RequestSession("https://music.163.com/", null, owner.revision),
                AudioSourceAccess(AudioProvider.NETEASE).identity, song, supportsDolby = preferDolby)
        }
        return AudioRequest(song.id, quality, accountRepository.state.value.account?.id ?: 0L, audioSession(), access.identity, song,
            supportsDolby = preferDolby)
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
    val audioCache = AudioCache(context.applicationContext, java.io.File(context.cacheDir, "audio$storageSuffix"), navidromeMedia,
        initialMaxBytes = AudioCache.UNLIMITED_BYTES, initialCapacityReady = false)
    val storage = io.github.currencortex.music.core.storage.StorageStore(context.applicationContext, audioCache)
    val audioSources = AudioSourceResolver(audioCache,
        { audioSession() },
        currentProvider = { audioSettings.access().identity },
        validateRequest = { request -> request.song?.takeIf { it.musicSource == io.github.currencortex.music.data.song.MusicSource.QQ_MUSIC }?.let {
            qqMusic.selectOwner()
            if (request.session != qqMusic.session() ||
                request.accountId != (accountRepository.state.value.account?.id ?: 0L)) false
            else {
                val accountIdentity = qqMusic.audioIdentity
                if (request.providerIdentity.substringBefore(":lx:") != accountIdentity) false
                else {
                    if (request.providerIdentity != qqAudioIdentity(accountIdentity, audioSettings.access()))
                        throw ApiException(ErrorKind.AudioSourceChanged)
                    true
                }
            }
        } ?: request.song?.takeIf { it.musicSource == io.github.currencortex.music.data.song.MusicSource.NAVIDROME }?.let {
            navidrome.accepts(it) && request.session == navidrome.session() && request.providerIdentity == "navidrome:" + navidrome.state.value.identity
        } ?: request.song?.takeIf { it.musicSource == io.github.currencortex.music.data.song.MusicSource.EMBY }?.let {
            emby.accepts(it) && request.session == emby.session() && request.providerIdentity == "emby:" + emby.state.value.identity
        } ?: request.song?.takeIf { it.neteaseCloud }?.let {
            val owner = neteaseSessions.state.value
            owner.loggedIn && !owner.stale && request.accountId == owner.profile?.uid &&
                request.session == RequestSession("https://music.163.com/", null, owner.revision) &&
                request.providerIdentity == "netease-cloud"
        } },
        loadTrack = { request -> request.song?.let {
            musicRepository.source(it, request.quality, request.session, request.refresh, request.supportsDolby)
        } ?: musicRepository.source(request.songId, request.quality, request.session, request.refresh, request.supportsDolby) },
        load = { id, quality, session -> musicRepository.source(id, quality, session) })
    val libraryRepository = io.github.currencortex.music.data.library.LibraryRepository(apiClient,
        { accountRepository.state.value.account?.id ?: 0L }, { musicSession() })
    val todayListening = TodayListeningStore(context.applicationContext, storageSuffix)
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
    private val neteaseAvatarImporter by lazy {
        val image = io.github.currencortex.music.data.profile.RemoteAvatarImage()
        io.github.currencortex.music.data.profile.NeteaseAvatarImporter(accountRepository, authRepository,
            profileRepository, bindingRepository, { musicSession() }, image::download)
    }
    fun importNeteaseAvatarAfterLogin(expected: RequestSession) {
        appScope.launch {
            when (val result = appResult { neteaseAvatarImporter.importAfterLogin(expected) }) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> logger.warn("Profile", "NetEase avatar import failed: ${result.kind}")
            }
        }
    }
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
    val songLyrics = io.github.currencortex.music.feature.lyrics.data.SongLyricsStore(
        java.io.File(context.filesDir, "song-lyrics$storageSuffix"))
    private val qqLyricsMatching = io.github.currencortex.music.feature.lyrics.data.QqLyricsMatchSource(
        log = { logger.debug("Lyrics", it) })
    private val qishuiLyricsMatching = io.github.currencortex.music.feature.lyrics.data.QishuiLyricsMatchSource()
    val onlineLyrics = io.github.currencortex.music.feature.lyrics.data.OnlineLyricsMatcher(
        searchNetease = { query -> when (val result = musicRepository.searchForLyricsMatching(query,
            io.github.currencortex.music.data.settings.LyricsSource.NETEASE)) {
            is io.github.currencortex.music.core.network.AppResult.Success -> result.value.songs
            is io.github.currencortex.music.core.network.AppResult.Failure -> throw io.github.currencortex.music.feature.lyrics.data.LyricsMatchException(result.message)
        } },
        loadNetease = { id -> when (val result = musicRepository.lyricsForMatching(id,
            io.github.currencortex.music.data.settings.LyricsSource.NETEASE)) {
            is io.github.currencortex.music.core.network.AppResult.Success -> result.value
            is io.github.currencortex.music.core.network.AppResult.Failure -> throw io.github.currencortex.music.feature.lyrics.data.LyricsMatchException(result.message)
        } },
        searchCurrentMusic = { query -> when (val result = musicRepository.searchForLyricsMatching(query,
            io.github.currencortex.music.data.settings.LyricsSource.CURRENT_MUSIC)) {
            is io.github.currencortex.music.core.network.AppResult.Success -> result.value.songs
            is io.github.currencortex.music.core.network.AppResult.Failure -> throw io.github.currencortex.music.feature.lyrics.data.LyricsMatchException(result.message)
        } },
        loadCurrentMusic = { id -> when (val result = musicRepository.lyricsForMatching(id,
            io.github.currencortex.music.data.settings.LyricsSource.CURRENT_MUSIC)) {
            is io.github.currencortex.music.core.network.AppResult.Success -> result.value
            is io.github.currencortex.music.core.network.AppResult.Failure -> throw io.github.currencortex.music.feature.lyrics.data.LyricsMatchException(result.message)
        } },
        additionalSources = mapOf(
            io.github.currencortex.music.feature.lyrics.data.LyricsMatchSource.QQ_MUSIC to
                io.github.currencortex.music.feature.lyrics.data.LyricsMatchAdapter(qqLyricsMatching::search, qqLyricsMatching::load),
            io.github.currencortex.music.feature.lyrics.data.LyricsMatchSource.QISHUI to
                io.github.currencortex.music.feature.lyrics.data.LyricsMatchAdapter(qishuiLyricsMatching::search, qishuiLyricsMatching::load)))
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
                    when (source) {
                        io.github.currencortex.music.data.settings.LyricsSource.LOCAL ->
                            io.github.currencortex.music.feature.lyrics.data.LyricsProvider {
                                io.github.currencortex.music.feature.lyrics.data.LyricsProviderResult.Failure(
                                    io.github.currencortex.music.feature.lyrics.model.LyricsErrorCode.NO_LYRICS, "没有本地内嵌歌词，可在歌词管理中导入歌词文件")
                            }
                        io.github.currencortex.music.data.settings.LyricsSource.CURRENT_MUSIC -> projectLyricsProvider
                        else -> nativeLyricsProvider
                    },
                    io.github.currencortex.music.feature.lyrics.data.LyricsOrigin(source.label, source.name.lowercase()),
                    automatic = source == io.github.currencortex.music.data.settings.LyricsSource.AUTO)
            }
        }, overrides = songLyrics, autoMatch = if (automaticLyricsProvider != null || (storageNamespace.isEmpty() && ttmlProvider == null)) {
            { song ->
                when {
                    musicSettings.state.value.lyricsSource != io.github.currencortex.music.data.settings.LyricsSource.AUTO -> null
                    automaticLyricsProvider != null -> automaticLyricsProvider(song)
                    else -> onlineLyrics.bestMatch(song)
                }
            }
        } else null, alignMatch = if (storageNamespace.isEmpty() && ttmlProvider == null) onlineLyrics::align else null,
        localLyrics = io.github.currencortex.music.feature.lyrics.data.LocalLyricsProvider(context)::load)
    val playbackQueue = PlaybackQueue()
    val dockAudioLevel = DockAudioLevel()
    val playerController = PlayerController(context.applicationContext, playbackQueue, playerScope)
    val personalRoaming = io.github.currencortex.music.feature.roaming.PersonalRoamingController(
        io.github.currencortex.music.feature.roaming.PersonalRoamingRepositoryAdapter(
            io.github.currencortex.music.data.roaming.PersonalRoamingRepository(neteaseTransport)),
        io.github.currencortex.music.feature.roaming.NeteaseRoamingSessionStore(neteaseSessions), playerController,
        io.github.currencortex.music.data.roaming.RoamingPreferences(settingsStore), playerScope,
        audioSettings.state.map { audioSettings.access().identity }.distinctUntilChanged()
            .stateIn(playerScope, SharingStarted.Eagerly, audioSettings.access().identity))
    val qqMusicPersonalRoaming = io.github.currencortex.music.feature.roaming.PersonalRoamingController(
        io.github.currencortex.music.data.roaming.QqMusicRoamingRepository(qqMusic),
        io.github.currencortex.music.feature.roaming.QqMusicRoamingSessionStore(qqMusic.state), playerController,
        io.github.currencortex.music.data.roaming.RoamingPreferences(settingsStore), playerScope,
        musicSettings.state.map { it.musicPlatform.name }.distinctUntilChanged()
            .stateIn(playerScope, SharingStarted.Eagerly, musicSettings.state.value.musicPlatform.name))
    val activePersonalRoaming
        get() = if (playerController.state.value.song?.musicSource == io.github.currencortex.music.data.song.MusicSource.QQ_MUSIC ||
            musicSettings.state.value.musicPlatform == io.github.currencortex.music.data.settings.MusicPlatform.QQ_MUSIC)
            qqMusicPersonalRoaming else personalRoaming
    /** True when the station the roaming UI is bound to is QQ Music. */
    val activePersonalRoamingIsQq: Boolean get() = activePersonalRoaming === qqMusicPersonalRoaming
    init {
        // One shared dispatcher: both stations publish boundaries on the same player.
        playerController.onRadioBoundary = {
            val session = playbackQueue.state.value.radioSession
            val owner = listOf(personalRoaming, qqMusicPersonalRoaming).firstOrNull { it.ownsRadio(session) }
            owner?.awaitNext()
        }
    }
    val smartTuning = SmartTuningController(equalizerSettings.state, soundEffectsSettings.state,
        playerController.state.map { it.song }, neteaseSoundEffects::analyze, playerScope)
    private val engineReady = MutableStateFlow(NeteaseNativeEngine.available() && NeteaseEngineLibraries.moodAvailable())
    internal val originalEffectsEngineReady = engineReady.asStateFlow()
    private val effectsReady = MutableStateFlow(engineReady.value && originalEffectsAssets.state.value.available)
    internal val originalEffectsReady = effectsReady.asStateFlow()
    val originalSoundEffects: OriginalSoundEffectsController = OriginalSoundEffectsController(neteaseSoundEffects,
        neteaseSessions, originalEffectsSettings, playerScope, engineAvailable = { effectsReady.value }) {
        disableOriginalSmart()
        disableOriginalMood()
        disableOriginalAdditional()
        soundEffectsSettings.setSmartEnabled(false)
        smartTuning.clearManualPreview()
    }
    val originalSmartEffects: OriginalSmartEffectsController = OriginalSmartEffectsController(neteaseSoundEffects, neteaseSessions,
        originalSmartSettings, playerController.state.map { it.song }, originalEffectsReady, playerScope,
        beforeEnable = {
            originalSoundEffects.clear()
            disableOriginalMood()
            disableOriginalAdditional()
            soundEffectsSettings.setSmartEnabled(false)
            smartTuning.clearManualPreview()
        }, definition = NeteaseNativeEngine::smartDefinition)
    private fun disableOriginalSmart() = originalSmartEffects.disable()
    private fun disableOriginalMood() = originalMoodEffects.disable()
    val originalMoodEffects: OriginalMoodEffectsController = OriginalMoodEffectsController(originalMoodModels, neteaseSessions,
        originalMoodSettings, playerController.state.map { it.song }, originalEffectsReady, playerScope,
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
        originalEffectsReady, playerScope, beforeEnable = {
            originalSoundEffects.clear()
            originalSmartEffects.disable()
            originalMoodEffects.disable()
            originalSpeakerEffects.disable()
            soundEffectsSettings.setSmartEnabled(false)
            smartTuning.clearManualPreview()
        }, definition = { originalEngineContext.assets.open("netease-ai-master-codec.js").use { it.readBytes() } })
    val originalSpeakerEffects: OriginalSpeakerEffectsController = OriginalSpeakerEffectsController(
        originalSpeakerRepository, neteaseSessions, OriginalSpeakerEnvironmentObserver(originalEngineContext).state,
        originalEffectsReady, playerScope, beforeEnable = {
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
    }.combine(originalEffectsReady) { preset, ready -> preset.takeIf { ready } }
        .stateIn(playerScope, SharingStarted.Eagerly, null)
    internal fun createEffectsAudition(scope: CoroutineScope) = EffectsAuditionController(originalEngineContext, this, scope)
    fun refreshOriginalEngine() {
        engineReady.value = NeteaseNativeEngine.available() && NeteaseEngineLibraries.moodAvailable()
        effectsReady.value = engineReady.value && originalEffectsAssets.state.value.available
        originalSoundEffects.refreshAvailability()
        originalSmartEffects.refresh()
        originalMoodEffects.refresh()
        originalAiMasterEffects.refresh()
        originalSpeakerEffects.refresh()
    }
    val playbackLyrics = io.github.currencortex.music.feature.lyrics.data.PlaybackLyrics(
        io.github.currencortex.music.feature.lyrics.data.managedLyricsRequests(
        kotlinx.coroutines.flow.combine(playbackQueue.state.map { it.current }.distinctUntilChanged(),
            accountRepository.sessionRevision, neteaseSessions.state.map { it.revision }.distinctUntilChanged(),
            navidrome.state.map { it.revision }.distinctUntilChanged(),
            musicSettings.state.map { it.server to it.lyricsSource }.distinctUntilChanged()) { song, account, netease, navidrome, lyricsSettings ->
            io.github.currencortex.music.feature.lyrics.data.LyricsRequest(song, listOf(account, netease, navidrome, lyricsSettings))
        }, songLyrics.state), lyricsRepository::load, playerScope, songLyrics.state)
    val sleepTimer = io.github.currencortex.music.core.media.SleepTimer(playerController, playerScope)
    val roomRepository = io.github.currencortex.music.data.room.RoomRepository(apiClient) { RequestSession(accountRepository.server, accountRepository.token) }
    val roomSession = io.github.currencortex.music.core.room.RoomSession(roomRepository,
        io.github.currencortex.music.core.room.RoomSseClient(routeServer = serverRouter::resolve), externalPlayer ?: playerController, playerScope,
        { accountRepository.state.value.account?.id ?: 0 }, accountRepository::expired)
    internal val listenTogetherHistory = io.github.currencortex.music.data.netease.listen.NeteaseListenTogetherHistoryRepository(neteaseTransport)
    internal val listenTogetherFate = io.github.currencortex.music.data.netease.listen.NeteaseListenTogetherFateRepository(neteaseTransport)
    val listenTogether = io.github.currencortex.music.core.netease.listen.NeteaseListenTogetherSession(
        io.github.currencortex.music.data.netease.listen.NeteaseListenTogetherRepository(neteaseTransport,
            trace = { if (BuildConfig.DEBUG) android.util.Log.d("ListenTogetherSync", it) }),
        externalPlayer ?: playerController, playerScope, musicRepository::detail,
        { song -> musicRepository.source(song, musicSettings.state.value
            .qualityFor(song.musicSource, playbackNetworkStatus.value.kind), audioSession()).url },
        details = musicRepository::details,
        trace = { if (BuildConfig.DEBUG) android.util.Log.d("ListenTogetherSync", it) })
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
        playerScope.launch {
            var previous = emby.state.value.revision
            emby.state.collect { account ->
                if (previous != account.revision) {
                    audioSources.invalidate()
                    if (playbackQueue.state.value.current?.musicSource == io.github.currencortex.music.data.song.MusicSource.EMBY) playerController.pause()
                    previous = account.revision
                }
            }
        }
        if (nativeNetease != null) appScope.launch {
            sessionRestored.await()
            neteaseSessions.ready.await()
            combine(accountRepository.state, accountRepository.sessionRevision, musicSettings.state, neteaseSessions.state) {
                account, _, _, local ->
                if (account.account != null && !accountRepository.token.isNullOrBlank() && local.loggedIn && !local.stale)
                    musicSession() else null
            }.distinctUntilChanged().collectLatest { owner ->
                if (owner != null) bindingRepository.autoSyncProjectBinding(owner)
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
                qqMusic.selectOwner()
                audioSources.invalidate(); roomSession.disconnect(); listenTogether.disconnect()
                bindingRepository.clearSession(); neteaseLibrary.invalidate(); neteaseSongActions.clearSession()
            }
        }
        playerScope.launch {
            sessionRestored.await()
            var identity = qqMusic.audioIdentity
            qqMusic.state.collect {
                val next = qqMusic.audioIdentity
                if (next != identity) {
                    identity = next
                    audioSources.invalidate()
                    if (playbackQueue.state.value.current?.musicSource == io.github.currencortex.music.data.song.MusicSource.QQ_MUSIC)
                        playerController.pause()
                }
            }
        }
        appScope.launch { audioSettings.state.collect { audioSources.invalidate() } }
        playerScope.launch {
            sessionRestored.await()
            accountRepository.sessionRevision.collect {
                val track = playbackQueue.state.value.current
                if (audioSettings.hasDetachedServerSource() &&
                    track?.let { it.musicSource == MusicSource.NETEASE && !it.neteaseCloud && !it.video } == true) {
                    appResult { playerController.discardLoadedLocalAudio() }
                }
                appResult { audioSettings.refreshServerSourceOwner() }
            }
        }
        appScope.launch {
            storageSettings.limitBytes.collect { bytes ->
                try { audioCache.setMaxBytes(bytes) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { logger.warn("Storage", "Audio cache capacity update failed", e) }
            }
        }
        appScope.launch {
            bindingRepository.state.map { it?.profile?.uid to it?.bound }.distinctUntilChanged().collect {
                neteaseSongActions.clearSession(); neteaseLibrary.invalidate()
            }
        }
        appScope.launch {
            kotlinx.coroutines.flow.combine(accountRepository.state, musicSettings.state) { account, preferences ->
                account.account?.id to preferences.server
            }.distinctUntilChanged().collect {
                qqMusic.selectOwner()
                libraryRepository.clearSession(); bindingRepository.clearSession()
            }
        }
        appScope.launch {
            try {
                val initial = musicSettings.snapshot()
                audioSettings.ready.await()
                navidrome.ready.await()
                emby.ready.await()
                if (nativeNetease != null) neteaseSessions.ready.await()
                accountRepository.server = initial.server
                serverRouter.configure(initial.serverMode, initial.server)
                appScope.launch {
                    musicSettings.serverSelection.collectLatest { (mode, server) ->
                        serverRouter.configure(mode, server)
                        if (mode == CurrentMusicServer.AUTO) serverRouter.refresh()
                    }
                }
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
                accountRepository.state.value = AccountState(error = "CurrentMusic 本地会话恢复失败，请重新登录")
            } finally {
                sessionRestored.complete(Unit)
            }
        }
    }
    val settings = SettingsRepository(settingsStore, appScope)
    val playerBackgrounds = io.github.currencortex.music.data.settings.PlayerBackgroundStore(context, settings, storageSuffix)
    val updateSettings = UpdateSettingsRepository(settingsStore, appScope)
    val updates = UpdateService(
        checker = releaseChecker ?: GitHubUpdateChecker(
            owner = AppMetadata.RELEASES_OWNER,
            repository = AppMetadata.RELEASES_REPO,
            installedVersion = BuildConfig.VERSION_NAME,
            userAgent = "${AppMetadata.APP_NAME}/${BuildConfig.VERSION_NAME}",
            nativeAssetsOnly = true,
        ),
        settings = updateSettings,
        logger = logger,
        bundledRelease = {
            val json = try {
                context.assets.open("installed-release-notes.json").bufferedReader().use { it.readText() }
            } catch (_: java.io.FileNotFoundException) { "" }
            io.github.currencortex.music.core.update.parseBundledReleaseNotes(json, BuildConfig.VERSION_NAME)
        },
    )
    override fun close() {
        todayListening.close()
        lxSources.close()
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
