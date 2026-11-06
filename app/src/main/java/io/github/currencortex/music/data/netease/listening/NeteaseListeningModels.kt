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
)

internal data class NeteaseListeningYear(val year: Int, val durationMs: Long?, val songCount: Long?)
