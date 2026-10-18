package io.github.currencortex.music.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.core.media.ListenTogetherPair
import io.github.currencortex.music.data.netease.listen.*
import io.github.currencortex.music.ui.component.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Read-only archive pages. Network requests, invitations and sharing belong to their explicit callbacks. */
@Composable internal fun ListenTogetherHistoryContent(
    state: ListenTogetherArchiveState,
    onBack: () -> Unit,
    onHistory: (Long?) -> Unit,
    onRanking: () -> Unit,
    onReport: (String) -> Unit,
    onMore: () -> Unit,
    onRetry: () -> Unit,
    onInvite: (ListenTogetherParticipant) -> Unit,
    onShare: (ListenTogetherRecordReport) -> Unit,
    onCollect: () -> Unit,
    onOfficial: (ListenTogetherRecordReport) -> Unit,
    activeRoom: Boolean = false,
    onFate: ((ListenTogetherParticipant, Int) -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    val title = when (state.page) {
        ListenArchivePage.RANKING -> "一起听排行"
        ListenArchivePage.REPORT -> "和 TA 的一起听"
        else -> "一起听记录"
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
        .testTag("listen_archive_screen")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(colors.surfaceContainer)
                .clickable(role = Role.Button, onClick = onBack).testTag("listen_archive_back"), contentAlignment = Alignment.Center) {
                Icon(MusicIcons.Back, "返回", Modifier.size(22.dp), tint = colors.onSurface)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("网易云音乐", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
            }
            if (state.page != ListenArchivePage.HISTORY || state.peerFilter != null) {
                MusicTextAction("全部记录", { onHistory(null) }, Modifier.testTag("listen_archive_all"))
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("listen_archive_list"),
            contentPadding = musicScrollPadding(PaddingValues(top = 8.dp, bottom = 24.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) item { ArchiveMessage("正在读取一起听记录…", tag = "listen_archive_loading") }
            state.error?.let { error -> item {
                ArchiveMessage(if (state.permissionRequired) "需要网易云会员权限" else "暂时无法读取", error,
                    if (state.permissionRequired) "网易云限制了这项数据的访问，请在账号具备权限后重试。" else null,
                    "listen_archive_error", onRetry)
            } }
            when (state.page) {
                ListenArchivePage.HISTORY -> {
                    val page = state.history
                    if (state.peerFilter == null) {
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("和 TA 最常一起听", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            MusicTextAction("排行", onRanking, Modifier.testTag("listen_archive_ranking"))
                        } }
                        page?.mostListen?.let { most -> item {
                            Card(Modifier.fillMaxWidth().testTag("listen_archive_most")) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val self = state.self?.let { current -> current.copy(avatarUrl = most.selfAvatarUrl.ifBlank { current.avatarUrl }) }
                                        ArchivePair(listOfNotNull(self, most.user), Modifier.size(88.dp, 55.dp))
                                        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text("你和${most.user.displayName()}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(if (most.online) "好友在线" else "一起听的陪伴", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                                        }
                                    }
                                    ArchiveStats("陪伴彼此", durationLabel(most.totalDurationMs), "一起听过", countLabel(most.totalSongCount))
                                    if (most.totalDurationMs == null || most.totalSongCount == null) ArchiveMuted("部分累计数据仅会员可查看")
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        MusicTextAction("查看你们的记录", { onHistory(most.user.userId) }, Modifier.weight(1f).testTag("listen_archive_most_history"))
                                        if (onFate != null) MusicTextAction("缘分报告", { onFate(most.user, most.ltType) },
                                            Modifier.weight(1f).testTag("listen_archive_most_fate"))
                                    }
                                }
                            }
                        } }
                    }
                    val records = page?.records.orEmpty().filter { state.peerFilter == null || it.user.userId == state.peerFilter }
                    if (activeRoom) item { ArchiveMuted("正在一起听中，当前会话结束后可再次邀请好友。") }
                    item { Text(if (state.peerFilter == null) "一起听历史" else "和 TA 的一起听历史",
                        Modifier.padding(top = 4.dp, bottom = 4.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    if (!state.loading && state.error == null && records.isEmpty()) item {
                        ArchiveMessage("暂无一起听记录", "一起听结束后，可在这里查看网易云保存的记录。", tag = "listen_archive_empty")
                    }
                    // Keep server ordering and group only consecutive dates; do not reorder sessions locally.
                    records.forEachIndexed { index, record ->
                        val date = record.date.ifBlank { dateLabel(record.startTimeMs, "M月d日") }
                        val previous = records.getOrNull(index - 1)?.let { it.date.ifBlank { dateLabel(it.startTimeMs, "M月d日") } }
                        if (index == 0 || date != previous) item { ArchiveMuted(date.ifBlank { "日期未提供" }) }
                        item { HistoryRecordCard(record, activeRoom, { onReport(record.roomId) }, { onInvite(record.user) }) }
                    }
                    if (page?.hasMore == true) item {
                        MusicTextAction(if (state.loadingMore) "正在加载…" else "加载更多", onMore,
                            Modifier.fillMaxWidth().testTag("listen_archive_more"), enabled = !state.loadingMore && !state.loading)
                    }
                }
                ListenArchivePage.RANKING -> {
                    item { ArchiveMuted("按网易云提供的累计陪伴数据展示") }
                    val rankings = state.ranking?.rankings.orEmpty()
                    if (!state.loading && state.error == null && rankings.isEmpty()) item {
                        ArchiveMessage("暂无一起听排行", "暂时没有可展示的排行数据。", tag = "listen_archive_empty")
                    }
                    items(rankings) { entry ->
                        Card(Modifier.fillMaxWidth().testTag("listen_archive_rank_${entry.user.userId}")) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.rank.takeIf { it > 0 }?.toString() ?: "—", Modifier.width(28.dp), fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold, color = if (entry.rank == 1) colors.primary else colors.onSurfaceVariantSummary)
                                    ArchiveAvatar(entry.user, entry.online)
                                    Text(entry.user.displayName(), Modifier.weight(1f).padding(start = 12.dp), fontSize = 16.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                ArchiveRule()
                                ArchiveStats("陪伴彼此", durationLabel(entry.totalDurationMs), "一起听过", countLabel(entry.totalSongCount))
                                if (entry.totalDurationMs == null || entry.totalSongCount == null) ArchiveMuted("部分累计数据仅会员可查看")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MusicTextAction("查看记录", { onHistory(entry.user.userId) },
                                        Modifier.weight(1f).testTag("listen_archive_rank_history_${entry.user.userId}"))
                                    if (onFate != null) MusicTextAction("缘分报告", { onFate(entry.user, entry.ltType) },
                                        Modifier.weight(1f).testTag("listen_archive_rank_fate_${entry.user.userId}"))
                                }
                            }
                        }
                    }
                }
                ListenArchivePage.REPORT -> {
                    val raw = state.report
                    if (raw != null) {
                        val report = resolveReportPeople(raw, state)
                        val peer = report.peer
                        item { Card(Modifier.fillMaxWidth().testTag("listen_archive_partner")) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (peer != null) ArchiveAvatar(peer, report.online)
                                    Column(Modifier.weight(1f).padding(start = if (peer != null) 12.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(peer?.displayName() ?: "本次一起听", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                        ArchiveMuted(if (report.online) "好友在线" else "一起听已结束")
                                    }
                                }
                                MusicTextAction(if (activeRoom) "正在一起听中" else "再次邀请", { peer?.let(onInvite) },
                                    Modifier.testTag("listen_archive_report_invite"), enabled = peer != null && report.canInvite && !activeRoom)
                                if (activeRoom) ArchiveMuted("当前会话结束后可再次邀请好友。")
                                if (peer != null) onFate?.let { open ->
                                    MusicTextAction("查看和 TA 的缘分报告", { open(peer, report.ltType) },
                                        Modifier.testTag("listen_archive_report_fate"))
                                }
                                report.failMessage.takeIf(String::isNotBlank)?.let { ArchiveMuted(it) }
                            }
                        } }
                        item { Card(Modifier.fillMaxWidth().testTag("listen_archive_report")) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("这一次的陪伴", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    report.endTimeMs?.takeIf { it > 0 }?.let { ArchiveMuted(dateLabel(it, "yyyy.MM.dd")) }
                                }
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    ArchivePair(listOfNotNull(report.self, report.peer), Modifier.size(128.dp, 80.dp))
                                }
                                ArchiveStats("本次一起听了", countLabel(report.songCount), "本次陪伴彼此", durationLabel(report.durationMs), emphasized = true)
                                ArchiveRule()
                                ArchiveStats("累计一起听过", countLabel(report.totalSongCount), "累计陪伴彼此", durationLabel(report.totalDurationMs))
                                if (listOf(report.durationMs, report.songCount, report.totalDurationMs, report.totalSongCount).any { it == null }) {
                                    ArchiveMuted("— 表示网易云未提供该项数据，部分累计数据仅会员可查看。")
                                }
                                if (report.showCollectButton) Button(onClick = onCollect, enabled = !state.collecting,
                                    modifier = Modifier.fillMaxWidth().testTag("listen_archive_collect")) {
                                    Text(if (state.collecting) "正在读取歌曲…" else "收藏本次歌曲到歌单", fontSize = 14.sp)
                                }
                                state.collectionError?.let { Text(it, Modifier.testTag("listen_archive_collection_error"), fontSize = 12.sp, color = colors.error) }
                                Button(onClick = { onShare(report) }, colors = ButtonDefaults.buttonColorsPrimary(),
                                    modifier = Modifier.fillMaxWidth().testTag("listen_archive_share")) {
                                    Text("分享一起听报告", color = colors.onPrimary, fontSize = 14.sp)
                                }
                                MusicTextAction("在网易云查看报告", { onOfficial(report) }, Modifier.testTag("listen_archive_official"))
                            }
                        } }
                    } else if (!state.loading && state.error == null) item {
                        ArchiveMessage("暂无本次报告", "网易云暂未提供这次一起听的数据。", tag = "listen_archive_empty")
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable private fun HistoryRecordCard(record: ListenTogetherHistoryRecord, activeRoom: Boolean, onReport: () -> Unit, onInvite: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("listen_archive_record_${record.id}")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(enabled = record.roomId.isNotBlank(), role = Role.Button, onClick = onReport)
                .testTag("listen_archive_open_${record.id}"), verticalAlignment = Alignment.CenterVertically) {
                ArchiveAvatar(record.user, record.online)
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(record.user.displayName(), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ArchiveMuted(listOf(dateLabel(record.startTimeMs, "HH:mm"), durationLabel(record.durationMs), countLabel(record.songCount))
                        .filter(String::isNotBlank).joinToString(" · "))
                    record.failMessage.takeIf(String::isNotBlank)?.let { ArchiveMuted(it) }
                }
            }
            MusicTextAction("邀请", onInvite, Modifier.testTag("listen_archive_invite_${record.id}"), enabled = record.canInvite && !activeRoom)
        }
    }
}

