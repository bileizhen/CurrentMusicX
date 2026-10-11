package io.github.currencortex.music.feature.navidrome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.navidrome.NavidromeEntry
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.*
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun NavidromeScreen(vm: NavidromeViewModel, library: LibraryViewModel, navigate: (String) -> Unit,
    play: (List<Song>, Int) -> Unit, root: Boolean = false) {
    val account by vm.account.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var configuration by rememberSaveable { mutableStateOf(false) }
    var server by rememberSaveable(account.server) { mutableStateOf(account.server) }
    var username by rememberSaveable(account.username) { mutableStateOf(account.username) }
    // Password never enters saved state, UI diagnostics or a URL.
    var password by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var removeIndex by remember { mutableIntStateOf(-1) }
    BackHandler(configuration || vm.nested) { if (configuration) configuration = false else vm.back() }
    BoxWithConstraints(Modifier.fillMaxSize().then(if (root) Modifier else Modifier.statusBarsPadding()).testTag("navidrome_screen")) {
        val columns = if (maxWidth > 600.dp) 3 else 2
        val hint = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = musicScrollPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (configuration || !account.configured) "Navidrome" else state.page.title.takeIf { vm.nested } ?: "我的曲库",
                    Modifier.weight(1f), fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (account.configured) MusicTextAction(if (configuration) "曲库" else "账号", { configuration = !configuration }, Modifier.testTag("navidrome_account"))
            } }
            if (!account.configured || configuration) {
                item { Text("连接自己的音乐服务器", fontSize = 16.sp); Text("兼容 Navidrome / Subsonic，账号加密保存在本机。", fontSize = 12.sp, color = hint) }
                item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(server, { server = it }, label = "服务器地址", singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.testTag("navidrome_server"))
                    TextField(username, { username = it }, label = "用户名", singleLine = true, modifier = Modifier.testTag("navidrome_username"))
                    TextField(password, { password = it }, label = if (account.configured) "密码（重新连接时填写）" else "密码", singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.testTag("navidrome_password"))
                    TextButton(if (busy) "正在连接…" else if (account.configured) "重新连接" else "连接服务器", onClick = {
                        vm.connect(server, username, password) { password = ""; configuration = false }
                    }, enabled = !busy && server.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("navidrome_connect"))
                } } }
                if (account.configured) item { Card {
                    SettingsSwitch("用作首页曲库", account.home, vm::home, summary = "首页直接浏览自建曲库；关闭后恢复原首页", enabled = !busy)
                    MusicDestinationRow("断开连接", { dialog = "disconnect" }, enabled = !busy, chevron = false)
                } }
                if (account.configured) item { Text("${account.username} · Navidrome ${account.version}", fontSize = 12.sp, color = hint) }
            } else {
                item { Text(account.username + " · Navidrome", fontSize = 12.sp, color = hint) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(query, { query = it }, label = "搜索歌曲、专辑、艺术家", singleLine = true, modifier = Modifier.weight(1f).testTag("navidrome_query"))
                    MusicTextAction("搜索", { vm.search(query) }, Modifier.testTag("navidrome_search"), enabled = query.isNotBlank() && !state.loading)
                } }
                if (!vm.nested) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("albums" to "专辑", "artists" to "艺术家", "playlists" to "歌单", "likes" to "我喜欢").forEach { (kind, label) ->
                        MusicTextAction((if (state.page.kind == kind) "• " else "") + label, { vm.tab(kind) }, Modifier.testTag("navidrome_tab_$kind"))
                    }
                } } else item { MusicTextAction("返回曲库", { vm.back() }, Modifier.testTag("navidrome_back")) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MusicTextAction("刷新", { vm.refresh() }, enabled = !state.loading)
                    if (state.page.kind == "playlists") MusicTextAction("新建歌单", { name = ""; dialog = "create" }, enabled = !busy)
                    if (state.page.kind == "playlist" && state.owned) {
                        MusicTextAction("重命名", { name = state.page.title; dialog = "rename" }, enabled = !busy)
                        MusicTextAction("删除", { dialog = "delete" }, enabled = !busy, destructive = true)
                    }
                } }
                state.error?.let { error -> item { Text(error, color = MiuixTheme.colorScheme.error); TextButton("重试", { vm.refresh() }) } }
                if (state.songs.isNotEmpty()) {
                    item { Card(Modifier.fillMaxWidth(), onClick = { play(state.songs, 0) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("▶  播放全部", fontSize = 20.sp, modifier = Modifier.weight(1f))
                            Text("${state.songs.size} 首", fontSize = 12.sp, color = hint)
                        }
                    } }
                    itemsIndexed(state.songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
                        SongRow(SongRowUi(song), { play(state.songs, index) }, { vm.enqueue(song, next = true) },
                            { vm.enqueue(song) }, extraActions = {
                                LibrarySongActions(library, song, navigate)
                                if (state.page.kind == "playlist" && state.owned) {
                                    val dismiss = LocalDismissSongMenu.current
                                    MusicDestinationRow("移出歌单", { dismiss(); removeIndex = index; dialog = "remove" }, chevron = false)
                                }
                            })
                    }
                }
                if (state.artists.isNotEmpty()) item { Text("艺术家", fontSize = 18.sp) }
                items(state.artists, key = { "artist-${it.id}" }) { entry -> NavidromeEntryRow(entry) { vm.open("artist", entry) } }
                if (state.albums.isNotEmpty()) item { Text("专辑", fontSize = 18.sp) }
                val entries = state.entries + state.albums
                items(entries.chunked(columns), key = { it.joinToString { entry -> entry.id } }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { entry -> Column(Modifier.weight(1f).clickable {
                            vm.open(when (state.page.kind) { "artists" -> "artist"; "playlists" -> "playlist"; else -> "album" }, entry)
                        }.testTag("navidrome_entry_${entry.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MusicCover(entry.cover, Modifier.fillMaxWidth().aspectRatio(1f), pixels = 480)
                            Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                            Text(entry.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = hint)
                        } }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (state.more) item { TextButton("加载更多", { vm.refresh(true) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) }
                if (state.loading) item { Text(if (state.entries.isEmpty() && state.songs.isEmpty()) "正在读取曲库…" else "正在后台更新…", color = hint) }
                else if (state.entries.isEmpty() && state.songs.isEmpty() && state.albums.isEmpty() && state.artists.isEmpty() && state.error == null)
                    item { Text(if (state.page.kind == "search") "没有找到匹配的音乐" else "这里还没有内容", color = hint) }
            }
            message?.let { item { Text(it, Modifier.testTag("navidrome_message"), fontSize = 13.sp) } }
        }
    }
    if (dialog.isNotEmpty()) MusicDialog(when (dialog) { "disconnect" -> "断开连接"; "delete" -> "删除歌单"; "remove" -> "移出歌单"; "rename" -> "重命名歌单"; else -> "新建歌单" }, { dialog = "" }) {
        if (dialog in setOf("create", "rename")) TextField(name, { name = it }, label = "歌单名称", singleLine = true, modifier = Modifier.testTag("navidrome_playlist_name"))
        else Text(when (dialog) { "disconnect" -> "退出此服务器账号，保留其他音乐服务和播放队列。"; "remove" -> "将这首歌移出当前歌单？"; else -> "确定删除这个歌单？音乐文件会保留。" })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", { dialog = "" }, modifier = Modifier.weight(1f))
            TextButton("确定", {
                when (dialog) { "disconnect" -> vm.disconnect(); "create" -> vm.create(name); "rename" -> vm.rename(name); "delete" -> vm.delete(); "remove" -> vm.remove(removeIndex) }
                dialog = ""
            }, enabled = !busy && (dialog !in setOf("create", "rename") || name.isNotBlank()), modifier = Modifier.weight(1f))
        }
    }
}

@Composable private fun NavidromeEntryRow(entry: NavidromeEntry, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        MusicCover(entry.cover, Modifier.size(52.dp))
        Text(entry.name, Modifier.padding(start = 12.dp), fontSize = 16.sp)
    }
}
