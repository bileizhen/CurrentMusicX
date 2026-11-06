package io.github.currencortex.music.feature.listening

import io.github.currencortex.music.data.netease.listening.*
import io.github.currencortex.music.data.profile.listeningDuration
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDate

internal suspend fun loadCloudListening(repository: NeteaseListeningRepository, period: ListeningPeriod, date: LocalDate): ListeningDisplay = coroutineScope {
    val session = repository.captureSession()
    val total = async { repository.totalDurationMs(session) }
    val display = if (period == ListeningPeriod.YEAR) {
        val years = repository.years(session)
        val selected = years?.find { it.year == date.year }
        ListeningDisplay(totalMs = selected?.durationMs, songCount = selected?.songCount?.count(),
            yearFacts = years.orEmpty().map { year -> ListeningFact("${year.year} 年", year.durationMs?.let(::listeningDuration) ?: "时长暂无数据",
                year.songCount?.let { "收听 $it 首歌曲" }.orEmpty()) },
            note = "年度数据以网易云已发布的年度听歌报告为准。尚未发布的年份没有完整汇总。")
    } else {
        val report = repository.report(session, if (period == ListeningPeriod.WEEK) NeteaseListeningPeriod.WEEK else NeteaseListeningPeriod.MONTH, date)
        report?.display() ?: ListeningDisplay(note = "该时段的完整报告尚未生成，请稍后再来。")
    }
    display.copy(cumulativeMs = total.await()).also { repository.checkSession(session) }
}

private fun NeteaseListeningReport.display() = ListeningDisplay(totalMs = listenDurationMs,
    days = days.mapNotNull { day -> listOfNotNull(day.durationMs, day.podcastDurationMs, day.audiobookDurationMs)
        .takeIf { it.isNotEmpty() }?.let { durations -> day.date to durations.fold(0L, Math::addExact) } }.toMap(),
    songCount = wallSongCount?.count(), listenedDays = listenDays?.count(),
    tracks = wallSongs.mapIndexed { index, song -> ListeningTrack(song.id?.toString() ?: "cloud_$index", song.name, song.artists, song.coverUrl) },
    coverUrls = coverUrls,
    todaySongCount = if (current) todaySongCount?.count() else null,
    todayCovers = if (current) todayCoverUrls else emptyList(),
    facts = buildList {
        if (achievementTitle.isNotBlank()) add(ListeningFact("听歌印记", achievementTitle, achievementSubtitle))
        sections.forEach { section -> when (section.type) {
            "lateSleep", "earlySleep" -> section.value.toLongOrNull()?.takeIf { it > 0 }?.let {
                val time = Instant.ofEpochMilli(it).atZone(neteaseListeningZone).toLocalTime()
                add(ListeningFact(if (section.type == "lateSleep") "最晚收听" else "最早收听", "%02d:%02d".format(time.hour, time.minute)))
            }
            "durationDiffPositive", "durationDiffNegative" -> section.value.toLongOrNull()?.takeIf { it in 0..Long.MAX_VALUE / 1000 }?.let {
                add(ListeningFact("与上期相比", "${if (section.type == "durationDiffNegative") "少听" else "多听"} ${listeningDuration(it * 1000)}"))
            }
            "maxPlayUserLikeChannel", "maxPlayRcmdChannel" -> section.value.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }?.let {
                add(ListeningFact(if (section.type == "maxPlayUserLikeChannel") "红心收听" else "推荐收听", "${percentText(it)}%"))
            }
        } }
        ages.forEach { age -> age.age?.let { value ->
            add(ListeningFact("听歌年代 · ${ageLabel(value)}", age.songCount?.let { "$it 首" } ?: "暂无数量",
                age.song?.name.orEmpty(), age.song?.coverUrl.orEmpty()))
        } }
        languages.forEach { language ->
            add(ListeningFact("听歌语言 · ${language.language}", language.percent?.let { "${percentText(it)}%" } ?: "暂无占比",
                language.song?.name.orEmpty(), language.song?.coverUrl.orEmpty()))
        }
    }, note = if (current) "云端数据可能延迟更新，完整报告以网易云发布为准。" else "来自网易云历史听歌报告。")

internal fun cloudListeningDay(report: ListeningDisplay, selected: LocalDate, today: LocalDate): ListeningDisplay =
    ListeningDisplay(totalMs = report.days[selected], songCount = if (selected == today) report.todaySongCount else null,
        tracks = if (selected != today) emptyList() else report.todayCovers.mapIndexed { index, url -> ListeningTrack("today_$index", "今日歌曲封面", cover = url) })

private fun Long.count(): Int? = takeIf { it in 0..Int.MAX_VALUE }?.toInt()
private fun percentText(value: Double) = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
private fun ageLabel(year: Int): String = when {
    year >= 2020 -> "2020 年后"
    year >= 2000 -> "${year / 10 * 10} – ${year / 10 * 10 + 9}"
    year >= 1910 -> "${year % 100 / 10 * 10} 年代"
    year >= 1900 -> "20 世纪初"
    else -> "$year 年代"
}
