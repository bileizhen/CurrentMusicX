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
