package io.github.currencortex.music.data.room

import io.github.currencortex.music.data.song.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlin.math.roundToLong

/** Legacy IDs are numeric; accepting an opaque string also supports newer server deployments. */
object RoomIdSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("RoomId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) { encoder.encodeString(value) }
    override fun deserialize(decoder: Decoder): String {
        val value = (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive?.content ?: decoder.decodeString()
        if (!value.matches(Regex("[A-Za-z0-9_-]{1,128}"))) throw SerializationException("Invalid room identifier")
        return value
    }
}
object RoomBooleanSerializer : KSerializer<Boolean> {
    override val descriptor = PrimitiveSerialDescriptor("RoomBoolean", PrimitiveKind.BOOLEAN)
    override fun serialize(encoder: Encoder, value: Boolean) { encoder.encodeBoolean(value) }
    override fun deserialize(decoder: Decoder): Boolean {
        if (decoder !is JsonDecoder) return decoder.decodeBoolean()
        return when (decoder.decodeJsonElement().jsonPrimitive.content.lowercase()) {
            "true", "1" -> true; "false", "0" -> false
            else -> throw SerializationException("Invalid room boolean")
        }
    }
}
object RoomPositionSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("RoomPosition", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Long) { encoder.encodeLong(value) }
    override fun deserialize(decoder: Decoder): Long {
        if (decoder !is JsonDecoder) return decoder.decodeLong()
        val value = decoder.decodeJsonElement().jsonPrimitive.doubleOrNull ?: throw SerializationException("Invalid room position")
        if (!value.isFinite() || value < 0 || value > Long.MAX_VALUE.toDouble()) throw SerializationException("Invalid room position")
        return value.roundToLong()
    }
}

enum class RoomRole { OWNER, ADMIN, MEMBER;
    val controls get() = this != MEMBER
    companion object { fun parse(value: String) = entries.firstOrNull { it.name.equals(value, true) } ?: MEMBER }
}
@Serializable data class RoomInfo(@Serializable(with = RoomIdSerializer::class) val id: String, val name: String = "", val code: String = "",
    @Serializable(with = RoomBooleanSerializer::class) val hasPassword: Boolean = false,
    @Serializable(with = RoomBooleanSerializer::class) val freeMode: Boolean = false,
    @Serializable(with = RoomBooleanSerializer::class) val isPublic: Boolean = true,
    @Serializable(with = RoomBooleanSerializer::class) val joinLocked: Boolean = false,
    @Serializable(with = RoomBooleanSerializer::class) val needApproval: Boolean = true)
@Serializable data class RoomCard(@Serializable(with = RoomIdSerializer::class) val id: String, val name: String = "", val code: String = "", val online: Int = 0,
    @SerialName("owner_name") val ownerName: String = "", @SerialName("free_mode") @Serializable(with = RoomBooleanSerializer::class) val freeMode: Boolean = false)
@Serializable data class RoomsDto(val rooms: List<RoomCard> = emptyList(), val total: Int = 0)
@Serializable data class RoomMember(val userId: Long, val nickname: String = "", val avatar: String = "",
    val avatarDecoration: String = "", val role: String = "member")
@Serializable data class RoomTrack(@SerialName("ncm_id") val id: Long = 0, val name: String = "",
    val artists: String = "", val pic: String = "", val duration: Long = 0) {
    fun song(fallback: Long = id) = Song(id.takeIf { it > 0 } ?: fallback, name, artists, cover = pic, durationMs = duration)
    companion object { fun from(song: Song) = RoomTrack(song.id, song.name, song.artists, song.cover, song.durationMs) }
}
@Serializable data class RoomStream(val url: String = "")
@Serializable data class RoomTimeline(val trackNcmId: Long = 0, val trackMeta: RoomTrack? = null,
    val stream: RoomStream? = null, @Serializable(with = RoomBooleanSerializer::class) val playing: Boolean = false,
    @Serializable(with = RoomPositionSerializer::class) val basePosition: Long = 0, val baseAt: Long = 0)
@Serializable data class RoomQueueItem(@Serializable(with = RoomIdSerializer::class) val id: String, val name: String = "", val artists: String = "",
    val status: String = "pending", val requester: String = "", @Serializable(with = RoomBooleanSerializer::class) val mine: Boolean = false)
@Serializable data class RoomDetail(val room: RoomInfo, val members: List<RoomMember> = emptyList(),
    val queue: List<RoomQueueItem> = emptyList(), val timeline: RoomTimeline? = null, val latestSeq: Long = 0) {
    fun role(userId: Long) = members.firstOrNull { it.userId == userId }?.let { RoomRole.parse(it.role) }
}
object RoomPermissions {
    fun kick(actor: RoomRole?, target: RoomRole) = actor == RoomRole.OWNER && target != RoomRole.OWNER || actor == RoomRole.ADMIN && target == RoomRole.MEMBER
    fun remove(actor: RoomRole?, item: RoomQueueItem) = actor != null && item.status != "playing" && (actor.controls || item.mine)
}
