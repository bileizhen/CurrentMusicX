package io.github.currencortex.music.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.ui.util.collectAsPageState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.profile.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.auth.AuthViewModel
import io.github.currencortex.music.feature.auth.LoginScreen
import io.github.currencortex.music.feature.library.LibraryLinks
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable fun UserAvatar(user: ProfileUser, container: AppContainer, size: androidx.compose.ui.unit.Dp = 64.dp,
    decoration: String = user.decoration, reserveOverlay: Boolean = true, flightState: AvatarFlightState? = null) {
    val preferences by container.musicSettings.state.collectAsStateWithLifecycle()
    val scales by container.profileRepository.scales.collectAsStateWithLifecycle()
    val versions by container.profileRepository.avatarVersions.collectAsStateWithLifecycle()
    val repository = container.profileRepository
    var avatarUrl = repository.avatarUrl(preferences.server, user.avatar)
    if (!user.avatar.startsWith("http") && versions[user.id] != null)
        avatarUrl = avatarUrl?.toHttpUrlOrNull()?.newBuilder()?.setQueryParameter("v", versions[user.id].toString())?.build()?.toString()
    val decorationUrl = repository.decorationUrl(preferences.server, decoration)
    val scale = scales[decoration] ?: 1.0
    val density = LocalDensity.current
    val placement = LocalRootTabPlacement.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val originProvider by rememberUpdatedState<() -> AvatarFlightOrigin?>({
        coordinates?.takeIf { it.isAttached }?.boundsInRoot()?.takeIf { it.top >= 0f }?.let {
            val radius = with(density) { size.toPx() / 2f }
            val bounds = Rect(it.center - Offset(radius, radius), it.center + Offset(radius, radius))
            if (placement?.visible(bounds) == false) null else AvatarFlightOrigin(avatarUrl, bounds, decorationUrl, scale)
        }
    })
    DisposableEffect(flightState) {
        val provider = { originProvider() }
        flightState?.profileOrigin = provider
        onDispose { if (flightState != null && flightState.profileOrigin === provider) flightState.profileOrigin = null }
    }
    DecoratedAvatar(avatarUrl, decorationUrl, modifier = if (flightState != null)
        Modifier.onGloballyPositioned { coordinates = it } else Modifier,
        scale = scale, size = size, reserveOverlay = reserveOverlay, onImageError = {
            // Report the failure category only; image URLs can contain signed credentials.
            container.logger.warn("Image", "User image failed: ${it.javaClass.simpleName}", null)
        })
}

@Composable fun MeScreen(vm: ProfileViewModel, auth: AuthViewModel, navigate: (String) -> Unit, onSettings: () -> Unit,
    play: (List<Song>, Int) -> Unit) {
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    // The bottom navigation already owns "设置"; a second entry above the login form was redundant.
    if (account.account == null && vm.container.nativeNetease != null) NativeMeScreen(vm.container, navigate, onSettings)
    else if (account.account == null) LoginScreen(auth) else ProfileScreen(vm, navigate, play, onSettings = onSettings)
}

@Composable private fun NativeMeScreen(container: AppContainer, navigate: (String) -> Unit, onSettings: () -> Unit) {
    val account by container.neteaseSessions.state.collectAsStateWithLifecycle()
    val bottomInset = LocalMusicBottomInset.current
    val avatarFlight = LocalAvatarFlight.current
    val placement = LocalRootTabPlacement.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    LaunchedEffect(avatarFlight?.request) { if (avatarFlight?.active == true && !avatarFlight.returning) listState.scrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize().testTag("profile_screen"), state = listState,
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("我的", Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            MusicTextAction("设置", onSettings)
        } }
        account.profile?.let { native -> item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.testTag("my_profile_avatar").onGloballyPositioned {
                    val center = (placement?.landingBounds(it) ?: it.boundsInRoot()).center
                    val radius = with(density) { 32.dp.toPx() }
                    if (avatarFlight?.returning != true) avatarFlight?.destination = Rect(center - Offset(radius, radius), center + Offset(radius, radius))
                }.graphicsLayer { alpha = if (avatarFlight?.active == true) 0f else 1f }) {
                    UserAvatar(ProfileUser(id = native.uid, nickname = native.nickname, avatar = native.avatar), container,
                        reserveOverlay = false, flightState = avatarFlight)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(native.nickname, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (account.stale) "网易云登录已过期" else "网易云音乐", fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
            }
        } }
        item { MusicSectionHeader("我的音乐"); LibraryLinks(navigate) }
        item { Card(Modifier.fillMaxWidth()) {
            MusicDestinationRow(if (account.loggedIn) "网易云账号" else "登录网易云", { navigate("user/binding") },
                Modifier.testTag("open_binding"), "我喜欢、歌单与最近播放")
            MusicDestinationRow("CurrentMusic 账户", { navigate("user/login") }, Modifier.testTag("open_currentmusic_login"),
                "头像装饰与一起听房间")
        } }
    }
}

