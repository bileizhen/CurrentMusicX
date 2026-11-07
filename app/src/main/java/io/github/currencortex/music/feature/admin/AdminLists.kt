package io.github.currencortex.music.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.admin.*
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val adminSorts = listOf("reg" to "最新注册", "reg_asc" to "最早注册", "name" to "昵称", "active" to "最近活跃",
    "sessions" to "在线设备", "likes" to "点赞", "plays" to "播放", "role" to "角色")

@Composable internal fun AdminSearch(state: AdminState, actions: AdminActions) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    LaunchedEffect(state.query) { query = state.query }
    var sorting by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun search(value: String = query, sort: String = state.sort) {
        if (state.busy) return
        focus.clearFocus(); keyboard?.hide(); actions.search(value, sort)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        MusicSearchBar(query, { search() }, Modifier.fillMaxWidth(), onInput = {
            query = it
            if (it.isEmpty() && state.query.isNotEmpty()) search("")
        }, placeholder = "用户名、昵称、邮箱或手机号", inputTag = "admin_user_query", submitTag = "admin_search",
            clearTag = "admin_search_clear", onCancel = { query = ""; focus.clearFocus(); keyboard?.hide() })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.loading) "正在查找…" else "${state.users?.total ?: 0} 位用户", Modifier.weight(1f), fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Row(Modifier.heightIn(min = 48.dp).clickable(enabled = !state.busy, role = Role.Button) {
                focus.clearFocus(); keyboard?.hide(); sorting = true
            }.testTag("admin_sort").padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(adminSorts.firstOrNull { it.first == state.sort }?.second ?: "排序", fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary)
                Icon(MusicIcons.Sort, null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.primary)
            }
        }
    }
    if (sorting) MusicDialog("用户排序", { sorting = false }) {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            adminSorts.forEach { (value, label) -> MusicDialogChoiceRow(label, state.sort == value, {
                sorting = false; search(query, value)
            }) }
        }
    }
}

internal fun LazyListScope.adminUsers(state: AdminState, actions: AdminActions, open: (AdminDestination) -> Unit) {
    val result = state.users ?: return
    if (result.users.isEmpty()) item { AdminEmpty("没有匹配的用户", "试试其他关键词，或查看全部账号", "清除搜索") { actions.search("", state.sort) } }
    items(result.users, key = { "user-${it.id}" }) { user -> SettingsGroup {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, enabled = !state.loading && !state.busy) { open(AdminDestination.User(user)) }
            .testTag("admin_manage_${user.id}").padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MiuixTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Text(user.name.take(1).uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(user.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@${user.username}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (user.isAdmin || user.isSuper || user.isOwner) AdminBadge(adminRole(user))
                    if (user.disabled) AdminBadge("已禁用", warning = true)
                    if (user.sessions > 0) Text("${user.sessions} 台在线", fontSize = 11.sp, color = MiuixTheme.colorScheme.primary)
                }
            }
            Icon(MusicIcons.Forward, null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    } }
    if (result.total > 0 || state.offset > 0) item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        MusicTextAction("上一页", { actions.page(-1) }, Modifier.testTag("admin_previous_page"), enabled = !state.loading && !state.busy && state.offset > 0)
        Text("${state.offset / AdminRepository.PAGE_SIZE + 1} / ${((result.total + AdminRepository.PAGE_SIZE - 1) / AdminRepository.PAGE_SIZE).coerceAtLeast(1)}",
            fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        MusicTextAction("下一页", { actions.page(1) }, Modifier.testTag("admin_next_page"),
            enabled = !state.loading && !state.busy && state.offset + AdminRepository.PAGE_SIZE < result.total)
    } }
}

internal fun LazyListScope.adminAnnouncements(state: AdminState, open: (AdminDestination) -> Unit) {
    val rows = state.announcements ?: return
    if (rows.isEmpty()) item { AdminEmpty("还没有公告", "发布后，用户将在客户端中看到公告。") }
    items(rows, key = { "announcement-${it.id}" }) { item -> SettingsGroup {
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button, enabled = !state.busy) { open(AdminDestination.Announcement(item)) }
            .testTag("admin_announcement_${item.id}").padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AdminBadge(if (item.removed) "已移除" else if (item.pinned) "置顶" else "生效中", warning = item.removed)
                if (!item.canEdit) AdminBadge("只读")
            }
            Text(item.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.body, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${item.author} · ${adminTime(item.createdAt)}", Modifier.weight(1f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Icon(MusicIcons.Forward, null, Modifier.size(16.dp))
            }
        }
    } }
}

internal fun LazyListScope.adminDevices(state: AdminState, confirm: (AdminConfirmation) -> Unit) {
    val sessions = state.sessions ?: return
    val groups = sessions.groupBy { it.user_id }.values.toList()
    item { Text("${groups.size} 位用户 · ${sessions.size} 台设备" + if (sessions.size >= 100) "（最多显示 100 台）" else "",
        Modifier.padding(horizontal = 2.dp, vertical = 4.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
    if (groups.isEmpty()) item { AdminEmpty("暂无在线设备", "新会话会在刷新后显示。") }
    items(groups, key = { "devices-${it.first().user_id}" }) { rows ->
        val first = rows.first()
        AdminExpandable(first.nickname.ifBlank { first.username }, "@${first.username} · ${rows.size} 台设备", "admin_devices_${first.user_id}") {
            rows.forEachIndexed { index, session ->
                if (index > 0) SettingsDivider(0.dp)
                Text(session.device, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("${session.platform} · 最近活跃 ${adminTime(session.last_seen)}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            if (first.user_id == state.actor?.id) SettingsHint("当前账号，请在账号与安全中管理自己的登录。")
            else MusicTextAction("全部设备下线", { confirm(AdminConfirmation.Kick(first)) }, enabled = !state.loading && !state.busy, destructive = true)
        }
    }
}
internal fun adminRole(user: AdminUser) = if (user.isOwner) "站长" else if (user.isSuper) "超级管理员" else if (user.isAdmin) "管理员" else "普通用户"
