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
import java.util.concurrent.atomic.AtomicBoolean

/** One process owner, private disk storage, and no account headers on CDN requests. */
@androidx.annotation.OptIn(UnstableApi::class)
class AudioCache(context: Context, directory: File,
    client: OkHttpClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build(),
    initialMaxBytes: Long = DEFAULT_MAX_BYTES, initialCapacityReady: Boolean = true) : java.io.Closeable {
    init { require(initialMaxBytes > 0) { "Audio cache capacity must be positive" } }
    // This is reclaimable playback cache, never the user's explicit downloads directory.
    // Defer accounting until outside SimpleCache's lock to avoid lock inversion on eviction.
    private val evicted = AtomicBoolean(false)
    private val evictor = DynamicLruCacheEvictor(initialMaxBytes) { evicted.set(true) }
    private val cacheDelegate = lazy { SimpleCache(directory, evictor, StandaloneDatabaseProvider(context.applicationContext)) }
    private val cache get() = cacheDelegate.value
    private val upstream = OkHttpDataSource.Factory(client)
    private val appContext = context.applicationContext
    private val localAware = DefaultDataSource.Factory(context.applicationContext, upstream)
    private val writers = ConcurrentHashMap.newKeySet<CacheWriter>()
    private val writeLock = Mutex()
    private val usageLock = Any()
    private var usageInitialized = false
    private val cachedKeys = mutableSetOf<String>()
    private val capacityReady = CompletableDeferred<Unit>().apply { if (initialCapacityReady) complete(Unit) }
    val bytes = MutableStateFlow(0L)
    /** Cached track count, for the storage page. */
    val entries = MutableStateFlow(0)
    val maxBytes = MutableStateFlow(initialMaxBytes)
    val clearing = MutableStateFlow(false)
    private fun cachedFactory(block: Boolean = false) = CacheDataSource.Factory().setCache(cache)
        .setUpstreamDataSourceFactory(upstream).setFlags(if (block) CacheDataSource.FLAG_BLOCK_ON_CACHE else CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Called on the resolver's IO dispatcher; the provider enforces current URI read access. */
    internal fun validateLocalUri(uri: Uri) {
        require(uri.scheme == "content" && !uri.authority.isNullOrBlank()) { "本地音频地址无效" }
        if (uri.authority == "media") return
        require(android.provider.DocumentsContract.isDocumentUri(appContext, uri)) { "本地音频地址无效" }
        appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { }
            ?: throw java.io.FileNotFoundException("无法读取本地音频，请重新选择文件夹")
    }

    // MV and room URLs have no custom key and bypass the audio cache entirely.
    val dataSourceFactory = DataSource.Factory { object : DataSource {
        private var delegate: DataSource? = null
        private var activeKey: String? = null
        private val listeners = mutableListOf<TransferListener>()
        override fun addTransferListener(listener: TransferListener) { listeners += listener; delegate?.addTransferListener(listener) }
        override fun open(dataSpec: DataSpec): Long {
            activeKey = dataSpec.key?.takeIf { it.startsWith(PREFIX) }
            val source = if (activeKey != null && capacityReady.isCompleted)
                runCatching { cachedFactory().createDataSource() }.getOrElse { upstream.createDataSource() }
                else localAware.createDataSource()
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
            activeKey?.takeIf { capacityReady.isCompleted }?.let { key ->
                runCatching { forgetRedirect(key); updateUsage(key) }
            }; activeKey = null
        } }
    } }

    fun refreshUsage() {
        awaitCapacityReady()
        synchronized(usageLock) {
            cachedKeys.clear()
            cache.keys.filterTo(cachedKeys) { cache.getCachedSpans(it).any { span -> span.length > 0 } }
            usageInitialized = true
            bytes.value = cache.cacheSpace
            entries.value = cachedKeys.size
        }
    }

    suspend fun setMaxBytes(value: Long) = writeLock.withLock {
        withContext(Dispatchers.IO) {
            require(value > 0) { "Audio cache capacity must be positive" }
            maxBytes.value = value
            try {
                evictor.setMaxBytes(cache, value)
                capacityReady.complete(Unit)
                refreshUsage()
            } finally {
                capacityReady.complete(Unit)
            }
        }
    }
    private fun updateUsage(key: String) {
        synchronized(usageLock) {
            if (!capacityReady.isCompleted) return
            // An LRU write can remove another track, not only the key being written.
            if (!usageInitialized || evicted.getAndSet(false)) refreshUsage()
            if (cache.getCachedSpans(key).any { it.length > 0 }) cachedKeys.add(key) else cachedKeys.remove(key)
            bytes.value = cache.cacheSpace
            entries.value = cachedKeys.size
        }
    }
    fun cachedSource(key: String): AudioSource? {
        awaitCapacityReady()
        return runCatching {
            cache.getContentMetadata(key).get(SOURCE, "")?.takeIf { it.isNotBlank() }?.let { ApiJson.decodeFromString<AudioSource>(it) }
        }.getOrNull()
    }
    fun matchedSourceKey(requestKey: String): String? = runCatching {
        awaitCapacityReady()
        cache.getContentMetadata(requestKey).get("cm.audio.matchedKey", "")
            ?.takeIf { it.startsWith("$requestKey:match:") }
    }.getOrNull()
    fun rememberSelection(requestKey: String, sourceKey: String) {
        awaitCapacityReady()
        cache.applyContentMetadataMutations(requestKey, ContentMetadataMutations()
            .set("cm.audio.matchedKey", sourceKey.takeUnless { it == requestKey }.orEmpty()))
    }
    fun rememberSource(key: String, source: AudioSource) {
        awaitCapacityReady()
        val old = cachedSource(key)
        if (old != null && (old.level != source.level || old.sampleRate != source.sampleRate || old.channelCount != source.channelCount ||
                old.format != source.format || old.md5 != source.md5)) cache.removeResource(key)
        val metadata = ContentMetadataMutations().set(SOURCE, ApiJson.encodeToString(source.copy(url = "cache://audio/${key.substringAfter(PREFIX)}")))
        ContentMetadataMutations.setRedirectedUri(metadata, null)
        cache.applyContentMetadataMutations(key, metadata)
        updateUsage(key)
    }
    fun completeSource(key: String): AudioSource? = runCatching {
        awaitCapacityReady()
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        if (length > 0 && cache.isCached(key, 0, length)) cachedSource(key) else null
    }.getOrNull()
    fun cachedBytes(key: String, length: Long): Long {
        awaitCapacityReady()
        return cache.getCachedBytes(key, 0, length)
    }
    private fun forgetRedirect(key: String) {
        if (ContentMetadata.getRedirectedUri(cache.getContentMetadata(key)) == null) return
        val metadata = ContentMetadataMutations()
        ContentMetadataMutations.setRedirectedUri(metadata, null)
        cache.applyContentMetadataMutations(key, metadata)
    }

    /** Only the next track's opening bytes, not a background whole-library download. */
    suspend fun preload(key: String, source: AudioSource, limit: Long = PRELOAD_BYTES) {
        capacityReady.await()
        writeLock.withLock {
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
            } finally { writers -= writer; runCatching { forgetRedirect(key) }; updateUsage(key) }
        }
        }
    }
    suspend fun clear() {
        capacityReady.await()
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
    private fun awaitCapacityReady() {
        if (!capacityReady.isCompleted) runBlocking { capacityReady.await() }
    }
    companion object {
        const val DEFAULT_MAX_BYTES = 1024L * 1024 * 1024
        const val UNLIMITED_BYTES = Long.MAX_VALUE
        const val PRELOAD_BYTES = 2L * 1024 * 1024
        private const val PREFIX = "cm-audio-v1:"
        private const val SOURCE = "cm.audio.source"
        fun key(server: String, accountId: Long, songId: Long, quality: AudioQuality, providerIdentity: String = "netease",
            supportsDolby: Boolean = false): String {
            val provider = if (providerIdentity == "currentmusic") "" else "|$providerIdentity"
            val scope = MessageDigest.getInstance("SHA-256").digest("${server.trimEnd('/')}|$accountId$provider".toByteArray())
                .joinToString("") { "%02x".format(it) }
            val preference = if (quality == AudioQuality.AUTO && supportsDolby) ":prefer-dolby" else ""
            return "$PREFIX$scope:$songId:${quality.value}$preference"
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class DynamicLruCacheEvictor(initialMaxBytes: Long,
    private val onSpanRemoved: () -> Unit) : CacheEvictor {
    @Volatile private var maxBytes = initialMaxBytes
    private val leastRecentlyUsed = java.util.TreeSet<CacheSpan> { left, right ->
        val delta = left.lastTouchTimestamp.compareTo(right.lastTouchTimestamp)
        if (delta != 0) delta else left.compareTo(right)
    }
    private var currentSize = 0L

    fun setMaxBytes(cache: Cache, value: Long) {
        synchronized(cache) {
            maxBytes = value
            evictCache(cache, 0L)
        }
    }
    override fun requiresCacheSpanTouches() = true
    override fun onCacheInitialized() = Unit
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evictCache(cache, length)
    }
    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        currentSize += span.length
        evictCache(cache, 0L)
    }
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSize -= span.length
        onSpanRemoved()
    }
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }
    private fun evictCache(cache: Cache, requiredSpace: Long) {
        while (exceedsLimit(requiredSpace) && leastRecentlyUsed.isNotEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first())
        }
    }
    private fun exceedsLimit(requiredSpace: Long) = when {
        maxBytes == Long.MAX_VALUE -> false
        requiredSpace > maxBytes -> true
        else -> currentSize > maxBytes - requiredSpace
    }
}
