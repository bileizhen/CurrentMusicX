package io.github.currencortex.music.data.netease.listen

import io.github.currencortex.music.core.netease.NeteaseClientProfile
import io.github.currencortex.music.core.netease.NeteaseSession
import io.github.currencortex.music.core.netease.NeteaseTransport
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.serialization.json.*

/** Protocol recovered from NetEase's official fate/index.761e5442.js, linked by its 9.5.30 APK. */
internal class NeteaseListenTogetherFateRepository(private val transport: NeteaseTransport) : ListenTogetherFateProtocol {
    private val history = NeteaseListenTogetherHistoryRepository(transport)

    override suspend fun captureSession(): NeteaseSession = history.captureSession()
    override fun checkSession(expected: NeteaseSession) = history.checkSession(expected)

    override suspend fun fate(expected: NeteaseSession, partnerUid: Long, scene: Int): ListenTogetherFateReport {
        require(partnerUid > 0 && scene >= 0)
        requireMembership(expected)
        val body = request("single/record/new", buildJsonObject {
            put("partnerUid", partnerUid); put("scene", scene)
        }, expected)
        val data = body["data"] as? JsonObject ?: parseError()
        // The official H5 treats an empty successful object as a deleted report.
        if (data.isEmpty()) throw ApiException(ErrorKind.NotFound)
        val returnedPartner = data.long("partnerUid")
        if (returnedPartner != null && returnedPartner != partnerUid) parseError()
        val returnedType = (data["ltType"] as? JsonPrimitive)?.intOrNull ?: parseError()
        if (returnedType <= 0) parseError()
        val owner = expected.credential.profile?.uid?.takeIf { it > 0 } ?: throw ApiException(ErrorKind.NeteaseBindingRequired)
        val masked = data.bool("newUnmaskFlag") == false
        val display = if (masked) data.obj("matchPlayInfo")?.obj("maskDisplay") else null
        val self = ListenTogetherParticipant(owner, data.text("selfNick"),
            if (masked) display?.obj("selfMaskAsset")?.text("assetUrl").orEmpty().https() else data.text("selfAvatar").https(), masked)
        val peer = ListenTogetherParticipant(partnerUid,
            if (masked) display?.text("peerDisplayName").orEmpty().ifBlank { "匿名听友" } else data.text("partnerNick"),
            if (masked) display?.obj("peerMaskAsset")?.text("assetUrl").orEmpty().https() else data.text("partnerAvatar").https(), masked)
        return ListenTogetherFateReport(partnerUid, self, peer, data.stat("totalDuration"), data.stat("totalSongNum"),
            data.stat("commonRedSongNum"), data.timestamp("startListenTime"), data.stat("startListenDayNum"),
            data.timestamp("lastestTime"), data.text("mostPopSongName"), data.stat("longestDuration"),
            returnedType, data.int("vipType"), data.bool("canInvite") == true, data.text("failMsg"), data.bool("openAct") == true)
    }

    override suspend fun deleteFate(expected: NeteaseSession, partnerUid: Long, ltType: Int, deleteRecords: Boolean) {
        require(partnerUid > 0 && ltType > 0)
        requireMembership(expected)
        // The official confirmation initializes recordDel=false. The caller must
        // separately opt in to deleting all listening records with the same peer.
        val body = request("rank/delete", buildJsonObject {
            put("recordDel", deleteRecords); put("partnerUid", partnerUid); put("ltType", ltType)
        }, expected)
        val result = body["data"]
        if ((result as? JsonPrimitive)?.booleanOrNull == false ||
            (result as? JsonObject)?.bool("success") == false || result?.let { (it as? JsonObject)?.bool("result") } == false)
            throw ApiException(ErrorKind.Server)
    }

    private suspend fun requireMembership(expected: NeteaseSession) {
        if (!history.history(expected).vip) throw ApiException(ErrorKind.Forbidden)
        checkSession(expected)
    }

    private suspend fun request(path: String, params: JsonObject, expected: NeteaseSession): JsonObject {
        checkSession(expected)
        val body = transport.request("/api/listen/together/$path", params, session = expected,
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
        return body
    }

    private fun JsonObject.obj(key: String) = get(key) as? JsonObject
    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.long(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
    private fun JsonObject.stat(key: String) = long(key)?.takeIf { it >= 0 }
    private fun JsonObject.timestamp(key: String) = long(key)?.takeIf { it > 0 }
    private fun JsonObject.int(key: String) = (get(key) as? JsonPrimitive)?.intOrNull ?: 0
    private fun JsonObject.bool(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull
    private fun String.https() = replaceFirst(Regex("^http://"), "https://")
    private fun parseError(): Nothing = throw ApiException(ErrorKind.Parse)
}
