package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class PostAttachment(val type: String, val id: Long, val name: String = "",
    val sub: String = "", val pic: String = "")
@Serializable data class CommunityPost(val id: Long, val content: String = "", val author: ProfileUser = ProfileUser(),
    val attachments: List<PostAttachment> = emptyList(), val createdAt: Long = 0,
    val likeCount: Int = 0, val commentCount: Int = 0, val likedByMe: Boolean = false, val canDelete: Boolean = false,
    val title: String = "")
@Serializable data class PostsPage(val posts: List<CommunityPost> = emptyList(), val total: Int = 0)
@Serializable data class PostReplyTarget(val id: Long, val userId: Long, val nickname: String = "")
@Serializable data class PostComment(val id: Long, val content: String = "", val author: ProfileUser = ProfileUser(),
    val createdAt: Long = 0, val canDelete: Boolean = false, val replyTo: PostReplyTarget? = null)
@Serializable data class PostComments(val comments: List<PostComment> = emptyList(), val nextAfter: Long? = null)
@Serializable private data class PostDetails(val post: CommunityPost)
internal data class PostDetail(val post: CommunityPost, val shareable: Boolean)
@Serializable private data class CreatedPost(val id: Long)
@Serializable private data class PostLike(val likeCount: Int)
data class PostAttachmentPage(val items: List<PostAttachment>, val nextOffset: Int, val hasMore: Boolean)

internal const val POST_TITLE_LIMIT = 80
internal const val POST_CONTENT_LIMIT = 5000
internal const val POST_COMMENT_LIMIT = 500
internal fun String.postCharacterCount(): Int = trim().let { it.codePointCount(0, it.length) }
internal fun String.validPostTitle(): Boolean = postCharacterCount() <= POST_TITLE_LIMIT &&
    trim().none { it in "\n\r\u000B\u000C\u001C\u001D\u001E\u0085\u2028\u2029" }

internal data class PostChanges(
    val revision: Long = 0,
    private val session: RequestSession? = null,
    private val likes: Map<Long, Like> = emptyMap(),
    private val deletions: Map<Long, Long> = emptyMap(),
) {
    data class Like(val revision: Long, val liked: Boolean, val count: Int)

    fun forSession(expected: RequestSession): PostChanges =
        if (expected == session) this else PostChanges(revision, expected)

    fun recordLike(expected: RequestSession, id: Long, liked: Boolean, count: Int): PostChanges {
        val current = forSession(expected)
        val next = revision + 1
        return current.copy(revision = next, likes = current.likes + (id to Like(next, liked, count)))
    }

    fun recordDeletion(expected: RequestSession, id: Long): PostChanges {
        val current = forSession(expected)
        val next = revision + 1
        return current.copy(revision = next, likes = current.likes - id, deletions = current.deletions + (id to next))
    }

    fun deletedAfter(expected: RequestSession, since: Long): Set<Long> =
        if (expected == session) deletions.filterValues { it > since }.keys else emptySet()

    fun isDeleted(id: Long, expected: RequestSession): Boolean = expected == session && id in deletions

    fun applyTo(post: CommunityPost, expected: RequestSession, since: Long): CommunityPost {
        if (expected != session) return post
        val change = likes[post.id]?.takeIf { it.revision > since } ?: return post
        return post.copy(likedByMe = change.liked, likeCount = change.count)
    }
}

class PostsRepository(private val api: ApiClient, private val session: () -> RequestSession) {
    private val mutations = MutableStateFlow(PostChanges())
    private val postWrites = List(16) { Mutex() }
    internal val changes = mutations.asStateFlow()
    internal fun resetForCurrentSession() = mutations.update { it.forSession(session()) }

