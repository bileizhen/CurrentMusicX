package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.data.profile.ProfileUser
import io.github.currencortex.music.data.profile.listeningDuration
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.catalogRoute
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One personal music landing page for guest, NetEase and CurrentMusic accounts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun MyMusicScreen(vm: ProfileViewModel, navigate: (String) -> Unit, play: (List<Song>, Int) -> Unit) {
    val container = vm.container
    val account by container.accountRepository.state.collectAsStateWithLifecycle()
    val native by container.neteaseSessions.state.collectAsStateWithLifecycle()
    val preferences by container.musicSettings.state.collectAsStateWithLifecycle()
    val navidrome by container.navidrome.state.collectAsStateWithLifecycle()
    val together by container.listenTogether.state.collectAsStateWithLifecycle()
    val profileState by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val project = account.account
    // A late response from the previous account must never appear in the new identity's page.
    val profile = profileState.profile?.takeIf { it.user.id == project?.id }
    val user = profile?.user ?: project?.let {
        ProfileUser(it.id, it.username, it.nickname, it.avatar, isAdmin = it.isAdmin, isSuper = it.isSuper, title = it.title)
    } ?: native.profile?.let { ProfileUser(id = it.uid, nickname = it.nickname, avatar = it.avatar) }
    val displayName = user?.let { it.nickname.ifBlank { it.username }.ifBlank {
        if (project != null) "CurrentMusic 用户" else "网易云用户"
    } } ?: if (account.loading) "正在恢复账号…" else "还没有登录"
    val identityRoute = if (project != null) "user/profile/${project.id}" else if (container.nativeNetease != null) "user/binding" else "user/login"
    fun open(route: String) = navigate(myMusicDestination(route, project != null, native.loggedIn,
        native.stale, preferences.neteaseMainLibrary, container.nativeNetease != null))
    val bottomInset = LocalMusicBottomInset.current
    val list = rememberLazyListState()
    val flight = LocalAvatarFlight.current
    val placement = LocalRootTabPlacement.current
    val density = LocalDensity.current
    val colors = MiuixTheme.colorScheme
    val secondary = colors.onSurfaceVariantSummary
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycle, project?.id) {
        if (project != null) lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollStats() }
    }
    LaunchedEffect(project?.id, native.profile?.uid) { list.scrollToItem(0) }
    LaunchedEffect(flight?.request) { if (flight?.active == true && !flight.returning) list.scrollToItem(0) }
    LaunchedEffect(list, flight) {
        snapshotFlow { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 }
            .collect { if (!it) flight?.profileLanding = null }
    }
    val page: @Composable () -> Unit = {
    Box(Modifier.fillMaxSize().testTag("profile_screen"), contentAlignment = Alignment.TopCenter) {
    LazyColumn(Modifier.widthIn(max = 680.dp).fillMaxWidth().fillMaxHeight().testTag("my_music_list"), state = list,
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item("heading") {
            Text("我的", Modifier.fillMaxWidth().then(LocalRootTabSwipe.current).padding(vertical = 4.dp),
                fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        }
        item("identity") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.testTag("my_profile_avatar").onGloballyPositioned {
                    val center = (placement?.landingBounds(it) ?: it.boundsInRoot()).center
                    val radius = with(density) { 32.dp.toPx() }
                    val landing = Rect(center - Offset(radius, radius), center + Offset(radius, radius))
                    if (list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0) flight?.profileLanding = landing
                    if (flight?.returning != true && (flight?.active != true || (list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0)))
                        flight?.destination = landing
                }.graphicsLayer { alpha = if (flight?.active == true) 0f else 1f }
                    .semantics { contentDescription = if (project != null) "打开个人主页" else "打开账号登录与管理" }
                    .clickable(role = Role.Button) { navigate(identityRoute) }) {
                    UserAvatar(user ?: ProfileUser(), container, 64.dp, flightState = flight)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(displayName, fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(when {
                            project != null && account.offline -> "CurrentMusic · 离线账号已保留"
                            project != null && profileState.loading && profile == null -> "个人资料更新中…"
                            project != null -> "CurrentMusic 账户"
                            native.stale -> "网易云登录已过期"
                            native.loggedIn -> "网易云音乐"
                            else -> "登录后同步喜欢与歌单"
                        }, Modifier.align(Alignment.CenterVertically), fontSize = 12.sp, color = secondary)
                        if (project != null) UserRoleBadge(user?.role, Modifier.align(Alignment.CenterVertically))
                    }
                    if (project != null) FlowRow(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        MusicTextAction("编辑资料", { vm.dialog.value = "edit" }, Modifier.testTag("edit_profile"), !busy && profile != null)
                        MusicTextAction("个人主页", { navigate(identityRoute) }, Modifier.testTag("me_identity_action"))
                    } else MusicTextAction(if (native.loggedIn && !native.stale) "管理账号" else if (container.nativeNetease != null) "登录网易云" else "登录账号",
                        { navigate(identityRoute) }, Modifier.testTag("me_identity_action"))
                }
            }
        }
        item("music") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MusicSectionHeader("我的音乐")
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(10.dp)) {
                    MyMusicActions({ open(it) }, together.active,
                        together.room?.participants?.firstOrNull { it.userId != native.profile?.uid }?.nickname)
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MusicTextAction("歌手", { navigate(catalogRoute(false)) }, Modifier.testTag("me_artists"))
                        MusicTextAction("专辑", { navigate(catalogRoute(true)) }, Modifier.testTag("me_albums"))
                    }
                }
            }
        }
        item("accounts") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MusicSectionHeader("账号与服务")
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    if (container.nativeNetease != null) MyServiceRow("网易云音乐", when {
                        native.stale -> "登录已过期，重新登录后恢复同步"
                        native.loggedIn -> native.profile?.nickname.orEmpty().ifBlank { "本机账号" } + " · 已登录"
                        else -> "同步喜欢、歌单与双人一起听"
                    }, if (native.stale) "重登" else if (native.loggedIn) "管理" else "登录", MusicIcons.Headphones,
                        Modifier.testTag("open_binding")) { navigate("user/binding") }
                    MyServiceRow("CurrentMusic 账户", project?.let { "${it.nickname.ifBlank { it.username }.ifBlank { "本机账号" }} · ${if (account.offline) "离线" else "已登录"}" }
                        ?: "个人资料、头像挂件与站内功能", if (project != null) "管理" else "登录", MusicIcons.Account,
                        Modifier.testTag(if (project != null) "open_security" else "open_currentmusic_login")) {
                        navigate(if (project != null) "user/security" else "user/login")
                    }
                    MyServiceRow("Navidrome 自建曲库", if (navidrome.configured) "已连接自己的音乐服务器" else "连接自己的音乐服务器",
                        if (navidrome.configured) "管理" else "连接", MusicIcons.Storage, Modifier.testTag("profile_navidrome")) { navigate("navidrome") }
                    if (project != null) MyServiceRow("头像挂件", "预览与更换头像装饰", "", MusicIcons.Image,
                        Modifier.testTag("open_decorations")) { navigate("user/decorations") }
                }
            }
        }
        profile?.let { data ->
            item("stats") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MusicSectionHeader("CurrentMusic 听歌记录")
                    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            MyListeningStat("听歌天数", "${data.stat.playDays} 天", Modifier.weight(1f))
                            MyListeningStat("累计时长", listeningDuration(data.stat.listenMs), Modifier.weight(1f))
                        }
                    }
                }
            }
            val recent = data.recent.take(3).map { it.toDomain() }
            if (recent.isNotEmpty()) {
                item("recent_heading") { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("最近播放", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    MusicTextAction("全部", { open("lib/recent") })
                } }
                item("recent") { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    recent.forEachIndexed { index, song ->
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { play(recent, index) }
                            .padding(horizontal = 16.dp, vertical = 12.dp).testTag("me_recent_${song.id}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            MusicCover(song.cover, Modifier.size(44.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(song.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artists, fontSize = 12.sp, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(MusicIcons.Play, null, Modifier.size(20.dp), tint = secondary)
                        }
                    }
                } }
            }
        }
        if (project != null && profileState.error != null) item("retry") {
            MusicTextAction("重新加载个人资料", vm::reload, Modifier.testTag("me_retry_profile"), !profileState.loading)
        }
    }
    }
    }
    if (project != null) MusicPullToRefresh(profileState.loading, vm::reload, Modifier.fillMaxSize()) { page() }
    else page()
}

