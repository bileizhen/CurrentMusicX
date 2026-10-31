package io.github.currencortex.music.data.binding

import io.github.currencortex.music.core.network.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URLEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val projectSync = ProjectBindingSync(session, { owner ->
        val local = requireNotNull(api.netease).sessions.snapshot()
        check(owner.neteaseRevision == null || owner.neteaseRevision == local.revision)
        val cookie = local.credential.cookies.filterKeys { it in setOf("MUSIC_U", "__csrf", "NMTID", "MUSIC_A") }
            .entries.joinToString("; ") { (key, value) -> "$key=$value" }
        BindingCredential(local.credential.profile?.uid ?: 0, cookie)
    }, { login, owner ->
        val result = api.request("POST", "ncmbind/import", body = buildJsonObject {
            put("cookie", login.cookie); put("expectedNcmUid", login.uid)
        }, authenticated = true, expectedSession = owner, projectOnly = true,
            retryConnection = false, callTimeoutMillis = 15_000)
        check(result.jsonObject["ok"]?.jsonPrimitive?.booleanOrNull == true &&
            result.jsonObject["profile"]?.jsonObject?.get("uid")?.jsonPrimitive?.longOrNull == login.uid)
    })
    val projectBindingSync = projectSync.state
    suspend fun syncProjectBinding() { if (api.hasLocalNeteaseLogin) projectSync.sync() }
    private suspend fun shareNewBinding(original: RequestSession) {
        if (api.netease != null && sameOwner(original, loginChanged = true)) projectSync.sync(session())
    }
    val state = MutableStateFlow<BindingState?>(null)
    private val statusMutex = Mutex()
    private var stateOwner: RequestSession? = null
    private fun sameOwner(expected: RequestSession, loginChanged: Boolean = false): Boolean {
        val now = session()
        return expected.server == now.server && expected.token == now.token &&
            (loginChanged || expected.neteaseRevision == null || expected.neteaseRevision == now.neteaseRevision)
    }
    fun clearSession() { stateOwner = null; state.value = null; projectSync.reset() }
    private suspend fun readStatus(): BindingState {
        val expected = session()
        val value = api.decode<BindingState>(api.request("GET", "ncmbind", authenticated = true, expectedSession = expected))
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        stateOwner = expected; state.value = value
        return value
    }
    suspend fun status() = statusMutex.withLock { readStatus() }
    suspend fun medals(): NeteaseMedals {
        val expected = session()
        val value = api.decode<NeteaseMedals>(api.request("GET", "ncmbind/medals", authenticated = true, expectedSession = expected))
        if (expected != session()) throw ApiException(ErrorKind.NeteaseSessionChanged)
        return value
    }
    suspend fun membership(): NeteaseMembership {
        val expected = session()
        val value = api.decode<NeteaseMembership>(api.request("GET", "ncmbind/membership", authenticated = true, expectedSession = expected))
        if (expected != session()) throw ApiException(ErrorKind.NeteaseSessionChanged)
        return value
    }
    suspend fun cachedStatus() = statusMutex.withLock { state.value?.takeIf { stateOwner == session() } ?: readStatus() }
    suspend fun live() = api.get<LiveBinding>("ncmbind/live", authenticated = true)
    suspend fun refresh(expected: RequestSession = session()) { write("POST", "ncmbind/refresh", expected = expected) }
    suspend fun unbind(expected: RequestSession = session()) {
        write("DELETE", "ncmbind", expected = expected)
        if (!sameOwner(expected, loginChanged = api.netease != null)) throw ApiException(ErrorKind.Unauthorized)
        stateOwner = session(); state.value = BindingState(); invalidate()
    }
    private suspend fun write(method: String, path: String, body: JsonObject = buildJsonObject {}, expected: RequestSession = session()) =
        api.request(method, path, body = body, authenticated = true, expectedSession = expected,
            retryConnection = !path.startsWith("ncmbind/phone/"))
    suspend fun sync(expected: RequestSession = session()): SyncResult = ApiJson.decodeFromJsonElement<SyncResult>(write("POST", "ncmbind/sync", expected = expected)).also { invalidate() }
    private fun checkPhoneResult(value: JsonElement) {
        val result = value as? JsonObject ?: return
        val code = (result["code"] as? JsonPrimitive)?.intOrNull
        if (code != null && code != 200) throw ApiException(when (code) {
            406, 429 -> ErrorKind.RateLimited
            else -> ErrorKind.Server
        }, code)
        if ((result["data"] as? JsonPrimitive)?.booleanOrNull == false ||
            (result["ok"] as? JsonPrimitive)?.booleanOrNull == false) throw ApiException(ErrorKind.Unknown)
    }
    suspend fun sendCode(phone: String, country: String, alternate: Boolean = false, expected: RequestSession = session()) {
        require(phone.matches(Regex("[0-9]{5,15}")) && country.matches(Regex("[0-9]{1,4}")))
        // Explicitly selected only: never retry a potentially delivered SMS automatically.
        val value = if (alternate) api.request("GET", "ncm/captcha/sent/v1",
            mapOf("phone" to phone, "ctcode" to country, "confirm" to "1"), authenticated = true,
            expectedSession = expected, retryConnection = false)
        else write("POST", "ncmbind/phone/code", buildJsonObject { put("phone", phone); put("ctcode", country) }, expected)
        checkPhoneResult(value)
        if (!sameOwner(expected)) throw ApiException(ErrorKind.Unauthorized)
    }
    suspend fun bindPhone(phone: String, code: String, country: String, expected: RequestSession = session()) {
        require(phone.matches(Regex("[0-9]{5,15}")) && code.isNotBlank() && country.matches(Regex("[0-9]{1,4}")))
        checkPhoneResult(write("POST", "ncmbind/phone/login", buildJsonObject {
            put("phone", phone); put("captcha", code.trim()); put("ctcode", country)
        }, expected))
        if (!sameOwner(expected, loginChanged = api.netease != null)) throw ApiException(ErrorKind.Unauthorized)
        clearSession(); invalidate()
        shareNewBinding(expected)
    }
    suspend fun qrKey(expected: RequestSession) = ApiJson.decodeFromJsonElement<QrKey>(
        api.request("POST", "ncmbind/qr/key", authenticated = true, expectedSession = expected)).key
    suspend fun qrStatus(key: String, expected: RequestSession) = ApiJson.decodeFromJsonElement<QrStatus>(
        api.request("GET", "ncmbind/qr/check", mapOf("key" to key), authenticated = true, expectedSession = expected)).also {
            if (!sameOwner(expected, loginChanged = api.netease != null && it.code == 803)) throw ApiException(ErrorKind.Unauthorized)
            if (it.code == 803) { clearSession(); invalidate(); shareNewBinding(expected) }
        }
    fun qrUrl(key: String) = "https://music.163.com/login?codekey=${URLEncoder.encode(key, "UTF-8")}"
}
