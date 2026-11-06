package io.github.currencortex.music.feature.room

import io.github.currencortex.music.ui.component.MusicChoiceDialog

import io.github.currencortex.music.ui.component.MusicDialogButton

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
import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicCover
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.MusicCategoryTabs
import io.github.currencortex.music.ui.component.MusicSearchBar
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.component.MusicDestinationRow
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import top.yukonga.miuix.kmp.basic.*

@Composable fun RoomScreen(vm: RoomViewModel, onBack: () -> Unit, onSearch: () -> Unit, onPlayer: () -> Unit,
    onDialogActive: (Boolean) -> Unit = {}, onBinding: (() -> Unit)? = null) {
    val native by vm.container.listenTogether.state.collectAsStateWithLifecycle()
    var nativePage by rememberSaveable { mutableStateOf(native.active || native.endedRoom != null) }
    var nativeDialog by remember { mutableStateOf(false) }
    var roomDialog by remember { mutableStateOf(false) }
    LaunchedEffect(nativePage, nativeDialog, roomDialog) { onDialogActive(if (nativePage) nativeDialog else roomDialog) }
    DisposableEffect(Unit) { onDispose { onDialogActive(false) } }
    LaunchedEffect(native.active, native.endedRoom?.roomId) { if (native.active || native.endedRoom != null) nativePage = true }
    SettingsUiScope {
    ListenTogetherPageTransition(nativePage, { if (it) "netease" else "rooms" }, { if (it) 1 else 0 }, Modifier.fillMaxSize()) { showNative ->
        if (showNative) NeteaseListenTogetherScreen(vm.container, onBack = { nativePage = false }, onSearch = onSearch,
            onPlayer = onPlayer, onBinding = onBinding, onDialogActive = { nativeDialog = it })
        else CurrentMusicRoomContent(vm, onBack, onSearch, onPlayer, { roomDialog = it }, onNative = { nativePage = true })
    }
    }
}

