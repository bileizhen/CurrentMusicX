package io.github.currencortex.music.core.media

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.AudioSource
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

data class AudioRequest(val songId: Long, val quality: AudioQuality, val accountId: Long, val session: RequestSession,
    val providerIdentity: String = "currentmusic", val song: io.github.currencortex.music.data.song.Song? = null,
    val refresh: Boolean = false, val supportsDolby: Boolean = false) {
    val key get() = AudioCache.key(session.server, accountId, songId, quality, providerIdentity, supportsDolby)

    private val dolbyAuto get() = quality == AudioQuality.AUTO &&
        (providerIdentity == "currentmusic" || providerIdentity == "netease")

    internal fun accepts(source: AudioSource): Boolean = !dolbyAuto || supportsDolby || source.level != AudioQuality.DOLBY.value

    internal fun offlineAudio(cached: (AudioRequest) -> ResolvedAudio?): Pair<AudioRequest, ResolvedAudio>? {
        cached(this)?.takeIf { accepts(it.source) }?.let { return this to it }
        if (!dolbyAuto) return null
        val alternate = copy(supportsDolby = !supportsDolby)
        return cached(alternate)?.takeIf { accepts(it.source) }?.let { alternate to it }
    }

    internal fun selectedDolby(source: AudioSource): Boolean =
        quality == AudioQuality.AUTO && supportsDolby && source.level == AudioQuality.DOLBY.value
}
data class ResolvedAudio(val source: AudioSource, val key: String, val refreshAfterMs: Long = 25_000)

