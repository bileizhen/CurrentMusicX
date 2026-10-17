package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.Base64

@Serializable data class ChatMessage(val id: Long, val userId: Long = 0, val content: String = "",
    val image: String = "", val createdAt: Long = 0, val user: ProfileUser? = null)
@Serializable data class ChatMessages(val messages: List<ChatMessage> = emptyList())
@Serializable data class ChatMembers(val members: List<ProfileUser> = emptyList())
@Serializable data class ChatLiveEvent(val seq: Long, val type: String, val payload: JsonElement)
@Serializable data class ChatLivePage(val events: List<ChatLiveEvent> = emptyList(), val latestSeq: Long = 0)
@Serializable private data class ChatImage(val image: String)

class ChatRepository(private val api: ApiClient, private val session: () -> RequestSession) {
    private suspend fun request(method: String, path: String, expected: RequestSession,
        query: Map<String, String> = emptyMap(), body: JsonElement? = null): JsonElement {
        if (method != "GET" && expected.token.isNullOrBlank()) throw ApiException(ErrorKind.Unauthorized)
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        val result = api.request(method, path, query, body, authenticated = true,
            expectedSession = expected, retryConnection = method == "GET")
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        return result
    }

    suspend fun messages(before: Long = 0, expected: RequestSession = session()): List<ChatMessage> {
        require(before >= 0)
        return api.decode<ChatMessages>(request("GET", "chat/messages", expected,
            buildMap { put("limit", "30"); if (before > 0) put("before", before.toString()) })).messages
    }

    suspend fun members(expected: RequestSession = session()): List<ProfileUser> =
        api.decode<ChatMembers>(request("GET", "chat/members", expected)).members

    suspend fun live(since: Long, expected: RequestSession = session()): ChatLivePage {
        require(since >= 0)
        return api.decode(request("GET", "chat/live", expected, mapOf("since" to since.toString())))
    }

    suspend fun send(content: String, image: String = "", mentions: List<Long> = emptyList(),
        expected: RequestSession = session()): ChatMessage {
        require(content.isNotBlank() || image.isNotBlank())
        require(image.isEmpty() || validChatImageName(image))
        require(mentions.all { it > 0 } && mentions.distinct().size <= 10)
        return api.decode(request("POST", "chat/messages", expected, body = buildJsonObject {
            put("content", content.trim()); put("image", image)
            put("mentions", JsonArray(mentions.distinct().map(::JsonPrimitive)))
        }))
    }

    suspend fun delete(id: Long, expected: RequestSession = session()) {
        require(id > 0)
        request("DELETE", "chat/messages/$id", expected)
    }

    // The native picker supplies a compressed JPEG, matching the public web client.
    suspend fun upload(bytes: ByteArray, expected: RequestSession = session()): String {
        require(bytes.isNotEmpty() && bytes.size <= 10 * 1024 * 1024)
        val result = api.decode<ChatImage>(request("POST", "chat/image", expected, body = buildJsonObject {
            put("file", Base64.getEncoder().encodeToString(bytes))
            put("filename", "chat.jpg"); put("contentType", "image/jpeg")
        }))
        require(validChatImageName(result.image))
        return result.image
    }

    fun imageUrl(server: String, image: String): String? = if (!validChatImageName(image)) null else
        ServerUrl.endpoint(server, "chat/image", emptyMap()).newBuilder().addPathSegment(image).build().toString()
}

private fun validChatImageName(value: String) = value.isNotBlank() && !value.contains("..") &&
    value.none { it == '/' || it == '\\' || it == ':' || it.isISOControl() }
