package io.github.currencortex.music.data.netease.listen

import io.github.currencortex.music.core.netease.NeteaseClientProfile
import io.github.currencortex.music.core.netease.NeteaseSession
import io.github.currencortex.music.core.netease.NeteaseTransport
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.serialization.json.*

/**
 * Read-only native history. Routes and fields come from NetEase's own record-list,
 * ranking and ending H5 bundles linked by Android 9.5.30 assets/default_link_config.json.
 * All statistics are server values, not local playback/queue estimates.
 */
internal class NeteaseListenTogetherHistoryRepository(private val transport: NeteaseTransport) : ListenTogetherHistoryProtocol {
    override suspend fun captureSession(): NeteaseSession = transport.sessions.snapshot().also(::checkSession)

    override fun checkSession(expected: NeteaseSession) {
        transport.sessions.check(expected)
        if (expected.credential.cookies["MUSIC_U"].isNullOrBlank() || expected.credential.stale ||
            (expected.credential.profile?.uid ?: 0) <= 0) throw ApiException(ErrorKind.NeteaseBindingRequired)
    }

    override suspend fun history(expected: NeteaseSession, cursor: String, size: Int): ListenTogetherHistoryPage {
        require(size in 1..50 && cursor.length <= 4096)
        // The official H5 only exposes deeper history to an entitled account. Recheck
        // with a fresh first page rather than treating an old UI flag as permission.
        if (cursor.isNotEmpty() && !history(expected, "", size).vip) throw ApiException(ErrorKind.Forbidden)
        val data = request("full/records", buildJsonObject {
            // The official H5 fetch middleware stringifies object-valued form fields.
            put("page", buildJsonObject { put("size", size); put("cursor", cursor) }.toString())
        }, expected)
        val vip = data.bool("vip") ?: false
        val rows = data.array("records") ?: parseError()
        // et() in NetEase's official list bundle replaces the last non-member row
        // with a locked placeholder, even when the server included its fields.
        val visible = if (vip) rows else rows.dropLast(1)
        val records = visible.mapNotNull { value ->
            val item = value as? JsonObject ?: parseError()
            // Native history can also contain group-room entries. They have no
            // single peer to represent in this two-person history UI.
            if ((item.number("userId") ?: 0) <= 0) return@mapNotNull null
            ListenTogetherHistoryRecord(item.text("id"), item.text("roomId"),
                displayedUser(item, "userId", "nick", "avatar"), item.text("date"),
                item.number("startTime") ?: 0, item.stat("duration"), item.stat("totalSong"),
                item.bool("online") == true, item.bool("canInvite") == true, item.integer("ltType"),
                item.text("recordRoomType").ifBlank { "listen_together" }, item.text("failMsg"))
        }
        val most = data.obj("mostListen")?.takeIf { (it.number("friendUid") ?: 0) > 0 }?.let { item ->
            ListenTogetherMostListen(displayedUser(item, "friendUid", "friendNick", "friendAvatar"),
                item.text("selfAvatar").https(), if (vip) item.stat("totalDuration") else null,
                if (vip) item.stat("totalSong") else null, item.bool("online") == true, item.integer("ltType"))
        }
        return ListenTogetherHistoryPage(records, most, vip && data.bool("hasMore") == true,
            if (vip) data.text("nextCursor") else "", vip, data.integer("vipType"))
    }

    override suspend fun ranking(expected: NeteaseSession): ListenTogetherRanking {
        if (!history(expected).vip) throw ApiException(ErrorKind.Forbidden)
        val data = request("rank/list", buildJsonObject {}, expected)
        val ranks = data.array("rankLists") ?: parseError()
        return ListenTogetherRanking(ranks.mapIndexed { index, value ->
            val item = value as? JsonObject ?: parseError()
            ListenTogetherRankEntry(displayedUser(item, "uid", "nickname", "avatar"), index + 1,
                item.stat("totalDuration"), item.stat("totalSongCount"), item.bool("online") == true, item.integer("ltType"))
        }, data.integer("vipType"))
    }

