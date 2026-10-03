package io.github.currencortex.music.data.profile

import io.github.currencortex.music.data.library.PlaylistDto
import io.github.currencortex.music.data.song.SongDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class ProfileStats(val likes: Int = 0, val favs: Int = 0, val playlists: Int = 0,
    val playDays: Int = 0, val listenMs: Long = 0)
@Serializable data class ProfileUser(val id: Long = 0, val username: String = "", val nickname: String = "",
    val avatar: String = "", val bio: String = "", val avatarDecoration: String = "",
    @SerialName("avatar_decoration") val legacyDecoration: String = "", val stat: ProfileStats = ProfileStats(),
    val publicSquare: Boolean? = null, val public: Boolean? = null,
    @SerialName("public_square") val legacyPublic: Boolean? = null) {
    val decoration get() = avatarDecoration.ifBlank { legacyDecoration }
    val visible get() = publicSquare ?: public ?: legacyPublic
}
@Serializable data class ProfileDto(val user: ProfileUser, val stat: ProfileStats = ProfileStats(),
    val current: SongDto? = null, val recent: List<SongDto> = emptyList(), val playlists: List<PlaylistDto> = emptyList())
@Serializable data class SquareUser(val id: Long, val nickname: String = "", val avatar: String = "", val bio: String = "",
    val avatarDecoration: String = "", @SerialName("avatar_decoration") val legacyDecoration: String = "",
    val likes: Int = 0, val playlists: Int = 0, val days: Int = 0, val listenMs: Long = 0, val current: SongDto? = null) {
    fun profileUser() = ProfileUser(id = id, nickname = nickname, avatar = avatar, bio = bio,
        avatarDecoration = avatarDecoration.ifBlank { legacyDecoration })
}
@Serializable data class SquareStats(val users: Int = 0, val listening: Int = 0)
@Serializable data class SquareDto(val total: Int = 0, val users: List<SquareUser> = emptyList(), val stats: SquareStats = SquareStats())
@Serializable data class Decoration(val id: String, val name: String = "")
@Serializable data class DecorationsDto(val decorations: List<Decoration> = emptyList(), val current: String = "",
    val unlocked: Boolean = false, val listenMs: Long = 0, val minListenMs: Long = 0, val scales: Map<String, Double> = emptyMap())
@Serializable data class DecorationScales(val scales: Map<String, Double> = emptyMap())

fun listeningDuration(ms: Long): String {
    val minutes = ms.coerceAtLeast(0) / 60_000
    return if (minutes >= 60) "${minutes / 60} 小时 ${minutes % 60} 分" else "$minutes 分钟"
}
