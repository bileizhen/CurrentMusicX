package io.github.currencortex.music.feature.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.data.admin.*
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.json.JsonObject
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal sealed interface AdminDialog {
    data class User(val user: AdminUser, val action: AdminUserAction? = null) : AdminDialog
    data class Announcement(val item: AdminAnnouncement? = null) : AdminDialog
    data class AnnouncementAction(val item: AdminAnnouncement, val action: String, val label: String) : AdminDialog
    data class Kick(val session: AdminSession) : AdminDialog
    data object Cache : AdminDialog
}

@Composable fun AdminScreen(vm: AdminViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val revision by vm.container.accountRepository.sessionRevision.collectAsStateWithLifecycle()
    val settings by vm.container.musicSettings.state.collectAsStateWithLifecycle()
    val identity = AdminIdentity(vm.container.accountRepository.server, account.account?.id, revision)
    val visible = state.identity == identity && identity.server == settings.server && state.authorized && !account.loading && !account.offline &&
        account.account?.let { it.isAdmin || it.isSuper || it.isOwner } == true
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.refresh(); awaitCancellation() }
    }
    // Compose forgets all selected users and form fields as soon as a login/server/permission changes.
    key(identity, settings.server, visible) {
        var dialog by remember { mutableStateOf<AdminDialog?>(null) }
        var query by remember { mutableStateOf(state.query) }
        val enabled = !state.loading && !state.busy
        MusicSubpage("管理员面板", "admin_screen", onBack, actions = {
            MusicTextAction(if (state.loading) "加载中" else "刷新", vm::refresh, enabled = enabled)
        }) { topInset ->
            LazyColumn(Modifier.fillMaxSize().testTag("admin_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 12.dp,
                    bottom = LocalMusicBottomInset.current + 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!visible) {
                    item { SettingsHint(if (state.loading) "正在验证管理权限…" else state.error ?: "仅管理员可访问，请登录有管理权限的账号") }
                } else {
                    item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AdminTab.entries.forEach { tab -> GlassActionButton(tab.label, { vm.select(tab) },
                            Modifier.testTag("admin_tab_${tab.name.lowercase()}"), enabled = !state.busy, primary = state.tab == tab, fillWidth = false) }
                    } }
                    state.error?.let { item { SettingsHint(it, error = true) } }
                    state.message?.let { item { SettingsHint(it) } }
                    if (state.loading) item { SettingsHint("正在更新…") }
                    when (state.tab) {
                        AdminTab.OVERVIEW -> overview(state)
                        AdminTab.USERS -> {
                            item { SettingsGroup { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("admin_user_query"))
                                Text("搜索用户名、昵称、邮箱或手机号", fontSize = 12.sp)
                                MusicTextAction("搜索", { vm.search(query) }, enabled = enabled)
                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                    listOf("reg" to "最新注册", "reg_asc" to "最早注册", "name" to "昵称", "active" to "最近活跃",
                                        "sessions" to "在线设备", "likes" to "点赞", "plays" to "播放", "role" to "角色").forEach { (sort, label) ->
                                        GlassActionButton(label, { vm.search(query, sort) }, enabled = enabled, primary = state.sort == sort, fillWidth = false)
                                    }
                                }
                            } } }
                            state.users?.let { result ->
                                item { SettingsListHeader("共 ${result.total} 位用户") }
                                if (result.users.isEmpty()) item { SettingsHint("没有匹配的用户") }
                                items(result.users, key = { "user-${it.id}" }) { user -> AdminCard(user.name) {
                                    Text("@${user.username} · ID ${user.id}", fontSize = 13.sp)
                                    Text(listOfNotNull(if (user.isOwner) "站长" else if (user.isSuper) "超级管理员" else if (user.isAdmin) "管理员" else "普通用户",
                                        if (user.disabled) "已禁用" else null, user.title.ifBlank { null }).joinToString(" · "), fontSize = 13.sp)
                                    if (user.email.isNotBlank() || user.phone.isNotBlank()) Text(listOf(user.email, user.phone).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp)
                                    Text("${user.points} 积分 · 等级 ${user.level} · ${user.sessions} 台在线设备", fontSize = 13.sp)
                                    Text("点赞 ${user.likes} · 歌单 ${user.playlists} · 播放 ${user.plays}", fontSize = 13.sp)
                                    if (AdminUserAction.entries.any { it.allowed(requireNotNull(state.actor), user) })
                                        MusicTextAction("管理用户", { dialog = AdminDialog.User(user) }, enabled = enabled,
                                            modifier = Modifier.testTag("admin_manage_${user.id}"))
                                } }
                                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    MusicTextAction("上一页", { vm.page(-1) }, enabled = enabled && state.offset > 0)
                                    Text("第 ${state.offset / AdminRepository.PAGE_SIZE + 1} 页", Modifier.padding(top = 12.dp), fontSize = 13.sp)
                                    MusicTextAction("下一页", { vm.page(1) }, enabled = enabled && state.offset + AdminRepository.PAGE_SIZE < result.total)
                                } }
                            }
                        }
                        AdminTab.ANNOUNCEMENTS -> {
                            item { MusicTextAction("发布公告", { dialog = AdminDialog.Announcement() }, enabled = enabled) }
                            state.announcements?.let { rows ->
                                if (rows.isEmpty()) item { SettingsHint("暂无公告") }
                                items(rows, key = { "announcement-${it.id}" }) { announcement -> AdminCard(announcement.title) {
                                    Text("${announcement.author} · ${adminTime(announcement.createdAt)}" +
                                        if (announcement.removed) " · 已移除" else if (announcement.pinned) " · 置顶" else "", fontSize = 12.sp)
                                    Text(announcement.body, fontSize = 14.sp, maxLines = 8)
                                    if (announcement.canEdit) Row(Modifier.horizontalScroll(rememberScrollState())) {
                                        MusicTextAction("编辑", { dialog = AdminDialog.Announcement(announcement) }, enabled = enabled)
                                        if (announcement.removed) MusicTextAction("恢复", {
                                            dialog = AdminDialog.AnnouncementAction(announcement, "restore", "恢复公告") }, enabled = enabled)
                                        else {
                                            MusicTextAction(if (announcement.pinned) "取消置顶" else "置顶", {
                                                dialog = AdminDialog.AnnouncementAction(announcement, "pin", if (announcement.pinned) "取消置顶" else "置顶公告") }, enabled = enabled)
                                            MusicTextAction("移除", { dialog = AdminDialog.AnnouncementAction(announcement, "remove", "移除公告") }, enabled = enabled, destructive = true)
                                        }
                                    }
                                } }
                            }
                        }
                        AdminTab.DEVICES -> state.sessions?.let { rows ->
                            item { SettingsHint("${rows.size} 台在线设备（服务器最多返回 100 台）；强制下线会撤销该用户的全部会话。") }
                            if (rows.isEmpty()) item { SettingsHint("暂无在线设备") }
                            items(rows, key = { "session-${it.id}" }) { session -> AdminCard(session.nickname.ifBlank { session.username }) {
                                Text("@${session.username} · ${session.platform}", fontSize = 13.sp)
                                Text(session.device, fontSize = 14.sp)
                                Text("登录：${adminTime(session.created_at)}\n最近活跃：${adminTime(session.last_seen)}", fontSize = 12.sp)
                                MusicTextAction("该用户全部设备下线", { dialog = AdminDialog.Kick(session) },
                                    enabled = enabled && session.user_id != state.actor?.id, destructive = true)
                            } }
                        }
                        AdminTab.CLIENTS -> state.clients?.let { clients ->
                            val current = clients.obj("current"); val cmx = clients.obj("cmx"); val apk = cmx.obj("apk")
                            item { AdminCard("CurrentMusic") {
                                AdminMetric("版本", current.text("version")); AdminMetric("安装包", adminBytes(current.number("size")))
                                AdminMetric("发布时间", adminTime(current.number("publishedAt") ?: 0))
                                AdminMetric("镜像", if (current.flag("mirrored")) "已同步" else "未同步")
                                if (current.text("changelog").isNotBlank()) Text(current.text("changelog"), fontSize = 13.sp)
                            } }
                            item { AdminCard("CurrentMusicX") {
                                AdminMetric("版本", cmx.text("version")); AdminMetric("安装包", adminBytes(apk.number("size")))
                                AdminMetric("下载次数", apk.text("downloads")); AdminMetric("镜像", if (apk.flag("mirrored")) "已同步" else "未同步")
                                AdminMetric("发布时间", adminTime(cmx.number("publishedAt") ?: 0))
                                if (cmx.text("body").isNotBlank()) Text(cmx.text("body"), fontSize = 13.sp)
                            } }
                            item { SettingsListHeader("历史版本") }
                            items(clients.obj("cmx").rows("history")) { release -> AdminCard("v${release.text("version")}") {
                                AdminMetric("下载次数", release.text("apkDownloads")); AdminMetric("安装包", adminBytes(release.number("apkSize")))
                                AdminMetric("发布于", release.text("publishedAt"))
                            } }
                        }
                        AdminTab.SYSTEM -> state.overview?.let { stats ->
                            item { AdminCard("系统与数据") {
                                listOf("playlists" to "自建歌单", "tracks" to "歌单曲目", "bindings" to "网易云绑定", "songs" to "歌曲元数据", "cache" to "辅助缓存")
                                    .forEach { (key, label) -> AdminMetric(label, stats.text(key)) }
                            } }
                            item { roleCounts(stats) }
                            item { AdminCard("缓存维护") {
                                Text("清理歌词、评论、详情等辅助缓存，不影响音质档位探测缓存。清理后首次访问对应内容可能稍慢。", fontSize = 13.sp)
                                MusicTextAction("清理辅助缓存", { dialog = AdminDialog.Cache }, enabled = enabled, destructive = true)
                            } }
                        }
                    }
                }
            }
        }
        if (visible) dialog?.let { selection -> SettingsUiScope {
            AdminDialogContent(selection, state, vm, { dialog = it }, { if (!state.busy) dialog = null })
        } }
    }
}

