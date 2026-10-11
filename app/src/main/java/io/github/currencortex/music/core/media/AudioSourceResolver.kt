package io.github.currencortex.music.core.media

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.AudioSource
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

data class AudioRequest(val songId: Long, val quality: AudioQuality, val accountId: Long, val session: RequestSession,
    val providerIdentity: String = "currentmusic") {
    val key get() = AudioCache.key(session.server, accountId, songId, quality, providerIdentity)
}
data class ResolvedAudio(val source: AudioSource, val key: String, val refreshAfterMs: Long = 25_000)

/** Short-lived URL reuse for prefetched tracks; persisted bytes have a separate lifetime. */
class AudioSourceResolver(private val cache: AudioCache, private val currentSession: () -> RequestSession,
    private val now: () -> Long = System::nanoTime,
    private val currentProvider: () -> String = { "currentmusic" },
    private val load: suspend (Long, AudioQuality, RequestSession) -> AudioSource) {
    private data class Entry(val request: AudioRequest, val source: AudioSource, val until: Long)
    private val memo = java.util.concurrent.ConcurrentHashMap<String, Entry>()
    private val locks = Array(8) { Mutex() }
    fun invalidate() { memo.clear() }
    private fun checkRequest(request: AudioRequest) {
        if (request.session != currentSession()) throw ApiException(ErrorKind.Unauthorized)
        if (request.providerIdentity != currentProvider()) throw ApiException(ErrorKind.AudioSourceChanged)
    }
    private fun saved(request: AudioRequest): ResolvedAudio? {
        checkRequest(request)
        val entry = memo[request.key]?.takeIf { it.request == request && it.until > now() } ?: return null
        return result(entry)
    }
    private fun result(entry: Entry): ResolvedAudio {
        val remainingMs = (entry.until - now()) / 1_000_000
        val advanceMs = minOf(30_000, entry.source.validForSeconds.coerceIn(1, 600) * 200)
        return ResolvedAudio(entry.source, entry.request.key, (remainingMs - advanceMs).coerceAtLeast(250))
    }
    suspend fun resolve(request: AudioRequest, refresh: Boolean = false): ResolvedAudio = withContext(Dispatchers.IO) {
        // A background renewal must not block a switch that already has a valid URL.
        if (!refresh) saved(request)?.let { return@withContext it }
        val key = request.key
        locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            if (!refresh) saved(request)?.let { return@withLock it }
            checkRequest(request)
            val started = now()
            val source = try { load(request.songId, request.quality, request.session) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // Never turn a denied or expired account response into an offline success.
                val unavailable = if (e is ApiException) e.kind == ErrorKind.Server else e is IOException
                if (!unavailable) throw e
                cache.completeSource(key) ?: throw e
            }
            checkRequest(request)
            // Disk cache failure must not turn a valid network source into playback failure.
            runCatching { cache.rememberSource(key, source) }
            // Bound reuse, reserve expiry headroom, and include the time spent resolving.
            // A memo hit never extends the original URL's lifetime.
            val seconds = source.validForSeconds.coerceIn(1, 600)
            val lifetime = seconds * 1_000_000_000L
            val entry = Entry(request, source, started + lifetime - minOf(lifetime / 10, 30_000_000_000L))
            memo[key] = entry
            if (memo.size > 24) memo.entries.minByOrNull { it.value.until }?.let { memo.remove(it.key, it.value) }
            result(entry)
        }
    }
}
