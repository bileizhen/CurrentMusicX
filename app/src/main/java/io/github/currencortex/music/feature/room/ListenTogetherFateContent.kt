package io.github.currencortex.music.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import io.github.currencortex.music.data.netease.listen.ListenTogetherFateReport
import io.github.currencortex.music.ui.component.ListeningPairArtwork
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.musicScrollPadding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Native relationship statistics are read-only here. Deletion only requests the caller's confirmation. */
@Composable internal fun ListenTogetherFateContent(
    report: ListenTogetherFateReport?,
    loading: Boolean,
    permissionRequired: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onHistory: (Long) -> Unit,
    onShare: (ListenTogetherFateReport) -> Unit,
    onDeleteRequest: () -> Unit,
    onOfficial: (ListenTogetherFateReport) -> Unit,
    busy: Boolean = false,
    sharing: Boolean = false,
    error: Boolean = false,
    onInvite: ((ListenTogetherFateReport) -> Unit)? = null,
    activeRoom: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
        .testTag("listen_fate_screen")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(colors.surfaceContainer)
                .clickable(role = Role.Button, onClick = onBack).testTag("listen_fate_back"), contentAlignment = Alignment.Center) {
                Icon(MusicIcons.Back, "返回", Modifier.size(22.dp), tint = colors.onSurface)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("和 TA 的缘分报告", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                FateMuted("网易云一起听")
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("listen_fate_list"),
            contentPadding = musicScrollPadding(PaddingValues(top = 8.dp, bottom = 24.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) item { FateStatus("正在读取缘分报告…", "listen_fate_loading") }
            if (permissionRequired) item {
                FateStatus("需要网易云会员权限", "listen_fate_permission", "请使用具备报告访问权限的账号后重试。", onRetry)
            } else if (error) item {
                // The detailed failure is delivered by the application's top notice channel.
                FateStatus("暂时无法读取报告", "listen_fate_error", "请稍后重试。", onRetry)
            }
            if (report != null && !permissionRequired) {
                item { Card(Modifier.fillMaxWidth().testTag("listen_fate_summary")) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val people = listOfNotNull(report.self, report.peer)
                            ListeningPairArtwork(ListenTogetherPair(people.map {
                                io.github.currencortex.music.core.media.ListenTogetherParticipant(it.userId, it.nickname, it.avatarUrl)
                            }), Modifier.size(88.dp, 55.dp))
                            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(people.joinToString(" · ") { it.nickname.ifBlank { "网易云用户" } }.ifBlank { "一起听的你们" },
                                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                FateMuted("属于你们的音乐时光")
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FateMuted("累计陪伴彼此")
                            Text(fateDuration(report.totalDurationMs), Modifier.testTag("listen_fate_total_duration"),
                                fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            FateMetric("一起听过", report.totalSongCount?.let { "$it 首歌曲" } ?: "—",
                                Modifier.weight(1f).testTag("listen_fate_total_songs"))
                            FateMetric("共同红心", report.commonRedSongCount?.let { "$it 首歌曲" } ?: "—",
                                Modifier.weight(1f).testTag("listen_fate_common_red"))
                        }
                        onInvite?.let { invite -> MusicTextAction(if (activeRoom) "正在一起听中" else "再次邀请", { invite(report) },
                            Modifier.testTag("listen_fate_invite"), enabled = report.canInvite && !report.peer.masked && !activeRoom && !busy) }
                        report.failMessage.takeIf(String::isNotBlank)?.let { FateMuted(it) }
                        if (listOf(report.totalDurationMs, report.totalSongCount, report.commonRedSongCount).any { it == null }) {
                            FateMuted("— 表示网易云未提供这项数据。")
                        }
                    }
                } }
                item { Card(Modifier.fillMaxWidth().testTag("listen_fate_first")) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("从第一次一起听开始", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            FateMetric("首次一起听", fateDate(report.firstListenTimeMs, "yyyy.MM.dd HH:mm"), Modifier.weight(1f))
                            FateMetric("相识天数", report.daysSinceFirstListen?.let { "$it 天" } ?: "—", Modifier.weight(1f))
                        }
                    }
                } }
                if (report.latestLateNightTimeMs != null || report.mostPlayedSongName.isNotBlank()) item {
                    Card(Modifier.fillMaxWidth().testTag("listen_fate_memories")) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("难忘的音乐时刻", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            if (report.latestLateNightTimeMs != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FateMuted("一起听到最晚的那天")
                                    Text(fateDate(report.latestLateNightTimeMs, "yyyy.MM.dd  HH:mm"), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            if (report.mostPlayedSongName.isNotBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FateMuted("一起听得最多的歌")
                                    Text(report.mostPlayedSongName, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                item { Card(Modifier.fillMaxWidth().testTag("listen_fate_longest")) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("一次最长的陪伴", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(fateDuration(report.longestDurationMs), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    }
                } }
                item { Card(Modifier.fillMaxWidth().testTag("listen_fate_actions")) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onShare(report) }, enabled = !busy && !sharing && !loading,
                            colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.fillMaxWidth().testTag("listen_fate_share")) {
                            Text(if (sharing) "正在生成报告…" else "分享缘分报告", fontSize = 14.sp, color = colors.onPrimary)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MusicTextAction("一起听记录", { onHistory(report.partnerUid) }, Modifier.weight(1f).testTag("listen_fate_history"),
                                enabled = report.partnerUid > 0 && !busy)
                            MusicTextAction("网易云报告", { onOfficial(report) }, Modifier.weight(1f).testTag("listen_fate_official"), enabled = !busy)
                        }
                        MusicTextAction("删除缘分报告", onDeleteRequest, Modifier.testTag("listen_fate_delete"),
                            enabled = !busy && !sharing && !loading && report.partnerUid > 0, destructive = true)
                        if (report.openActivity) FateMuted("删除报告会回收关联的一起听徽章与进度。")
                    }
                } }
            } else if (!loading && !permissionRequired && !error) item {
                FateStatus("暂无缘分报告", "listen_fate_empty", "网易云暂未提供你们的缘分数据。")
            }
        }
    }
}

@Composable private fun FateMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FateMuted(label)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun FateMuted(text: String) {
    Text(text, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable private fun FateStatus(title: String, tag: String, message: String? = null, retry: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            message?.let { FateMuted(it) }
            retry?.let { MusicTextAction("重试", it, Modifier.testTag("listen_fate_retry")) }
        }
    }
}

private fun fateDuration(ms: Long?): String {
    if (ms == null) return "—"
    val minutes = ms / 60_000
    return when { ms > 0 && minutes == 0L -> "小于 1 分钟"; minutes < 60 -> "$minutes 分钟"; else -> "${minutes / 60} 小时 ${minutes % 60} 分钟" }
}
private fun fateDate(ms: Long?, pattern: String): String = ms?.takeIf { it > 0 }
    ?.let { SimpleDateFormat(pattern, Locale.CHINA).format(Date(it)) } ?: "—"
