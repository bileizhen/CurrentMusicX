package io.github.currencortex.music.data.netease.listen

import io.github.currencortex.music.core.netease.NeteaseClientProfile
import io.github.currencortex.music.core.netease.NeteaseSession
import io.github.currencortex.music.core.netease.NeteaseTransport
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.serialization.json.*

/**
 * Direct, session-bound NetEase two-person listen-together protocol.
 * Paths and payloads were recovered from NetEase Android 9.5.30 rg0.t0 inner classes.
 * Heartbeat is liveness only. playlist() is the original recovery snapshot route;
 * polling it is an app fallback and does not claim NetEase IM delivery latency.
 */
internal class NeteaseListenTogetherRepository(private val transport: NeteaseTransport) : ListenTogetherProtocol {
    override suspend fun captureSession(): NeteaseSession = transport.sessions.snapshot().also { expected ->
        if (expected.credential.cookies["MUSIC_U"].isNullOrBlank() || expected.credential.stale ||
            (expected.credential.profile?.uid ?: 0) <= 0) throw ApiException(ErrorKind.NeteaseBindingRequired)
    }

    override fun checkSession(expected: NeteaseSession) = transport.sessions.check(expected)

    override suspend fun create(expected: NeteaseSession, refer: String, inviteUid: Long?): ListenTogetherRoom {
        require(inviteUid == null || inviteUid > 0)
        return roomResult(request("room/create", buildJsonObject {
            put("refer", refer); put("robotUid", ""); put("inviteUid", inviteUid?.toString().orEmpty())
        }, expected))
    }

    override suspend fun accept(expected: NeteaseSession, invitation: ListenTogetherInvitation,
        refer: String): ListenTogetherRoom = roomResult(request("play/invitation/accept", buildJsonObject {
            put("roomId", invitation.roomId); put("inviterId", invitation.inviterId.toString())
            if (refer.isNotEmpty()) put("refer", refer)
        }, expected)).also { room ->
            check(room.roomId == invitation.roomId) { "邀请房间与返回房间不一致，请检查已有会话" }
        }

    override suspend fun status(expected: NeteaseSession): ListenTogetherStatus {
        val data = request("status/get", buildJsonObject {}, expected).obj("data") ?: parseError()
        val inRoom = data.bool("inRoom") ?: parseError()
        return ListenTogetherStatus(inRoom, if (inRoom) data.obj("roomInfo")?.let(::parseRoom) ?: parseError() else null,
            data.text("status"))
    }

    override suspend fun requestSync(expected: NeteaseSession, roomId: String) {
        requireRoom(roomId)
        request("sync/notice", buildJsonObject { put("roomId", roomId) }, expected)
    }

    override suspend fun heartbeat(expected: NeteaseSession, roomId: String, songId: Long,
        playing: Boolean, positionMs: Long, version: List<ListenTogetherVersion>): Boolean {
        requireRoom(roomId); require(songId >= 0 && positionMs >= 0)
        val data = request("heartbeat", buildJsonObject {
            put("roomId", roomId); put("songId", songId.toString()); put("playStatus", if (playing) "PLAY" else "PAUSE")
            put("progress", positionMs.toString()); put("playlistVersion", versionJson(version).toString())
        }, expected).obj("data") ?: parseError()
        return data.bool("result") ?: data.bool("isResult") ?: parseError()
    }

    override suspend fun playlist(expected: NeteaseSession, roomId: String,
        local: ListenTogetherQueue): ListenTogetherSnapshot {
        requireRoom(roomId)
        val body = request("sync/playlist/get", buildJsonObject {
            put("roomId", roomId); put("playlistParam", queueJson(local, report = false).toString())
        }, expected)
        val data = body.obj("data") ?: parseError()
        val playlist = data.obj("playlist") ?: parseError()
        val display = playlist.obj("displayList")
        val random = playlist.obj("randomList")
        val queue = local.copy(songIds = resultList(display, local.songIds), randomSongIds = resultList(random, local.randomSongIds),
            playMode = playlist.text("playMode").ifBlank { local.playMode },
            version = (playlist["version"] as? JsonArray)?.map { entry ->
                val item = entry as? JsonObject ?: parseError()
                ListenTogetherVersion(item.long("userId") ?: parseError(), item.long("version") ?: parseError(),
                    (item["outerId"] as? JsonPrimitive)?.contentOrNull)
            } ?: local.version)
        return ListenTogetherSnapshot(queue, data.obj("playCommand")?.let(::parsePlayback))
    }

    override suspend fun reportPlayback(expected: NeteaseSession, roomId: String, playback: ListenTogetherPlayback) {
        requireRoom(roomId)
        require(playback.commandType in setOf("PLAY", "PAUSE", "PROGRESS", "GOTO", "NEXT", "PREVIOUS"))
        require(playback.targetSongId > 0 && playback.formerSongId >= 0 && playback.positionMs >= 0 && playback.clientSeq >= 0)
        require(playback.triggerType in setOf("MANUAL", "AUTO"))
        request("play/command/report", buildJsonObject {
            put("roomId", roomId); put("commandInfo", buildJsonObject {
                put("commandType", playback.commandType); put("targetSongId", playback.targetSongId.toString())
                put("formerSongId", playback.formerSongId.toString()); put("progress", playback.positionMs)
                put("playStatus", if (playback.playing) "PLAY" else "PAUSE"); put("triggerType", playback.triggerType)
                put("clientSeq", playback.clientSeq)
            }.toString())
        }, expected)
    }

