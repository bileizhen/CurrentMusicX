package io.github.currencortex.music.feature.listening

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.currencortex.music.data.profile.listeningDuration
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

@Composable internal fun ListeningPage(source: ListeningSource, period: ListeningPeriod, range: ListeningRange,
    today: LocalDate, selectedDay: LocalDate, report: ListeningDisplay?, day: ListeningDisplay?, loading: Boolean,
    error: String?, signedIn: Boolean, dayLoading: Boolean, dayError: String?,
    onSource: (ListeningSource) -> Unit, onPeriod: (ListeningPeriod) -> Unit, onMove: (Long) -> Unit,
    onDay: (LocalDate) -> Unit, onRetry: () -> Unit, onLogin: () -> Unit, onBack: () -> Unit) {
    var rules by remember { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme
    MusicSubpage("听歌时长", "listening_screen", onBack, actions = {
        MusicTextAction("规则", { rules = !rules }, Modifier.testTag("listening_rules"))
    }) { inset ->
        LazyColumn(Modifier.fillMaxSize().testTag("listening_list"), contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = inset + 12.dp, bottom = LocalMusicBottomInset.current + 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item("source") {
                SettingsSegmentedControl(ListeningSource.entries.map { it.label }, source.ordinal,
                    { onSource(ListeningSource.entries[it]) }, tag = "listening_source")
            }
            item("heading") {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (source == ListeningSource.LOCAL) "我的听歌足迹" else "云村听歌足迹", fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(report?.takeIf { source == ListeningSource.LOCAL || signedIn }?.cumulativeMs?.let { "累计 ${listeningDuration(it)}" }
                        ?: if (source == ListeningSource.LOCAL) "记录这台设备上的音乐时光" else "绑定账号的云端收听记录",
                        fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                }
            }
            if (rules) item("rules") { SettingsGroup {
                SettingsHint(if (source == ListeningSource.LOCAL)
                    "统计这台设备在 CurrentMusicX 中的实际音乐播放时间，暂停、缓冲、拖动进度和 MV 不计入。按播放时的本地日期记录。历史版本保留下来的时长会继续显示，歌曲与时段明细从升级后开始积累。本机记录不随账号切换，不等同于账号累计时长。"
                    else "读取当前绑定的网易云账号听歌足迹，云端收听时长包含音乐、播客和有声书。按北京时间、周日为一周起点，报告可能延迟更新。尚未生成的报告不显示为零。与本机音乐记录分别统计，不相加。")
            } }
            item("period") { SettingsSegmentedControl(ListeningPeriod.entries.map { it.label }, period.ordinal,
                { onPeriod(ListeningPeriod.entries[it]) }, tag = "listening_period") }
            item("range") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton({ onMove(-1) }, "上一${period.label}", Modifier.testTag("listening_previous")) {
                        Icon(MusicIcons.Back, null)
                    }
                    Text(if (period == ListeningPeriod.YEAR) "${range.start.year} 年" else range.label,
                        Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 14.sp)
                    GlassIconButton({ onMove(1) }, "下一${period.label}", Modifier.testTag("listening_next"),
                        enabled = range.end < today) { Icon(MusicIcons.Forward, null) }
                }
            }
            if (source == ListeningSource.NETEASE && !signedIn) item("login") { SettingsGroup {
                SettingsHint("绑定网易云账号后，查看云端听歌足迹。")
                MusicTextAction("绑定网易云", onLogin, Modifier.padding(16.dp).testTag("listening_login"))
            } } else {
                if (loading && report == null) item("loading") { SettingsGroup { SettingsHint("正在整理听歌足迹…") } }
                if (error != null) item("error") { SettingsGroup {
                    SettingsHint(error, error = true)
                    MusicTextAction("重试", onRetry, Modifier.padding(horizontal = 16.dp).testTag("listening_retry"))
                } }
                if (report != null) {
                    item("summary") { SettingsGroup {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(when (period) { ListeningPeriod.WEEK -> if (today in range.start..range.end) "本周收听时长" else "每周收听时长"; ListeningPeriod.MONTH -> "${range.start.monthValue} 月收听时长"; ListeningPeriod.YEAR -> "年度收听时长" },
                                fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariantSummary)
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(report.totalMs?.let(::listeningDuration) ?: "报告尚未生成", Modifier.testTag("listening_total"),
                                    fontSize = 30.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                                report.listenedDays?.let { Text("已听 $it 天", fontSize = 13.sp, color = colors.onSurfaceVariantSummary) }
                            }
                            when (period) {
                                ListeningPeriod.WEEK -> ListeningBars(range.dates.map { it to (report.days[it] ?: 0L) },
                                    selectedDay, today, onDay, daily = true, available = report.days.isNotEmpty())
                                ListeningPeriod.MONTH -> ListeningCalendar(range, report.days, selectedDay, today, onDay)
                                ListeningPeriod.YEAR -> if (report.days.isNotEmpty()) ListeningBars((1..12).map { month ->
                                    LocalDate.of(range.start.year, month, 1) to report.days.filterKeys { it.monthValue == month }.values.sum()
                                }, selectedDay, today, {}, daily = false, available = report.days.isNotEmpty())
                            }
                            if (report.totalMs == 0L) Text("还没有收听记录，听首喜欢的歌吧。", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                        }
                    } }
                    if (period != ListeningPeriod.YEAR) item("day") { SettingsGroup {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (selectedDay == today) "今日收听" else "${selectedDay.monthValue} 月 ${selectedDay.dayOfMonth} 日收听", fontWeight = FontWeight.SemiBold)
                            if (dayLoading) Text("正在读取当天歌曲…", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                            else if (dayError != null) Text(dayError, fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                            else {
                                Text(day?.songCount?.let { "$it 首歌曲" } ?: "暂无歌曲明细", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                if (day?.tracks?.isNotEmpty() == true) ListeningCovers(day.tracks.take(12))
                                day?.totalMs?.let { Text(listeningDuration(it), fontSize = 13.sp, color = colors.onSurfaceVariantSummary) }
                            }
                        }
                    } }
                    if (report.facts.isNotEmpty() || report.yearFacts.isNotEmpty()) item("facts") { SettingsGroup {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            (report.facts + report.yearFacts).forEach { fact ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (fact.cover.isNotBlank()) ListeningCover(fact.cover, fact.title, Modifier.size(56.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(fact.title, fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                                        Text(fact.value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                        if (fact.detail.isNotBlank()) Text(fact.detail, fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                                    }
                                }
                            }
                        }
                    } }
                    if (report.tracks.isNotEmpty() || report.coverUrls.isNotEmpty()) item("wall") { SettingsGroup {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("${period.label}度唱片墙", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            report.songCount?.let { Text("$it 首歌，留下这一${period.label}的回忆", fontSize = 13.sp, color = colors.onSurfaceVariantSummary) }
                            (report.tracks.ifEmpty { report.coverUrls.mapIndexed { index, cover -> ListeningTrack("wall_$index", "收听歌曲封面", cover = cover) } }).take(12).chunked(4).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { track -> ListeningCover(track.cover, track.title, Modifier.weight(1f).aspectRatio(1f)) }
                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    } }
                    if (report.tracks.any { it.title.isNotBlank() }) item("songs") { SettingsGroup {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(if (source == ListeningSource.LOCAL) "常听歌曲" else "收听歌曲", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            report.tracks.take(5).forEachIndexed { index, track -> Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${index + 1}", fontSize = 16.sp, color = colors.onSurfaceVariantSummary)
                                ListeningCover(track.cover, track.title, Modifier.size(44.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(track.title, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariantSummary)
                                    track.durationMs?.let { Text(listeningDuration(it), fontSize = 12.sp, color = colors.primary) }
                                }
                            } }
                        }
                    } }
                    if (report.note.isNotBlank()) item("note") { SettingsHint(report.note) }
                }
            }
        }
    }
}

@Composable private fun ListeningBars(values: List<Pair<LocalDate, Long>>, selected: LocalDate, today: LocalDate,
    select: (LocalDate) -> Unit, daily: Boolean, available: Boolean) {
    val colors = MiuixTheme.colorScheme
    val actualMaximum = values.maxOfOrNull { it.second } ?: 0
    val maximum = actualMaximum.coerceAtLeast(60_000)
    val light = rememberListeningBarLight(actualMaximum.toFloat() / maximum * 140f >= 48f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (available) "最高 ${listeningDuration(actualMaximum)}" else "暂无每日明细", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
        Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(if (daily) 10.dp else 4.dp)) {
            values.forEach { (date, duration) ->
                val label = if (daily) listOf("日", "一", "二", "三", "四", "五", "六")[date.dayOfWeek.value % 7] else "${date.monthValue}"
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                    .then(if (daily) Modifier.selectable(date == selected, enabled = date <= today, role = Role.Button, onClick = { select(date) }) else Modifier)
                    .semantics { contentDescription = "$date，${if (available) listeningDuration(duration) else "暂无数据"}" }
                    .testTag("listening_bar_$date"), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        val barHeight = if (duration > 0) (140f * duration.toFloat() / maximum).coerceAtLeast(6f) else 3f
                        ListeningDurationBar(colors.primary, date == selected || !daily,
                            light.takeIf { barHeight >= 48f }, Modifier.fillMaxWidth(.85f).height(barHeight.dp))
                    }
                    Text(if (daily && date == today) "今" else label, Modifier.padding(top = 10.dp, bottom = 4.dp), fontSize = 11.sp,
                        color = if (daily && date == selected) colors.primary else colors.onSurfaceVariantSummary)
                }
            }
        }
    }
}

