package io.github.currencortex.music.feature.room

import io.github.currencortex.music.ui.component.musicScrollPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import io.github.currencortex.music.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.testTag
import android.content.ClipData
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.util.collectAsPageState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.room.*
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicCover
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.MusicCategoryTabs
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import top.yukonga.miuix.kmp.basic.*

@Composable fun RoomScreen(vm: RoomViewModel, onBack: () -> Unit, onSearch: () -> Unit, onPlayer: () -> Unit,
    onDialogActive: (Boolean) -> Unit = {}) {
    val browser by vm.browser.collectAsPageState()
    val live by vm.session.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val passwordRoom by vm.passwordRoom.collectAsStateWithLifecycle()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var create by rememberSaveable { mutableStateOf(false) }
    var exit by remember { mutableStateOf(false) }
    var close by remember { mutableStateOf(false) }
    var memberDialog by remember { mutableStateOf(false) }
    val dialogActive = create || exit || close || memberDialog || passwordRoom != null
    LaunchedEffect(dialogActive) { onDialogActive(dialogActive) }
    DisposableEffect(Unit) { onDispose { onDialogActive(false) } }
    val clipboard = LocalClipboard.current; val scope = rememberCoroutineScope()
    LaunchedEffect(live.detail?.room?.id) { if (live.detail != null) create = false }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp).testTag("room_screen")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MusicTextAction("返回", onClick = onBack)
            Text("一起听", Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        }
        val detail = live.detail
        if (detail == null) {
            TextField(query, { query = it }, label = "六位房间号或房间名称", modifier = Modifier.fillMaxWidth().testTag("room_query"))
            Row { MusicTextAction("查找", onClick = { vm.search(query.trim()) }, enabled = !busy)
                MusicTextAction("创建房间", onClick = { create = true }, enabled = account.account != null && !busy)
                MusicTextAction(if (browser.loading) "刷新中" else "刷新", onClick = { vm.load() }, enabled = !browser.loading && !busy) }
            if (busy) Text("正在进入房间…", color = MiuixTheme.colorScheme.primary, fontSize = 14.sp)
            if (browser.loading && browser.rooms.isEmpty()) Text("正在加载房间…")
            browser.error?.let { Text(it); MusicTextAction("重试", onClick = { vm.load() }) }
            live.error?.let { Text(it) }
            LazyColumn(Modifier.weight(1f), contentPadding = musicScrollPadding(PaddingValues(0.dp)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(browser.rooms, key = { it.id }) { room ->
                    Card(Modifier.fillMaxWidth().animateItem()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(room.name, Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            MusicTextAction("加入", onClick = { vm.find(room.code) }, enabled = !busy)
                        }
                        Text("#${room.code} · ${room.online} 人在线", color = MiuixTheme.colorScheme.primary, fontSize = 14.sp)
                        Text(listOf(room.ownerName.takeIf { it.isNotBlank() }, if (room.freeMode) "自由点歌" else "点歌需要审批").filterNotNull().joinToString(" · "),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 14.sp)
                    } }
                }
                if (browser.more) item { MusicTextAction("加载更多", onClick = { vm.load(more = true) }, enabled = !browser.loading) }
                if (!browser.loading && browser.rooms.isEmpty() && browser.error == null) item { Text("暂无公开房间") }
            }
        } else {
            val role = detail.role(account.account?.id ?: 0)
            Text(detail.room.name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("#${detail.room.code} · ${detail.members.size} 人 · ${when(role) { RoomRole.OWNER -> "房主"; RoomRole.ADMIN -> "管理员"; else -> "成员" }}",
                fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(if (live.connected) "已连接 · 播放同步中" else "正在连接房间，自动恢复同步…", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
            Row { MusicTextAction("复制房间号", onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("房间号", detail.room.code))) } })
                MusicTextAction("退出房间", onClick = { exit = true }, enabled = !busy) }
            live.error?.let { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, Modifier.weight(1f), fontSize = 13.sp)
                MusicTextAction("知道了", onClick = vm.session::dismissError)
            } }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    val track = detail.timeline?.trackMeta
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MusicCover(track?.pic.orEmpty(), Modifier.size(56.dp), cornerRadius = 16.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(track?.name ?: "等待点歌", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track?.artists?.takeIf { it.isNotBlank() } ?: if (detail.room.freeMode) "所有成员都可以点歌" else "点歌通过审批后加入队列",
                                fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MusicTextAction("点歌", onClick = onSearch)
                        MusicTextAction("播放器", onClick = onPlayer)
                        MusicTextAction(if (live.refreshing) "刷新中" else "刷新", onClick = vm.session::refreshWithFeedback, enabled = !live.refreshing)
                    }
                    if (role?.controls == true) {
                        val controlsBusy = live.pendingActions.any { it in setOf("prev", "next", "play", "pause", "seek") }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            RoomTransport("上一首", R.drawable.player_symbol_skip_previous_fill1, vm.session::previous, !controlsBusy)
                            RoomTransport(if(detail.timeline?.playing == true) "暂停" else "播放", if (detail.timeline?.playing == true) R.drawable.player_symbol_pause_fill1 else R.drawable.player_symbol_play_arrow_fill1,
                                { vm.session.play(detail.timeline?.playing != true) }, !controlsBusy, primary = true)
                            RoomTransport("下一首", R.drawable.player_symbol_skip_next_fill1, vm.session::next, !controlsBusy)
                        }
                        if (controlsBusy) Text("正在同步操作…", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(Modifier.weight(1f)) {
                if (maxWidth >= 700.dp) Row(Modifier.fillMaxSize().testTag("room_two_panes"), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    RoomQueue(detail, role, vm, Modifier.weight(1f)); RoomMembers(detail, role, vm, Modifier.weight(1f), { close = true }, { memberDialog = it })
                } else {
                    var members by rememberSaveable { mutableStateOf(false) }
                    Column {
                        MusicCategoryTabs(listOf("queue" to "点歌队列 ${detail.queue.size}", "members" to "成员与设置 ${detail.members.size}"),
                            if (members) "members" else "queue", { members = it == "members" }, "room_tab")
                        if (members) RoomMembers(detail, role, vm, Modifier.weight(1f), { close = true }, { memberDialog = it })
                        else RoomQueue(detail, role, vm, Modifier.weight(1f)) }
                }
            }
        }
    }
    if (create) RoomForm("创建房间", { if (!busy) create = false }, saving = busy, error = browser.error) { name, pw, public, free -> vm.create(name, pw.orEmpty(), public, free) }
    passwordRoom?.let { room ->
        var password by remember(room.id) { mutableStateOf("") }
        MusicDialog("加入 ${room.name}", onDismiss = { vm.passwordRoom.value = null }) {
            TextField(password, { password = it }, label = "房间密码", visualTransformation = PasswordVisualTransformation())
            browser.error?.let { Text(it, fontSize = 13.sp) }
            MusicTextAction(if (busy) "正在加入…" else "加入", onClick = { vm.join(room, password) }, enabled = !busy)
        }
    }
    if (exit) MusicDialog("退出一起听", onDismiss = { exit = false }) {
        Text("退出后恢复本地队列，保持暂停。")
        MusicTextAction("退出房间", onClick = { exit = false; vm.leave() }, modifier = Modifier.testTag("confirm_leave_room"))
    }
    if (close) MusicDialog("关闭房间", onDismiss = { close = false }) {
        Text("所有成员都会离开此房间。")
        MusicTextAction("关闭房间", onClick = { close = false; vm.session.action("close", permission = { it == RoomRole.OWNER }) })
    }
}
@Composable private fun RoomTransport(label: String, icon: Int, onClick: () -> Unit, enabled: Boolean, primary: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    val ink = (if (primary) colors.onPrimary else colors.onSurface).copy(alpha = if (enabled) 1f else .35f)
    Column(Modifier.width(72.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(44.dp).background(if (primary) colors.primary.copy(alpha = if (enabled) 1f else .35f) else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp), tint = ink)
        }
        Text(label, fontSize = 12.sp, color = colors.onSurface.copy(alpha = if (enabled) .65f else .3f))
    }
}
@Composable private fun RoomQueue(detail: RoomDetail, role: RoomRole?, vm: RoomViewModel, modifier: Modifier) {
    val live by vm.session.state.collectAsStateWithLifecycle()
    LazyColumn(modifier, contentPadding = musicScrollPadding(PaddingValues(0.dp)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(detail.queue, key = { it.id }) { item -> Card(Modifier.fillMaxWidth().animateItem()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val pending = "queue:${item.id}" in live.pendingActions
            Text(item.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val subtitle = listOf(item.artists, item.requester).filter { it.isNotBlank() }.joinToString(" · ")
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(when(item.status) { "pending" -> "待审批"; "playing" -> "正在播放"; "rejected" -> "已拒绝"; else -> "已加入队列" })
            if (pending) Text("正在提交…", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
            Row {
                if (item.status == "pending" && role?.controls == true) {
                    MusicTextAction("通过", onClick = { vm.session.queueAction(item, "approve") }, enabled = !pending)
                    MusicTextAction("拒绝", onClick = { vm.session.queueAction(item, "reject") }, enabled = !pending)
                }
                if (RoomPermissions.remove(role, item)) MusicTextAction("移除", onClick = { vm.session.queueAction(item, "remove") }, enabled = !pending)
            }
        } } }
        if (detail.queue.isEmpty()) item { Text("还没有点歌。进入搜索，选择“加入队列”即可提交点歌。") }
    }
}
@Composable private fun RoomMembers(detail: RoomDetail, role: RoomRole?, vm: RoomViewModel, modifier: Modifier, onClose: () -> Unit,
    onDialogActive: (Boolean) -> Unit) {
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf<RoomMember?>(null) }
    var settings by remember { mutableStateOf(false) }
    LaunchedEffect(target != null || settings) { onDialogActive(target != null || settings) }
    DisposableEffect(Unit) { onDispose { onDialogActive(false) } }
    LazyColumn(modifier, contentPadding = musicScrollPadding(PaddingValues(0.dp)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (role == RoomRole.OWNER) item {
            MusicTextAction("房间设置", onClick = { settings = true })
            MusicTextAction(if (detail.room.joinLocked) "允许加入" else "锁定加入", onClick = {
                vm.session.action("settings", buildJsonObject { put("joinLocked", !detail.room.joinLocked) }, { it == RoomRole.OWNER }) })
            MusicTextAction("关闭房间", onClick = onClose)
        }
        items(detail.members, key = { it.userId }) { member ->
            Row { Text("${member.nickname} · ${when(RoomRole.parse(member.role)) { RoomRole.OWNER -> "房主"; RoomRole.ADMIN -> "管理员"; else -> "成员" }}", Modifier.weight(1f))
                if (member.userId != account.account?.id && RoomPermissions.kick(role, RoomRole.parse(member.role)))
                    MusicTextAction("管理", onClick = { target = member }) }
        }
    }
    target?.let { member -> MusicDialog("管理 ${member.nickname}", onDismiss = { target = null }) {
        if (role == RoomRole.OWNER) {
            MusicTextAction(if (RoomRole.parse(member.role) == RoomRole.ADMIN) "取消管理员" else "设为管理员", onClick = {
                vm.session.action("admins", buildJsonObject { put("userId", member.userId); put("on", RoomRole.parse(member.role) != RoomRole.ADMIN) }, { it == RoomRole.OWNER }); target = null })
            var transfer by remember { mutableStateOf(false) }
            if (!transfer) MusicTextAction("转让房主", onClick = { transfer = true })
            else MusicTextAction("确认转让房主", onClick = { vm.session.action("transfer", buildJsonObject { put("userId", member.userId) }, { it == RoomRole.OWNER }); target = null })
        }
        MusicTextAction("移出房间", onClick = { vm.session.action("kick", buildJsonObject { put("userId", member.userId); put("ban", false) }, { RoomPermissions.kick(it, RoomRole.parse(member.role)) }); target = null })
        MusicTextAction("移出并禁止加入", onClick = { vm.session.action("kick", buildJsonObject { put("userId", member.userId); put("ban", true) }, { RoomPermissions.kick(it, RoomRole.parse(member.role)) }); target = null })
    } }
    if (settings) RoomForm("房间设置", { settings = false }, detail.room) { name, password, public, free ->
        vm.session.action("settings", buildJsonObject { put("name", name); put("isPublic", public); put("freeMode", free); put("needApproval", !free)
            if (password != null) put("password", password) }, { it == RoomRole.OWNER }); settings = false
    }
}
@Composable private fun RoomForm(title: String, onDismiss: () -> Unit, initial: RoomInfo? = null,
    saving: Boolean = false, error: String? = null,
    onSave: (String, String?, Boolean, Boolean) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }; var password by remember { mutableStateOf("") }
    var public by remember { mutableStateOf(initial?.isPublic ?: true) }; var free by remember { mutableStateOf(initial?.freeMode ?: false) }
    var changePassword by remember { mutableStateOf(initial == null) }
    MusicDialog(title, onDismiss = onDismiss) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).testTag("room_form"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(name, { name = it }, label = "房间名称")
            if (initial != null) Row { Text("修改密码（留空解除密码）", Modifier.weight(1f)); Switch(changePassword, { changePassword = it }) }
            if (changePassword) TextField(password, { password = it }, label = "房间密码", visualTransformation = PasswordVisualTransformation())
            Row { Text("公开房间", Modifier.weight(1f)); Switch(public, { public = it }) }
            Row { Text("自由点歌", Modifier.weight(1f)); Switch(free, { free = it }) }
            error?.let { Text(it, fontSize = 13.sp) }
            MusicTextAction(if (saving) "正在创建…" else "保存", enabled = name.isNotBlank() && !saving, onClick = { onSave(name, if (changePassword) password else null, public, free) })
        }
    }
}
