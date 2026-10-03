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

class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settingsStore = PreferenceDataStoreFactory.create(
        scope = appScope,
        produceFile = { context.preferencesDataStoreFile("app.preferences_pb") },
    )

    val updateTransfer = io.github.currencortex.music.core.update.UpdateTransfer(
        io.github.currencortex.music.core.update.UpdateDownloader(okhttp3.OkHttpClient(), java.io.File(context.cacheDir, "updates")),
        io.github.currencortex.music.core.update.AndroidUpdateInstaller(context.applicationContext), appScope)
    val logger = AppLogger(context)
    val musicSettings = MusicSettingsRepository(settingsStore, appScope)
    val accountRepository = AccountRepository(SecureTokenStore(context), appScope)
    val apiClient = ApiClient(
        server = { accountRepository.server.ifBlank { musicSettings.state.value.server } },
        token = { accountRepository.token }, onUnauthorized = accountRepository::expired,
        log = { logger.info("Network", it) },
    )
    val authRepository = AuthRepository(apiClient, accountRepository, "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        musicSettings::setAccount)
    val musicRepository = MusicRepository(apiClient)
    val database = Room.databaseBuilder(context.applicationContext, MusicDatabase::class.java, "music.db").build()
    val playbackQueue = PlaybackQueue()
    val playerController = PlayerController(context.applicationContext, playbackQueue, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    val ready = CompletableDeferred<Unit>()
    val sessionRestored = CompletableDeferred<Unit>()
    init {
        appScope.launch {
            try {
                val initial = musicSettings.snapshot()
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
            owner = AppMetadata.GITHUB_OWNER,
            repository = AppMetadata.GITHUB_REPO,
            installedVersion = BuildConfig.VERSION_NAME,
            userAgent = "${AppMetadata.APP_NAME}/${BuildConfig.VERSION_NAME}",
            nativeAssetsOnly = true,
        ),
        settings = updateSettings,
        logger = logger,
    )
}
