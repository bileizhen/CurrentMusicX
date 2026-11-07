package io.github.currencortex.music.data.netease.listening

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

internal val neteaseListeningZone: ZoneId = ZoneId.of("Asia/Shanghai")

internal enum class NeteaseListeningPeriod(val wire: String) {
    WEEK("week"), MONTH("month");

    fun start(date: LocalDate): LocalDate = when (this) {
        WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        MONTH -> date.withDayOfMonth(1)
    }

    fun next(date: LocalDate): LocalDate = when (this) {
        WEEK -> start(date).plusWeeks(1)
        MONTH -> start(date).plusMonths(1)
    }

    // Official listen-home picks one hour before the final millisecond of the period.
    fun requestEndTime(date: LocalDate): Long = next(date).atStartOfDay(neteaseListeningZone)
        .toInstant().toEpochMilli() - 1 - 3_600_000
}

/** All durations are milliseconds; null means unavailable, never an invented zero. */
internal data class NeteaseListeningDay(
    val date: LocalDate,
    val durationMs: Long?,
    val podcastDurationMs: Long?,
    val audiobookDurationMs: Long?,
)

internal data class NeteaseListeningSong(
    val id: Long?, val name: String, val artists: String, val coverUrl: String,
    val playCount: Long? = null,
    val albumName: String = "", val aliasName: String = "", val redStar: Boolean? = null,
    val lastPlayTimeMs: Long? = null,
)

internal data class NeteaseListeningRank(
    val period: NeteaseListeningPeriod, val startTimeMs: Long, val endTimeMs: Long,
    val songCount: Long?, val songs: List<NeteaseListeningSong>, val coverUrls: List<String>,
)

internal data class NeteaseListeningAge(val age: Int?, val songCount: Long?, val song: NeteaseListeningSong?)
internal data class NeteaseListeningLanguage(val language: String, val percent: Double?, val song: NeteaseListeningSong?)
/** lateSleep/earlySleep values are epoch milliseconds; durationDiff values are seconds. */
internal data class NeteaseListeningSection(val type: String, val value: String)

internal data class NeteaseListeningReport(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val current: Boolean,
    val listenDurationMs: Long?,
    val listenDays: Long?,
    val days: List<NeteaseListeningDay>,
    val todaySongCount: Long?,
    val todayCoverUrls: List<String>,
    val wallSongCount: Long?,
    val wallSongs: List<NeteaseListeningSong>,
    val coverUrls: List<String>,
    val ages: List<NeteaseListeningAge>,
    val languages: List<NeteaseListeningLanguage>,
    val achievementTitle: String,
    val achievementSubtitle: String,
    val sections: List<NeteaseListeningSection>,
    val monthly: NeteaseListeningMonthly? = null,
    val sleep: NeteaseListeningSleep? = null,
    val weekly: NeteaseListeningWeekly? = null,
)

/** The server's editorial text and identifiers must survive mapping to the screen. */
internal data class NeteaseListeningHighlightSection(
    val type: String, val field: String, val text: String, val mainText: String,
    val songId: Long?, val songName: String, val artistId: Long?, val artistName: String, val coverUrl: String,
)
internal data class NeteaseListeningHighlight(
    val id: Long?, val name: String, val coverUrl: String, val playCount: Long?,
    val sections: List<NeteaseListeningHighlightSection>,
)
internal data class NeteaseListeningGenre(val id: Long?, val name: String, val percent: Double?)
internal data class NeteaseListeningRecommendation(val type: String, val resourceId: Long?, val mainText: String)
internal data class NeteaseListeningStyle(
    val genreId: Long?, val genreName: String, val secondGenreId: Long?, val secondGenreName: String,
    val sections: List<NeteaseListeningGenre>, val items: List<NeteaseListeningRecommendation>,
)
internal data class NeteaseListeningFriend(val userId: Long?, val username: String, val avatarUrl: String)
internal data class NeteaseListeningFriendSong(
    val songId: Long?, val songName: String, val coverUrl: String, val friend: NeteaseListeningFriend,
    val playCount: Long?, val collected: Boolean?,
    val artistName: String = "",
)
internal data class NeteaseListeningFriendKeyword(
    val title: String, val subtitle: String, val enumCode: String, val friends: List<NeteaseListeningFriend>,
)
internal data class NeteaseListeningLongAudio(
    val type: String, val topId: Long?, val topName: String, val topCoverUrl: String,
    val topDurationMs: Long?, val topEpisodeCount: Long?, val durationMs: Long?, val episodeCount: Long?,
)
internal data class NeteaseListeningSleep(
    val listenDays: Long?, val scene: String, val days: List<NeteaseListeningDay>,
    val averageDurationMs: Long?, val maximumDurationMs: Long?,
)

