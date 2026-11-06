package io.github.currencortex.music.feature.listening

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal enum class ListeningSource(val label: String) { LOCAL("CurrentMusicX"), NETEASE("网易云") }
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
internal data class ListeningFact(val title: String, val value: String, val detail: String = "", val cover: String = "")
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
)
