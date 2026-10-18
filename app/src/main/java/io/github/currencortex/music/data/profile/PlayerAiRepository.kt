package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.*
import java.io.IOException
import java.util.concurrent.TimeUnit

data class PlayerAiEvent(val type: String, val text: String = "", val cards: List<PostAttachment> = emptyList())

/** Private player scope. Never posts to the public chat and never retries a POST automatically. */
class PlayerAiRepository(private val session: () -> RequestSession,
    client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()) {
    private val factory = EventSources.createFactory(client.newBuilder().followRedirects(false).retryOnConnectionFailure(false).build())
    fun ask(question: String, song: PostAttachment, reset: Boolean, expected: RequestSession = session()): Flow<PlayerAiEvent> = callbackFlow {
        require(question.isNotBlank() && question.length <= 500 && song.type == "song" && validMusicAttachments(listOf(song)).size == 1)
        if (expected.token.isNullOrBlank() || expected != session()) { close(ApiException(ErrorKind.Unauthorized)); return@callbackFlow }
        val body = buildJsonObject { put("question", question.trim()); put("song", ApiJson.encodeToJsonElement(song)); put("reset", reset) }
        val request = Request.Builder().url(ServerUrl.endpoint(expected.server, "ai/live", emptyMap()))
            .header("Authorization", "Bearer ${expected.token}").header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        var terminal = false
        val source = factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (expected != session()) { close(ApiException(ErrorKind.Unauthorized)); eventSource.cancel(); return }
                if (type !in setOf("start", "delta", "done", "error")) return
                try {
                    val value = ApiJson.parseToJsonElement(data).jsonObject
                    if (type == "error") { terminal = true; close(IOException("AI 暂时不可用，请重试")); eventSource.cancel(); return }
                    val text = (value["text"] as? JsonPrimitive)?.contentOrNull.orEmpty().take(50000)
                    val cards = value["cards"]?.let { validMusicAttachments(ApiJson.decodeFromJsonElement<List<PostAttachment>>(it)) }.orEmpty().take(3)
                    if (!trySend(PlayerAiEvent(type!!, text, cards)).isSuccess) { close(IOException("回答接收中断，请重试")); eventSource.cancel() }
                    if (type == "done") { terminal = true; close(); eventSource.cancel() }
                } catch (_: Exception) { close(IOException("AI 返回格式异常，请重试")); eventSource.cancel() }
            }
            override fun onClosed(eventSource: EventSource) { if (!terminal) close(IOException("回答尚未完成，连接已断开")) }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (terminal) return
                close(when (response?.code) {
                    401 -> ApiException(ErrorKind.Unauthorized); 403 -> ApiException(ErrorKind.Forbidden)
                    429 -> ApiException(ErrorKind.RateLimited)
                    else -> IOException("AI 连接失败，请稍后重试")
                })
            }
        })
        awaitClose { source.cancel() }
    }.buffer(Channel.CONFLATED)
}
