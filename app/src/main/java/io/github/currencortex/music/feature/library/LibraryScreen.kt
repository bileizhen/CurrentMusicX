package io.github.currencortex.music.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.currencortex.music.ui.util.collectAsPageState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.library.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun catalogRoute(album: Boolean, query: String = "") = "lib/browse/${if (album) "album" else "artist"}/${URLEncoder.encode(query, StandardCharsets.UTF_8.name())}"

@Composable fun LibraryLinks(navigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("我喜欢", "lib/likes", "open_likes"), Triple("歌单", "lib/playlists", "open_playlists"),
                Triple("最近播放", "lib/recent", "open_recent")).forEach { (title, route, tag) ->
                Card(Modifier.weight(1f).fillMaxHeight().testTag(tag), onClick = { navigate(route) }, showIndication = true) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("打开音乐库", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
                    }
                }
            }
        }
        MusicDestinationRow("Emby 曲库", { navigate("emby") }, Modifier.testTag("library_open_emby"))
        Card(Modifier.fillMaxWidth().testTag("open_local_music"), onClick = { navigate("lib/local") }, showIndication = true) {
            Column(Modifier.padding(14.dp)) {
                Text("本地音乐", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("扫描手机里的音乐", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
            }
        }
    }
}

@Composable fun LibrarySongActions(vm: LibraryViewModel, song: Song, navigate: (String) -> Unit) {
    if (song.musicSource == io.github.currencortex.music.data.song.MusicSource.LOCAL) return
    if (song.musicSource == io.github.currencortex.music.data.song.MusicSource.EMBY) {
        val dismiss = LocalDismissSongMenu.current
        MusicDestinationRow("喜欢 / 取消喜欢", { dismiss(); vm.togglePrimaryLike(song) })
        MusicDestinationRow("打开 Emby 曲库", { dismiss(); navigate("emby") })
        return
    }
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val status = statuses[song.id] ?: SongStatus()
    val dismiss = LocalDismissSongMenu.current
    LaunchedEffect(song.id) { vm.refreshStatuses(listOf(song)) }
    MusicDestinationRow("喜欢 / 取消喜欢", onClick = { dismiss(); vm.togglePrimaryLike(song) }, enabled = !status.pending && !song.video,
        modifier = Modifier.testTag("song_like_${song.id}"), chevron = false)
    MusicDestinationRow("加入歌单", onClick = { dismiss(); vm.choosePlaylist(song) }, modifier = Modifier.testTag("song_playlist_${song.id}"), chevron = false)
    if (song.musicSource == io.github.currencortex.music.data.song.MusicSource.NAVIDROME) {
        MusicDestinationRow("打开 Navidrome 曲库", { dismiss(); navigate("navidrome") })
        return
    }
    if (song.artistIds.isNotEmpty()) song.artistIds.forEachIndexed { index, id ->
        MusicDestinationRow("歌手 · ${song.artists.split(" / ").getOrNull(index).orEmpty()}", onClick = { dismiss(); navigate("lib/artist/$id") })
    } else if (song.artists.isNotBlank()) MusicDestinationRow("查看歌手", onClick = { dismiss(); navigate(catalogRoute(false, song.artists)) })
    if (song.album.isNotBlank()) MusicDestinationRow("查看专辑", onClick = { dismiss(); navigate(catalogRoute(true, song.album)) })
    if (song.mv > 0) MusicDestinationRow("播放 MV", onClick = { dismiss(); navigate("lib/mv/${song.mv}") }, modifier = Modifier.testTag("song_mv_${song.id}"))
}

@Composable fun LibraryDialogs(vm: LibraryViewModel) {
    val likes by vm.likes.state.collectAsStateWithLifecycle()
    if (likes.song != null) SongLikeSheet(likes, vm.likes::toggle, vm.likes::dismiss)
    val song by vm.selectedSong.collectAsStateWithLifecycle()
    val lists by vm.availablePlaylists.collectAsStateWithLifecycle()
    val playlistError by vm.playlistError.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    if (song != null) MusicDialog("收藏到歌单", onDismiss = { vm.selectedSong.value = null }) {
        if (busy && lists.isEmpty()) Text("正在读取歌单…")
        if (!busy && lists.isEmpty() && playlistError == null) Text("没有可编辑的歌单，请先创建歌单")
        playlistError?.let { error -> Text(error); MusicDialogButton("重试", onClick = { vm.choosePlaylist(song!!) }, enabled = !busy) }
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(lists, key = { "${it.source}-${it.id}" }) { playlist ->
                MusicDestinationRow(playlist.name, { vm.addToPlaylist(playlist) },
                    Modifier.testTag("collect_playlist_${playlist.source}_${playlist.id}"),
                    summary = when (playlist.source) { "netease" -> "网易云音乐"; "navidrome" -> "Navidrome"; else -> "CurrentMusic" }, enabled = !busy, chevron = false)
            }
        }
    }
}

