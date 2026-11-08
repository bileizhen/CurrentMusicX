package io.github.currencortex.music.data.admin

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.profile.ProfileUser
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

val ProfileUser.canAdminister get() = isAdmin || isSuper || isOwner

@OptIn(ExperimentalSerializationApi::class)
@Serializable data class AdminUser(
    val id: Long, val username: String = "", val nickname: String = "", val email: String = "", val phone: String = "",
    @JsonNames("is_admin") @Serializable(with = ApiBooleanSerializer::class) val isAdmin: Boolean = false,
    @JsonNames("is_super") @Serializable(with = ApiBooleanSerializer::class) val isSuper: Boolean = false,
    @JsonNames("is_owner") @Serializable(with = ApiBooleanSerializer::class) val isOwner: Boolean = false,
    @Serializable(with = ApiBooleanSerializer::class) val disabled: Boolean = false,
    val title: String = "", val points: Long = 0, val level: Int = 1,
    val likes: Long = 0, val playlists: Long = 0, val plays: Long = 0, val sessions: Int = 0,
) { val name get() = nickname.ifBlank { username } }
@Serializable data class AdminUsers(val total: Int = 0, val users: List<AdminUser> = emptyList())
@Serializable data class AdminAnnouncement(val id: Long, val title: String = "", val body: String = "",
    val link: String = "", val pinned: Boolean = false, val removed: Boolean = false, val author: String = "",
    val createdAt: Long = 0, val canEdit: Boolean = false)
@Serializable data class AdminAnnouncements(val items: List<AdminAnnouncement> = emptyList())
@Serializable data class AdminSession(val id: String, val user_id: Long, val username: String = "",
    val nickname: String = "", val device: String = "", val platform: String = "",
    val created_at: Long = 0, val last_seen: Long = 0)
@Serializable data class AdminSessions(val sessions: List<AdminSession> = emptyList())

enum class AdminUserAction(val path: String, val label: String) {
    DISABLE("disable", "禁用账号"), KICK("kick", "强制下线"), PASSWORD("password", "重置密码"),
    TITLE("title", "设置头衔"), PROGRESSION("progression", "设置积分与等级"),
    ADMIN("admin", "管理员权限"), SUPER("super", "超级管理员权限"), OWNER("owner", "站长权限");

    fun allowed(actor: ProfileUser, target: AdminUser): Boolean {
        if (!actor.canAdminister || actor.id == target.id || target.username == "__internal__") return false
        if (target.isOwner && !actor.isOwner) return false
        if (target.isSuper && !(actor.isSuper || actor.isOwner)) return false
        return when (this) {
            OWNER -> actor.isOwner
            SUPER -> (actor.isSuper || actor.isOwner) && !target.isOwner
            ADMIN -> !target.isSuper && !target.isOwner
            else -> true
        }
    }
}

/** All management requests stay on the selected project server and are bound to one login. */
class AdminRepository(private val api: ApiClient) {
    suspend fun read(path: String, owner: RequestSession, query: Map<String, String> = emptyMap()): JsonObject =
        api.request("GET", path, query, authenticated = true, expectedSession = owner, projectOnly = true).jsonObject

    suspend fun actor(owner: RequestSession): ProfileUser = api.decode<ProfileUser>(read("auth/me", owner))
    suspend fun users(owner: RequestSession, query: String, sort: String, offset: Int): AdminUsers =
        api.decode(read("admin/users", owner, mapOf("query" to query, "sort" to sort,
            "offset" to offset.toString(), "limit" to PAGE_SIZE.toString())))
    suspend fun announcements(owner: RequestSession): AdminAnnouncements = api.decode(read("admin/announcements", owner))
    suspend fun sessions(owner: RequestSession): AdminSessions = api.decode(read("admin/sessions", owner))
    suspend fun write(method: String, path: String, body: JsonObject, owner: RequestSession) {
        api.request(method, path, body = body, authenticated = true, expectedSession = owner,
            projectOnly = true, retryConnection = false)
    }
    companion object { const val PAGE_SIZE = 20 }
}

internal fun JsonObject.obj(key: String) = this[key] as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.rows(key: String) = (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
internal fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.flag(key: String) = text(key) in setOf("true", "1")
internal fun JsonObject.number(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
