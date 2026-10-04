package io.github.currencortex.music.feature.room

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.testTag
import android.content.ClipData
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.room.*
import io.github.currencortex.music.ui.component.MusicDialog
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import top.yukonga.miuix.kmp.basic.*

@Composable fun RoomScreen(vm: RoomViewModel, onBack: () -> Unit, onSearch: () -> Unit, onPlayer: () -> Unit,
    onDialogActive: (Boolean) -> Unit = {}) {
    val browser by vm.browser.collectAsStateWithLifecycle()
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
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp).testTag("room_screen")) {
        Row { TextButton("返回", onClick = onBack); Text("一起听", Modifier.weight(1f), fontSize = 26.sp) }
        val detail = live.detail
        if (detail == null) {
            TextField(query, { query = it }, label = "六位房间号或房间名称", modifier = Modifier.fillMaxWidth().testTag("room_query"))
            Row { TextButton("查找", onClick = { vm.search(query.trim()) }, enabled = !busy)
                TextButton("创建房间", onClick = { create = true }, enabled = account.account != null && !busy) }
            if (browser.loading) Text("正在加载房间…")
            browser.error?.let { Text(it); TextButton("重试", onClick = { vm.load() }) }
            live.error?.let { Text(it) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(browser.rooms, key = { it.id }) { room ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                        Text(room.name, fontSize = 22.sp); Text("#${room.code} · ${room.online} 人 · ${room.ownerName}")
                        Text(if (room.freeMode) "自由点歌" else "点歌需要审批")
                        TextButton("加入", onClick = { vm.find(room.code) }, enabled = !busy)
                    } }
                }
                if (browser.more) item { TextButton("加载更多", onClick = { vm.load(more = true) }, enabled = !browser.loading) }
                if (!browser.loading && browser.rooms.isEmpty() && browser.error == null) item { Text("暂无公开房间") }
            }
        } else {
            val role = detail.role(account.account?.id ?: 0)
            Text(detail.room.name, fontSize = 24.sp)
            Text("#${detail.room.code} · ${when(role) { RoomRole.OWNER -> "房主"; RoomRole.ADMIN -> "管理员"; else -> "成员" }} · ${if(live.connected) "已连接" else "连接中"}")
            Row { TextButton("复制房间号", onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("房间号", detail.room.code))) } })
                TextButton("退出房间", onClick = { exit = true }, enabled = !busy) }
            live.error?.let { Text(it) }
            Text(detail.timeline?.trackMeta?.name ?: "等待点歌")
            Row {
                TextButton("点歌", onClick = onSearch)
                TextButton("播放器", onClick = onPlayer)
                TextButton("刷新", onClick = { scope.launch { io.github.currencortex.music.core.network.appResult { vm.session.refresh() } } })
            }
            if (role?.controls == true) Row {
                TextButton("上一首", onClick = vm.session::previous)
                TextButton(if(detail.timeline?.playing == true) "暂停" else "播放", onClick = { vm.session.play(detail.timeline?.playing != true) })
                TextButton("下一首", onClick = vm.session::next)
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                if (maxWidth >= 700.dp) Row(Modifier.fillMaxSize().testTag("room_two_panes"), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    RoomQueue(detail, role, vm, Modifier.weight(1f)); RoomMembers(detail, role, vm, Modifier.weight(1f), { close = true }, { memberDialog = it })
                } else {
                    var members by rememberSaveable { mutableStateOf(false) }
                    Column { Row { TextButton("点歌队列 ${detail.queue.size}", onClick = { members = false })
                        TextButton("成员与设置 ${detail.members.size}", onClick = { members = true }) }
                        if (members) RoomMembers(detail, role, vm, Modifier.weight(1f), { close = true }, { memberDialog = it })
                        else RoomQueue(detail, role, vm, Modifier.weight(1f)) }
                }
            }
        }
    }
    if (create) RoomForm("创建房间", { create = false }) { name, pw, public, free -> vm.create(name, pw.orEmpty(), public, free); create = false }
    passwordRoom?.let { room ->
        var password by remember(room.id) { mutableStateOf("") }
        MusicDialog("加入 ${room.name}", onDismiss = { vm.passwordRoom.value = null }) {
            TextField(password, { password = it }, label = "房间密码", visualTransformation = PasswordVisualTransformation())
            TextButton("加入", onClick = { vm.join(room, password); password = "" }, enabled = !busy)
        }
    }
    if (exit) MusicDialog("退出一起听", onDismiss = { exit = false }) {
        Text("退出后恢复本地队列，保持暂停。")
        TextButton("退出房间", onClick = { exit = false; vm.leave() }, modifier = Modifier.testTag("confirm_leave_room"))
    }
    if (close) MusicDialog("关闭房间", onDismiss = { close = false }) {
        Text("所有成员都会离开此房间。")
        TextButton("关闭房间", onClick = { close = false; vm.session.action("close", permission = { it == RoomRole.OWNER }) })
    }
}
@Composable private fun RoomQueue(detail: RoomDetail, role: RoomRole?, vm: RoomViewModel, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(detail.queue, key = { it.id }) { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text(item.name); Text("${item.artists} · ${item.requester}")
            Text(when(item.status) { "pending" -> "待审批"; "playing" -> "正在播放"; "rejected" -> "已拒绝"; else -> "已加入队列" })
            Row {
                if (item.status == "pending" && role?.controls == true) {
                    TextButton("通过", onClick = { vm.session.queueAction(item, "approve") })
                    TextButton("拒绝", onClick = { vm.session.queueAction(item, "reject") })
                }
                if (RoomPermissions.remove(role, item)) TextButton("移除", onClick = { vm.session.queueAction(item, "remove") })
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
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (role == RoomRole.OWNER) item {
            TextButton("房间设置", onClick = { settings = true })
            TextButton(if (detail.room.joinLocked) "允许加入" else "锁定加入", onClick = {
                vm.session.action("settings", buildJsonObject { put("joinLocked", !detail.room.joinLocked) }, { it == RoomRole.OWNER }) })
            TextButton("关闭房间", onClick = onClose)
        }
        items(detail.members, key = { it.userId }) { member ->
            Row { Text("${member.nickname} · ${when(RoomRole.parse(member.role)) { RoomRole.OWNER -> "房主"; RoomRole.ADMIN -> "管理员"; else -> "成员" }}", Modifier.weight(1f))
                if (member.userId != account.account?.id && RoomPermissions.kick(role, RoomRole.parse(member.role)))
                    TextButton("管理", onClick = { target = member }) }
        }
    }
    target?.let { member -> MusicDialog("管理 ${member.nickname}", onDismiss = { target = null }) {
        if (role == RoomRole.OWNER) {
            TextButton(if (RoomRole.parse(member.role) == RoomRole.ADMIN) "取消管理员" else "设为管理员", onClick = {
                vm.session.action("admins", buildJsonObject { put("userId", member.userId); put("on", RoomRole.parse(member.role) != RoomRole.ADMIN) }, { it == RoomRole.OWNER }); target = null })
            var transfer by remember { mutableStateOf(false) }
            if (!transfer) TextButton("转让房主", onClick = { transfer = true })
            else TextButton("确认转让房主", onClick = { vm.session.action("transfer", buildJsonObject { put("userId", member.userId) }, { it == RoomRole.OWNER }); target = null })
        }
        TextButton("移出房间", onClick = { vm.session.action("kick", buildJsonObject { put("userId", member.userId); put("ban", false) }, { RoomPermissions.kick(it, RoomRole.parse(member.role)) }); target = null })
        TextButton("移出并禁止加入", onClick = { vm.session.action("kick", buildJsonObject { put("userId", member.userId); put("ban", true) }, { RoomPermissions.kick(it, RoomRole.parse(member.role)) }); target = null })
    } }
    if (settings) RoomForm("房间设置", { settings = false }, detail.room) { name, password, public, free ->
        vm.session.action("settings", buildJsonObject { put("name", name); put("isPublic", public); put("freeMode", free); put("needApproval", !free)
            if (password != null) put("password", password) }, { it == RoomRole.OWNER }); settings = false
    }
}
@Composable private fun RoomForm(title: String, onDismiss: () -> Unit, initial: RoomInfo? = null,
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
            TextButton("保存", enabled = name.isNotBlank(), onClick = { onSave(name, if (changePassword) password else null, public, free); password = "" })
        }
    }
}