/** Short-lived URL reuse for prefetched tracks; persisted bytes have a separate lifetime. */
class AudioSourceResolver(private val cache: AudioCache, private val currentSession: () -> RequestSession,
    private val now: () -> Long = System::nanoTime,
    private val currentProvider: () -> String = { "currentmusic" },
    private val validateRequest: (AudioRequest) -> Boolean? = { null },
    private val loadTrack: (suspend (AudioRequest) -> AudioSource)? = null,
    private val load: suspend (Long, AudioQuality, RequestSession) -> AudioSource) {
    private data class Entry(val request: AudioRequest, val source: AudioSource, val until: Long,
        val cacheKey: String = sourceCacheKey(request.key, source))
    private data class Denial(val request: AudioRequest, val error: ApiException)
    private val memo = java.util.concurrent.ConcurrentHashMap<String, Entry>()
    private val denied = java.util.concurrent.ConcurrentHashMap<String, Denial>()
    private val locks = Array(8) { Mutex() }
    fun invalidate() { memo.clear(); denied.clear() }
    private fun checkRequest(request: AudioRequest) {
        when (validateRequest(request)) { true -> return; false -> throw ApiException(ErrorKind.Unauthorized); null -> Unit }
        if (request.session != currentSession()) throw ApiException(ErrorKind.Unauthorized)
        if (request.providerIdentity != currentProvider()) throw ApiException(ErrorKind.AudioSourceChanged)
    }
    private fun saved(request: AudioRequest): ResolvedAudio? {
        checkRequest(request)
        val entry = memo[request.key]?.takeIf { it.request == request && it.until > now() } ?: return null
        // A network failure can memoize a fully cached source. LRU may reclaim its spans
        // before the memo expires; metadata and a cache:// URI alone are not playable audio.
        if (entry.source.url.startsWith("cache://audio/") && cache.completeSource(entry.cacheKey) == null) {
            memo.remove(request.key, entry)
            return null
        }
        return result(entry)
    }
    private fun result(entry: Entry): ResolvedAudio {
        val remainingMs = (entry.until - now()) / 1_000_000
        val advanceMs = minOf(30_000, entry.source.validForSeconds.coerceIn(1, 600) * 200)
        return ResolvedAudio(entry.source, entry.cacheKey, (remainingMs - advanceMs).coerceAtLeast(250))
    }
    suspend fun resolve(request: AudioRequest, refresh: Boolean = false): ResolvedAudio = withContext(Dispatchers.IO) {
        request.song?.takeIf { it.musicSource == io.github.currencortex.music.data.song.MusicSource.LOCAL }?.let { song ->
            val uri = android.net.Uri.parse(song.localUri)
            cache.validateLocalUri(uri)
            return@withContext ResolvedAudio(AudioSource(uri.toString(), "local", 0, 0), "", Long.MAX_VALUE)
        }
        checkRequest(request)
        val key = request.key
        if (!refresh) {
            denied[key]?.takeIf { it.request == request }?.let { throw it.error }
            // Complete, account/provider/quality-scoped audio does not need a CDN URL.
            // Partial cache must still resolve a live URL to read the missing bytes.
            cache.completeSource(key)?.takeIf(request::accepts)?.takeUnless { it.matched || cache.matchedSourceKey(key) != null }?.let {
                checkRequest(request)
                return@withContext ResolvedAudio(it, key, refreshAfterMs = Long.MAX_VALUE)
            }
        }
        // A background renewal must not block a switch that already has a valid URL.
        if (!refresh) saved(request)?.let { return@withContext it }
        locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            if (!refresh) saved(request)?.let { return@withLock it }
            checkRequest(request)
            val started = now()
            var fallbackKey: String? = null
            val source = try { loadTrack?.invoke(request.copy(refresh = refresh)) ?: load(request.songId, request.quality, request.session) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // Never turn a denied or expired account response into an offline success.
                val unavailable = if (e is ApiException) e.kind == ErrorKind.Server else e is IOException
                if (!unavailable) {
                    if (e is ApiException && e.kind != ErrorKind.RateLimited) {
                        denied[key] = Denial(request, e)
                        memo.remove(key)
                    }
                    throw e
                }
                denied[key]?.takeIf { it.request == request }?.let { throw it.error }
                val (cachedRequest, cachedAudio) = request.offlineAudio { candidate ->
                    val candidateKey = if (candidate == request) key else candidate.key
                    if (denied[candidateKey]?.request == candidate) return@offlineAudio null
                    val cachedKey = cache.matchedSourceKey(candidateKey) ?: candidateKey
                    cache.completeSource(cachedKey)?.takeUnless { cachedKey == candidateKey && it.matched }
                        ?.let { ResolvedAudio(it, cachedKey) }
                } ?: throw e
                if (cachedRequest != request) {
                    checkRequest(request)
                    // A later online lookup should still be able to select the requested preference.
                    return@withLock cachedAudio.copy(refreshAfterMs = Long.MAX_VALUE)
                }
                fallbackKey = cachedAudio.key
                cachedAudio.source
            }
            checkRequest(request)
            denied.remove(key)
            // Disk cache failure must not turn a valid network source into playback failure.
            val sourceKey = fallbackKey ?: sourceCacheKey(key, source)
            if (fallbackKey == null) runCatching {
                cache.rememberSource(sourceKey, source)
                cache.rememberSelection(key, sourceKey)
            }
            // Bound reuse, reserve expiry headroom, and include the time spent resolving.
            // A memo hit never extends the original URL's lifetime.
            val seconds = source.validForSeconds.coerceIn(1, 600)
            val lifetime = seconds * 1_000_000_000L
            val entry = Entry(request, source, started + lifetime - minOf(lifetime / 10, 30_000_000_000L), sourceKey)
            memo[key] = entry
            if (memo.size > 24) memo.entries.minByOrNull { it.value.until }?.let { memo.remove(it.key, it.value) }
            result(entry)
        }
    }
}

/** Resolved candidates never share byte ranges with a different recording or plugin URL. */
internal fun sourceCacheKey(requestKey: String, source: AudioSource): String =
    if (source.matched || source.source.startsWith("lx:")) "$requestKey:match:${source.cacheIdentity}" else requestKey