/** Week sections are editorial columns, unlike the month's single highlighted song/artist. */
internal data class NeteaseListeningWeekly(
    val isComplete: Boolean,
    val songs: List<NeteaseListeningHighlightSection>? = null,
    val artists: List<NeteaseListeningHighlightSection>? = null,
    val vipSections: List<NeteaseListeningHighlightSection>? = null,
    val genres: List<NeteaseListeningGenre>? = null,
    val ages: List<NeteaseListeningAge>? = null,
    val languages: List<NeteaseListeningLanguage>? = null,
    val longAudio: List<NeteaseListeningLongAudio>? = null,
    val friendSongs: List<NeteaseListeningFriendSong>? = null,
    val friendKeywords: List<NeteaseListeningFriendKeyword>? = null,
    val currentFriends: List<NeteaseListeningFriendSong>? = null,
    val blackVip: Boolean? = null,
    val superVip: Boolean? = null,
    val friendsEnabled: Boolean? = null,
    val friendSongsEnabled: Boolean? = null,
    val friendKeywordsEnabled: Boolean? = null,
    val currentFriendsEnabled: Boolean? = null,
    val wallpaperBottom: Boolean? = null,
) {
    val canShowVip: Boolean get() = isComplete && (blackVip == true || superVip == true) && !vipSections.isNullOrEmpty()
    val canShowFriendSongs: Boolean get() = isComplete && friendsEnabled == true && friendSongsEnabled == true && (friendSongs?.size ?: 0) > 2
    val canShowFriendKeywords: Boolean get() = isComplete && friendsEnabled == true && friendKeywordsEnabled == true && (friendKeywords?.size ?: 0) > 1
    val canShowCurrentFriends: Boolean get() = !isComplete && friendsEnabled == true && currentFriendsEnabled == true && !currentFriends.isNullOrEmpty()
}
internal data class NeteaseListeningMonthly(
    val topSong: NeteaseListeningHighlight? = null,
    val topArtist: NeteaseListeningHighlight? = null,
    val style: NeteaseListeningStyle? = null,
    val vip: NeteaseListeningHighlight? = null,
    val longAudio: List<NeteaseListeningLongAudio>? = null,
    val friendSongs: List<NeteaseListeningFriendSong>? = null,
    val friendKeywords: List<NeteaseListeningFriendKeyword>? = null,
    val sendVipDays: Long? = null,
    // Unknown permission is not an affirmative entitlement or privacy setting.
    val blackVip: Boolean? = null,
    val superVip: Boolean? = null,
    val friendsEnabled: Boolean? = null,
    val friendSongsEnabled: Boolean? = null,
    val friendKeywordsEnabled: Boolean? = null,
    val wallpaperBottom: Boolean? = null,
) {
    val canShowVip: Boolean get() = (blackVip == true || superVip == true) &&
        vip?.let { it.sections.isNotEmpty() || it.coverUrl.isNotBlank() } == true
    val canShowFriends: Boolean get() = friendsEnabled == true
    val canShowFriendSongs: Boolean get() = canShowFriends && friendSongsEnabled == true
    val canShowFriendKeywords: Boolean get() = canShowFriends && friendKeywordsEnabled == true
}

internal data class NeteaseListeningYear(val year: Int, val durationMs: Long?, val songCount: Long?)
