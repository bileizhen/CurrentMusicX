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
import androidx.room.Room
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AppContainer(context: Context, storageNamespace: String = "", externalPlayer: ExternalPlayer? = null,
    ttmlProvider: io.github.currencortex.music.feature.lyrics.data.LyricsProvider? = null) : java.io.Closeable {
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
        io.github.currencortex.music.core.update.AndroidUpdateInstaller(context.applicationContext), appScope)
    val logger = AppLogger(context)
    val musicSettings = MusicSettingsRepository(settingsStore, appScope)
    // Published by the service ExoPlayer. Zero means unavailable, never the output mix.
    internal val audioSessionId = kotlinx.coroutines.flow.MutableStateFlow(0)
    internal val audioTimelineRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val audioSettings = io.github.currencortex.music.data.settings.AudioSourceSettings(settingsStore,
        SecureTokenStore(context, ".leiz$storageSuffix"), appScope)
    val accountVault = EncryptedAccountVault.create(context, storageSuffix, settingsStore, appScope)
    val accountRepository = AccountRepository(SecureTokenStore(context, storageSuffix), appScope, accountVault)
    val apiClient = ApiClient(
        server = { accountRepository.server.ifBlank { musicSettings.state.value.server } },
        token = { accountRepository.token }, onUnauthorized = accountRepository::expired,
        log = { logger.info("Network", it) },
    )
    val authRepository = AuthRepository(apiClient, accountRepository, "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        persistServer = musicSettings::setServer, persistAccount = musicSettings::setAccount)
    val musicRepository = MusicRepository(apiClient, audioSettings::access)
    val songDownloads = io.github.currencortex.music.core.download.SongDownloadManager(context.applicationContext, storageSuffix)
    val musicStyles = io.github.currencortex.music.data.style.MusicStyleRepository(apiClient) {
        RequestSession(accountRepository.server.ifBlank { musicSettings.state.value.server }, null)
    }
    val neteaseSongActions = io.github.currencortex.music.data.song.NeteaseSongActionsRepository(apiClient) {
        RequestSession(accountRepository.server.ifBlank { musicSettings.state.value.server }, accountRepository.token)
    }
    val audioCache = AudioCache(context.applicationContext, java.io.File(context.cacheDir, "audio$storageSuffix"))
    val storage = io.github.currencortex.music.core.storage.StorageStore(context.applicationContext, audioCache)
    val audioSources = AudioSourceResolver(audioCache,
        { RequestSession(accountRepository.server, accountRepository.token) },
        currentProvider = { audioSettings.access().identity }, load = musicRepository::source)
    val libraryRepository = io.github.currencortex.music.data.library.LibraryRepository(apiClient,
        { accountRepository.state.value.account?.id ?: 0L }, { RequestSession(accountRepository.server, accountRepository.token) })
    val profileRepository = io.github.currencortex.music.data.profile.ProfileRepository(apiClient) { RequestSession(accountRepository.server, accountRepository.token) }
    val bindingRepository: io.github.currencortex.music.data.binding.BindingRepository = io.github.currencortex.music.data.binding.BindingRepository(apiClient,
        { RequestSession(accountRepository.server, accountRepository.token) }) { libraryRepository.invalidate(); neteaseLibrary.invalidate() }
    val neteaseLibrary = io.github.currencortex.music.data.library.NeteaseLibraryRepository(apiClient, bindingRepository,
        neteaseSongActions) { RequestSession(accountRepository.server, accountRepository.token) }
    val primaryLibrary = io.github.currencortex.music.data.library.PrimaryMusicLibrary(libraryRepository,
        neteaseLibrary, bindingRepository, musicSettings, appScope)
    val database = Room.databaseBuilder(context.applicationContext, MusicDatabase::class.java, "music$storageSuffix.db").build()
    // Isolated containers use only explicitly injected external providers, never live GitHub.
    val lyricsRepository = io.github.currencortex.music.feature.lyrics.data.LyricsRepository(
        ttmlProvider ?: if (storageNamespace.isEmpty()) io.github.currencortex.music.feature.lyrics.data.AmllLyricsProvider()
        else io.github.currencortex.music.feature.lyrics.data.LyricsProvider {
            io.github.currencortex.music.feature.lyrics.data.LyricsProviderResult.Failure(io.github.currencortex.music.feature.lyrics.model.LyricsErrorCode.NO_LYRICS, "Isolated external provider")
        },
        io.github.currencortex.music.feature.lyrics.data.CurrentMusicLyricsProvider(musicRepository, database.music()) { logger.debug("Lyrics", it) },
        io.github.currencortex.music.feature.lyrics.data.LyricsCache(java.io.File(context.cacheDir, "lyrics$storageSuffix")),
        { logger.debug("Lyrics", it) })
    val playbackQueue = PlaybackQueue()
    val playerController = PlayerController(context.applicationContext, playbackQueue, playerScope)
    val sleepTimer = io.github.currencortex.music.core.media.SleepTimer(playerController, playerScope)
    val roomRepository = io.github.currencortex.music.data.room.RoomRepository(apiClient) { RequestSession(accountRepository.server, accountRepository.token) }
    val roomSession = io.github.currencortex.music.core.room.RoomSession(roomRepository,
        io.github.currencortex.music.core.room.RoomSseClient(), externalPlayer ?: playerController, playerScope,
        { accountRepository.state.value.account?.id ?: 0 }, accountRepository::expired)
    private val dlnaSoap = io.github.currencortex.music.core.dlna.SoapClient(networkSocketFactory = {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        manager.allNetworks.firstOrNull { manager.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true }?.socketFactory
    })
    val dlnaDiscovery = io.github.currencortex.music.core.dlna.DlnaDiscovery(context, soap = dlnaSoap)
    val dlnaController = io.github.currencortex.music.core.dlna.DlnaController(externalPlayer ?: playerController, musicRepository,
        { RequestSession(accountRepository.server, accountRepository.token) }, playerScope, dlnaSoap)
    val ready = CompletableDeferred<Unit>()
    val sessionRestored = CompletableDeferred<Unit>()
    init {
        playerScope.launch {
            accountRepository.sessionRevision.collect {
                audioSources.invalidate(); roomSession.disconnect(); dlnaController.stop()
                bindingRepository.clearSession(); neteaseLibrary.invalidate(); neteaseSongActions.clearSession()
            }
        }
        appScope.launch { audioSettings.state.collect { audioSources.invalidate() } }
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
                accountRepository.server = initial.server
                if (initial.restoreQueue) database.music().queue()?.let {
                    playbackQueue.restore(ApiJson.decodeFromString<QueueSnapshot>(it.payload))
                }
                ready.complete(Unit)
                authRepository.restore(initial.server)
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
        roomSession.disconnect()
        appScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        playerScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        database.close()
        audioCache.close()
    }
}
