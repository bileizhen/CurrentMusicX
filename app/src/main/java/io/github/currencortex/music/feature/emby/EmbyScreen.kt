package io.github.currencortex.music.feature.emby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.feature.library.PlaylistTrack
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.flow.distinctUntilChanged

data class EmbyNavigation(val selected: Int, val onSelect: (Int) -> Unit) {
    companion object {
        val kinds = listOf("songs", "artists", "playlists", "likes")
        val labels = listOf("歌曲", "艺术家", "歌单", "我喜欢")
        val icons = listOf(MusicIcons.Headphones, MusicIcons.Account, MusicIcons.List, MusicIcons.Heart)
    }
}

@Composable fun EmbyScreen(vm: EmbyViewModel, play: (List<Song>, Int) -> Unit, onBack: () -> Unit,
    onNavigationChange: ((EmbyNavigation?) -> Unit)? = null) {
    val account by vm.account.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val likes by vm.repository.likes.collectAsStateWithLifecycle()
    val queue by vm.container.playbackQueue.state.collectAsStateWithLifecycle()
    var settings by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val configuration = settings || !account.configured
    val selectTab: (Int) -> Unit = remember(vm) { { index ->
        searching = false; query = ""; vm.tab(EmbyNavigation.kinds[index])
    } }
    val navigation = remember(vm.selectedTab, selectTab) { EmbyNavigation(vm.selectedTab, selectTab) }
    val navigationChanged by rememberUpdatedState(onNavigationChange)
    LaunchedEffect(navigation, configuration) { navigationChanged?.invoke(navigation.takeUnless { configuration }) }
    DisposableEffect(Unit) { onDispose { navigationChanged?.invoke(null) } }
    fun back() { if (settings && account.configured) settings = false else if (!vm.back()) onBack() }
    BackHandler(settings || vm.nested) { back() }
    Box(Modifier.fillMaxSize()) {
    MusicSubpage(if (configuration) "Emby 账号" else state.route.title, "emby_screen", ::back,
        toolbar = if (configuration) null else ({
            GlassSearchToolbar(state.route.title, 1f, ::back, searching, {
                searching = it
                if (!it && state.route.kind == "search") vm.back()
            }, query, { query = it },
                action = GlassToolbarAction(Icons.Outlined.Refresh, "刷新曲库", { vm.refresh() }, "emby_refresh"),
                trailing = GlassToolbarAction(MusicIcons.Account, "账号设置", { settings = true }, "emby_account"),
                modifier = Modifier.testTag("emby_screen_toolbar"), inputTag = "emby_query", searchTag = "emby_search",
                placeholder = "搜索 Emby 歌曲", onSubmit = { vm.search(query) })
        }), actions = {
        if (account.configured) GlassIconButton({ settings = !configuration }, if (configuration) "返回曲库" else "账号设置",
            Modifier.testTag("emby_account")) {
            Icon(if (configuration) MusicIcons.List else MusicIcons.Account, null, Modifier.size(22.dp))
        }
    }) { topInset ->
        if (configuration) EmbyAccountPage(vm, topInset) { settings = false }
        else Column(Modifier.fillMaxSize().padding(top = topInset)) {
            Text("${account.username} · Emby", Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            error?.let { Text(it, Modifier.padding(16.dp), color = MiuixTheme.colorScheme.error) }
            val list = remember(state.route, account.revision) { LazyListState(state.index, state.offset) }
            val route = state.route
            DisposableEffect(list) { onDispose { vm.position(route, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) } }
            val latest by rememberUpdatedState(state)
            LaunchedEffect(list) {
                snapshotFlow {
                    val value = latest
                    value.page.more && !value.loading && value.error == null && list.layoutInfo.totalItemsCount > 0 &&
                        (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= list.layoutInfo.totalItemsCount - 4
                }.distinctUntilChanged().collect { if (it) vm.refresh(true) }
            }
            LazyColumn(Modifier.fillMaxSize().testTag("emby_list"), state = list,
                contentPadding = musicScrollPadding(PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp))) {
                items(state.page.entries, key = { "${it.type}:${it.id}" }) { entry ->
                    val cover by produceState(entry.cover, entry, account.revision) {
                        val result = appResult { vm.repository.entryCover(entry, account.revision) }
                        if (result is AppResult.Success) value = result.value
                    }
                    Row(Modifier.fillMaxWidth().clickable { vm.open(entry) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        MusicCover(cover, Modifier.size(60.dp), cornerRadius = 12.dp)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (entry.subtitle.isNotBlank()) Text(entry.subtitle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                itemsIndexed(state.page.songs, key = { index, song -> "$index:${song.id}" }) { index, song ->
                    PlaylistTrack(song, queue.current?.id == song.id, likes[song.id] == true,
                        { if (vm.container.playerController.state.value.mode == io.github.currencortex.music.core.media.PlayerMode.LOCAL)
                            play(state.page.songs, index) else vm.error.value = "请先退出一起听或结束投屏" },
                        { vm.enqueue(song, true) }, { vm.enqueue(song, false) }) {
                        val dismiss = LocalDismissSongMenu.current
                        MusicDestinationRow(if (likes[song.id] == true) "取消喜欢" else "喜欢", { dismiss(); vm.toggleLike(song) }, enabled = !busy)
                    }
                }
                if (state.loading) item("loading") { LoadingSongList(4) }
                state.error?.let { message -> item("error") {
                    Text(message, color = MiuixTheme.colorScheme.error)
                    MusicTextAction("重试", { vm.refresh(state.loaded && state.page.more) })
                } }
                if (state.loaded && !state.loading && state.page.entries.isEmpty() && state.page.songs.isEmpty())
                    item("empty") { Text("这里还没有音乐", Modifier.padding(vertical = 32.dp)) }
                if (state.page.more && !state.loading) item("more") { MusicTextAction("加载更多", { vm.refresh(true) }) }
            }
        }
    }
    if (!configuration && onNavigationChange == null) PlainFloatingBar(navigation.selected,
        EmbyNavigation.labels, EmbyNavigation.icons, navigation.onSelect,
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("emby_bottom_bar"))
    }
}

@Composable private fun EmbyAccountPage(vm: EmbyViewModel, inset: androidx.compose.ui.unit.Dp, connected: () -> Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var server by rememberSaveable(account.server) { mutableStateOf(account.server) }
    var username by rememberSaveable(account.username) { mutableStateOf(account.username) }
    var password by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val canConnect = !busy && server.isNotBlank() && username.isNotBlank()
    fun connect() {
        if (!canConnect) return
        focus.clearFocus(); keyboard?.hide()
        vm.connect(server, username, password) { password = ""; connected() }
    }
    SettingsUiScope {
        LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = musicScrollPadding(PaddingValues(16.dp, inset + 8.dp, 16.dp, 32.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { SettingsHint("连接 Emby 音乐库，按需分页读取，原音质播放。登录令牌加密保存在本机，不保存密码。") }
            if (account.configured) item { SettingsSection("当前账号", MusicIcons.Device) {
                SettingsPreference(account.username, account.server)
                SettingsHint(if (account.download) "服务器允许下载原文件" else "此账号未开放下载权限，可在线播放")
            } }
            item { SettingsSection("服务器连接", MusicIcons.Storage) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(server, { server = it; vm.error.value = null }, label = "服务器地址", singleLine = true, enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.testTag("emby_server"))
                    TextField(username, { username = it; vm.error.value = null }, label = "用户名", singleLine = true, enabled = !busy, modifier = Modifier.testTag("emby_username"))
                    TextField(password, { password = it; vm.error.value = null }, label = "密码", singleLine = true, enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { connect() }),
                        modifier = Modifier.testTag("emby_password"))
                    error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
                }
            } }
            item { GlassActionButton(if (busy) "正在连接…" else if (account.configured) "更换连接" else "连接服务器", ::connect,
                primary = true, enabled = canConnect, modifier = Modifier.fillMaxWidth().testTag("emby_connect")) }
            if (account.configured) item { MusicTextAction("断开连接", { password = ""; vm.disconnect() }, enabled = !busy) }
        }
    }
}