@Composable fun ProfileScreen(vm: ProfileViewModel, navigate: (String) -> Unit, play: (List<Song>, Int) -> Unit,
    onBack: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    val state = vm.state.collectAsPageState().value
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { vm.avatar.value = uri; vm.dialog.value = "avatar" }
    }
    val user = state.profile?.user ?: account.account?.takeIf { onBack == null }?.let {
        ProfileUser(id = it.id, username = it.username, nickname = it.nickname, avatar = it.avatar)
    }
    val own = user != null && user.id == account.account?.id
    val bottomInset = LocalMusicBottomInset.current
    val listState = rememberLazyListState()
    val avatarFlight = LocalAvatarFlight.current.takeIf { onBack == null }
    val placement = LocalRootTabPlacement.current
    val density = LocalDensity.current
    LaunchedEffect(avatarFlight?.request) {
        if (avatarFlight?.active == true && !avatarFlight.returning) listState.scrollToItem(0)
    }
    MusicPullToRefresh(state.loading, vm::reload, Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().testTag("profile_screen"), state = listState, contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            // No back button here either: system/predictive back is handled by the navigation layer.
            Text(if (onBack == null) "我的" else "用户主页", Modifier.weight(1f), fontSize = 28.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            onSettings?.let { MusicTextAction("设置", it) }
            MusicTextAction("刷新", vm::reload, enabled = !state.loading)
        } }
        if ((state.loading || account.loading) && user == null) item { LoadingSongList(2) }
        state.error?.let { item { Text(it); MusicTextAction("重试", vm::reload) } }
        if (user != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.testTag("my_profile_avatar").onGloballyPositioned {
                        val center = (placement?.landingBounds(it) ?: it.boundsInRoot()).center
                        val radius = with(density) { 32.dp.toPx() }
                        if (avatarFlight?.returning != true && (avatarFlight?.active != true || (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0)))
                            avatarFlight?.destination = Rect(center - Offset(radius, radius), center + Offset(radius, radius))
                    }.graphicsLayer { alpha = if (avatarFlight?.active == true) 0f else 1f }) {
                        UserAvatar(user, vm.container, 64.dp, flightState = avatarFlight)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(user.nickname.ifBlank { user.username }, fontSize = 23.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text(if (state.loading && state.profile == null) "正在加载资料…" else user.bio.ifBlank { "还没有填写简介" }, fontSize = 13.sp, maxLines = 2,
                            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                        if (own) Row {
                            MusicTextAction("编辑资料", { vm.dialog.value = "edit" }, Modifier.testTag("edit_profile"), !busy && state.profile != null)
                            MusicTextAction("更换头像", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy)
                        }
                    }
                }
            }
            if (state.loading && state.profile == null) item { LoadingSongList(2) }
            if (state.profile != null) item { val stat = state.profile!!.stat
                LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    items(listOf("点赞" to "${stat.likes}", "收藏" to "${stat.favs}", "歌单" to "${stat.playlists}",
                        "听歌天数" to "${stat.playDays}", "听歌时长" to listeningDuration(stat.listenMs))) { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(value, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                            Text(label, fontSize = 11.sp, color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                        }
                    }
                }
            }
            if (own) {
                item { MusicSectionHeader("我的音乐"); LibraryLinks(navigate) }
                item { Card(Modifier.fillMaxWidth()) {
                    MusicDestinationRow("网易云账号", { navigate("user/binding") }, Modifier.testTag("open_binding"), "绑定与同步歌单")
                    MusicDestinationRow("头像挂件", { navigate("user/decorations") }, Modifier.testTag("open_decorations"))
                    MusicDestinationRow("账号与安全", { navigate("user/security") }, Modifier.testTag("open_security"), "切换账号、修改密码、退出登录")
                } }
            }
            state.profile?.current?.let { song -> item { MusicSectionHeader("当前在听")
                SongRow(SongRowUi(song.toDomain()), { play(listOf(song.toDomain()), 0) },
                    { vm.container.playerController.add(song.toDomain(), true) }, { vm.container.playerController.add(song.toDomain()) })
            } }
            val recent = state.profile?.recent.orEmpty().map { it.toDomain() }
            if (recent.isNotEmpty()) item { MusicSectionHeader("最近听过") }
            itemsIndexed(recent, key = { _, song -> "recent-${song.id}" }) { index, song ->
                SongRow(SongRowUi(song), { play(recent, index) }, { vm.container.playerController.add(song, true) }, { vm.container.playerController.add(song) })
            }
            val lists = state.profile?.playlists.orEmpty()
            if (lists.isNotEmpty()) item { MusicSectionHeader(if (own) "我的歌单" else "公开歌单") }
            items(lists, key = { "playlist-${it.id}" }) { list ->
                Row(Modifier.fillMaxWidth().clickable { navigate("lib/${if (list.source == "netease") "ncmplaylist" else "playlist"}/${list.id}") }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    MusicCover(list.cover, Modifier.size(56.dp)); Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(list.name, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("${list.count} 首" + if (list.source in setOf("ncm", "netease")) " · 网易云" else "", fontSize = 12.sp,
                            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                    }
                }
            }
        }
        state.sectionErrors.forEach { item { Text(it, fontSize = 13.sp) } }
    }
    }
}

