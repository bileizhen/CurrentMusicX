package io.github.currencortex.music.core.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.*
import androidx.media3.datasource.okhttp.OkHttpDataSource
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.song.AudioSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** One process owner, private disk storage, and no account headers on CDN requests. */
@androidx.annotation.OptIn(UnstableApi::class)
class AudioCache(context: Context, directory: File, maxBytes: Long = MAX_BYTES,
    client: OkHttpClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()) : java.io.Closeable {
    private val cacheDelegate = lazy { SimpleCache(directory, LeastRecentlyUsedCacheEvictor(maxBytes), StandaloneDatabaseProvider(context.applicationContext)) }
    private val cache get() = cacheDelegate.value
    private val upstream = OkHttpDataSource.Factory(client)
    private val writers = ConcurrentHashMap.newKeySet<CacheWriter>()
    private val writeLock = Mutex()
    val bytes = MutableStateFlow(0L)
    val clearing = MutableStateFlow(false)
    private fun cachedFactory(block: Boolean = false) = CacheDataSource.Factory().setCache(cache)
        .setUpstreamDataSourceFactory(upstream).setFlags(if (block) CacheDataSource.FLAG_BLOCK_ON_CACHE else CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    // MV and room URLs have no custom key and bypass the audio cache entirely.
    val dataSourceFactory = DataSource.Factory { object : DataSource {
        private var delegate: DataSource? = null
        private var activeKey: String? = null
        private val listeners = mutableListOf<TransferListener>()
        override fun addTransferListener(listener: TransferListener) { listeners += listener; delegate?.addTransferListener(listener) }
        override fun open(dataSpec: DataSpec): Long {
            activeKey = dataSpec.key?.takeIf { it.startsWith(PREFIX) }
            val source = if (dataSpec.key?.startsWith(PREFIX) == true)
                runCatching { cachedFactory().createDataSource() }.getOrElse { upstream.createDataSource() }
                else upstream.createDataSource()
            delegate = source; listeners.forEach(source::addTransferListener)
            return try { source.open(dataSpec) } catch (e: Cache.CacheException) {
                runCatching { source.close() }
                val fallback = upstream.createDataSource()
                delegate = fallback; listeners.forEach(fallback::addTransferListener)
                fallback.open(dataSpec)
            }
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int) = checkNotNull(delegate).read(buffer, offset, length)
        override fun getUri(): Uri? = delegate?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()
        override fun close() { try { delegate?.close() } finally {
            delegate = null
            activeKey?.let { runCatching { forgetRedirect(it) } }; activeKey = null
            runCatching { refreshUsage() }
        } }
    } }

    fun refreshUsage() { bytes.value = cache.cacheSpace }
    fun cachedSource(key: String): AudioSource? = runCatching {
        cache.getContentMetadata(key).get(SOURCE, "")?.takeIf { it.isNotBlank() }?.let { ApiJson.decodeFromString<AudioSource>(it) }
    }.getOrNull()
    fun rememberSource(key: String, source: AudioSource) {
        val old = cachedSource(key)
        if (old != null && (old.level != source.level || old.sampleRate != source.sampleRate || old.channelCount != source.channelCount ||
                old.format != source.format || old.md5 != source.md5)) cache.removeResource(key)
        val metadata = ContentMetadataMutations().set(SOURCE, ApiJson.encodeToString(source.copy(url = "cache://audio/${key.substringAfter(PREFIX)}")))
        ContentMetadataMutations.setRedirectedUri(metadata, null)
        cache.applyContentMetadataMutations(key, metadata)
        refreshUsage()
    }
    fun completeSource(key: String): AudioSource? = runCatching {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        if (length > 0 && cache.isCached(key, 0, length)) cachedSource(key) else null
    }.getOrNull()
    fun cachedBytes(key: String, length: Long): Long = cache.getCachedBytes(key, 0, length)
    private fun forgetRedirect(key: String) {
        val metadata = ContentMetadataMutations()
        ContentMetadataMutations.setRedirectedUri(metadata, null)
        cache.applyContentMetadataMutations(key, metadata)
    }

    /** Only the next track's opening bytes, not a background whole-library download. */
    suspend fun preload(key: String, source: AudioSource, limit: Long = PRELOAD_BYTES) = writeLock.withLock {
        withContext(Dispatchers.IO) {
            if (clearing.value) return@withContext
            val writer = CacheWriter(cachedFactory(block = true).createDataSource(),
                DataSpec.Builder().setUri(source.url).setKey(key).setLength(limit).build(), null, null)
            writers += writer
            try {
                coroutineScope {
                    val task = async(Dispatchers.IO) {
                        try { writer.cache() } catch (e: IOException) {
                            // A cancelled blocking writer may report InterruptedIOException.
                            currentCoroutineContext().ensureActive()
                            throw e
                        }
                    }
                    try { task.await() } finally {
                        // await is cancellable even while the blocking CDN reader is still running.
                        writer.cancel()
                        withContext(NonCancellable) { task.join() }
                    }
                }
            } finally { writers -= writer; runCatching { forgetRedirect(key) }; refreshUsage() }
        }
    }
    suspend fun clear() {
        clearing.value = true
        writers.forEach(CacheWriter::cancel)
        try { writeLock.withLock { withContext(Dispatchers.IO) {
            cache.keys.toList().forEach(cache::removeResource)
            refreshUsage()
        } } } finally { clearing.value = false }
    }
    override fun close() {
        writers.forEach(CacheWriter::cancel)
        if (cacheDelegate.isInitialized()) cache.release()
    }
    companion object {
        const val MAX_BYTES = 256L * 1024 * 1024
        const val PRELOAD_BYTES = 2L * 1024 * 1024
        private const val PREFIX = "cm-audio-v1:"
        private const val SOURCE = "cm.audio.source"
        fun key(server: String, accountId: Long, songId: Long, quality: AudioQuality): String {
            val scope = MessageDigest.getInstance("SHA-256").digest("${server.trimEnd('/')}|$accountId".toByteArray())
                .joinToString("") { "%02x".format(it) }
            return "$PREFIX$scope:$songId:${quality.value}"
        }
    }
}