    private suspend fun request(method: String, path: String, query: Map<String, String> = emptyMap(),
        body: JsonElement? = null, expected: RequestSession = session()): JsonElement {
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        if (method != "GET" && expected.token.isNullOrBlank()) throw ApiException(ErrorKind.Unauthorized)
        val result = api.request(method, path, query, body, authenticated = !expected.token.isNullOrBlank(), expectedSession = expected,
            retryConnection = method == "GET")
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        return result
    }
    suspend fun list(offset: Int = 0, author: Long? = null, expected: RequestSession = session()): PostsPage {
        val since = changes.value.revision
        val page = api.decode<PostsPage>(request("GET", "posts", buildMap {
            put("offset", offset.coerceAtLeast(0).toString())
            put("limit", "20")
            if (author != null) put("author", author.toString())
        }, expected = expected))
        val latest = changes.value
        val posts = page.posts.filterNot { latest.isDeleted(it.id, expected) }.map { latest.applyTo(it, expected, since) }
        return page.copy(posts = posts, total = (page.total - (page.posts.size - posts.size)).coerceAtLeast(0))
    }
    internal suspend fun detail(id: Long, cached: CommunityPost? = null, publishedBy: Long? = null): PostDetail {
        require(id > 0)
        val expected = session()
        val since = changes.value.revision
        val detail = try {
            PostDetail(api.decode<PostDetails>(request("GET", "posts/$id", expected = expected)).post, shareable = true)
        } catch (error: ApiException) {
            if (error.status != 404 || error.userMessage != "未知接口 GET /posts/$id") throw error
            val post = cached?.takeIf { it.id == id }
                ?: publishedBy?.let { author -> list(author = author, expected = expected).posts.find { it.id == id } }
                ?: throw error
            PostDetail(post, shareable = false)
        }
        val latest = changes.value
        if (latest.isDeleted(id, expected)) throw ApiException(ErrorKind.NotFound)
        return detail.copy(post = latest.applyTo(detail.post, expected, since))
    }
    fun link(id: Long): String {
        require(id > 0)
        val endpoint = ServerUrl.endpoint(api.routeServer(session().server), "posts/$id", emptyMap())
        val basePath = endpoint.encodedPath.removeSuffix("/posts/$id").removeSuffix("/cm").trimEnd('/')
        return endpoint.newBuilder().encodedPath("$basePath/app/").fragment("/post/$id").build().toString()
    }
    suspend fun create(content: String, attachments: List<PostAttachment>, title: String, expected: RequestSession): Long {
        val music = validMusicAttachments(attachments)
        require(content.postCharacterCount() in 1..POST_CONTENT_LIMIT && title.validPostTitle() &&
            attachments.size <= 5 && music.size == attachments.size)
        return api.decode<CreatedPost>(request("POST", "posts", body = buildJsonObject {
            if (title.isNotBlank()) put("title", title.trim())
            put("content", content.trim())
            put("attachments", ApiJson.encodeToJsonElement(music))
        }, expected = expected)).id
    }
    suspend fun searchAttachments(query: String, type: String, offset: Int = 0): PostAttachmentPage {
        require(type in setOf("song", "album", "artist", "playlist") && query.isNotBlank() && offset >= 0)
        val result = request("GET", "ncm/search", mapOf("keywords" to query.trim().take(100),
            "type" to type, "offset" to "$offset", "limit" to "30")).jsonObject
        val raw = (result[type + "s"] as? JsonArray).orEmpty()
        val cards = raw.mapNotNull { value ->
            val item = value as? JsonObject ?: return@mapNotNull null
            fun text(key: String) = (item[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val id = ((if (type == "song") item["ncm_id"] ?: item["id"] else item["id"]) as? JsonPrimitive)?.longOrNull
                ?: return@mapNotNull null
            val sub = when (type) {
                "song" -> text("artists")
                "album" -> text("artist")
                "artist" -> text("alias")
                else -> listOf(text("creator"), text("trackCount").takeIf { it.isNotBlank() }?.let { "$it 首" }.orEmpty())
                    .filter(String::isNotBlank).joinToString(" · ")
            }
            validMusicAttachments(listOf(PostAttachment(type, id, text("name"), sub,
                text("pic").ifBlank { text("cover").ifBlank { text("picUrl") } }))).singleOrNull()
        }.distinctBy { it.type to it.id }
        val more = ((result["hasMore"] as? JsonObject)?.get(type) as? JsonPrimitive)?.booleanOrNull ?: (raw.size == 30)
        return PostAttachmentPage(cards, offset + raw.size, raw.isNotEmpty() && more)
    }
    suspend fun like(id: Long, on: Boolean, expected: RequestSession): Int {
        require(id > 0)
        return postWrites[(id % postWrites.size).toInt()].withLock {
            val count = api.decode<PostLike>(request("POST", "posts/$id/like",
                body = buildJsonObject { put("on", on) }, expected = expected)).likeCount
            mutations.update { if (expected == session()) it.recordLike(expected, id, on, count) else it }
            count
        }
    }
    suspend fun delete(id: Long, expected: RequestSession) {
        require(id > 0)
        postWrites[(id % postWrites.size).toInt()].withLock {
            request("DELETE", "posts/$id", expected = expected)
            mutations.update { if (expected == session()) it.recordDeletion(expected, id) else it }
        }
    }
    suspend fun comments(id: Long, after: Long = 0): PostComments {
        require(id > 0 && after >= 0)
        return api.decode(request("GET", "posts/$id/comments", mapOf("after" to after.toString(), "limit" to "30")))
    }
    suspend fun comment(id: Long, content: String, replyTo: Long?, expected: RequestSession): Long {
        require(id > 0 && content.postCharacterCount() in 1..POST_COMMENT_LIMIT)
        return api.decode<CreatedPost>(request("POST", "posts/$id/comments", body = buildJsonObject {
            put("content", content.trim())
            if (replyTo != null) put("replyTo", replyTo)
        }, expected = expected)).id
    }
    suspend fun deleteComment(id: Long, expected: RequestSession) {
        require(id > 0)
        request("DELETE", "posts/comments/$id", expected = expected)
    }
}
