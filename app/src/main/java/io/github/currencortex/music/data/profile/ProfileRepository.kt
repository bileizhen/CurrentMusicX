package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.auth.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.*
import java.util.Base64

class ProfileRepository(private val api: ApiClient, private val avatarCache: AvatarCache? = null,
    private val session: () -> RequestSession) {
    val revision = MutableStateFlow(0L)
    val scales = MutableStateFlow<Map<String, Double>>(emptyMap())
    val avatarVersions = MutableStateFlow(avatarCache?.versions.orEmpty())
    private suspend fun own(expected: RequestSession) = api.decode<ProfileUser>(
        api.request("GET", "auth/me", authenticated = true, expectedSession = expected))
    suspend fun me() = own(session())
    suspend fun profile(id: Long) = api.get<ProfileDto>("users/$id/profile")
    suspend fun square(query: String, sort: String, offset: Int, listening: Boolean, limit: Int = 30): SquareDto {
        require(sort in setOf("reg", "reg_asc", "name", "days", "listen", "likes"))
        require(limit > 0)
        return api.get("users/square", mapOf("query" to query, "sort" to sort, "offset" to "$offset", "limit" to "$limit", "listening" to if (listening) "1" else "0"))
    }
    suspend fun loadScales() { scales.value = api.get<DecorationScales>("decorations/scales").scales }
    suspend fun decorations(expected: RequestSession = session()): DecorationsDto = api.decode<DecorationsDto>(
        api.request("GET", "decorations", authenticated = true, expectedSession = expected)).also { scales.value = it.scales }
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
    suspend fun avatar(bytes: ByteArray, expected: RequestSession = session(), previousAvatar: String = ""): ProfileUser {
        require(bytes.isNotEmpty() && bytes.size <= 1024 * 1024)
        api.request("PUT", "profile/avatar", body = buildJsonObject { put("data", Base64.getEncoder().encodeToString(bytes)) },
            authenticated = true, expectedSession = expected)
        val previousUrl = rawAvatarUrl(expected.server, previousAvatar)
        // The upload already succeeded even if the subsequent account refresh fails.
        previousUrl?.let { invalidateAvatar(it) }
        val user = own(expected)
        rawAvatarUrl(expected.server, user.avatar)?.takeIf { it != previousUrl }?.let { invalidateAvatar(it) }
        revision.update { it + 1 }
        return user
    }
    private suspend fun invalidateAvatar(url: String) {
        avatarVersions.value = avatarCache?.invalidate(setOf(url))
            ?: (avatarVersions.value + (url to java.util.UUID.randomUUID().toString()))
    }
    suspend fun decorate(id: String, expected: RequestSession = session()): ProfileUser =
        write("decorations/mine", buildJsonObject { put("id", id) }, expected)
    suspend fun password(oldPassword: String, newPassword: String) {
        require(oldPassword.isNotEmpty() && newPassword.length >= 6)
        api.request("PUT", "profile/password", body = buildJsonObject { put("oldPassword", oldPassword); put("newPassword", newPassword) },
            authenticated = true, expectedSession = session())
    }
    fun avatarUrl(server: String, filename: String, versions: Map<String, String> = avatarVersions.value): String? =
        rawAvatarUrl(server, filename)?.let { versionedAvatarUrl(it, versions[it]) }
    private fun rawAvatarUrl(server: String, filename: String): String? {
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
    fun account(user: ProfileUser) = UserDto(user.id, user.username, user.nickname, user.avatar, user.isAdmin, user.isSuper, user.title)
}
