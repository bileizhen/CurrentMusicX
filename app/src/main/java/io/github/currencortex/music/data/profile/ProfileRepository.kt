package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.auth.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.util.Base64

internal const val PROFILE_BANNER_LIMIT = 1024 * 1024
internal const val PROFILE_BANNER_VIDEO_LIMIT = 16L * 1024 * 1024
internal const val PROFILE_BANNER_VIDEO_MAX_MS = 5_000L
internal const val PROFILE_BANNER_VIDEO_MAX_DIMENSION = 3840
internal const val PROFILE_BANNER_VIDEO_MAX_PIXELS = 8_294_400L
private val profileBannerPath = Regex("/users/([0-9]+)/banner\\?v=([0-9a-f]{24})")

@Serializable data class ProfileBannerUpload(val bannerUrl: String, val bannerType: String = "")

class ProfileRepository(private val api: ApiClient, private val avatarCache: AvatarCache? = null,
    private val session: () -> RequestSession) {
    val revision = MutableStateFlow(0L)
    val scales = MutableStateFlow<Map<String, Double>>(emptyMap())
    val avatarVersions = MutableStateFlow(avatarCache?.versions.orEmpty())
    private val avatarMutex = Mutex()
    private val bannerMutex = Mutex()
    private suspend fun own(expected: RequestSession) = api.decode<ProfileUser>(
        api.request("GET", "auth/me", authenticated = true, expectedSession = expected))
    suspend fun me() = own(session())
    suspend fun profile(id: Long) = api.get<ProfileDto>("users/$id/profile")
    suspend fun square(query: String, sort: String, offset: Int, listening: Boolean, limit: Int = 30): SquareDto {
        require(sort in setOf("composite", "active", "level", "reg", "reg_asc", "name", "days", "listen", "likes"))
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
    suspend fun avatar(bytes: ByteArray, expected: RequestSession = session(), previousAvatar: String = ""): ProfileUser =
        avatarMutex.withLock { uploadAvatar(bytes, expected, previousAvatar) }

    suspend fun banner(bytes: ByteArray?, expected: RequestSession): String =
        bannerMutex.withLock { uploadBanner(bytes, expected).bannerUrl }

    suspend fun bannerUpload(bytes: ByteArray?, expected: RequestSession): ProfileBannerUpload =
        bannerMutex.withLock { uploadBanner(bytes, expected) }

    suspend fun bannerVideo(file: File, contentType: String, expected: RequestSession): ProfileBannerUpload =
        bannerMutex.withLock {
            require(file.isFile && file.length() in 1L..PROFILE_BANNER_VIDEO_LIMIT)
            require(contentType in setOf("video/mp4", "video/quicktime"))
            val result = api.decode<ProfileBannerUpload>(api.uploadProfileBannerVideo(expected, file, contentType))
            revision.update { it + 1 }
            result
        }

    suspend fun bannerIfDefault(bytes: ByteArray, expected: RequestSession, accountId: Long,
        stillAllowed: () -> Boolean): String? = bannerMutex.withLock {
        if (!stillAllowed()) return@withLock null
        val current = own(expected)
        if (current.id != accountId || current.bannerUrl.isNotBlank() || !stillAllowed()) return@withLock null
        uploadBanner(bytes, expected).bannerUrl
    }

    private suspend fun uploadBanner(bytes: ByteArray?, expected: RequestSession): ProfileBannerUpload {
        val body = bytes?.let {
            require(it.isNotEmpty() && it.size <= PROFILE_BANNER_LIMIT)
            buildJsonObject { put("data", Base64.getEncoder().encodeToString(it)) }
        }
        val result = api.decode<ProfileBannerUpload>(api.request(if (body == null) "DELETE" else "PUT", "profile/banner",
            body = body, authenticated = true, expectedSession = expected, retryConnection = false))
        revision.update { it + 1 }
        return result
    }

    fun bannerImageUrl(server: String, user: ProfileUser): String? {
        val path = profileBannerPath.matchEntire(user.bannerUrl) ?: return null
        if (path.groupValues[1].toLongOrNull() != user.id) return null
        return ServerUrl.endpoint(api.routeServer(server), "users/${user.id}/banner",
            mapOf("v" to path.groupValues[2])).toString()
    }

    suspend fun avatarIfDefault(bytes: ByteArray, expected: RequestSession, accountId: Long,
        stillAllowed: () -> Boolean): ProfileUser? = avatarMutex.withLock {
        if (!stillAllowed()) return@withLock null
        val current = own(expected)
        if (current.id != accountId || current.avatar.isNotBlank() || !stillAllowed()) return@withLock null
        uploadAvatar(bytes, expected, current.avatar)
    }

    private suspend fun uploadAvatar(bytes: ByteArray, expected: RequestSession, previousAvatar: String): ProfileUser {
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
        require(oldPassword.isNotEmpty() && newPassword.length in 6..64)
        api.request("PUT", "profile/password", body = buildJsonObject { put("oldPassword", oldPassword); put("newPassword", newPassword) },
            authenticated = true, expectedSession = session(), retryConnection = false)
    }
    suspend fun sendPasswordCode() {
        api.request("POST", "profile/password/code", authenticated = true, expectedSession = session(), retryConnection = false)
    }
    suspend fun passwordWithEmailCode(emailCode: String, newPassword: String) {
        require(emailCode.isNotBlank() && newPassword.length in 6..64)
        api.request("PUT", "profile/password", body = buildJsonObject {
            put("emailCode", emailCode.trim()); put("newPassword", newPassword)
        }, authenticated = true, expectedSession = session(), retryConnection = false)
    }
    fun avatarUrl(server: String, filename: String, versions: Map<String, String> = avatarVersions.value): String? =
        rawAvatarUrl(server, filename)?.let { versionedAvatarUrl(it, versions[it]) }
    private fun rawAvatarUrl(server: String, filename: String): String? {
        if (filename.startsWith("https://") || filename.startsWith("http://")) return filename
        if (!filename.matches(Regex("[a-zA-Z0-9_.-]+")) || filename.contains("..")) return null
        return ServerUrl.endpoint(api.routeServer(server), "avatar/$filename", emptyMap()).toString()
    }
    fun decorationUrl(server: String, id: String): String? {
        if (id.isBlank() || id.contains("..") || id.any { it == '/' || it == '\\' || it.isISOControl() }) return null
        // Production IDs include Chinese names. Encode the ID as one segment,
        // including reserved characters, instead of restricting it to ASCII.
        return ServerUrl.endpoint(api.routeServer(server), "decor", emptyMap()).newBuilder().addPathSegment("$id.gif").build().toString()
    }
    fun account(user: ProfileUser) = UserDto(user.id, user.username, user.nickname, user.avatar, user.isAdmin, user.isSuper, user.title, user.level, user.points, user.isOwner)
}
