package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class PostAttachment(val type: String, val id: Long, val name: String = "",
    val sub: String = "", val pic: String = "")
@Serializable data class CommunityPost(val id: Long, val content: String = "", val author: ProfileUser = ProfileUser(),
    val attachments: List<PostAttachment> = emptyList(), val createdAt: Long = 0,
    val likeCount: Int = 0, val commentCount: Int = 0, val likedByMe: Boolean = false, val canDelete: Boolean = false)
@Serializable data class PostsPage(val posts: List<CommunityPost> = emptyList(), val total: Int = 0)
@Serializable data class PostComment(val id: Long, val content: String = "", val author: ProfileUser = ProfileUser(),
    val createdAt: Long = 0, val canDelete: Boolean = false)
@Serializable data class PostComments(val comments: List<PostComment> = emptyList())

class PostsRepository(private val api: ApiClient, private val session: () -> RequestSession) {
    private suspend fun request(method: String, path: String, query: Map<String, String> = emptyMap(),
        body: JsonElement? = null): JsonElement {
        val expected = session()
        if (method != "GET" && expected.token == null) throw ApiException(ErrorKind.Unauthorized)
        val result = api.request(method, path, query, body, authenticated = true, expectedSession = expected,
            retryConnection = method == "GET")
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        return result
    }
    suspend fun list(offset: Int = 0): PostsPage = api.decode(request("GET", "posts",
        mapOf("offset" to offset.coerceAtLeast(0).toString(), "limit" to "20")))
    suspend fun create(content: String, attachments: List<PostAttachment> = emptyList()) {
        require(content.isNotBlank() && attachments.size <= 5)
        request("POST", "posts", body = buildJsonObject {
            put("content", content.trim()); put("attachments", ApiJson.encodeToJsonElement(attachments))
        })
    }
    suspend fun like(id: Long, on: Boolean) {
        require(id > 0); request("POST", "posts/$id/like", body = buildJsonObject { put("on", on) })
    }
    suspend fun delete(id: Long) { require(id > 0); request("DELETE", "posts/$id") }
    suspend fun comments(id: Long): List<PostComment> {
        require(id > 0); return api.decode<PostComments>(request("GET", "posts/$id/comments")).comments
    }
    suspend fun comment(id: Long, content: String) {
        require(id > 0 && content.isNotBlank())
        request("POST", "posts/$id/comments", body = buildJsonObject { put("content", content.trim()) })
    }
    suspend fun deleteComment(id: Long) { require(id > 0); request("DELETE", "posts/comments/$id") }
}
