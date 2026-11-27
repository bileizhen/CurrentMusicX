package io.github.currencortex.music.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.Closeable
import java.io.File

/** Disk LRU cache for remote video profile banners (`SimpleCache`), safe across restarts. */
@OptIn(UnstableApi::class)
class BannerVideoCache(
    context: Context,
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : Closeable {
    init {
        require(maxBytes > 0L) { "Banner video cache capacity must be positive" }
    }

    private val appContext = context.applicationContext
    private val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
    private val dbProviderDelegate = lazy { StandaloneDatabaseProvider(appContext) }
    private val cacheDelegate = lazy {
        SimpleCache(directory, evictor, dbProviderDelegate.value)
    }
    private val cache: SimpleCache get() = cacheDelegate.value
    private val mutex = Mutex()

    fun mediaSourceFactory(httpClient: OkHttpClient): MediaSource.Factory {
        val upstream = OkHttpDataSource.Factory(httpClient)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DefaultMediaSourceFactory(cacheFactory)
    }

    fun isCached(url: String): Boolean {
        if (url.isBlank() || !cacheDelegate.isInitialized()) return false
        return runCatching {
            val contentLength = cache.getContentMetadata(url).let { meta ->
                androidx.media3.datasource.cache.ContentMetadata.getContentLength(meta)
            }
            val cachedBytes = cache.getCachedBytes(url, 0L, Long.MAX_VALUE)
            cachedBytes > 0L && (contentLength <= 0L || cachedBytes >= contentLength)
        }.getOrDefault(false)
    }

    suspend fun evict(url: String) {
        if (url.isBlank()) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (!cacheDelegate.isInitialized()) return@withContext
                runCatching { cache.removeResource(url) }
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (cacheDelegate.isInitialized()) {
                    runCatching {
                        val keys = cache.keys.toList()
                        keys.forEach { key -> runCatching { cache.removeResource(key) } }
                    }
                } else if (directory.exists()) {
                    directory.deleteRecursively()
                }
            }
        }
    }

    override fun close() {
        if (cacheDelegate.isInitialized()) {
            runCatching { cacheDelegate.value.release() }
        }
        if (dbProviderDelegate.isInitialized()) {
            runCatching { dbProviderDelegate.value.close() }
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 64L * 1024 * 1024
    }
}
