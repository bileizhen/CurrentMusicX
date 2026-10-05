package io.github.currencortex.music.data.binding

import io.github.currencortex.music.core.network.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URLEncoder

@Serializable data class BoundProfile(val uid: Long = 0, val nickname: String = "", val avatar: String = "")
@Serializable data class BindingState(val bound: Boolean = false, val stale: Boolean = false,
    val profile: BoundProfile? = null, val lastSync: Long = 0, val lastSyncCount: Int = 0,
    // This is the imported CurrentMusic playlist ID, not an upstream NetEase playlist ID.
    @kotlinx.serialization.SerialName("ncmLikedPlId") val syncedLikedPlaylistId: Long = 0)
@Serializable data class LiveBinding(val bound: Boolean = false, val ok: Boolean = false, val profile: BoundProfile? = null)
@Serializable data class QrKey(val key: String)
@Serializable data class QrStatus(val code: Int, val profile: BoundProfile? = null)
@Serializable data class SyncResult(val imported: Int = 0, val tracks: Int = 0, val pending: Int = 0, val failed: Int = 0) {
    fun message() = "已同步 $imported 个歌单 / $tracks 首歌曲" +
        (if (pending > 0) "，$pending 个待续传" else "") + (if (failed > 0) "，$failed 个失败" else "")
}
class BindingRepository(private val api: ApiClient, private val session: () -> RequestSession, private val invalidate: () -> Unit) {
    suspend fun status() = api.get<BindingState>("ncmbind", authenticated = true)
    suspend fun live() = api.get<LiveBinding>("ncmbind/live", authenticated = true)
    suspend fun refresh() { write("POST", "ncmbind/refresh") }
    suspend fun unbind() { write("DELETE", "ncmbind"); invalidate() }
    private suspend fun write(method: String, path: String, body: JsonObject = buildJsonObject {}, expected: RequestSession = session()) =
        api.request(method, path, body = body, authenticated = true, expectedSession = expected)
    suspend fun sync(expected: RequestSession = session()): SyncResult = ApiJson.decodeFromJsonElement<SyncResult>(write("POST", "ncmbind/sync", expected = expected)).also { invalidate() }
    suspend fun sendCode(phone: String, country: String) {
        require(phone.matches(Regex("[0-9]{5,15}")) && country.matches(Regex("[0-9]{1,4}")))
        write("POST", "ncmbind/phone/code", buildJsonObject { put("phone", phone); put("ctcode", country) })
    }
    suspend fun bindPhone(phone: String, code: String, country: String) {
        require(phone.matches(Regex("[0-9]{5,15}")) && code.isNotBlank() && country.matches(Regex("[0-9]{1,4}")))
        write("POST", "ncmbind/phone/login", buildJsonObject { put("phone", phone); put("captcha", code); put("ctcode", country) })
    }
    suspend fun qrKey(expected: RequestSession) = ApiJson.decodeFromJsonElement<QrKey>(
        api.request("POST", "ncmbind/qr/key", authenticated = true, expectedSession = expected)).key
    suspend fun qrStatus(key: String, expected: RequestSession) = ApiJson.decodeFromJsonElement<QrStatus>(
        api.request("GET", "ncmbind/qr/check", mapOf("key" to key), authenticated = true, expectedSession = expected))
    fun qrUrl(key: String) = "https://music.163.com/login?codekey=${URLEncoder.encode(key, "UTF-8")}"
}