    override suspend fun record(expected: NeteaseSession, roomId: String): ListenTogetherRecordReport {
        require(ListenTogetherInvitation.validRoomId(roomId))
        val data = request("record/get", buildJsonObject { put("roomId", roomId) }, expected)
        if (data.text("roomId").isNotBlank() && data.text("roomId") != roomId) parseError()
        return ListenTogetherRecordReport(roomId, data.obj("userInfo")?.let(::participant),
            data.obj("anotherUserInfo")?.let { user ->
                val raw = participant(user)
                if (data.bool("newUnmaskFlag") == false) masked(data, raw) else raw
            }, data.secondsMs("duration"), data.stat("songCount"), data.secondsMs("totalDuration"),
            data.stat("totalSongCount"), data.stat("endTime"), data.bool("online") == true,
            data.bool("canInvite") == true, data.text("roomType"), data.text("scene"),
            data.bool("showCollectBtn") == true, data.text("failMsg"), data.integer("ltType"))
    }

    override suspend fun roomSongs(expected: NeteaseSession, roomId: String): List<Long> {
        require(ListenTogetherInvitation.validRoomId(roomId))
        val data = request("room/songs/list", buildJsonObject { put("roomId", roomId) }, expected)
        return (data.array("songIds") ?: parseError()).map { value ->
            (value as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: parseError()
        }.distinct()
    }

    private suspend fun request(path: String, payload: JsonObject, expected: NeteaseSession): JsonObject {
        checkSession(expected)
        val body = transport.request("/api/listen/together/$path", payload, session = expected,
            clientProfile = NeteaseClientProfile.ANDROID).body
        checkSession(expected)
        val code = (body["code"] as? JsonPrimitive)?.intOrNull ?: parseError()
        if (code != 200) throw ApiException(when (code) {
            301, 401 -> ErrorKind.NeteaseBindingRequired
            403 -> ErrorKind.Forbidden
            404, 488 -> ErrorKind.NotFound
            429 -> ErrorKind.RateLimited
            else -> ErrorKind.Server
        }, code)
        return body.obj("data") ?: parseError()
    }

    private fun participant(item: JsonObject): ListenTogetherParticipant = ListenTogetherParticipant(
        item.number("userId")?.takeIf { it > 0 } ?: parseError(), item.text("nickname"), item.text("avatarUrl").https())

    private fun displayedUser(item: JsonObject, id: String, name: String, avatar: String): ListenTogetherParticipant {
        val user = ListenTogetherParticipant(item.number(id)?.takeIf { it > 0 } ?: parseError(),
            item.text(name), item.text(avatar).https())
        return if (item.bool("newUnmaskFlag") == false) masked(item, user) else user
    }

    private fun masked(item: JsonObject, user: ListenTogetherParticipant): ListenTogetherParticipant {
        val display = item.obj("matchPlayInfo")?.obj("maskDisplay")
        return user.copy(masked = true, nickname = display?.text("peerDisplayName").orEmpty().ifBlank { "匿名听友" },
            avatarUrl = display?.obj("peerMaskAsset")?.text("assetUrl").orEmpty().https())
    }

    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
    private fun JsonObject.stat(key: String) = number(key)?.takeIf { it >= 0 }
    // The ending H5 formats record/get durations as seconds; history/rank use milliseconds.
    private fun JsonObject.secondsMs(key: String) = stat(key)?.takeIf { it <= Long.MAX_VALUE / 1000 }?.times(1000)
    private fun JsonObject.integer(key: String) = (get(key) as? JsonPrimitive)?.intOrNull ?: 0
    private fun JsonObject.bool(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.obj(key: String) = get(key) as? JsonObject
    private fun JsonObject.array(key: String) = get(key) as? JsonArray
    private fun String.https() = replaceFirst(Regex("^http://"), "https://")
    private fun parseError(): Nothing = throw ApiException(ErrorKind.Parse)
}
