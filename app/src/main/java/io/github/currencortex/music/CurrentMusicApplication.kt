package io.github.currencortex.music

import android.app.Application
import io.github.currencortex.music.core.logging.CrashLogger

class CurrentMusicApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CrashLogger.install(container.logger)
        container.logger.info("Application", "Application started")
    }
}
