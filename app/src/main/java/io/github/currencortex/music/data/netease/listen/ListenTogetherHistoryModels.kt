package io.github.currencortex.music.data.netease.listen

import io.github.currencortex.music.core.netease.NeteaseSession
import okhttp3.HttpUrl.Companion.toHttpUrl

internal interface ListenTogetherHistoryProtocol {
    suspend fun captureSession(): NeteaseSession
    fun checkSession(expected: NeteaseSession)
    suspend fun history(expected: NeteaseSession, cursor: String = "", size: Int = 50): ListenTogetherHistoryPage
    suspend fun ranking(expected: NeteaseSession): ListenTogetherRanking
    suspend fun record(expected: NeteaseSession, roomId: String): ListenTogetherRecordReport
    suspend fun roomSongs(expected: NeteaseSession, roomId: String): List<Long>
}

/** Native history durations are milliseconds. Missing or member-masked statistics stay null. */
data class ListenTogetherHistoryRecord(
    val id: String,
    val roomId: String,
    val user: ListenTogetherParticipant,
    val date: String = "",
    val startTimeMs: Long = 0,
    val durationMs: Long? = null,
    val songCount: Long? = null,
    val online: Boolean = false,
    val canInvite: Boolean = false,
    val ltType: Int = 0,
    val recordRoomType: String = "listen_together",
    val failMessage: String = "",
)

data class ListenTogetherMostListen(
    val user: ListenTogetherParticipant,
    val selfAvatarUrl: String = "",
    val totalDurationMs: Long? = null,
    val totalSongCount: Long? = null,
    val online: Boolean = false,
    val ltType: Int = 0,
)

data class ListenTogetherHistoryPage(
    val records: List<ListenTogetherHistoryRecord> = emptyList(),
    val mostListen: ListenTogetherMostListen? = null,
    val hasMore: Boolean = false,
    val nextCursor: String = "",
    val vip: Boolean = false,
    val vipType: Int = 0,
)

data class ListenTogetherRankEntry(
    val user: ListenTogetherParticipant,
    val rank: Int,
    val totalDurationMs: Long? = null,
    val totalSongCount: Long? = null,
    val online: Boolean = false,
    val ltType: Int = 0,
)

data class ListenTogetherRanking(val rankings: List<ListenTogetherRankEntry> = emptyList(), val vipType: Int = 0)

data class ListenTogetherRecordReport(
    val roomId: String,
    val self: ListenTogetherParticipant? = null,
    val peer: ListenTogetherParticipant? = null,
    val durationMs: Long? = null,
    val songCount: Long? = null,
    val totalDurationMs: Long? = null,
    val totalSongCount: Long? = null,
    val endTimeMs: Long? = null,
    val online: Boolean = false,
    val canInvite: Boolean = false,
    val roomType: String = "",
    val scene: String = "",
    val showCollectButton: Boolean = false,
    val failMessage: String = "",
    val ltType: Int = 0,
) {
    /** The official two-person report route, recovered from the official record-list bundle. */
    fun officialUrl(): String = "https://mp.music.163.com/5fbcd6a217aef81f3a03eda5/ending/index.html".toHttpUrl()
        .newBuilder().addQueryParameter("roomId", roomId).addQueryParameter("nm_style", "sbt").build().toString()
}
