package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.auth.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.*
import java.util.Base64

class ProfileRepository(private val api: ApiClient, private val session: () -> RequestSession) {
    val revision = MutableStateFlow(0L)
    val scales = MutableStateFlow<Map<String, Double>>(emptyMap())
    val avatarVersions = MutableStateFlow<Map<Long, Long>>(emptyMap())
    private suspend fun own(expected: RequestSession) = ApiJson.decodeFromJsonElement<ProfileUser>(
        api.request("GET", "auth/me", authenticated = true, expectedSession = expected))
    suspend fun me() = own(session())
    suspend fun profile(id: Long) = api.get<ProfileDto>("users/$id/profile")
    suspend fun square(query: String, sort: String, offset: Int, listening: Boolean): SquareDto {
        require(sort in setOf("reg", "reg_asc", "name", "days", "listen", "likes"))
        return api.get("users/square", mapOf("query" to query, "sort" to sort, "offset" to "$offset", "limit" to "30", "listening" to if (listening) "1" else "0"))
    }
    suspend fun loadScales() { scales.value = api.get<DecorationScales>("decorations/scales").scales }
    suspend fun decorations(): DecorationsDto = api.get<DecorationsDto>("decorations", authenticated = true).also { scales.value = it.scales }
    private suspend fun write(path: String, body: JsonObject, expected: RequestSession = session()): ProfileUser {
        api.request("PUT", path, body = body, authenticated = true, expectedSession = expected)
        val user = own(expected)
        revision.update { it + 1 }
        return user
    }
    suspend fun update(nickname: String, bio: String, visible: Boolean?, expected: RequestSession = session()): ProfileUser {
        require(nickname.isNotBlank())
        return write("profile", buildJsonObject { put("nickname", nickname.trim()); put("bio", bio); if (visible != null) put("publicSquare", visible) }, expected)
    }
    suspend fun avatar(bytes: ByteArray, expected: RequestSession = session()): ProfileUser {
        require(bytes.isNotEmpty() && bytes.size <= 1024 * 1024)
        return write("profile/avatar", buildJsonObject { put("data", Base64.getEncoder().encodeToString(bytes)) }, expected).also { user ->
            avatarVersions.update { it + (user.id to System.currentTimeMillis()) }
        }
    }
    suspend fun decorate(id: String): ProfileUser = write("decorations/mine", buildJsonObject { put("id", id) })
    suspend fun password(oldPassword: String, newPassword: String) {
        require(oldPassword.isNotEmpty() && newPassword.length >= 6)
        api.request("PUT", "profile/password", body = buildJsonObject { put("oldPassword", oldPassword); put("newPassword", newPassword) },
            authenticated = true, expectedSession = session())
    }
    fun avatarUrl(server: String, filename: String): String? {
        if (filename.startsWith("https://") || filename.startsWith("http://")) return filename
        if (!filename.matches(Regex("[a-zA-Z0-9_.-]+")) || filename.contains("..")) return null
        return ServerUrl.endpoint(server, "avatar/$filename", emptyMap()).toString()
    }
    fun decorationUrl(server: String, id: String): String? {
        if (id.isBlank() || id.contains("..") || id.any { it == '/' || it == '\\' || it.isISOControl() }) return null
        // Production IDs include Chinese names. Encode the ID as one segment,
        // including reserved characters, instead of restricting it to ASCII.
        return ServerUrl.endpoint(server, "decor", emptyMap()).newBuilder().addPathSegment("$id.gif").build().toString()
    }
    fun account(user: ProfileUser) = UserDto(user.id, user.username, user.nickname, user.avatar)
}
