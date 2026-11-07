package io.github.currencortex.music.feature.listening

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal typealias ListeningSource = io.github.currencortex.music.data.settings.ListeningSource
internal enum class ListeningPeriod(val label: String) { WEEK("周"), MONTH("月"), YEAR("年") }
internal data class ListeningRange(val start: LocalDate, val end: LocalDate) {
    val dates: List<LocalDate> get() = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
    val label: String get() = if (start.year == end.year) "${start.year} 年 ${start.monthValue}.${start.dayOfMonth} – ${end.monthValue}.${end.dayOfMonth}"
        else "$start – $end"
}
internal fun listeningRange(period: ListeningPeriod, anchor: LocalDate): ListeningRange {
    val start = when (period) {
        ListeningPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        ListeningPeriod.MONTH -> anchor.withDayOfMonth(1)
        ListeningPeriod.YEAR -> anchor.withDayOfYear(1)
    }
    return ListeningRange(start, when (period) {
        ListeningPeriod.WEEK -> start.plusDays(6)
        ListeningPeriod.MONTH -> start.plusMonths(1).minusDays(1)
        ListeningPeriod.YEAR -> start.plusYears(1).minusDays(1)
    })
}
internal fun shiftListeningDate(period: ListeningPeriod, date: LocalDate, amount: Long): LocalDate = when (period) {
    ListeningPeriod.WEEK -> date.plusWeeks(amount)
    ListeningPeriod.MONTH -> date.plusMonths(amount)
    ListeningPeriod.YEAR -> date.plusYears(amount)
}
internal data class ListeningTrack(val key: String, val title: String, val artist: String = "", val cover: String = "", val durationMs: Long? = null)
internal data class ListeningFact(val title: String, val value: String, val detail: String = "", val cover: String = "", val songId: Long? = null)
internal data class ListeningDayBreakdown(val musicMs: Long?, val podcastMs: Long?, val audiobookMs: Long?)
internal data class ListeningDisplay(
    val totalMs: Long? = null,
    val cumulativeMs: Long? = null,
    val days: Map<LocalDate, Long> = emptyMap(),
    val songCount: Int? = null,
    val tracks: List<ListeningTrack> = emptyList(),
    val facts: List<ListeningFact> = emptyList(),
    val note: String = "",
    val listenedDays: Int? = null,
    val todaySongCount: Int? = null,
    val todayCovers: List<String> = emptyList(),
    val coverUrls: List<String> = emptyList(),
    val yearFacts: List<ListeningFact> = emptyList(),
    val dayBreakdowns: Map<LocalDate, ListeningDayBreakdown> = emptyMap(),
    val summarySections: List<ListeningSummarySection> = emptyList(),
    val monthly: ListeningMonthlyDisplay? = null,
    val sleep: ListeningSleepDisplay? = null,
    val cloudEndTimeMs: Long? = null,
    val weekly: ListeningWeeklyDisplay? = null,
    val cloudVip: Boolean? = null,
    val superVip: Boolean? = null,
)

internal sealed interface ListeningMonthlyAction {
    data class Song(val id: Long, val name: String = "", val artist: String = "", val cover: String = "") : ListeningMonthlyAction
    data class Artist(val id: Long) : ListeningMonthlyAction
    data class Playlist(val id: Long) : ListeningMonthlyAction
    data class User(val id: Long) : ListeningMonthlyAction
    data class Genre(val id: Long) : ListeningMonthlyAction
    data class VoiceList(val id: Long) : ListeningMonthlyAction
    data class Url(val url: String) : ListeningMonthlyAction
}

internal data class ListeningSummarySection(val type: String, val label: String, val value: String)
internal data class ListeningSleepDisplay(
    val listenDays: Long, val scene: String, val days: Map<LocalDate, Long>,
    val averageDurationMs: Long?, val maximumDurationMs: Long?, val complete: Boolean,
)
internal data class ListeningMonthlyHighlight(
    val name: String, val cover: String, val playCount: Long?, val action: ListeningMonthlyAction?,
    val sections: List<ListeningMonthlyHighlightRow> = emptyList(),
)
internal data class ListeningMonthlyHighlightRow(
    val type: String, val field: String, val text: String, val name: String,
    val cover: String, val action: ListeningMonthlyAction?,
)
internal data class ListeningMonthlyFriend(val name: String, val avatar: String, val action: ListeningMonthlyAction?)
internal data class ListeningMonthlyFriendSong(
    val name: String, val cover: String, val playCount: Long?, val action: ListeningMonthlyAction?,
    val friend: ListeningMonthlyFriend,
    val artist: String = "",
    val collected: Boolean? = null,
)
internal data class ListeningMonthlyFriendKeyword(
    val title: String, val subtitle: String, val code: String, val friends: List<ListeningMonthlyFriend>,
)
internal data class ListeningMonthlyLongAudio(
    val type: String, val name: String, val cover: String, val action: ListeningMonthlyAction?,
    val topDurationMs: Long?, val topEpisodes: Long?, val durationMs: Long?, val episodes: Long?,
)
internal data class ListeningMonthlyGenre(val name: String, val percent: Double?, val action: ListeningMonthlyAction?)
internal data class ListeningMonthlyRecommendation(val text: String, val action: ListeningMonthlyAction?)
internal data class ListeningMonthlyStyle(
    val main: ListeningMonthlyGenre, val secondary: ListeningMonthlyGenre?,
    val genres: List<ListeningMonthlyGenre>, val recommendation: ListeningMonthlyRecommendation?,
)
internal data class ListeningMonthlyDisplay(
    val complete: Boolean,
    val topSong: ListeningMonthlyHighlight? = null,
    val topArtist: ListeningMonthlyHighlight? = null,
    val vip: ListeningMonthlyHighlight? = null,
    val superVip: Boolean = false,
    val longAudio: List<ListeningMonthlyLongAudio> = emptyList(),
    val friendSongs: List<ListeningMonthlyFriendSong> = emptyList(),
    val friendKeywords: List<ListeningMonthlyFriendKeyword> = emptyList(),
    val style: ListeningMonthlyStyle? = null,
    val sendVipDays: Long? = null,
    val wallpaperBottom: Boolean = false,
)

internal data class ListeningWeeklyColumn(
    val name: String, val cover: String, val value: String, val field: String,
    val type: String = "", val action: ListeningMonthlyAction? = null,
)
internal data class ListeningWeeklyEra(
    val label: String, val song: String, val cover: String, val action: ListeningMonthlyAction?,
)
internal data class ListeningWeeklyLanguage(val name: String, val percent: Double?)
internal data class ListeningWeeklyAudio(val type: String, val durationMs: Long, val episodes: Long)
internal data class ListeningWeeklyDisplay(
    val complete: Boolean,
    val songs: List<ListeningWeeklyColumn> = emptyList(),
    val artists: List<ListeningWeeklyColumn> = emptyList(),
    val vip: List<ListeningSummarySection> = emptyList(),
    val superVip: Boolean = false,
    val genres: List<ListeningMonthlyGenre> = emptyList(),
    val eras: List<ListeningWeeklyEra> = emptyList(),
    val languages: List<ListeningWeeklyLanguage> = emptyList(),
    val longAudio: List<ListeningWeeklyAudio> = emptyList(),
    val friendSongs: List<ListeningMonthlyFriendSong> = emptyList(),
    val friendKeywords: List<ListeningMonthlyFriendKeyword> = emptyList(),
    val currentFriends: List<ListeningMonthlyFriendSong> = emptyList(),
    val wallpaperBottom: Boolean = false,
)
