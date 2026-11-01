package io.github.currencortex.music.feature.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.netease.tasks.*
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun NeteaseTasksScreen(vm: NeteaseTasksViewModel, onBack: (() -> Unit)? = null, onLogin: () -> Unit,
    onNavigate: (String) -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val busy by vm.automation.busy.collectAsStateWithLifecycle()
    val action by vm.currentAction.collectAsStateWithLifecycle()
    val colors = MiuixTheme.colorScheme
    val connected = account.loggedIn && !account.stale
    val list = remember(state.group) { LazyListState() }
    var expandedLogs by rememberSaveable(account.profile?.uid) { mutableStateOf(false) }
    MusicSubpage("网易云任务", "netease_tasks", onBack, backTag = "tasks_back", actions = {
        MusicTextAction(if (state.loading) "更新中" else "刷新", vm::refresh, Modifier.testTag("tasks_refresh"),
            enabled = connected && !state.loading && !busy)
    }) { topInset ->
        LazyColumn(Modifier.fillMaxSize().testTag("tasks_list"), state = list,
            contentPadding = musicScrollPadding(PaddingValues(16.dp, topInset + 8.dp, 16.dp, 32.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("account") { SettingsGroup {
            SettingsPreference(account.profile?.nickname ?: "尚未登录网易云",
                if (connected) "本机直连" else "需要本机登录", leading = {
                    Box(Modifier.size(8.dp).background(if (connected) colors.primary else colors.onSurfaceVariantSummary, CircleShape))
                })
        } }
        if (connected) item("categories") {
            SettingsSegmentedControl(NeteaseTaskGroup.entries.map { it.title }, state.group.ordinal,
                { vm.select(NeteaseTaskGroup.entries[it]) }, tag = "tasks_categories", role = Role.Tab,
                itemTag = { "tasks_tab_${NeteaseTaskGroup.entries[it].name}" })
        }
            if (!connected) item {
                SettingsGroup(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (account.stale) "网易云登录已过期" else "连接网易云账号", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text("登录后查看任务、签到和已完成的奖励。", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                        TaskAction(if (account.stale) "重新登录网易云" else "登录网易云", onLogin, Modifier.testTag("tasks_login"), primary = true)
                    }
                }
            } else {
                item {
                    val kind = when (state.group) {
                        NeteaseTaskGroup.DAILY -> NeteaseCheckIn.DAILY; NeteaseTaskGroup.YUNBEI -> NeteaseCheckIn.YUNBEI
                        NeteaseTaskGroup.VIP -> NeteaseCheckIn.VIP; NeteaseTaskGroup.MUSICIAN -> NeteaseCheckIn.MUSICIAN
                    }
                    val done = options.signedDay(kind) == NeteaseTaskPreferences.day() ||
                        (state.group == NeteaseTaskGroup.DAILY && state.page?.signedToday == true)
                    SettingsGroup(Modifier.fillMaxWidth().animateContentSize(tween(200))) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(when (state.group) {
                                    NeteaseTaskGroup.DAILY -> "每日签到"; NeteaseTaskGroup.YUNBEI -> "云贝中心"
                                    NeteaseTaskGroup.VIP -> "会员成长"; NeteaseTaskGroup.MUSICIAN -> "音乐人任务"
                                }, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                if (done) Text("今日已签到", fontSize = 11.sp, color = colors.primary)
                            }
                            state.page?.summary?.let { Text(it, fontSize = 13.sp, color = colors.onSurfaceVariantSummary) }
                            if (state.page == null) Text(when (kind) {
                                NeteaseCheckIn.DAILY -> "每天签到，继续积累听歌经验"
                                NeteaseCheckIn.YUNBEI -> "查看云贝余额与任务进度"
                                NeteaseCheckIn.VIP -> "黑胶乐签与会员成长值"
                                NeteaseCheckIn.MUSICIAN -> "需要已认证的网易云音乐人账号"
                            }, fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                TaskAction(if (action == NeteaseTaskAction.SIGN) "签到中…" else if (done) "已签到" else "签到",
                                    { vm.sign(kind) }, Modifier.testTag("tasks_sign"), primary = !done,
                                    enabled = !busy && action == null && !done && (kind != NeteaseCheckIn.MUSICIAN || state.page != null))
                                if (state.group != NeteaseTaskGroup.DAILY && state.page != null) TaskAction(
                                    if (action == NeteaseTaskAction.CLAIM) "领取中…" else "领取奖励", vm::claim,
                                    Modifier.testTag("tasks_claim"), enabled = !busy && action == null)
                            }
                        }
                    }
                }
                item {
                    SettingsGroup(Modifier.fillMaxWidth()) {
                        SettingsSwitch("使用时自动签到", options.automatic, vm::automatic,
                            Modifier.testTag("tasks_automatic"), summary = "日常与云贝 · 每天一次", enabled = !busy && action == null)
                    }
                }
                if (state.loading && state.page == null) items(2, key = { "task-loading-$it" }) {
                    SettingsGroup(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            MusicPlaceholder(Modifier.fillMaxWidth(.45f).height(18.dp))
                            MusicPlaceholder(Modifier.fillMaxWidth(.8f).height(12.dp))
                        }
                    }
                }
                state.error?.let { error -> item {
                    SettingsGroup(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("暂时无法读取${state.group.title}任务", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(error, fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurfaceVariantSummary)
                            TaskAction("重新加载", vm::refresh, Modifier.testTag("tasks_retry"), enabled = !busy && !state.loading)
                        }
                    }
                } }
                state.page?.let { page ->
                    if (state.group != NeteaseTaskGroup.DAILY) {
                        item { Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("任务进度", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("${page.items.size} 项", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        } }
                        if (page.items.isEmpty()) item {
                            Text("暂无可展示的任务，可能未开通对应权益。", Modifier.padding(4.dp).testTag("tasks_empty"),
                                fontSize = 13.sp, lineHeight = 20.sp, color = colors.onSurfaceVariantSummary)
                        }
                    }
                    items(page.items, key = { it.id }) { item ->
                        SettingsGroup(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                if (item.detail.isNotBlank()) Text(item.detail, fontSize = 12.sp, lineHeight = 18.sp,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariantSummary)
                                if (item.progress != "以网易云任务状态为准") Text(item.progress, fontSize = 11.sp, color = colors.primary)
                                if (item.destination.isNotBlank()) MusicTextAction("去听日推", { onNavigate(item.destination) })
                            }
                        }
                    }
                }
                if (options.logs.isNotEmpty()) item {
                    SettingsGroup(Modifier.fillMaxWidth().testTag("tasks_journal").animateContentSize(tween(200))) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button) { expandedLogs = !expandedLogs }
                                .semantics { stateDescription = if (expandedLogs) "已展开" else "已收起" }.testTag("tasks_journal_toggle"),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("最近记录", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(if (expandedLogs) "收起" else "查看 ${options.logs.size} 条", fontSize = 12.sp, color = colors.primary)
                            }
                            AnimatedVisibility(expandedLogs, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    options.logs.forEach { log ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(log.label, Modifier.weight(1f), fontSize = 12.sp, lineHeight = 18.sp)
                                            Text(DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
                                                .format(Instant.ofEpochMilli(log.time)), fontSize = 10.sp, color = colors.onSurfaceVariantSummary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Text("进度与奖励由网易云确认；听歌任务按实际播放统计。", Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                    fontSize = 11.sp, lineHeight = 17.sp, color = colors.onSurfaceVariantSummary) }
            }
        }
    }
}

@Composable private fun TaskAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false, enabled: Boolean = true) {
    GlassActionButton(label, onClick, modifier, primary = primary, enabled = enabled, fillWidth = false)
}