private fun LazyListScope.overview(state: AdminState) {
    state.overview?.let { stats ->
        item { AdminCard("站点概览") {
            listOf("users" to "注册用户", "usersToday" to "今日新增", "usersWeek" to "近 7 日新增", "sessions" to "在线设备",
                "onlineUsers" to "近 1 小时活跃用户", "disabled" to "禁用账号", "plays" to "播放记录", "likes" to "站内点赞", "ncmLikes" to "网易云红心")
                .forEach { (key, label) -> AdminMetric(label, stats.text(key)) }
        } }
        item { roleCounts(stats) }
        item { AdminCard("站点出站流量") {
            val traffic = state.traffic
            if (traffic?.flag("available") == true) {
                AdminMetric("今日", adminBytes(traffic.number("today"))); AdminMetric("本月", adminBytes(traffic.number("month")))
                AdminMetric("实时速率（近 5 分钟均值）", adminBytes(traffic.text("rate5m").toDoubleOrNull()?.toLong()) + "/s")
                traffic.rows("top").forEach { AdminMetric(it.text("path"), adminBytes(it.number("bytes"))) }
            } else Text(state.trafficError ?: "流量统计暂不可用", fontSize = 13.sp)
        } }
        item { AdminCard("近 30 天用户增长") { stats.rows("userDaily").forEach { AdminMetric(it.text("day"), it.text("count") + " 人") } } }
        item { AdminCard("近 14 天播放活跃") { stats.rows("playDaily").forEach {
            AdminMetric(it.text("day"), "${it.text("plays")} 次 · ${it.text("users")} 人")
        } } }
    }
}
@Composable private fun roleCounts(stats: JsonObject) = AdminCard("角色构成") {
    listOf("owner" to "站长", "super" to "超级管理员", "admin" to "管理员", "user" to "普通用户")
        .forEach { (key, label) -> AdminMetric(label, stats.obj("roleCounts").text(key)) }
}
@Composable internal fun AdminCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    SettingsGroup { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        content()
    } }
}
@Composable internal fun AdminMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value.ifBlank { "—" }, Modifier.weight(1f), fontSize = 13.sp)
    }
}
internal fun adminTime(value: Long): String = if (value <= 0) "—" else
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(if (value < 100_000_000_000L) value * 1000 else value))
internal fun adminBytes(value: Long?): String {
    if (value == null) return "—"
    var size = value.toDouble(); var unit = 0
    while (size >= 1024 && unit < 4) { size /= 1024; unit++ }
    return String.format(Locale.getDefault(), "%.1f %s", size, listOf("B", "KB", "MB", "GB", "TB")[unit])
}
