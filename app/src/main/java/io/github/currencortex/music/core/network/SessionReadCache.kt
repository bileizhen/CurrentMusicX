package io.github.currencortex.music.core.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Short lived metadata only. Responses never survive an account, server or library revision change. */
class SessionReadCache(private val session: () -> RequestSession, private val revision: () -> Long,
    private val now: () -> Long = System::nanoTime) {
    private data class Entry(val session: RequestSession, val revision: Long, val epoch: Long, val expires: Long, val value: Any)
    private val entries = ConcurrentHashMap<String, Entry>()
    private val locks = Array(16) { Mutex() }
    private val epoch = java.util.concurrent.atomic.AtomicLong()
    fun clear() { epoch.incrementAndGet(); entries.clear() }
    suspend fun <T : Any> read(key: String, fresh: Boolean = false, load: suspend (RequestSession) -> T): T {
        val expected = session(); val version = revision(); val generation = epoch.get()
        return locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
            val cached = entries[key]
            if (!fresh && cached != null && cached.session == expected && cached.revision == version &&
                cached.epoch == generation && cached.expires > now()) {
                @Suppress("UNCHECKED_CAST")
                return@withLock cached.value as T
            }
            val value = load(expected)
            if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
            if (version == revision() && generation == epoch.get()) {
                entries[key] = Entry(expected, version, generation, now() + 60_000_000_000L, value)
                if (entries.size > 24) entries.entries.minByOrNull { it.value.expires }?.let { entries.remove(it.key, it.value) }
            }
            value
        }
    }
}
