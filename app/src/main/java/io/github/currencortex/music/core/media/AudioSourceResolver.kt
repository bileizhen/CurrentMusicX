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
data class ResolvedAudio(val source: AudioSource, val key: String)

/** Short-lived URL reuse for prefetched tracks; persisted bytes have a separate lifetime. */
class AudioSourceResolver(private val cache: AudioCache, private val currentSession: () -> RequestSession,
    private val now: () -> Long = System::nanoTime,
    private val currentProvider: () -> String = { "currentmusic" },
    private val load: suspend (Long, AudioQuality, RequestSession) -> AudioSource) {
    private data class Entry(val request: AudioRequest, val source: AudioSource, val until: Long)
    private val memo = java.util.concurrent.ConcurrentHashMap<String, Entry>()
    private val locks = Array(8) { Mutex() }
    fun invalidate() { memo.clear() }
    suspend fun resolve(request: AudioRequest): ResolvedAudio = withContext(Dispatchers.IO) {
        val key = request.key
        locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            if (request.session != currentSession()) throw ApiException(ErrorKind.Unauthorized)
            if (request.providerIdentity != currentProvider()) throw ApiException(ErrorKind.AudioSourceChanged)
            val saved = memo[key]?.takeIf { it.request == request && it.until > now() }
            val source = saved?.source ?: try { load(request.songId, request.quality, request.session) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // Never turn a denied or expired account response into an offline success.
                val unavailable = if (e is ApiException) e.kind == ErrorKind.Server else e is IOException
                if (!unavailable) throw e
                cache.completeSource(key) ?: throw e
            }
            if (request.session != currentSession()) throw ApiException(ErrorKind.Unauthorized)
            if (request.providerIdentity != currentProvider()) throw ApiException(ErrorKind.AudioSourceChanged)
            // Disk cache failure must not turn a valid network source into playback failure.
            runCatching { cache.rememberSource(key, source) }
            // Reusing an entry must not slide the expiry of its original signed URL.
            if (saved == null) memo[key] = Entry(request, source, now() + 30_000_000_000L)
            if (memo.size > 24) memo.entries.minByOrNull { it.value.until }?.let { memo.remove(it.key, it.value) }
            ResolvedAudio(source, key)
        }
    }
}
