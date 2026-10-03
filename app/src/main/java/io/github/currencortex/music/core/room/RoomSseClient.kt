package io.github.currencortex.music.core.room

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okhttp3.sse.*
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface RoomSignal {
    data object Open : RoomSignal
    data class Event(val type: String, val data: String, val id: String?) : RoomSignal
}
class RoomSseClient(client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).followRedirects(false).build()) {
    private val factory = EventSources.createFactory(client)
    fun events(roomId: String, seq: Long, lastId: String?, session: RequestSession) = callbackFlow {
        require(!session.token.isNullOrBlank())
        val request = Request.Builder().url(ServerUrl.endpoint(session.server, "live/$roomId/events",
            mapOf("since" to seq.toString(), "sse" to "1")))
            .header("Authorization", "Bearer ${session.token}").header("Accept", "text/event-stream")
        if (!lastId.isNullOrBlank()) request.header("Last-Event-ID", lastId)
        val source = factory.newEventSource(request.build(), object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) { trySend(RoomSignal.Open) }
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                // Closing on overflow forces a full snapshot and sequence replay on reconnect.
                if (!trySend(RoomSignal.Event(type ?: "message", data, id)).isSuccess) close(IOException("SSE overflow"))
            }
            override fun onClosed(eventSource: EventSource) { close(IOException("SSE ended")) }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val status = response?.code ?: 0
                close(if (status in setOf(401, 403, 404)) ApiException(when(status) { 401 -> ErrorKind.Unauthorized; 403 -> ErrorKind.Forbidden; else -> ErrorKind.NotFound }, status)
                    else IOException("SSE disconnected"))
            }
        })
        awaitClose { source.cancel() }
    }
}
