package io.github.currencortex.music.data.netease.listen

import io.github.currencortex.music.core.netease.NeteaseSession
import okhttp3.HttpUrl.Companion.toHttpUrl

internal interface ListenTogetherFateProtocol {
    suspend fun captureSession(): NeteaseSession
    fun checkSession(expected: NeteaseSession)
    suspend fun fate(expected: NeteaseSession, partnerUid: Long, scene: Int = 0): ListenTogetherFateReport
    /** Mutates the native account. Call only after explicit confirmation for this report and account. */
    suspend fun deleteFate(expected: NeteaseSession, partnerUid: Long, ltType: Int, deleteRecords: Boolean = false)
}

/** All times and durations in this model are milliseconds; missing server statistics remain absent. */
data class ListenTogetherFateReport(
    val partnerUid: Long,
    val self: ListenTogetherParticipant,
    val peer: ListenTogetherParticipant,
    val totalDurationMs: Long? = null,
    val totalSongCount: Long? = null,
    val commonRedSongCount: Long? = null,
    val firstListenTimeMs: Long? = null,
    val daysSinceFirstListen: Long? = null,
    val latestLateNightTimeMs: Long? = null,
    val mostPlayedSongName: String = "",
    val longestDurationMs: Long? = null,
    val ltType: Int = 0,
    val vipType: Int = 0,
    val canInvite: Boolean = false,
    val failMessage: String = "",
    /** Official deletion confirmation warns that linked badges and their progress will be reclaimed. */
    val openActivity: Boolean = false,
) {
    /** Authenticated official report route, not a fabricated public share-image URL. */
    fun officialUrl(): String = "https://mp.music.163.com/5fbcd6a217aef81f3a03eda5/fate/index.html".toHttpUrl()
        .newBuilder().addQueryParameter("partnerUid", partnerUid.toString()).addQueryParameter("scene", ltType.toString())
        .addQueryParameter("nm_style", "sbt").build().toString()
}
