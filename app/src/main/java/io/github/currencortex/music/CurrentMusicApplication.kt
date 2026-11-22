package io.github.currencortex.music

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import coil3.ImageLoader
import okio.Path.Companion.toOkioPath
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.currencortex.music.core.logging.CrashLogger
import io.github.currencortex.music.ui.util.PredictiveBackOptIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

open class CurrentMusicApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set
    private val containerReady = kotlinx.coroutines.CompletableDeferred<AppContainer>()
    suspend fun awaitContainer(): AppContainer = containerReady.await()
    private val launchPending = java.util.concurrent.atomic.AtomicBoolean(true)
    fun consumeLaunchAnimation(): Boolean = launchPending.getAndSet(false)

    override fun onCreate() {
        super.onCreate()
        // The isolated script service has a separate UID and does not start app infrastructure.
        if (Process.myUid() != applicationInfo.uid) return
        container = createContainer()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val predictiveBack = try {
                runBlocking(Dispatchers.IO) { container.settings.snapshot().predictiveBack }
            } catch (error: IOException) {
                Log.w("CurrentMusic", "Cannot restore predictive back setting", error)
                container.settings.state.value.predictiveBack
            }
            PredictiveBackOptIn.apply(applicationInfo, predictiveBack)
        }
        containerReady.complete(container)
        CrashLogger.install(container.logger)
        container.logger.info("Application", "Application started")
    }

    /** Performance builds override dependency creation without touching the user's storage. */
    protected open fun createContainer(): AppContainer = AppContainer(this)

    override fun newImageLoader(context: Context): ImageLoader {
        // Match the API's bounded timeout for cold server responses and large GIFs.
        // This client has no account interceptor, so images never receive the Token.
        val images = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(75, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS)
            .addInterceptor(container.navidrome.client.mediaInterceptor()).addInterceptor(container.emby.client.mediaInterceptor()).build()
        // Cover art is worth caching across sessions, but it must stay bounded: the storage page
        // reports it as part of the data cache and clearing it must not touch the audio cache.
        val covers = coil3.disk.DiskCache.Builder()
            .directory(context.cacheDir.resolve("image_cache").toOkioPath())
            .maxSizeBytes(128L * 1024 * 1024)
            .build()
        return ImageLoader.Builder(context).components {
            add(OkHttpNetworkFetcherFactory(callFactory = { images }))
        }.diskCache(covers).build()
    }
}
