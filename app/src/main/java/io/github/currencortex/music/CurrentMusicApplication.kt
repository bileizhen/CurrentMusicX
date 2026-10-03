package io.github.currencortex.music

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.currencortex.music.core.logging.CrashLogger
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CurrentMusicApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CrashLogger.install(container.logger)
        container.logger.info("Application", "Application started")
    }

    override fun newImageLoader(context: Context): ImageLoader {
        // Match the API's bounded timeout for cold server responses and large GIFs.
        // This client has no account interceptor, so images never receive the Token.
        val images = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(75, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
        return ImageLoader.Builder(context).components {
            add(OkHttpNetworkFetcherFactory(callFactory = { images }))
        }.build()
    }
}