@Composable fun PlaylistIndexScreen(vm: LibraryViewModel, navigate: (String) -> Unit, onBack: (() -> Unit)? = null) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val neteaseMain by vm.container.primaryLibrary.usesNetease.collectAsStateWithLifecycle()
    var create by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    PlaylistIndexBody(vm, navigate, onBack, onCreate = { create = true })
    SettingsUiScope {
    if (create) MusicDialog("新建歌单", onDismiss = { if (!busy) create = false }) {
        TextField(name, { name = it }, label = "歌单名称", modifier = Modifier.fillMaxWidth().then(Modifier.testTag("playlist_name")))
        if (!neteaseMain || vm.container.nativeNetease == null) TextField(description, { description = it }, label = "介绍", modifier = Modifier.fillMaxWidth())
        MusicDialogButton("创建", onClick = { vm.create(name, description) { create = false; name = ""; description = "" } },
            enabled = name.isNotBlank() && !busy, modifier = Modifier.testTag("confirm_create_playlist"))
    }
    }
}

@Composable fun PlaylistCard(playlist: Playlist, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MusicCover(playlist.cover, Modifier.size(64.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(playlist.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${playlist.count} 首" + if (playlist.source == "ncm") " · 网易云音乐 · 只读" else "")
        }
    }
}

@Composable fun LibraryDetailScreen(vm: LibraryDetailViewModel, library: LibraryViewModel, onBack: () -> Unit,
    navigate: (String) -> Unit, play: (List<Song>, Int) -> Unit, playMv: (MvDto) -> Unit, deleted: () -> Unit,
    onDialogActive: (Boolean) -> Unit = {}) {
    val state by vm.state.collectAsPageState()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val busy by library.busy.collectAsStateWithLifecycle()
    val playbackMode by vm.playbackMode.collectAsStateWithLifecycle()
    val roomRequest = LocalRoomSongRequest.current
    var rename by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val editable = vm.editable(state.playlist, account.account?.id ?: 0)
    LaunchedEffect(state.songs) { library.refreshStatuses(state.songs) }
    LaunchedEffect(state.refreshError) { state.refreshError?.let { library.container.errorNotices.show("刷新失败，已保留原列表：$it") } }
    PreloadMusicCovers(state.songs.take(12).map { it.cover })
    if (vm.route.startsWith("lib/mv/")) {
        io.github.currencortex.music.feature.mv.MvDetailScreen(state, vm.container, onBack, playMv,
            onArtist = { navigate("lib/artist/$it") }, onRefresh = { vm.reload(force = true) }, onDialogActive = onDialogActive)
    } else if (vm.route.split('/').getOrNull(1) in setOf("playlist", "ncmplaylist", "album", "likes", "daily", "foryou", "recent")) {
        PlaylistDetailBody(state, vm, library, onBack, navigate, play,
            onRename = { name = state.title; rename = true }, onDelete = { delete = true })
    } else {
    MusicPullToRefresh(state.loading || state.refreshing, { vm.reload(force = true) }, Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        LazyColumn(Modifier.fillMaxSize().testTag("library_detail"), contentPadding = musicScrollPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TextButton("返回", onClick = onBack); Text(state.title, fontSize = 28.sp) }
            if (state.loading && state.songs.isEmpty() && state.mv == null) item { LoadingSongList(4) }
            if (state.cover.isNotBlank()) item { MusicCover(state.cover, Modifier.size(180.dp), 500) }
            if (state.description.isNotBlank()) item { Text(state.description) }
            if (state.playlist?.source == "ncm") item { Text("网易云音乐 · 同步歌单只读") }
            state.error?.let { error -> item { Text(error); TextButton("重试", onClick = { vm.reload() }) } }
            state.mv?.let { mv -> item { TextButton("播放 MV", onClick = { playMv(mv) }, modifier = Modifier.testTag("play_mv")) } }
            if (state.songs.isNotEmpty()) item {
                LazyRow {
                    item { TextButton("播放全部", enabled = roomRequest == null && playbackMode == io.github.currencortex.music.core.media.PlayerMode.LOCAL,
                        onClick = { vm.container.playerController.setMode(io.github.currencortex.music.core.media.PlaybackMode.LIST); play(state.songs, 0) }, modifier = Modifier.testTag("play_library_all")) }
                    item { MusicTextAction("随机播放", enabled = roomRequest == null && playbackMode == io.github.currencortex.music.core.media.PlayerMode.LOCAL,
                        onClick = { vm.container.playerController.setMode(io.github.currencortex.music.core.media.PlaybackMode.SHUFFLE); play(state.songs, state.songs.indices.random()) }) }
                }
            }
            if (roomRequest != null) item { Text("点击歌曲，为当前房间点歌", fontSize = 13.sp) }
            if (editable) item { Row {
                TextButton("重命名", onClick = { name = state.title; rename = true }, modifier = Modifier.testTag("rename_playlist"))
                TextButton("删除歌单", onClick = { delete = true }, modifier = Modifier.testTag("delete_playlist"))
            } }
            if (!state.loading && state.songs.isEmpty() && state.mv == null && state.error == null) item { Text("这里还没有歌曲，可在搜索结果中加入歌单") }
            if (state.albums.isNotEmpty()) item { Text("专辑", fontSize = 22.sp) }
            if (state.albums.isNotEmpty()) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(state.albums, key = Album::id) { album ->
                        Column(Modifier.width(160.dp).testTag("catalog_entry_${album.id}").clickable { navigate("lib/album/${album.id}") }) {
                            MusicCover(album.cover, Modifier.size(160.dp), 400)
                            Text(album.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (state.moreAlbums) item { TextButton("更多专辑", onClick = vm::moreAlbums, enabled = !state.loading) }
            itemsIndexed(state.songs, key = { index, song -> "${song.id}-$index" }) { index, song ->
                SongRow(SongRowUi(song), { play(state.songs, index) }, { vm.container.playerController.add(song, true) }, { vm.container.playerController.add(song) }) {
                    LibrarySongActions(library, song, navigate)
                    val dismiss = LocalDismissSongMenu.current
                    if (editable) MusicDestinationRow("移出歌单", onClick = { dismiss(); library.action("已移出歌单") { vm.remove(song) } }, enabled = !busy, chevron = false)
                }
            }
            if (state.more) item { TextButton("加载更多歌曲", onClick = { vm.reload(true) }, enabled = !state.loading) }
        }
    }
    }
    SettingsUiScope {
    if (rename) MusicDialog("重命名歌单", onDismiss = { if (!busy) rename = false }) {
        TextField(name, { name = it }, label = "歌单名称", modifier = Modifier.fillMaxWidth().then(Modifier.testTag("rename_input")))
        MusicDialogButton("保存", onClick = { library.action("名称已更新") { vm.rename(name); rename = false } }, enabled = name.isNotBlank() && !busy)
    }
    val unsubscribe = state.playlist?.let { it.source == "netease" && !it.nativeOwned } == true
    if (delete) MusicDialog(if (unsubscribe) "取消收藏歌单？" else "删除歌单？", onDismiss = { if (!busy) delete = false }) {
        Text(if (unsubscribe) "歌单将从你的网易云收藏中移除。" else "删除后无法恢复。")
        MusicDialogButton(if (unsubscribe) "确认取消收藏" else "确认删除", onClick = {
            library.action(if (unsubscribe) "已取消收藏歌单" else "歌单已删除") { vm.delete(); delete = false; deleted() }
        }, enabled = !busy)
    }
    }
}

@Composable fun CatalogScreen(vm: CatalogViewModel, onBack: () -> Unit, navigate: (String) -> Unit) {
    val state by vm.state.collectAsPageState()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    fun submit() { keyboard?.hide(); focus.clearFocus(); vm.search() }
    MusicSubpage(if (state.albums) "专辑" else "歌手", "catalog_page", onBack) { topInset ->
    LazyColumn(Modifier.fillMaxSize().testTag("catalog_screen"),
        contentPadding = musicScrollPadding(PaddingValues(16.dp, topInset + 8.dp, 16.dp, 32.dp)), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            MusicSearchBar(state.query, ::submit, onInput = vm::input, placeholder = "搜索${if (state.albums) "专辑" else "歌手"}",
                inputTag = "catalog_query", submitTag = "catalog_submit")
        }
        if (state.loading) item { LoadingSongList(3) }
        state.error?.let { error -> item { Text(error); MusicTextAction("重试", onClick = { vm.search() }) } }
        if (state.submitted.isNotBlank() && !state.loading && state.entries.isEmpty() && state.error == null) item { Text("没有找到结果") }
        itemsIndexed(state.entries, key = { index, entry -> "${entry.id}-$index" }) { _, entry -> CatalogCard(entry) { navigate("lib/${if (state.albums) "album" else "artist"}/${entry.id}") } }
        if (state.more) item { MusicTextAction("加载更多", onClick = { vm.search(true) }, enabled = !state.loading) }
    }
    }
}

@Composable private fun CatalogCard(entry: CatalogEntry, open: () -> Unit) {
    SettingsGroup {
        SettingsPreference(entry.name, entry.subtitle, Modifier.testTag("catalog_entry_${entry.id}"), onClick = open,
            leading = { MusicCover(entry.cover, Modifier.size(56.dp), cornerRadius = 12.dp) },
            trailing = { Text("›", fontSize = 22.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) })
    }
}
