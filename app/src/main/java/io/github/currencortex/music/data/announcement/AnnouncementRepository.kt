package io.github.currencortex.music.data.announcement

import io.github.currencortex.music.core.network.ApiClient
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
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
class AnnouncementRepository(private val api: ApiClient, private val timeoutMillis: Long = 8_000) {
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
        retryCount: Int = 0, clearBeforeFetch: Boolean = true): Flow<List<Announcement>> =
        servers.distinctUntilChanged().transformLatest { server ->
        if (clearBeforeFetch) emit(emptyList())
        merge(flowOf(Unit), refreshes).collectLatest {
            var attempt = 0
            while (true) {
                val items = try {
                    withTimeoutOrNull(timeoutMillis) { fetch(server) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                // A failed refresh must not erase a notice already waiting to be shown.
                if (items != null) { emit(items); break }
                if (attempt >= retryCount) break
                delay(1_000L * ++attempt)
            }
        }
    }
}
