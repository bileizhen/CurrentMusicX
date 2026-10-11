package io.github.currencortex.music.data.announcement

import io.github.currencortex.music.core.network.ApiClient
import io.github.currencortex.music.core.network.RequestSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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
    suspend fun fetch(server: String): List<Announcement> {
        val response = api.decode<AnnouncementResponse>(api.request("GET", "announcements",
            query = mapOf("limit" to "20"), expectedSession = RequestSession(server, null), callTimeoutMillis = timeoutMillis))
        // Keep the server's pinned/date/id ordering.
        return response.items.filter { it.title.isNotBlank() && !it.removed }.take(20)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(servers: Flow<String>): Flow<List<Announcement>> = servers.distinctUntilChanged().transformLatest { server ->
        emit(emptyList())
        val items = try {
            withTimeoutOrNull(timeoutMillis) { fetch(server) }.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        emit(items)
    }
}