@Composable private fun CurrentMusicRoomContent(vm: RoomViewModel, onBack: () -> Unit, onSearch: () -> Unit,
    onPlayer: () -> Unit, onDialogActive: (Boolean) -> Unit, onNative: () -> Unit) {
    val native by vm.container.listenTogether.state.collectAsStateWithLifecycle()
    val browser by vm.browser.collectAsPageState()
    val live by vm.session.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val passwordRoom by vm.passwordRoom.collectAsStateWithLifecycle()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var create by rememberSaveable { mutableStateOf(false) }
    var exit by remember { mutableStateOf(false) }
    var close by remember { mutableStateOf(false) }
    var deleteRoom by remember(account.account?.id) { mutableStateOf<Pair<RoomCard, RequestSession>?>(null) }
    var memberDialog by remember { mutableStateOf(false) }
    val dialogActive = create || exit || close || memberDialog || passwordRoom != null || deleteRoom != null
    LaunchedEffect(dialogActive) { onDialogActive(dialogActive) }
    DisposableEffect(Unit) { onDispose { onDialogActive(false) } }
    val clipboard = LocalClipboard.current; val scope = rememberCoroutineScope()
    LaunchedEffect(live.detail?.room?.id) { if (live.detail != null) create = false }
    MusicSubpage("一起听", "room_page", onBack) { topInset ->
    Column(Modifier.fillMaxSize().testTag("room_screen")) {
        val detail = live.detail
        if (detail == null) {
            // Keep the viewport full-width so elastic controls can paint into the side gutters.
            LazyColumn(Modifier.fillMaxSize(),
                contentPadding = musicScrollPadding(PaddingValues(start = 16.dp, end = 16.dp,
                    top = topInset + 8.dp, bottom = 24.dp)),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    SettingsSection("与好友一起", io.github.currencortex.music.ui.component.MusicIcons.Headphones) {
                        MusicDestinationRow("网易云双人一起听", onNative, Modifier.testTag("open_netease_listen_together"),
                            summary = if (native.active) "正在一起听 · 查看会话" else "和网易云好友同步听歌")
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsInfoHeader("CurrentMusic 房间", io.github.currencortex.music.ui.component.MusicIcons.Room,
                            horizontalInset = 0.dp)
                        MusicCategoryTabs(listOf("public" to "公开房间", "mine" to "我的房间"), browser.scope.query,
                            { vm.load(query, roomScope = if (it == "mine") RoomScope.MINE else RoomScope.PUBLIC) }, "room_scope")
                    }
                }
                item {
                    MusicSearchBar(query, { vm.search(query.trim()) }, onInput = { query = it },
                        placeholder = "六位房间号或房间名称", inputTag = "room_query")
                }
                item {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassActionButton("查找", { vm.search(query.trim()) }, enabled = !busy, fillWidth = false)
                        GlassActionButton("创建房间", { create = true }, primary = true,
                            enabled = account.account != null && !busy, fillWidth = false)
                        GlassActionButton(if (browser.loading) "刷新中" else "刷新", { vm.load() },
                            enabled = !browser.loading && !busy, fillWidth = false)
                    }
                    if (account.account == null) SettingsHint("登录 CurrentMusic 账号后可创建房间。")
                }
                if (busy) item { ListenLoadingLabel("正在处理房间…") }
                if (browser.loading && browser.rooms.isEmpty()) item { ListenTogetherSkeleton("正在加载房间…", "room_browser_loading", rows = 2) }
                browser.error?.let { error -> item {
                    SettingsGroup { SettingsHint(error, error = true); MusicTextAction("重试", { vm.load() }, Modifier.padding(16.dp)) }
                } }
                live.error?.let { error -> item { SettingsHint(error, error = true) } }
                items(browser.rooms, key = { it.id }) { room ->
                    SettingsGroup(Modifier.animateItem()) {
                        SettingsPreference(room.name,
                            "#${room.code} · " + if (room.archived) {
                                if (room.archiveReason == "idle") "闲置后自动归档" else "已归档"
                            } else "${room.online} 人在线",
                            trailing = { MusicTextAction(if (room.archived) "恢复并进入" else "加入",
                                { if (browser.scope == RoomScope.MINE) vm.enterOwned(room) else vm.find(room.code) }, enabled = !busy) })
                        SettingsDivider()
                        SettingsHint(listOf(room.ownerName.takeIf { it.isNotBlank() },
                            if (room.freeMode) "自由点歌" else "普通模式").filterNotNull().joinToString(" · "))
                        if (browser.scope == RoomScope.MINE) MusicTextAction("永久删除", { deleteRoom = room to vm.container.roomRepository.session() },
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp), enabled = !busy)
                    }
                }
                if (browser.more) item { GlassActionButton("加载更多", { vm.load(more = true) }, enabled = !browser.loading) }
                if (!browser.loading && browser.rooms.isEmpty() && browser.error == null) item {
                    SettingsGroup { SettingsPreference(if (browser.scope == RoomScope.MINE) "暂无我的房间" else "暂无公开房间",
                        if (browser.scope == RoomScope.MINE) "你创建的房间和已归档房间会显示在这里。" else "创建一个房间，或使用好友的六位房间号加入。") }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = topInset + 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val role = detail.role(account.account?.id ?: 0)
            SettingsGroup {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(detail.room.name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("#${detail.room.code} · ${detail.members.size} 人 · ${when(role) { RoomRole.OWNER -> "房主"; RoomRole.ADMIN -> "管理员"; else -> "成员" }}",
                fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(if (live.connected) "已连接 · 播放同步中" else "正在连接房间，自动恢复同步…", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
            Row { MusicTextAction("复制房间号", onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("房间号", detail.room.code))) } })
                MusicTextAction("退出房间", onClick = { exit = true }, enabled = !busy) }
            } }
            live.error?.let { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, Modifier.weight(1f), fontSize = 13.sp)
                MusicTextAction("知道了", onClick = vm.session::dismissError)
            } }
            SettingsGroup(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    val track = detail.timeline?.trackMeta
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MusicCover(track?.pic.orEmpty(), Modifier.size(56.dp), cornerRadius = 16.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(track?.name ?: "等待点歌", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track?.artists?.takeIf { it.isNotBlank() } ?: if (detail.room.freeMode || !detail.room.needApproval) "所有成员可直接点歌" else "点歌通过审批后加入队列",
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
    }
    }
    if (create) RoomForm("创建房间", { if (!busy) create = false }, saving = busy, error = browser.error) { name, pw, public, free, approval, locked ->
        vm.create(name, pw.orEmpty(), public, free, approval, locked) }
    deleteRoom?.let { (room, expected) -> MusicDialog("永久删除房间", onDismiss = { deleteRoom = null }) {
        Text("将永久删除「${room.name}」（#${room.code}）及其成员、点歌队列、事件与封禁记录。此操作无法恢复。")
        MusicDialogButton("永久删除", onClick = { deleteRoom = null; vm.deleteOwned(room, expected) }, enabled = !busy,
            modifier = Modifier.testTag("confirm_delete_room"))
    } }
    passwordRoom?.let { room ->
        var password by remember(room.id) { mutableStateOf("") }
        MusicDialog("加入 ${room.name}", onDismiss = { vm.passwordRoom.value = null }) {
            TextField(password, { password = it }, label = "房间密码", visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            browser.error?.let { Text(it, fontSize = 13.sp) }
            MusicDialogButton(if (busy) "正在加入…" else "加入", onClick = { vm.join(room, password) }, enabled = !busy)
        }
    }
    if (exit) MusicDialog("退出一起听", onDismiss = { exit = false }) {
        Text("退出后恢复本地队列，保持暂停。")
        MusicDialogButton("退出房间", onClick = { exit = false; vm.leave() }, modifier = Modifier.testTag("confirm_leave_room"))
    }
    if (close) MusicDialog("归档房间", onDismiss = { close = false }) {
        Text("播放将停止，所有成员都会离开。房间号、队列和进度会保留，你可以从“我的房间”恢复。")
        MusicDialogButton("归档房间", onClick = { close = false; vm.session.action("close", permission = { it == RoomRole.OWNER }) })
    }
}
@Composable private fun RoomTransport(label: String, icon: Int, onClick: () -> Unit, enabled: Boolean, primary: Boolean = false) {
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        GlassIconButton(onClick, label, enabled = enabled, size = if (primary) 48.dp else 44.dp) {
            Icon(painterResource(icon), null, Modifier.size(28.dp),
                tint = (if (primary) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface).copy(alpha = if (enabled) 1f else .35f))
        }
        Text(label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
@Composable private fun RoomQueue(detail: RoomDetail, role: RoomRole?, vm: RoomViewModel, modifier: Modifier) {
    val live by vm.session.state.collectAsStateWithLifecycle()
    val waiting = detail.queue.filter { it.status == "approved" }
    LazyColumn(modifier, contentPadding = musicScrollPadding(PaddingValues(0.dp)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(detail.queue, key = { it.id }) { item -> SettingsGroup(Modifier.fillMaxWidth().animateItem()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val pending = "queue:${item.id}" in live.pendingActions
            Text(item.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val subtitle = listOf(item.artists, item.requester).filter { it.isNotBlank() }.joinToString(" · ")
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(when(item.status) { "pending" -> "待审批"; "playing" -> "正在播放"; "rejected" -> "已拒绝"; else -> "已加入队列" })
            if (pending) Text("正在提交…", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.status == "pending" && role?.controls == true) {
                    MusicTextAction("通过", onClick = { vm.session.queueAction(item, "approve") }, enabled = !pending)
                    MusicTextAction("拒绝", onClick = { vm.session.queueAction(item, "reject") }, enabled = !pending)
                }
                if (RoomPermissions.remove(role, item)) MusicTextAction("移除", onClick = { vm.session.queueAction(item, "remove") }, enabled = !pending)
                if (item.status == "approved" && role?.controls == true) {
                    val index = waiting.indexOfFirst { it.id == item.id }
                    val reordering = live.pendingActions.any { it.startsWith("queue:") }
                    if (index > 0) MusicTextAction("上移", { vm.session.reorder(item, index - 1) }, enabled = !reordering)
                    if (index >= 0 && index < waiting.lastIndex) MusicTextAction("下移", { vm.session.reorder(item, index + 1) }, enabled = !reordering)
                }
            }
        } } }
        if (detail.queue.isEmpty()) item {
            SettingsGroup { SettingsPreference("还没有点歌", "进入搜索，选择“加入队列”即可提交点歌。") }
        }
    }
}
@Composable private fun RoomMembers(detail: RoomDetail, role: RoomRole?, vm: RoomViewModel, modifier: Modifier, onClose: () -> Unit,
    onDialogActive: (Boolean) -> Unit) {
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf<RoomMember?>(null) }
    var settings by remember { mutableStateOf(false) }
    var showBans by remember(detail.room.id) { mutableStateOf(false) }
    val bans by vm.bans.collectAsStateWithLifecycle()
    LaunchedEffect(role) { if (role != RoomRole.OWNER) { settings = false; showBans = false } }
    LaunchedEffect(target != null || settings || showBans) { onDialogActive(target != null || settings || showBans) }
    DisposableEffect(Unit) { onDispose { onDialogActive(false) } }
    LazyColumn(modifier, contentPadding = musicScrollPadding(PaddingValues(0.dp)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (role == RoomRole.OWNER) item {
            SettingsGroup {
                MusicDestinationRow("房间设置", { settings = true }, summary = "名称、密码、可见性与点歌权限")
                SettingsDivider()
                SettingsToggle("锁定加入", detail.room.joinLocked, { locked ->
                    vm.session.action("settings", buildJsonObject { put("joinLocked", locked) }, { it == RoomRole.OWNER }) },
                    summary = "开启后不再接受新成员")
                SettingsDivider()
                MusicDestinationRow("封禁名单", { showBans = true; vm.loadBans() }, summary = "查看并解除禁止加入")
                SettingsDivider()
                SettingsPreference("归档房间", "保留房间号、队列和进度，可稍后恢复", onClick = onClose,
                    trailing = { Icon(io.github.currencortex.music.ui.component.MusicIcons.Close, null, tint = MiuixTheme.colorScheme.error) })
            }
        }
        item { SettingsSectionHeader("成员 · ${detail.members.size}") }
        items(detail.members, key = { it.userId }) { member ->
            SettingsGroup(Modifier.animateItem()) {
                SettingsPreference(member.nickname, when (RoomRole.parse(member.role)) {
                    RoomRole.OWNER -> "房主"; RoomRole.ADMIN -> "管理员"; else -> "成员"
                }, leading = { Icon(io.github.currencortex.music.ui.component.MusicIcons.Account, null) }, trailing = {
                    if (member.userId != account.account?.id && RoomPermissions.kick(role, RoomRole.parse(member.role)))
                        MusicTextAction("管理", { target = member })
                })
            }
        }
    }
    target?.let { member -> MusicChoiceDialog("管理 ${member.nickname}", onDismiss = { target = null }) {
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
    if (showBans) MusicDialog("封禁名单", onDismiss = { showBans = false }) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (bans.loading) Text("正在加载…")
            bans.error?.let { Text(it); MusicTextAction("重试", vm::loadBans) }
            if (!bans.loading && bans.error == null && bans.bans.isEmpty()) Text("暂无封禁成员")
            bans.bans.forEach { ban ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ban.nickname.ifBlank { "用户 ${ban.userId}" })
                        if (ban.reason.isNotBlank()) Text(ban.reason, fontSize = 12.sp)
                    }
                    MusicTextAction("解除封禁", { vm.unban(ban.userId) }, enabled = !bans.loading)
                }
            }
        }
    }
    if (settings) RoomForm("房间设置", { settings = false }, detail.room) { name, password, public, free, approval, locked ->
        vm.session.action("settings", buildJsonObject { put("name", name); put("isPublic", public); put("freeMode", free); put("needApproval", approval); put("joinLocked", locked)
            if (password != null) put("password", password) }, { it == RoomRole.OWNER }); settings = false
    }
}
@Composable private fun RoomForm(title: String, onDismiss: () -> Unit, initial: RoomInfo? = null,
    saving: Boolean = false, error: String? = null,
    onSave: (String, String?, Boolean, Boolean, Boolean, Boolean) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }; var password by remember { mutableStateOf("") }
    var public by remember { mutableStateOf(initial?.isPublic ?: true) }; var free by remember { mutableStateOf(initial?.freeMode ?: false) }
    var approval by remember { mutableStateOf(initial?.needApproval ?: true) }
    var locked by remember { mutableStateOf(initial?.joinLocked ?: false) }
    var changePassword by remember { mutableStateOf(initial == null) }
    MusicDialog(title, onDismiss = onDismiss) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).testTag("room_form"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(name, { if (it.length <= 60) name = it }, label = "房间名称", modifier = Modifier.fillMaxWidth())
            if (initial != null) SettingsToggle("修改密码", changePassword, { changePassword = it }, summary = "留空保存可解除密码")
            if (changePassword) TextField(password, { if (it.length <= 128) password = it }, label = "房间密码", visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            SettingsGroup {
                SettingsToggle("公开房间", public, { public = it }, summary = "在房间大厅中显示")
                SettingsDivider()
                SettingsToggle("自由点歌", free, { free = it }, summary = "开启后，普通成员点歌直接加入待播队列")
                SettingsDivider()
                SettingsToggle("点歌需要审批", approval, { approval = it }, summary = if (free) "自由点歌开启时暂不执行审批" else "关闭后，普通成员点歌直接入队")
                SettingsDivider()
                SettingsToggle("锁定加入", locked, { locked = it }, summary = "开启后不再接受新成员")
            }
            error?.let { Text(it, fontSize = 13.sp) }
            MusicDialogButton(if (saving) "正在创建…" else "保存", enabled = name.trim().length in 1..60 && !saving,
                onClick = { onSave(name.trim(), if (changePassword) password else null, public, free, approval, locked) })
        }
    }
}
