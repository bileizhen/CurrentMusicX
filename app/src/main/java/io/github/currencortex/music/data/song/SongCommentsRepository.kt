package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.network.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

enum class CommentSort(val value: String, val label: String) {
    RECOMMENDED("recommended", "推荐"), HOT("hot", "最热"), LATEST("latest", "最新")
}

@Serializable internal data class CommentDto(val id: String, val userId: Long = 0, val nickname: String = "",
    val avatar: String = "", val content: String = "", val time: Long = 0, val liked: Long = 0,
    val likedByMe: Boolean = false, val beNickname: String = "", val beContent: String? = null, val replyCount: Int = 0) {
    fun toDomain() = NeteaseComment(id.toLongOrNull() ?: 0, content, time, liked,
        NeteaseCommentUser(nickname, avatar),
        if (beNickname.isNotBlank() || !beContent.isNullOrBlank()) listOf(NeteaseCommentReply(beContent, NeteaseCommentUser(beNickname))) else emptyList(),
        id, userId, likedByMe, replyCount)
}
@Serializable internal data class CommentPageDto(val comments: List<CommentDto>, val total: Long = 0,
    val more: Boolean = false, val nextCursor: String? = null, val meUid: Long = 0)
data class SongCommentsPage(val comments: List<NeteaseComment>, val total: Long, val more: Boolean,
    val nextCursor: String?, val meUid: Long)

/** Local NetEase credentials stay on-device; otherwise the project uses its saved binding. */
class SongCommentsRepository(private val api: ApiClient, private val session: () -> RequestSession) {
    private suspend fun call(method: String, path: String, query: Map<String, String> = emptyMap(), body: JsonObject? = null): JsonElement {
        val owner = session()
        val native = api.hasLocalNeteaseLogin || api.nativeMediaEnabled
        val result = api.request(method, path, query, body, authenticated = method != "GET" || owner.token != null,
            expectedSession = owner, projectOnly = !native, retryConnection = method == "GET", callTimeoutMillis = 15_000)
        if (owner != session()) throw ApiException(ErrorKind.NeteaseSessionChanged)
        val code = (result as? JsonObject)?.get("code")?.jsonPrimitive?.intOrNull
        if (method == "POST" && code == null) throw ApiException(ErrorKind.Parse)
        if (code != null && code != 200) throw ApiException(when (code) {
            301, 401 -> ErrorKind.NeteaseBindingRequired
            403 -> ErrorKind.Forbidden
            429 -> ErrorKind.RateLimited
            else -> ErrorKind.Server
        }, code)
        if ((result as? JsonObject)?.get("ok")?.jsonPrimitive?.booleanOrNull == false) throw ApiException(ErrorKind.Server)
        return result
    }
    private suspend fun page(path: String, query: Map<String, String>): SongCommentsPage {
        val dto = api.decode<CommentPageDto>(call("GET", path, query))
        return SongCommentsPage(dto.comments.map(CommentDto::toDomain), dto.total, dto.more, dto.nextCursor, dto.meUid)
    }
    suspend fun comments(id: Long, sort: CommentSort, page: Int = 1, cursor: String? = null): SongCommentsPage =
        page("ncm/comments/page", buildMap {
            put("type", "0"); put("id", "$id"); put("sort", sort.value); put("page", "$page"); put("limit", "20")
            if (sort == CommentSort.LATEST && page > 1 && cursor != null) put("cursor", cursor)
        })
    suspend fun replies(id: Long, commentId: String, cursor: String? = null): SongCommentsPage =
        page("ncm/comments/replies", buildMap {
            put("type", "0"); put("id", "$id"); put("commentId", commentId); put("limit", "20")
            cursor?.let { put("cursor", it) }
        })
    suspend fun like(id: Long, commentId: String, liked: Boolean) {
        call("POST", "ncm/comment/like", body = buildJsonObject {
            put("id", id); put("type", 0); put("cid", commentId); put("t", if (liked) 1 else 0); put("confirm", 1)
        })
    }
    suspend fun send(id: Long, content: String, replyTo: String? = null) {
        require(content.isNotBlank() && content.trim().length <= 1000)
        call("POST", "ncm/comment", body = buildJsonObject {
            put("id", id); put("type", 0); put("t", if (replyTo == null) 1 else 2); put("confirm", 1)
            put("content", content.trim()); replyTo?.let { put("commentId", it) }
        })
    }
    suspend fun delete(id: Long, commentId: String) {
        call("POST", "ncm/comment", body = buildJsonObject {
            put("id", id); put("type", 0); put("t", 0); put("confirm", 1); put("commentId", commentId)
        })
    }
}