/** Accounts and security are one page: switching accounts and changing credentials are the same job. */
@Composable fun AccountSecurityScreen(vm: ProfileViewModel, auth: AuthViewModel, add: () -> Unit) {
    val container = vm.container
    val accounts by container.accountRepository.savedAccounts.collectAsStateWithLifecycle()
    val account by container.accountRepository.state.collectAsStateWithLifecycle()
    val state by auth.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val colors = MiuixTheme.colorScheme
    var removing by remember { mutableStateOf<io.github.currencortex.music.data.auth.SavedAccount?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("security_screen"), contentPadding = musicScrollPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // No back button: the navigation layer already owns system/predictive back.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("账号与安全", Modifier.weight(1f), fontSize = 28.sp)
                MusicTextAction("添加账号", add, Modifier.testTag("add_account"), enabled = !state.loading)
            }
        }
        item { MusicSectionHeader("已保存账号") }
        item { Card(Modifier.fillMaxWidth()) {
            accounts.forEach { saved ->
                val active = saved.id == account.account?.id && saved.server == container.accountRepository.server
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UserAvatar(ProfileUser(id = saved.id, avatar = saved.avatar), container, 40.dp, reserveOverlay = false)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(saved.nickname.ifBlank { saved.username }, fontSize = 16.sp, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(saved.server, fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    // The active account needs no action; the others get switch + remove in place.
                    if (active) Text("当前账号", fontSize = 13.sp, color = colors.primary,
                        modifier = Modifier.testTag("current_account_${saved.id}"))
                    else {
                        MusicTextAction("切换", { auth.switch(saved.key) },
                            Modifier.testTag("switch_account_${saved.id}"), enabled = !state.loading)
                        MusicTextAction("移除", { removing = saved },
                            Modifier.testTag("remove_account_${saved.id}"), enabled = !state.loading, destructive = true)
                    }
                }
            }
            if (accounts.isEmpty()) Text("本机还没有保存的账号", Modifier.padding(16.dp), fontSize = 13.sp,
                color = colors.onSurfaceVariantSummary)
        } }
        item { MusicSectionHeader("安全") }
        item { Card(Modifier.fillMaxWidth()) {
            MusicDestinationRow("修改密码", { vm.dialog.value = "password" }, Modifier.testTag("open_password"),
                "登录密码至少 6 位", enabled = !busy)
            MusicDestinationRow("退出登录", { vm.dialog.value = "logout" }, Modifier.testTag("open_logout"),
                "仅移除本机凭据，其他已保存账号保留", enabled = !busy)
        } }
        state.message?.let { item { Text(it, fontSize = 13.sp, color = colors.onSurfaceVariantSummary) } }
        if (state.loading) item { Text("正在验证账号…", fontSize = 13.sp, color = colors.onSurfaceVariantSummary) }
    }
    removing?.let { saved -> MusicDialog("移除已保存账号？", { removing = null }) {
        Text(saved.nickname.ifBlank { saved.username }); Text("仅移除本机凭据，账号本身不受影响。")
        TextButton("确认移除", onClick = { removing = null; auth.removeSaved(saved.key) },
            modifier = Modifier.testTag("confirm_remove_account"))
    } }
}