@Composable private fun ArchiveAvatar(user: ListenTogetherParticipant, online: Boolean) {
    Box(Modifier.size(44.dp)) {
        DecoratedAvatar(user.avatarUrl.takeIf(String::isNotBlank), null, size = 44.dp)
        if (online) Box(Modifier.align(Alignment.BottomEnd).size(10.dp).background(MiuixTheme.colorScheme.primary, CircleShape))
    }
}

@Composable private fun ArchivePair(users: List<ListenTogetherParticipant>, modifier: Modifier) {
    ListeningPairArtwork(ListenTogetherPair(users.map { io.github.currencortex.music.core.media.ListenTogetherParticipant(it.userId, it.displayName(), it.avatarUrl) }), modifier)
}

@Composable private fun ArchiveStats(firstLabel: String, first: String, secondLabel: String, second: String, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(firstLabel to first, secondLabel to second).forEach { (label, value) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ArchiveMuted(label)
                Text(value, fontSize = if (emphasized) 22.sp else 17.sp, fontWeight = FontWeight.SemiBold,
                    color = if (emphasized) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable private fun ArchiveMuted(text: String) {
    Text(text, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable private fun ArchiveRule() { Box(Modifier.fillMaxWidth().height(1.dp).background(MiuixTheme.colorScheme.onSurface.copy(alpha = .07f))) }

@Composable private fun ArchiveMessage(title: String, message: String? = null, detail: String? = null, tag: String, onRetry: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            message?.let { ArchiveMuted(it) }
            detail?.let { ArchiveMuted(it) }
            onRetry?.let { MusicTextAction("重试", it, Modifier.testTag("listen_archive_retry")) }
        }
    }
}

private fun ListenTogetherParticipant.displayName() = nickname.ifBlank { "网易云用户" }
private fun countLabel(count: Long?) = count?.let { "$it 首歌曲" } ?: "—"
private fun durationLabel(ms: Long?): String {
    if (ms == null) return "—"
    val minutes = ms.coerceAtLeast(0) / 60_000
    return when { ms > 0 && minutes == 0L -> "小于 1 分钟"; minutes < 60 -> "$minutes 分钟"; else -> "${minutes / 60} 小时 ${minutes % 60} 分钟" }
}
private fun dateLabel(ms: Long, pattern: String) = if (ms > 0) SimpleDateFormat(pattern, Locale.CHINA).format(Date(ms)) else ""

/** Fill only truly missing profile fields by matching ID; preserve anonymous names returned by NetEase. */
private fun resolveReportPeople(report: ListenTogetherRecordReport, state: ListenTogetherArchiveState): ListenTogetherRecordReport {
    val members = state.sourceRoom?.participants.orEmpty()
    fun resolve(raw: ListenTogetherParticipant?, fallback: ListenTogetherParticipant?): ListenTogetherParticipant? {
        if (raw == null) return fallback
        if (raw.masked) return raw
        val match = members.firstOrNull { it.userId == raw.userId } ?: fallback?.takeIf { it.userId == raw.userId }
        return raw.copy(nickname = raw.nickname.ifBlank { match?.nickname.orEmpty() }, avatarUrl = raw.avatarUrl.ifBlank { match?.avatarUrl.orEmpty() })
    }
    val self = resolve(report.self, state.self)
    return report.copy(self = self, peer = resolve(report.peer, members.firstOrNull { it.userId != self?.userId }))
}