@Composable private fun ListeningCalendar(range: ListeningRange, days: Map<LocalDate, Long>, selected: LocalDate,
    today: LocalDate, select: (LocalDate) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val maximum = (days.values.maxOrNull() ?: 0L).coerceAtLeast(1)
    val cells = List<LocalDate?>(range.start.dayOfWeek.value % 7) { null } + range.dates
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row { listOf("日", "一", "二", "三", "四", "五", "六").forEach { Text(it, Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp, color = colors.onSurfaceVariantSummary) } }
        cells.chunked(7).forEach { week -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            week.forEach { date ->
                val duration = days[date] ?: 0L
                Box(Modifier.weight(1f).heightIn(min = 44.dp).aspectRatio(1f).clip(CircleShape)
                    .background(if (date == selected) colors.primary else if (duration > 0) colors.primary.copy(alpha = .10f + .30f * duration.toFloat() / maximum) else colors.onSurface.copy(alpha = .025f))
                    .then(if (date == null) Modifier else Modifier.selectable(date == selected, enabled = date <= today,
                        role = Role.Button, onClick = { select(date) }).semantics { contentDescription = "$date，${if (days.containsKey(date)) listeningDuration(duration) else "暂无记录"}" }
                        .testTag("listening_day_$date")), contentAlignment = Alignment.Center) {
                    if (date != null) Text("${date.dayOfMonth}", fontSize = 13.sp,
                        color = if (date == selected) colors.onPrimary else if (date > today) colors.onSurface.copy(alpha = .25f) else colors.onSurface)
                }
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        } }
    }
}

@Composable private fun ListeningCovers(tracks: List<ListeningTrack>) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tracks.forEach { ListeningCover(it.cover, it.title, Modifier.size(50.dp)) }
    }
}

@Composable private fun ListeningCover(cover: String, description: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MiuixTheme.colorScheme.primary.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
        Icon(MusicIcons.Headphones, null, Modifier.size(22.dp), tint = MiuixTheme.colorScheme.primary.copy(alpha = .35f))
        if (cover.isNotBlank()) AsyncImage(cover, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
