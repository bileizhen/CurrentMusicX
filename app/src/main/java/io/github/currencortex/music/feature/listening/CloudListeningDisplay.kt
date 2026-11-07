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
        report?.display(period) ?: ListeningDisplay(note = "该时段的完整报告尚未生成，请稍后再来。")
    }
    display.copy(cumulativeMs = total.await()).also { repository.checkSession(session) }
}

internal fun NeteaseListeningReport.display(period: ListeningPeriod) = ListeningDisplay(totalMs = listenDurationMs,
    cloudEndTimeMs=endTimeMs,
    cloudVip=when { weekly?.blackVip==true || weekly?.superVip==true || monthly?.blackVip==true || monthly?.superVip==true -> true
        weekly?.blackVip==false && weekly.superVip==false || monthly?.blackVip==false && monthly.superVip==false -> false
        else -> null },
    superVip=weekly?.superVip ?: monthly?.superVip,
    weekly = if (period == ListeningPeriod.WEEK) weekly?.display() ?: ListeningWeeklyDisplay(complete = !current) else null,
    days = days.mapNotNull { day -> listOfNotNull(day.durationMs, day.podcastDurationMs, day.audiobookDurationMs)
        .takeIf { it.isNotEmpty() }?.let { durations -> day.date to durations.fold(0L, Math::addExact) } }.toMap(),
    dayBreakdowns = days.associate { it.date to ListeningDayBreakdown(it.durationMs, it.podcastDurationMs, it.audiobookDurationMs) },
    songCount = wallSongCount?.count(), listenedDays = listenDays?.count(),
    tracks = wallSongs.mapIndexed { index, song -> ListeningTrack(song.id?.toString() ?: "cloud_$index", song.name, song.artists, song.coverUrl) },
    coverUrls = coverUrls,
    todaySongCount = if (current) todaySongCount?.count() else null,
    todayCovers = if (current) todayCoverUrls else emptyList(),
    summarySections = if (sections.size >= 2) sections.take(2).mapNotNull { it.summary(period) } else emptyList(),
    monthly = if (period == ListeningPeriod.MONTH) monthly?.display(complete = !current)
        ?: ListeningMonthlyDisplay(complete = !current) else null,
    sleep = sleep?.takeIf { (it.listenDays ?: 0) > 0 }?.let { sleep -> ListeningSleepDisplay(
        listenDays = requireNotNull(sleep.listenDays), scene = sleep.scene,
        days = sleep.days.mapNotNull { day -> day.durationMs?.let { day.date to it } }.toMap(),
        averageDurationMs = sleep.averageDurationMs, maximumDurationMs = sleep.maximumDurationMs, complete = !current) },
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
                age.song?.name.orEmpty(), age.song?.coverUrl.orEmpty(), age.song?.id))
        } }
        languages.forEach { language ->
            add(ListeningFact("听歌语言 · ${language.language}", language.percent?.let { "${percentText(it)}%" } ?: "暂无占比",
                language.song?.name.orEmpty(), language.song?.coverUrl.orEmpty(), language.song?.id))
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

private fun NeteaseListeningSection.summary(period: ListeningPeriod): ListeningSummarySection? {
    val previous = if (period == ListeningPeriod.MONTH) "月" else "周"
    val label: String
    val text: String
    when (type) {
        "lateSleep", "earlySleep" -> {
            val timestamp = value.toLongOrNull()?.takeIf { it > 0 } ?: return null
            val time = Instant.ofEpochMilli(timestamp).atZone(neteaseListeningZone).toLocalTime()
            label = if (type == "lateSleep") "最晚听歌" else "最早听歌"
            text = "%02d:%02d".format(java.util.Locale.ROOT, time.hour, time.minute)
        }
        "durationDiffPositive", "durationDiffNegative" -> {
            val seconds = value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE.toDouble() } ?: return null
            val minutes = kotlin.math.floor(seconds / 60 + .5).toLong()
            label = "比上$previous${if (type == "durationDiffNegative") "少听" else "多听"}"
            text = if (minutes >= 60) "${minutes / 60}小时${minutes % 60}分" else "${minutes}分"
        }
        "maxPlayUserLikeChannel", "maxPlayRcmdChannel" -> {
            val percentage = value.toDoubleOrNull()?.takeIf { it in 0.0..100.0 } ?: return null
            label = if (type == "maxPlayUserLikeChannel") "红心收听" else "推荐收听"
            text = "${percentText(percentage)}%"
        }
        else -> return null
    }
    return ListeningSummarySection(type, label, text)
}