@Composable fun ProfileDialogs(vm: ProfileViewModel, auth: AuthViewModel) {
    val dialog by vm.dialog.collectAsStateWithLifecycle()
    val state by vm.state.collectAsPageState()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun close() { if (!busy) { vm.dialog.value = null; vm.avatar.value = null } }
    when (dialog) {
        "edit" -> state.profile?.user?.let { user ->
            var nickname by rememberSaveable(user.id) { mutableStateOf(user.nickname) }
            var bio by rememberSaveable(user.id) { mutableStateOf(user.bio) }
            var visible by rememberSaveable(user.id) { mutableStateOf(user.visible) }
            MusicDialog("编辑资料", ::close) {
                Text("昵称"); TextField(nickname, { nickname = it }, singleLine = true, modifier = Modifier.testTag("profile_nickname"))
                Text("简介"); TextField(bio, { bio = it }, modifier = Modifier.testTag("profile_bio"))
                visible?.let { current -> SettingsSwitch("在用户广场公开资料", current, { visible = it }) }
                TextButton("保存", onClick = { vm.update(nickname, bio, visible) }, enabled = nickname.isNotBlank() && !busy,
                    modifier = Modifier.testTag("save_profile"))
            }
        }
        "avatar" -> MusicDialog("更换头像", ::close) {
            AsyncImage(avatar, contentDescription = "新头像预览", modifier = Modifier.size(120.dp))
            Text("上传后将替换当前头像。")
            TextButton("确认上传", onClick = { vm.upload(context.contentResolver) }, enabled = !busy, modifier = Modifier.testTag("confirm_avatar_upload"))
        }
        "password" -> {
            var old by remember { mutableStateOf("") }; var new by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
            MusicDialog("修改密码", ::close) {
                Text("当前密码"); TextField(old, { old = it }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Text("新密码（至少 6 位）"); TextField(new, { new = it }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Text("再次输入新密码"); TextField(confirm, { confirm = it }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                TextButton("更新密码", enabled = old.isNotEmpty() && new.length >= 6 && new == confirm && !busy, onClick = {
                    vm.password(old, new); old = ""; new = ""; confirm = ""
                })
            }
        }
        "logout" -> MusicDialog("退出当前账号？", ::close) {
            Text("会移除本机保存的当前账号凭据，其他已保存账号保留。")
            TextButton("确认退出", onClick = { vm.container.playerController.pause(); vm.dialog.value = null; auth.logout() })
        }
    }
}

@Composable fun DiscoverScreen(vm: DiscoverViewModel, navigate: (String) -> Unit) {
    val state by vm.state.collectAsPageState()
    val styles by vm.styles.collectAsPageState()
    val bottomInset = LocalMusicBottomInset.current
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(vm, owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollStats() } }
    LazyColumn(Modifier.fillMaxSize().testTag("discover_screen"), contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp + bottomInset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("发现", fontSize = 30.sp)
        }
        item { io.github.currencortex.music.feature.style.MusicStyleCategories(styles, vm::loadStyles, navigate, vm::loadStyleCover) }
        item {
            Text("音乐社区", fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text("${state.stats.users} 位用户 · ${state.stats.listening} 人正在听歌", fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextField(state.query, vm::query, singleLine = true, modifier = Modifier.weight(1f).testTag("discover_query"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.submit() }))
                MusicTextAction("搜索", { vm.submit() }, Modifier.testTag("discover_submit"), enabled = !state.loading)
            }
        }
        item { Card(Modifier.fillMaxWidth()) { SettingsSwitch("仅显示正在听歌", state.listening, vm::filter) } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(listOf("reg" to "最新注册", "reg_asc" to "最早注册", "name" to "昵称", "days" to "听歌天数", "listen" to "听歌时长", "likes" to "点赞数")) { (key, label) ->
                TextButton((if (state.sort == key) "✓ " else "") + label, onClick = { vm.sort(key) })
            }
        } }
        if (state.loading && state.users.isEmpty()) item { LoadingSongList(4) }
        state.error?.let { item { Text(it, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f)); TextButton("重试", onClick = { vm.submit() }) } }
        if (!state.loading && state.users.isEmpty() && state.error == null) item {
            Text(if (state.listening) "当前没有正在听歌的用户" else "没有匹配的用户", fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
        }
        items(state.users, key = { it.id }) { user ->
            Card(Modifier.fillMaxWidth().clickable { navigate("user/profile/${user.id}") }.testTag("discover_user_${user.id}")) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UserAvatar(user.profileUser(), vm.container, 44.dp, reserveOverlay = false)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(user.nickname, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (user.bio.isNotBlank()) Text(user.bio, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                        Text("${user.days} 天 · ${listeningDuration(user.listenMs)}", fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
                        user.current?.let { Text("正在听：${it.name}", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        if (state.users.size < state.total) item { TextButton("加载更多", onClick = { vm.submit(true) }, enabled = !state.loading) }
    }
}

@Composable fun DecorationScreen(vm: DecorationViewModel) {
    val state by vm.state.collectAsPageState()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val catalog = state.catalog
    val user = ProfileUser(id = account.account?.id ?: 0L, avatar = account.account?.avatar.orEmpty())
    Column(Modifier.fillMaxSize().padding(20.dp).testTag("decoration_screen"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("头像挂件", fontSize = 28.sp)
        if (state.loading) Text("正在加载挂件…")
        state.error?.let { Text(it); TextButton("重试", onClick = vm::reload) }
        if (catalog != null) {
            if (!catalog.unlocked) Text("累计 ${listeningDuration(catalog.listenMs)}；解锁需 ${listeningDuration(catalog.minListenMs)}")
            TextField(state.query, vm::query, singleLine = true, modifier = Modifier.testTag("decoration_query"))
            Text("搜索名称或 ID，可预览挂件。")
            TextButton("取消佩戴", onClick = { vm.set("") }, enabled = catalog.current.isNotEmpty() && !busy)
            LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.weight(1f), contentPadding = musicScrollPadding(PaddingValues(0.dp)), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(catalog.decorations.filter { it.name.contains(state.query, true) || it.id.contains(state.query, true) }, key = { it.id }) { item ->
                    Column(Modifier.clickable { vm.preview(item) }.testTag("decoration_${item.id}"), horizontalAlignment = Alignment.CenterHorizontally) {
                        UserAvatar(user, vm.container, 38.dp, item.id)
                        Text((if (catalog.current == item.id) "✓ " else "") + item.name)
                    }
                }
            }
            state.preview?.let { item -> MusicDialog("挂件预览：${item.name}", { if (!busy) vm.preview(null) }) {
                UserAvatar(user, vm.container, 64.dp, item.id)
                TextButton("佩戴", onClick = { vm.set(item.id) }, enabled = catalog.unlocked && !busy, modifier = Modifier.testTag("wear_decoration"))
                if (!catalog.unlocked) Text("尚未达到服务器解锁条件")
            } }
        }
        val message by vm.message.collectAsStateWithLifecycle()
        message?.let { Text(it) }
    }
}
