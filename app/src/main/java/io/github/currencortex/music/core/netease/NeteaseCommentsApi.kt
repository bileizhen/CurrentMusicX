package io.github.currencortex.music.core.netease

import io.github.currencortex.music.core.network.*
import kotlinx.serialization.json.*

/** Adapts the same comment contract for a device-local NetEase account. */
internal class NeteaseCommentsApi(private val transport: NeteaseTransport) {
    suspend fun request(method: String, path: String, query: Map<String, String>, body: JsonObject,
        owner: NeteaseSession): JsonObject {
        val write = method == "POST"
        val authenticated = !owner.credential.stale && !owner.credential.cookies["MUSIC_U"].isNullOrBlank()
        if (write && !authenticated) throw ApiException(ErrorKind.NeteaseBindingRequired)
        val id = if (write) body.text("id") else query["id"].orEmpty()
        require(id.toLongOrNull()?.let { it > 0 } == true)
        require((if (write) body.text("type") else query["type"] ?: "0") == "0")
        val thread = "R_SO_4_$id"
        val floor = path == "ncm/comments/replies"
        val page = query["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val size = query["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
        val sort = when (query["sort"]) { "hot" -> 2; "latest" -> 3; else -> 99 }
        val operation = body.text("t")
        val endpoint = when (path) {
            "ncm/comments/page" -> "/api/v2/resource/comments"
            "ncm/comments/replies" -> "/api/resource/comment/floor/get"
            "ncm/comment/like" -> "/api/v1/comment/${if (operation == "1") "like" else "unlike"}"
            "ncm/comment" -> "/api/resource/comments/${when (operation) { "1" -> "add"; "2" -> "reply"; "0" -> "delete"; else -> throw ApiException(ErrorKind.Parse) }}"
            else -> throw ApiException(ErrorKind.NotFound)
        }
        if (write) require(body.text("confirm") == "1" && operation in setOf("0", "1", "2"))
        val payload = buildJsonObject {
            put("threadId", thread)
            when {
                path == "ncm/comment/like" -> put("commentId", body.text("cid"))
                write -> {
                    if (operation != "1") put("commentId", body.text("commentId"))
                    if (operation != "0") put("content", body.text("content"))
                }
                floor -> {
                    put("parentCommentId", query["commentId"].orEmpty()); put("time", query["cursor"] ?: "-1"); put("limit", size)
                }
                else -> {
                    put("pageNo", page); put("pageSize", size); put("sortType", sort); put("showInner", true)
                    put("cursor", when (sort) { 3 -> query["cursor"] ?: "0"; 2 -> "normalHot#${(page - 1L) * size}"; else -> "${(page - 1L) * size}" })
                }
            }
        }
        suspend fun fetch(anonymous: Boolean): JsonObject {
            val raw = transport.request(endpoint, payload,
                if (floor || path == "ncm/comment/like") NeteaseEncoding.WEB else NeteaseEncoding.DESKTOP,
                session = owner, cookieOverride = if (anonymous) emptyMap() else null, saveCookies = false,
                clientProfile = NeteaseClientProfile.ANDROID).body
            val code = raw["code"]?.jsonPrimitive?.intOrNull ?: 200
            if (code != 200) throw ApiException(when (code) {
                301, 401 -> ErrorKind.NeteaseBindingRequired; 403 -> ErrorKind.Forbidden
                429 -> ErrorKind.RateLimited; else -> ErrorKind.Server
            }, code)
            return raw
        }
        var anonymous = !authenticated
        val raw = try { fetch(anonymous) } catch (error: ApiException) {
            if (write || anonymous || error.kind != ErrorKind.NeteaseBindingRequired) throw error
            anonymous = true
            fetch(true)
        }
        transport.sessions.check(owner)
        if (write) return raw
        return normalizeComments(raw, if (anonymous) 0 else owner.credential.profile?.uid ?: 0, floor)
    }
}

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun normalizeComments(raw: JsonObject, uid: Long, floor: Boolean): JsonObject {
    val data = raw["data"] as? JsonObject ?: raw
    val rows = data["comments"] as? JsonArray ?: throw ApiException(ErrorKind.Parse)
    val comments = rows.mapNotNull { it as? JsonObject }.map { item ->
        val user = item["user"] as? JsonObject ?: buildJsonObject {}
        val reply = (item["beReplied"] as? JsonArray)?.firstOrNull() as? JsonObject
        buildJsonObject {
            put("id", item.text("commentId")); put("userId", user["userId"] ?: JsonPrimitive(0))
            put("nickname", user.text("nickname")); put("avatar", user.text("avatarUrl")); put("content", item.text("content"))
            put("time", item["time"] ?: JsonPrimitive(0)); put("liked", item["likedCount"] ?: JsonPrimitive(0))
            put("likedByMe", uid != 0L && (item["liked"] as? JsonPrimitive)?.booleanOrNull == true)
            put("replyCount", item["replyCount"] ?: (item["showFloorComment"] as? JsonObject)?.get("replyCount") ?: JsonPrimitive(0))
            put("beNickname", (reply?.get("user") as? JsonObject)?.text("nickname").orEmpty())
            put("beContent", reply?.get("content") ?: JsonNull)
        }
    }
    return buildJsonObject {
        put("comments", JsonArray(comments)); put("total", data["totalCount"] ?: data["total"] ?: JsonPrimitive(0))
        put("more", data["hasMore"] ?: data["more"] ?: JsonPrimitive(false)); put("meUid", uid)
        put("nextCursor", if (floor) (rows.lastOrNull() as? JsonObject)?.text("time")?.let(::JsonPrimitive) ?: JsonNull
            else data.text("cursor").takeIf(String::isNotBlank)?.let(::JsonPrimitive) ?: JsonNull)
    }
}
