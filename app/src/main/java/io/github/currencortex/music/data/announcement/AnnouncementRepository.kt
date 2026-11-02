package io.github.currencortex.music.data.announcement

import io.github.currencortex.music.core.network.ApiClient
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.core.network.RequestSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

@Serializable
data class Announcement(
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val link: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val author: String = "",
    val removed: Boolean = false,
) {
    val detailUrl: String? get() = link.toHttpUrlOrNull()?.takeIf {
        it.username.isEmpty() && it.password.isEmpty()
    }?.toString()
}

@Serializable
private data class AnnouncementResponse(val items: List<Announcement> = emptyList())

/** Public CurrentMusic announcements, independent of login and the audio provider. */
class AnnouncementRepository(private val api: ApiClient, private val timeoutMillis: Long = 8_000,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private data class Check(val at: Long, val items: List<Announcement>?)
    private val checks = linkedMapOf<String, Check>()
    private val refreshLock = Mutex()
    // Repository lifetime is the application process. Deliberately not persisted: an unchecked
    // "do not remind again" option allows the same announcement on the next process launch.
    private val dismissedAutomatic = mutableSetOf<String>()

    fun pendingAutomatic(server: String, items: List<Announcement>, readState: AnnouncementReadState): List<Announcement> =
        synchronized(dismissedAutomatic) {
            readState.unread(server, items).filter { announcementKey(server, it) !in dismissedAutomatic }
        }

    fun dismissAutomatic(server: String, items: List<Announcement>) {
        synchronized(dismissedAutomatic) {
            dismissedAutomatic += items.map { announcementKey(server, it) }
        }
    }
    suspend fun fetch(server: String): List<Announcement> = fetch(server, 20)

    /** Manual browsing includes read notices and is not restricted to the startup batch. */
    suspend fun fetchAll(server: String): List<Announcement> = fetch(server, 1000)

    private suspend fun fetch(server: String, limit: Int): List<Announcement> {
        val response = api.decode<AnnouncementResponse>(api.request("GET", "announcements",
            query = mapOf("limit" to limit.toString()), expectedSession = RequestSession(server, null), callTimeoutMillis = timeoutMillis))
        // Keep the server's pinned/date/id ordering.
        return response.items.filter { it.title.isNotBlank() && !it.removed }.take(limit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(servers: Flow<String>, refreshes: Flow<Unit> = emptyFlow(),
        retryCount: Int = 0, clearBeforeFetch: Boolean = true,
        minimumRefreshMillis: Long = 0): Flow<List<Announcement>> =
        servers.distinctUntilChanged().transformLatest { server ->
        if (clearBeforeFetch) emit(emptyList())
        merge(flowOf(Unit), refreshes).collectLatest {
            val refreshed = refreshLock.withLock {
                val previous = checks[server]
                if (minimumRefreshMillis > 0 && previous != null && nowMillis() - previous.at < minimumRefreshMillis)
                    return@withLock previous.items
                var attempt = 0
                var result: List<Announcement>? = null
                while (true) {
                    var retryable = true // A bounded timeout can be retried like a transport failure.
                    val items = try {
                        withTimeoutOrNull(timeoutMillis) { fetch(server) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        retryable = when (error) {
                            is ApiException -> error.kind == ErrorKind.Server || error.kind == ErrorKind.RateLimited
                            is IOException -> true
                            else -> false
                        }
                        null
                    }
                    // A failed refresh must not erase a notice already waiting to be shown.
                    if (items != null) { result = items; break }
                    if (!retryable || attempt >= retryCount) break
                    delay(1_000L shl attempt.coerceAtMost(5))
                    attempt++
                }
                // Share cooldowns across lifecycle collectors and recreated view models.
                // Cancelled checks never reach this point, so a resumed screen can retry them.
                checks[server] = Check(nowMillis(), result ?: previous?.items)
                if (checks.size > 8) checks.remove(checks.keys.first())
                result
            }
            if (refreshed != null) emit(refreshed)
        }
    }
}
