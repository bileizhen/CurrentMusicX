package io.github.currencortex.music.data.room

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.Song
import kotlinx.serialization.json.*

class RoomRepository(private val api: ApiClient, val session: () -> RequestSession) {
    private suspend fun request(method: String, path: String, body: JsonObject? = null,
        query: Map<String, String> = emptyMap(), expected: RequestSession = session()) =
        api.request(method, path, query, body, authenticated = true, expectedSession = expected,
            retryConnection = method == "GET")
    suspend fun list(query: String, offset: Int, scope: RoomScope = RoomScope.PUBLIC) = api.decode<RoomsDto>(request("GET", "rooms",
        query = mapOf("query" to query, "limit" to "20", "offset" to offset.toString(), "scope" to scope.query)))
    suspend fun find(code: String): RoomInfo {
        require(code.matches(Regex("\\d{6}")))
        return api.decode(request("GET", "rooms/search", query = mapOf("code" to code)).jsonObject.getValue("room"))
    }
    suspend fun detail(id: String, expected: RequestSession = session()): RoomDetail =
        api.decode(request("GET", "rooms/$id", expected = expected))
    suspend fun create(name: String, password: String, public: Boolean, free: Boolean, expected: RequestSession,
        needApproval: Boolean = !free, joinLocked: Boolean = false): RoomInfo {
        require(name.trim().length in 1..60 && password.length <= 128)
        return ApiJson.decodeFromJsonElement(request("POST", "rooms", buildJsonObject {
            put("name", name.trim()); put("password", password); put("isPublic", public)
            put("freeMode", free); put("needApproval", needApproval); put("joinLocked", joinLocked)
        }, expected = expected).jsonObject.getValue("room"))
    }
    suspend fun action(id: String, action: String, fields: JsonObject = buildJsonObject {}, expected: RequestSession = session()) {
        require(action in setOf("join", "leave", "close", "kick", "transfer", "admins", "settings", "play", "pause", "seek", "next", "prev", "heartbeat", "stream/renew"))
        request("POST", "rooms/$id/$action", fields, expected = expected)
    }
    suspend fun sync(id: String, expected: RequestSession): Long = request("POST", "rooms/$id/sync", expected = expected).jsonObject.getValue("serverNow").jsonPrimitive.long
    suspend fun reopen(id: String, expected: RequestSession): RoomInfo = api.decode(
        request("POST", "rooms/$id/reopen", expected = expected).jsonObject.getValue("room"))
    suspend fun delete(id: String, expected: RequestSession) { request("DELETE", "rooms/$id", expected = expected) }
    suspend fun bans(id: String, expected: RequestSession): List<RoomBan> =
        api.decode<RoomBansDto>(request("GET", "rooms/$id/bans", expected = expected)).bans
    suspend fun unban(id: String, userId: Long, expected: RequestSession) {
        request("DELETE", "rooms/$id/bans/$userId", expected = expected)
    }
    suspend fun reorder(id: String, qid: String, index: Int, expected: RequestSession) {
        require(index >= 0)
        request("POST", "rooms/$id/queue/reorder", buildJsonObject { put("qid", qid); put("index", index) }, expected = expected)
    }
    suspend fun add(id: String, song: Song, expected: RequestSession) {
        require(!song.video && song.id > 0)
        request("POST", "rooms/$id/queue", buildJsonObject { put("meta", ApiJson.encodeToJsonElement(RoomTrack.from(song))) }, expected = expected)
    }
    suspend fun queueAction(id: String, qid: String, action: String, expected: RequestSession) {
        require(action in setOf("approve", "reject", "remove"))
        request(if (action == "remove") "DELETE" else "POST", "rooms/$id/queue/$qid" + if (action == "remove") "" else "/$action", expected = expected)
    }
}
