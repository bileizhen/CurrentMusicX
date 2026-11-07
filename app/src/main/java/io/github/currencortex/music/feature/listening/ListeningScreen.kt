package io.github.currencortex.music.feature.listening

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.media.ListeningHistoryReport
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.profile.listeningDuration
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.Clock

internal fun listeningToday(source: ListeningSource, clock: Clock = Clock.systemUTC(),
    deviceZone: ZoneId = ZoneId.systemDefault()): LocalDate =
    LocalDate.now(clock.withZone(if (source == ListeningSource.NETEASE) ZoneId.of("Asia/Shanghai") else deviceZone))

@Composable internal fun ListeningScreen(container: AppContainer, onBack: () -> Unit, onLogin: () -> Unit,
    onNavigate: (String) -> Unit, onPlay: (io.github.currencortex.music.data.song.Song) -> Unit,
    onPlayList: (List<io.github.currencortex.music.data.song.Song>,Int) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val settings by container.musicSettings.state.collectAsStateWithLifecycle()
    val source = settings.listeningSource
    var today by remember(source) { mutableStateOf(listeningToday(source)) }
    var periodName by rememberSaveable { mutableStateOf(ListeningPeriod.WEEK.name) }
    var anchorText by rememberSaveable(source) { mutableStateOf(today.toString()) }
    var selectedText by rememberSaveable(source) { mutableStateOf(today.toString()) }
    var retry by remember { mutableIntStateOf(0) }
    val period = ListeningPeriod.valueOf(periodName)
    val anchor = LocalDate.parse(anchorText)
    val selected = LocalDate.parse(selectedText)
    val session by container.neteaseSessions.state.collectAsStateWithLifecycle()
    val playback by container.playerController.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(source) {
        retry = 0
        if (anchor > today) anchorText = today.toString()
        if (selected > today) selectedText = today.toString()
    }
    LaunchedEffect(source, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) {
            today = listeningToday(source)
            delay(15_000)
        }
    } }
    val range = listeningRange(period, anchor)
    val owner = if (source == ListeningSource.NETEASE) session.revision else 0L
    var cloudSongsToday by rememberSaveable(source.name,period.name,anchorText,owner) { mutableStateOf<Boolean?>(null) }
    var report by remember(source, period, anchor, owner, retry) { mutableStateOf<ListeningDisplay?>(null) }
    var day by remember(source, period, anchor, selected, owner, retry) { mutableStateOf<ListeningDisplay?>(null) }
    var error by remember(source, period, anchor, owner, retry) { mutableStateOf<String?>(null) }
    var dayError by remember(source, period, anchor, selected, owner, retry) { mutableStateOf<String?>(null) }
    var loading by remember(source, period, anchor, owner, retry) { mutableStateOf(true) }
    var dayLoading by remember(source, period, anchor, selected, owner, retry) { mutableStateOf(true) }
    val signedIn = session.loggedIn && !session.stale
    LaunchedEffect(container, source, period, anchor, owner, retry, lifecycle, today) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                loading = true
                when (val result = appResult {
                    if (source == ListeningSource.LOCAL) {
                        val history = container.todayListening.history(range.start, range.end)
                        val display = history.toDisplay()
                        if (period != ListeningPeriod.YEAR) display else {
                            val first = (history.firstRecordedDay?.year ?: today.year).coerceAtMost(today.year)
                            val years = (today.year downTo first).map { year ->
                                val annual = if (year == range.start.year) history else
                                    container.todayListening.history(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                                ListeningFact("$year 年", listeningDuration(annual.durationMs),
                                    if (annual.detailedDurationMs == 0L && annual.durationMs > 0) "歌曲明细暂无数据" else "收听 ${annual.songCount} 首歌曲")
                            }
                            display.copy(yearFacts = years)
                        }
                    }
                    else loadCloudListening(container.neteaseListening, period, anchor)
                }) {
                    is AppResult.Success -> { report = result.value; error = null }
                    is AppResult.Failure -> { error = result.message }
                }
                loading = false
                if (source == ListeningSource.NETEASE) awaitCancellation()
                delay(15_000)
            }
        }
    }
    LaunchedEffect(container, source, period, anchor, selected, owner, retry, lifecycle, report, today) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (source == ListeningSource.NETEASE) {
                day = report?.let { cloudListeningDay(it, selected, today) }
                dayLoading = loading
            } else {
                dayLoading = true
                when (val result = appResult { container.todayListening.history(selected, selected).toDisplay() }) {
                    is AppResult.Success -> { day = result.value; dayError = null }
                    is AppResult.Failure -> { dayError = result.message }
                }
                dayLoading = false
            }
        }
    }
    val exportInfo=ListeningWallExportInfo(
        nickname=if(source==ListeningSource.NETEASE) session.profile?.nickname.orEmpty() else "CurrentMusicX",
        timeline="${range.start.monthValue}.${range.start.dayOfMonth}-${range.end.monthValue}.${range.end.dayOfMonth}",
        songTitles=report?.tracks.orEmpty().map { it.title },isCloud=source==ListeningSource.NETEASE,
        cloudVip=report?.cloudVip,superVip=report?.superVip==true)
    val exportDepth=remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalListeningWallExportInfo provides exportInfo,LocalListeningExportDepth provides exportDepth,
        LocalListeningMotionEnabled provides (exportDepth.intValue==0)) {
    ListeningSocialActionsProvider(container,enabled=source==ListeningSource.NETEASE) {
    CompositionLocalProvider(LocalListeningMotionEnabled provides (exportDepth.intValue==0 && cloudSongsToday==null)) {
    key(source,owner) {
    ListeningPage(source, period, range, today, selected, report, day, loading, error, signedIn,
        if (source == ListeningSource.NETEASE) loading else dayLoading, dayError,
        onPeriod = { periodName = it.name; selectedText = minOf(today, listeningRange(it, anchor).end).toString() },
        onMove = { direction ->
            val next = shiftListeningDate(period, anchor, direction)
            if (listeningRange(period, next).start <= today) {
                anchorText = next.toString(); selectedText = minOf(today, listeningRange(period, next).end).toString()
            }
        }, onDay = { selectedText = it.toString() }, onRetry = { retry++ }, onLogin = onLogin, onBack = onBack,
        onOpenCloudSongs = { cloudSongsToday=it }, onMonthlyAction = { action ->
            when (action) {
                is ListeningMonthlyAction.Song -> onPlay(io.github.currencortex.music.data.song.Song(action.id,
                    action.name.ifBlank { "歌曲" },action.artist,cover=action.cover))
                is ListeningMonthlyAction.Artist -> onNavigate("lib/artist/${action.id}")
                is ListeningMonthlyAction.Playlist -> onNavigate("lib/ncmplaylist/${action.id}")
                is ListeningMonthlyAction.Genre -> onNavigate("lib/style/${action.id}")
                else -> {
                    val url = when(action) {
                        is ListeningMonthlyAction.User -> "https://music.163.com/#/user/home?id=${action.id}"
                        is ListeningMonthlyAction.VoiceList -> "orpheus://nm/voicelist/detail?id=${action.id}"
                        is ListeningMonthlyAction.Url -> action.url
                        else -> error("Unhandled monthly action")
                    }
                    try { uriHandler.openUri(url) } catch (_: IllegalArgumentException) {
                        android.widget.Toast.makeText(context,"暂时无法打开该内容",android.widget.Toast.LENGTH_SHORT).show()
                    } catch (_: android.content.ActivityNotFoundException) {
                        android.widget.Toast.makeText(context,"未找到可打开该内容的应用",android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
    }
    cloudSongsToday?.let { todaySongs ->
        ListeningCloudSongs(container.neteaseListening,period,anchor,report?.cloudEndTimeMs,todaySongs,
            dismiss={cloudSongsToday=null}, play={ action ->
                onPlay(io.github.currencortex.music.data.song.Song(action.id,action.name,action.artist,cover=action.cover))
            }, playRank={ songs,index ->
                onPlayList(songs.map { song -> io.github.currencortex.music.data.song.Song(requireNotNull(song.id),
                    song.name,song.artists,album=song.albumName,cover=song.coverUrl) },index)
            },currentSongId=playback.song?.takeIf { it.musicSource==io.github.currencortex.music.data.song.MusicSource.NETEASE && !it.video }?.id,
            playing=playback.playing)
    }
    }
    }
}

internal fun ListeningHistoryReport.toDisplay(): ListeningDisplay {
    val busiest = hourlyMs.indices.maxByOrNull { hourlyMs[it] }?.takeIf { hourlyMs[it] > 0 }
    val night = listOf(0, 1, 2, 3, 4, 5, 22, 23).sumOf { hourlyMs[it] }
    return ListeningDisplay(totalMs = durationMs, cumulativeMs = allTimeMs, days = days.associate { it.date to it.durationMs },
        songCount = if (detailedDurationMs == 0L && durationMs > 0) null else songCount,
        tracks = songs.map { ListeningTrack(it.key, it.name, it.artists, it.cover, it.durationMs) },
        facts = buildList {
            busiest?.let { add(ListeningFact("最常听歌的时段", "%02d:00 – %02d:00".format(it, (it + 1) % 24), listeningDuration(hourlyMs[it]))) }
            if (detailedDurationMs > 0) add(ListeningFact("夜间收听", "${night * 100 / detailedDurationMs}%", "22:00 – 06:00 · ${listeningDuration(night)}"))
        }, listenedDays = listeningDays,
        note = buildString {
            append("仅统计这台设备的实际播放。")
            firstRecordedDay?.let { append("现存记录始于 $it。") }
            if (detailedDurationMs < durationMs) append("部分历史记录只有时长，歌曲与时段明细尚不完整。")
        })
}