@Composable private fun MyMusicActions(open: (String) -> Unit, together: Boolean, partner: String?) {
    val density = LocalDensity.current
    val actions = listOf(
        MyMusicAction("我喜欢", "红心收藏", MusicIcons.Heart, "lib/likes", "open_likes"),
        MyMusicAction("歌单", "创建与收藏", MusicIcons.List, "lib/playlists", "open_playlists"),
        MyMusicAction("最近播放", "接着上次听", MusicIcons.Update, "lib/recent", "open_recent"),
        MyMusicAction(if (together) "回到一起听" else "双人一起听", if (together) partner.orEmpty().ifBlank { "会话进行中" } else "邀请朋友",
            MusicIcons.Headphones, "room/list", "me_listen_together"))
    BoxWithConstraints {
        val single = maxWidth < 300.dp || density.fontScale >= 1.4f
        Column {
            actions.chunked(if (single) 1 else 2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { action -> MyMusicActionCell(action, Modifier.weight(1f)) { open(action.route) } }
                }
            }
        }
    }
}

private data class MyMusicAction(val title: String, val summary: String, val icon: ImageVector, val route: String, val tag: String)

@Composable private fun MyMusicActionCell(action: MyMusicAction, modifier: Modifier, click: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(modifier.clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = click)
        .heightIn(min = 88.dp).padding(horizontal = 12.dp, vertical = 14.dp).testTag(action.tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(action.icon, null, Modifier.size(26.dp), tint = colors.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(action.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(action.summary, fontSize = 11.sp, color = colors.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun MyServiceRow(title: String, summary: String, action: String, icon: ImageVector,
    modifier: Modifier, click: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = click).heightIn(min = 76.dp)
        .padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(23.dp), tint = colors.onSurface.copy(alpha = .8f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(summary, fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (action.isNotEmpty()) Text(action, fontSize = 12.sp, color = colors.primary)
        Icon(MusicIcons.Forward, null, Modifier.size(16.dp), tint = colors.onSurfaceVariantSummary)
    }
}

@Composable private fun MyListeningStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
