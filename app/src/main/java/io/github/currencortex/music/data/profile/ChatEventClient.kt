package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.sse.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class ChatEventClient(client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()) {
    private val factory = EventSources.createFactory(client.newBuilder()
        .followRedirects(false).retryOnConnectionFailure(false).build())

    fun events(session: RequestSession, since: Long, onOpen: () -> Unit = {}): Flow<ChatLiveEvent> = callbackFlow {
        require(since >= 0)
        val request = Request.Builder().url(ServerUrl.endpoint(session.server, "live/chat",
            mapOf("sse" to "1", "since" to since.toString())))
            .header("Accept", "text/event-stream")
        if (!session.token.isNullOrBlank()) request.header("Authorization", "Bearer ${session.token}")
        val source = factory.newEventSource(request.build(), object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) { onOpen() }
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (type != null && type !in setOf("message", "delete")) return
                val event = try {
                    ApiJson.decodeFromString<ChatLiveEvent>(data).also {
                        require(it.seq > 0 && it.type in setOf("message", "delete"))
                        require(type == null || it.type == type)
                        require(it.payload is JsonObject)
                        if (it.type == "message") {
                            require(ApiJson.decodeFromJsonElement<ChatMessage>(it.payload).id > 0)
                        } else require(it.payload.jsonObject["id"]?.jsonPrimitive?.longOrNull?.let { value -> value > 0 } == true)
                    }
                } catch (_: Exception) {
                    close(IOException("Invalid chat event")); return
                }
                // Replay from the last applied sequence if the bounded channel overflows.
                if (!trySend(event).isSuccess) close(IOException("Chat event overflow"))
            }
            override fun onClosed(eventSource: EventSource) { close(IOException("Chat stream ended")) }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val status = response?.code ?: 0
                val kind = when (status) {
                    401 -> ErrorKind.Unauthorized; 403 -> ErrorKind.Forbidden
                    404 -> ErrorKind.NotFound; 429 -> ErrorKind.RateLimited
                    in 500..599 -> ErrorKind.Server; else -> null
                }
                close(if (kind != null) ApiException(kind, status) else IOException("Chat stream disconnected"))
            }
        })
        awaitClose { source.cancel() }
    }
}