private fun NeteaseListeningMonthly.display(complete: Boolean) = ListeningMonthlyDisplay(
    complete = complete,
    topSong = topSong?.highlight(artist = false),
    topArtist = topArtist?.highlight(artist = true),
    vip = vip?.takeIf { canShowVip }?.highlight(artist = false, vip = true),
    superVip = superVip == true,
    friendSongs = if (canShowFriendSongs) friendSongs.orEmpty().map { row -> ListeningMonthlyFriendSong(
        name = row.songName, cover = row.coverUrl, playCount = row.playCount,
        action = row.songId?.let { ListeningMonthlyAction.Song(it, row.songName, row.artistName, row.coverUrl) }, friend = row.friend.display(),
        artist=row.artistName,collected=row.collected) } else emptyList(),
    friendKeywords = if (canShowFriendKeywords) friendKeywords.orEmpty().map { row -> ListeningMonthlyFriendKeyword(
        row.title, row.subtitle, row.enumCode, row.friends.map { it.display() }) } else emptyList(),
    longAudio = longAudio.orEmpty().filter { it.topId != null }.sortedByDescending { it.durationMs ?: -1L }.map { audio ->
        ListeningMonthlyLongAudio(audio.type, audio.topName, audio.topCoverUrl,
            audio.topId?.let { ListeningMonthlyAction.VoiceList(it) }, audio.topDurationMs, audio.topEpisodeCount,
            audio.durationMs, audio.episodeCount) },
    style = style?.let { style -> ListeningMonthlyStyle(
        main = ListeningMonthlyGenre(style.genreName, null, style.genreId?.let { ListeningMonthlyAction.Genre(it) }),
        secondary = style.secondGenreName.takeIf(String::isNotBlank)?.let { ListeningMonthlyGenre(it, null,
            style.secondGenreId?.let { id -> ListeningMonthlyAction.Genre(id) }) },
        genres = style.sections.take(3).map { ListeningMonthlyGenre(it.name, it.percent, it.id?.let { id -> ListeningMonthlyAction.Genre(id) }) },
        recommendation = style.items.firstOrNull { it.type == "recommend" }?.let {
            ListeningMonthlyRecommendation(it.mainText, it.resourceId?.let { id -> ListeningMonthlyAction.Playlist(id) }) }) },
    sendVipDays = sendVipDays?.takeIf { it > 0 }, wallpaperBottom = wallpaperBottom == true,
)

private fun NeteaseListeningFriend.display() = ListeningMonthlyFriend(username, avatarUrl, userId?.let { ListeningMonthlyAction.User(it) })

private fun NeteaseListeningHighlight.highlight(artist: Boolean, vip: Boolean = false): ListeningMonthlyHighlight =
    ListeningMonthlyHighlight(name, coverUrl, playCount,
        id?.let { if (artist) ListeningMonthlyAction.Artist(it) else ListeningMonthlyAction.Song(it, name, cover = coverUrl) },
        sections.take(2).map { section -> ListeningMonthlyHighlightRow(section.type, section.field,
            if (vip) section.mainText else section.text,
            if (artist) section.artistName else section.songName, section.coverUrl,
            if (artist) section.artistId?.let { ListeningMonthlyAction.Artist(it) }
            else section.songId?.let { ListeningMonthlyAction.Song(it, section.songName, cover = section.coverUrl) }) })

private fun NeteaseListeningWeekly.display() = ListeningWeeklyDisplay(
    complete = isComplete,
    songs = songs.orEmpty().map { it.weeklyColumn(artist = false) },
    artists = artists.orEmpty().map { it.weeklyColumn(artist = true) },
    vip = if (canShowVip) vipSections.orEmpty().take(2).map { ListeningSummarySection(it.type, when (it.type) {
        "vipSongCount" -> "会员歌曲收听数"
        "vipSongDuration" -> "会员歌曲收听时长"
        else -> it.field
    }, it.mainText) } else emptyList(),
    superVip = superVip == true,
    genres = genres.orEmpty().take(3).map { ListeningMonthlyGenre(it.name, it.percent, it.id?.let { id -> ListeningMonthlyAction.Genre(id) }) },
    // Week rankings are already ordered by the server. Month's chronological sort does not apply.
    eras = ages.orEmpty().take(3).mapNotNull { age -> age.age?.let { year -> ListeningWeeklyEra(
        ageLabel(year).replace(" – ", "-").replace(" 年后", "年后").replace(" 年代", "年代"),
        age.song?.name.orEmpty(), age.song?.coverUrl.orEmpty(), age.song?.let { song -> song.id?.let { id ->
            ListeningMonthlyAction.Song(id, song.name, song.artists, song.coverUrl) } }) } },
    languages = languages.orEmpty().take(3).map { ListeningWeeklyLanguage(it.language, it.percent) },
    longAudio = longAudio.orEmpty().filter { (it.durationMs ?: 0) > 0 && (it.episodeCount ?: 0) > 0 }
        .sortedByDescending { it.durationMs }.map { ListeningWeeklyAudio(it.type, requireNotNull(it.durationMs), requireNotNull(it.episodeCount)) },
    friendSongs = if (canShowFriendSongs) friendSongs.orEmpty().map { it.weeklyFriend() } else emptyList(),
    friendKeywords = if (canShowFriendKeywords) friendKeywords.orEmpty().map {
        ListeningMonthlyFriendKeyword(it.title, it.subtitle, it.enumCode, it.friends.map { friend -> friend.display() }) } else emptyList(),
    currentFriends = if (canShowCurrentFriends) currentFriends.orEmpty().take(3).map { it.weeklyFriend() } else emptyList(),
    wallpaperBottom = wallpaperBottom == true,
)

private fun NeteaseListeningHighlightSection.weeklyColumn(artist: Boolean) = ListeningWeeklyColumn(
    name = if (artist) artistName else songName, cover = coverUrl, value = text, field = field, type = type,
    action = if (artist) artistId?.let { ListeningMonthlyAction.Artist(it) }
    else songId?.let { ListeningMonthlyAction.Song(it, songName, cover = coverUrl) },
)
private fun NeteaseListeningFriendSong.weeklyFriend() = ListeningMonthlyFriendSong(songName, coverUrl, playCount,
    songId?.let { ListeningMonthlyAction.Song(it, songName, artistName, coverUrl) }, friend.display(), artistName,collected)
