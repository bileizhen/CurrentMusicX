package io.github.currencortex.music.feature.listening

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

@Composable internal fun ListeningScreen(container: AppContainer, onBack: () -> Unit, onLogin: () -> Unit) {
    var sourceName by rememberSaveable { mutableStateOf(ListeningSource.LOCAL.name) }
    var periodName by rememberSaveable { mutableStateOf(ListeningPeriod.WEEK.name) }
    var anchorText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var retry by remember { mutableIntStateOf(0) }
    val source = ListeningSource.valueOf(sourceName)
    val period = ListeningPeriod.valueOf(periodName)
    val anchor = LocalDate.parse(anchorText)
    val selected = LocalDate.parse(selectedText)
    val session by container.neteaseSessions.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var today by remember(source) { mutableStateOf(LocalDate.now(if (source == ListeningSource.NETEASE) ZoneId.of("Asia/Shanghai") else ZoneId.systemDefault())) }
    LaunchedEffect(source, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) {
            today = LocalDate.now(if (source == ListeningSource.NETEASE) ZoneId.of("Asia/Shanghai") else ZoneId.systemDefault())
            delay(15_000)
        }
    } }
    val range = listeningRange(period, anchor)
    val owner = if (source == ListeningSource.NETEASE) session.revision else 0L
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
                    if (source == ListeningSource.LOCAL) container.todayListening.history(range.start, range.end).toDisplay()
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
    ListeningPage(source, period, range, today, selected, report, day, loading, error, signedIn,
        if (source == ListeningSource.NETEASE) loading else dayLoading, dayError,
        onSource = {
            sourceName = it.name; retry = 0
            val newToday = LocalDate.now(if (it == ListeningSource.NETEASE) ZoneId.of("Asia/Shanghai") else ZoneId.systemDefault())
            if (anchor > newToday) anchorText = newToday.toString()
            if (selected > newToday) selectedText = newToday.toString()
        },
        onPeriod = { periodName = it.name; selectedText = minOf(today, listeningRange(it, anchor).end).toString() },
        onMove = { direction ->
            val next = shiftListeningDate(period, anchor, direction)
            if (listeningRange(period, next).start <= today) {
                anchorText = next.toString(); selectedText = minOf(today, listeningRange(period, next).end).toString()
            }
        }, onDay = { selectedText = it.toString() }, onRetry = { retry++ }, onLogin = onLogin, onBack = onBack)
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
