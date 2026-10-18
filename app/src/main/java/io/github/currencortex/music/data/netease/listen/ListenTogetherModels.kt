package io.github.currencortex.music.data.netease.listen

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI
import java.net.URLDecoder

data class ListenTogetherParticipant(val userId: Long, val nickname: String = "", val avatarUrl: String = "")

data class ListenTogetherRoom(val roomId: String, val creatorId: Long,
    val participants: List<ListenTogetherParticipant> = emptyList(), val roomType: String = "FRIEND",
    val effectiveDurationMs: Long = 0, val roomCreateTime: Long = 0)

data class ListenTogetherStatus(val inRoom: Boolean, val room: ListenTogetherRoom? = null, val status: String = "")

/** NetEase uses millisecond progress and independent client/server sequence numbers. */
data class ListenTogetherPlayback(val commandType: String = "PLAY", val targetSongId: Long = 0,
    val formerSongId: Long = 0, val playing: Boolean = false, val positionMs: Long = 0,
    val clientSeq: Int = 0, val serverSeq: Long = 0, val userId: Long = 0, val triggerType: String = "MANUAL")

/** Version vectors are per-user objects, not a list of sequence integers. */
data class ListenTogetherVersion(val userId: Long, val version: Long, val outerId: String? = null)

data class ListenTogetherQueue(val songIds: List<Long> = emptyList(), val randomSongIds: List<Long> = emptyList(),
    val playMode: String = "ORDER_LOOP", val anchorSongId: Long = 0, val anchorPosition: Int = 0,
    val version: List<ListenTogetherVersion> = emptyList(), val clientSeq: Int = 0)

data class ListenTogetherSnapshot(val queue: ListenTogetherQueue, val playback: ListenTogetherPlayback? = null)

data class ListenTogetherInvitation(val roomId: String, val inviterId: Long) {
    init { require(validRoomId(roomId) && inviterId > 0) }

    /** Same public invitation URL and fields as NetEase 9.5.30 LTInviteShareHandler.k. */
    fun shareUrl(songId: Long? = null): String = SHARE_BASE.toHttpUrl().newBuilder()
        .addQueryParameter("roomId", roomId).addQueryParameter("inviterId", inviterId.toString())
        .apply { songId?.takeIf { it > 0 }?.let { addQueryParameter("songId", it.toString()) } }.build().toString()

    companion object {
        private const val SHARE_BASE = "https://st.music.163.com/listen-together/share/index.html"
        internal fun candidate(text: String): String = (Regex("(?:https?://|orpheus://)[^\\s<>\"，。]+")
            .find(text.trim())?.value ?: text.trim()).trimEnd(')', '）', ']', '。', '，')

        fun isSupported(text: String): Boolean = parse(text) != null || candidate(text).toHttpUrlOrNull()?.let {
            it.scheme == "https" && it.host == "163cn.tv" && it.port == 443 && it.username.isEmpty() && it.password.isEmpty()
        } == true

        /** Parse locally; pasted links are never fetched and never receive account cookies. */
        fun parse(text: String): ListenTogetherInvitation? = runCatching {
            val candidate = candidate(text)
            val http = candidate.toHttpUrlOrNull()
            val query = when {
                http != null -> {
                    require(http.host in setOf("st.music.163.com", "music.163.com"))
                    http.encodedQuery.orEmpty()
                }
                candidate.startsWith("orpheus://") -> URI(candidate).rawQuery.orEmpty()
                candidate.startsWith("roomId=") -> candidate
                else -> return null
            }
            val pairs = query.split('&').mapNotNull { item ->
                val parts = item.split('=', limit = 2)
                if (parts.size != 2) null else URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
            }
            require(pairs.count { it.first == "roomId" } == 1 && pairs.count { it.first == "inviterId" } == 1)
            val values = pairs.toMap()
            ListenTogetherInvitation(values.getValue("roomId"), values.getValue("inviterId").toLong())
        }.getOrNull()

        internal fun validRoomId(value: String): Boolean = value.length in 1..256 &&
            value.all { it.isLetterOrDigit() || it in "_-" }
    }
}