    override suspend fun reportQueue(expected: NeteaseSession, roomId: String, queue: ListenTogetherQueue) {
        requireRoom(roomId)
        require(queue.songIds.isNotEmpty() && queue.songIds.all { it > 0 } && queue.songIds.size <= 10000 &&
            queue.randomSongIds.all { it > 0 } && queue.clientSeq >= 0 && queue.anchorPosition >= 0)
        request("sync/list/command/report", buildJsonObject {
            put("roomId", roomId); put("playlistParam", queueJson(queue, report = true).toString())
            put("clientSeq", queue.clientSeq.toString())
        }, expected)
    }

    override suspend fun leave(expected: NeteaseSession, roomId: String) {
        requireRoom(roomId)
        request("end/v2", buildJsonObject {
            put("roomId", roomId); put("shareInfo", "null"); put("needRecord", false)
            put("scene", ""); put("exitType", "NORMAL_END")
        }, expected)
    }

    private suspend fun request(path: String, data: JsonObject, expected: NeteaseSession): JsonObject {
        transport.sessions.check(expected)
        if (expected.credential.cookies["MUSIC_U"].isNullOrBlank() || expected.credential.stale)
            throw ApiException(ErrorKind.NeteaseBindingRequired)
        val result = transport.request("/api/listen/together/$path", data, session = expected,
            clientProfile = NeteaseClientProfile.ANDROID).body
        val code = (result["code"] as? JsonPrimitive)?.intOrNull ?: parseError()
        if (code != 200) throw ApiException(when (code) {
            301, 401 -> ErrorKind.NeteaseBindingRequired
            403 -> ErrorKind.Forbidden
            429 -> ErrorKind.RateLimited
            404, 488 -> ErrorKind.NotFound
            else -> ErrorKind.Server
        }, code)
        val responseData = result.obj("data")
        if ((path != "heartbeat" && responseData?.bool("result") == false) || responseData?.bool("success") == false)
            throw ApiException(ErrorKind.Server, code)
        transport.sessions.check(expected)
        return result
    }

    private fun roomResult(body: JsonObject): ListenTogetherRoom {
        val data = body.obj("data") ?: parseError()
        check(data.text("type") != "ALREADY_IN_ROOM") { "网易云已有一起听会话，请选择“恢复一起听”" }
        if (data.text("type") == "UNKNOWN") throw ApiException(ErrorKind.Server)
        return data.obj("roomInfo")?.let(::parseRoom) ?: parseError()
    }

    private fun parseRoom(data: JsonObject): ListenTogetherRoom {
        val id = data.text("roomId").takeIf { ListenTogetherInvitation.validRoomId(it) } ?: parseError()
        val users = (data["roomUsers"] as? JsonArray)?.mapNotNull { item ->
            val user = item as? JsonObject ?: return@mapNotNull null
            user.long("userId")?.takeIf { it > 0 }?.let {
                ListenTogetherParticipant(it, user.text("nickname"), user.text("avatarUrl").replace("http:", "https:"))
            }
        }?.distinctBy { it.userId }.orEmpty()
        return ListenTogetherRoom(id, data.long("creatorId") ?: 0, users, data.text("roomType").ifBlank { "FRIEND" },
            data.long("effectiveDurationMs") ?: 0, data.long("roomCreateTime") ?: 0)
    }

    private fun parsePlayback(data: JsonObject): ListenTogetherPlayback? {
        val song = data.long("targetSongId")?.takeIf { it > 0 } ?: return null
        val status = data.text("playStatus")
        if (status !in setOf("PLAY", "PAUSE")) return null
        return ListenTogetherPlayback(data.text("commandType"), song, data.long("formerSongId") ?: 0, status == "PLAY",
            (data.long("progress") ?: 0).coerceAtLeast(0), (data.long("clientSeq") ?: 0).toInt(),
            data.long("serverSeq") ?: 0, data.long("userId") ?: 0, data.text("triggerType"))
    }

    private fun resultList(data: JsonObject?, previous: List<Long>): List<Long> = when {
        data == null || data.bool("changed") == false -> previous
        else -> data.longList("result") ?: parseError()
    }

    private fun queueJson(queue: ListenTogetherQueue, report: Boolean): JsonObject = buildJsonObject {
        require(queue.playMode in setOf("ORDER_LOOP", "SINGLE_LOOP", "RANDOM"))
        put("anchorSongId", queue.anchorSongId.toString()); put("anchorPosition", queue.anchorPosition)
        put("playMode", queue.playMode); put("displayList", JsonArray(queue.songIds.map { JsonPrimitive(it.toString()) }))
        put("randomList", JsonArray(queue.randomSongIds.map { JsonPrimitive(it.toString()) }))
        if (report) {
            put("commandType", "REPLACE"); put("clientSeq", queue.clientSeq)
            put("version", versionJson(queue.version))
        }
    }

    private fun versionJson(version: List<ListenTogetherVersion>) = JsonArray(version.map { entry -> buildJsonObject {
        put("userId", entry.userId); put("version", entry.version)
        put("outerId", entry.outerId?.let(::JsonPrimitive) ?: JsonNull)
    } })

    private fun requireRoom(roomId: String) = require(ListenTogetherInvitation.validRoomId(roomId))
    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.longList(key: String): List<Long>? = (this[key] as? JsonArray)?.map { item ->
        (item as? JsonPrimitive)?.longOrNull ?: parseError()
    }
    private fun parseError(): Nothing = throw ApiException(ErrorKind.Parse)

    companion object { const val HEARTBEAT_INTERVAL_MS = 20_000L }
}
