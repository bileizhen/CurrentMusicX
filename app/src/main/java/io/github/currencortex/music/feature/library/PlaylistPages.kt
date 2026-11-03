package io.github.currencortex.music.feature.library

import android.content.Intent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.R
import io.github.currencortex.music.core.media.PlaybackMode
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.style.StyleArtwork
import io.github.currencortex.music.feature.style.rememberStyleTint
import io.github.currencortex.music.ui.component.*
import kotlinx.coroutines.launch
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Cover-first library entrance; the existing create dialog and server ownership stay in the host. */
@Composable internal fun PlaylistIndexBody(vm: LibraryViewModel,
    navigate: (String) -> Unit, onBack: (() -> Unit)? = null, onCreate: () -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val neteaseMain by vm.container.primaryLibrary.usesNetease.collectAsStateWithLifecycle()
    val nativeAccount by vm.container.neteaseSessions.state.collectAsStateWithLifecycle()
    MusicSubpage("我的歌单", "playlist_index_page", onBack, actions = {
        if (account.account != null || (nativeAccount.loggedIn && !nativeAccount.stale))
            GlassIconButton(onCreate, "新建歌单", Modifier.testTag("create_playlist")) { Icon(Icons.Default.Add, null) }
    }) { topInset ->
    MusicPullToRefresh(home.loading, vm::refresh, Modifier.fillMaxSize(), showIndicator = false) {
        LazyVerticalGrid(GridCells.Adaptive(148.dp), Modifier.fillMaxSize().testTag("playlist_index"),
            contentPadding = musicScrollPadding(PaddingValues(16.dp, topInset + 8.dp, 16.dp, 32.dp)),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { SettingsHint("${home.playlists.size} 个歌单 · 你的音乐收藏") }
            home.errors["我的歌单"]?.let { error -> item(span = { GridItemSpan(maxLineSpan) }) {
                Column { Text(error); TextButton("重试", onClick = vm::refresh) }
            } }
            if ((home.loading || account.loading) && home.playlists.isEmpty()) items(4) {
                LoadingCoverTile()
            }
            if (!home.loading && !account.loading && home.playlists.isEmpty() && home.errors["我的歌单"] == null)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(MusicIcons.List, null, Modifier.size(40.dp), tint = MiuixTheme.colorScheme.primary.copy(alpha = .6f))
                        Text(if (account.account == null) "登录后查看你的歌单" else "为喜欢的音乐建一个歌单", Modifier.padding(top = 16.dp), fontSize = 17.sp)
                        Text(if (account.account == null) "绑定网易云账号后也可同步收藏" else "点右上角 + 开始收藏", Modifier.padding(top = 8.dp),
                            fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                    }
                }
            items(home.playlists, key = { "${it.source}-${it.id}" }) { playlist ->
                Column(Modifier.testTag("playlist_card_${playlist.id}").clickable(role = Role.Button) {
                    navigate("lib/${if (playlist.source == "netease") "ncmplaylist" else "playlist"}/${playlist.id}")
                }) {
                    MusicCover(playlist.cover.ifBlank { playlist.songs.firstOrNull()?.cover.orEmpty() },
                        Modifier.fillMaxWidth().aspectRatio(1f), 480, cornerRadius = 18.dp)
                    Text(playlist.name, Modifier.padding(top = 10.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${playlist.count.takeIf { it > 0 } ?: playlist.songs.size} 首 · ${if (playlist.source in setOf("ncm", "netease")) "网易云音乐" else "CurrentMusic"}",
                        Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
            }
        }
    }
    }
}

internal data class LocalMusicPlaylistActions(val allowed: Boolean, val rescan: () -> Unit,
    val requestPermission: () -> Unit, val openSettings: () -> Unit, val permissionMessage: String? = null,
    val chooseScope: () -> Unit = requestPermission)

@Composable internal fun PlaylistDetailBody(state: LibraryDetailState, vm: LibraryDetailViewModel,
    library: LibraryViewModel, onBack: () -> Unit, navigate: (String) -> Unit,
    play: (List<Song>, Int) -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
    localMusic: LocalMusicPlaylistActions? = null) {
    fun refresh() {
        if (localMusic == null) vm.reload(force = true)
        else if (localMusic.allowed) localMusic.rescan() else localMusic.requestPermission()
    }
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val statuses by library.statuses.collectAsStateWithLifecycle()
    val likesUseNetease by vm.container.primaryLibrary.likesUseNetease.collectAsStateWithLifecycle()
    val neteaseMain by vm.container.primaryLibrary.usesNetease.collectAsStateWithLifecycle()
    val neteaseLikes by vm.container.neteaseSongActions.likedState.collectAsStateWithLifecycle()
    val navidromeLikes by vm.container.navidrome.likes.collectAsStateWithLifecycle()
    val likeBusy by library.primaryLikeBusy.collectAsStateWithLifecycle()
    val playback by vm.container.playerController.state.collectAsStateWithLifecycle()
    val queue by vm.container.playbackQueue.state.collectAsStateWithLifecycle()
    val busy by library.busy.collectAsStateWithLifecycle()
    val importState by vm.container.playlistLikeImporter.state.collectAsStateWithLifecycle()
    var importOpen by rememberSaveable(vm.route) { mutableStateOf(false) }
    val appearance by vm.container.settings.state.collectAsStateWithLifecycle()
    val room = LocalRoomSongRequest.current
    val editable = vm.editable(state.playlist, account.account?.id ?: 0)
    val removable = vm.removable(state.playlist, account.account?.id ?: 0)
    val cover = state.cover.ifBlank { state.songs.firstOrNull { it.cover.isNotBlank() }?.cover.orEmpty() }
    val tint = rememberStyleTint(cover)
    val surface = MiuixTheme.colorScheme.background
    val list = rememberLazyListState()
    var heroHeightPx by remember { mutableIntStateOf(0) }
    val pullMotion = rememberPlaylistPullMotion(state.loading || state.refreshing, ::refresh)
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable(vm.route) { mutableStateOf(false) }
    var query by rememberSaveable(vm.route) { mutableStateOf("") }
    var order by rememberSaveable(vm.route) { mutableIntStateOf(0) }
    var sortOpen by rememberSaveable(vm.route) { mutableStateOf(false) }
    var menuOpen by rememberSaveable(vm.route) { mutableStateOf(false) }
    var infoOpen by rememberSaveable(vm.route) { mutableStateOf(false) }
    // Reserve result scroll range without inserting an editor into the song list.
    val searchClearance = with(LocalDensity.current) { if (searching && query.isNotBlank()) heroHeightPx.toDp() else 0.dp }
    val songs = remember(state.songs, query, order) {
        val needle = MusicSearchQuery(query)
        val filtered = state.songs.filter { needle.matches(it.name, it.artists, it.album) }
        val collator = java.text.Collator.getInstance(java.util.Locale.SIMPLIFIED_CHINESE).apply {
            strength = java.text.Collator.PRIMARY
        }
        when (order) {
            1 -> filtered.sortedWith { left, right -> collator.compare(left.name, right.name) }
            2 -> filtered.sortedWith { left, right -> collator.compare(left.artists, right.artists) }
            else -> filtered
        }
    }
    val songKeys = remember(songs) { playlistSongKeys(songs) }
    val canPlay = room == null && playback.mode == PlayerMode.LOCAL
    if (appearance.playlistRecordShelf && state.songs.isNotEmpty()) {
        val previewContext = androidx.compose.ui.platform.LocalContext.current
        val previewLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
        val preview = remember(vm.container) { RecordShelfPreview(previewContext, vm.container) }
        val previewPageActive = io.github.currencortex.music.ui.component.LocalMusicPageActive.current
        val previewAllowed by rememberUpdatedState(canPlay && previewPageActive)
        DisposableEffect(preview, previewLifecycle) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
                preview.setActive(previewAllowed && previewLifecycle.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            }
            previewLifecycle.lifecycle.addObserver(observer)
            onDispose { previewLifecycle.lifecycle.removeObserver(observer); preview.close() }
        }
        SideEffect {
            preview.setActive(previewAllowed && previewLifecycle.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            preview.setFullLength(appearance.recordShelfFullPreview)
            preview.setMode(appearance.recordShelfPreviewMode)
        }
        LaunchedEffect(state.songs, likesUseNetease) {
            library.refreshSearchLikes(state.songs.filter { !it.privateLibrary })
        }
        PlaylistRecordShelf(state.songs, canPlay, onPlay = { index -> preview.commit { play(state.songs, index) } },
            onPreview = preview::select,
            previewMode = appearance.recordShelfPreviewMode,
            fullPreview = appearance.recordShelfFullPreview,
            onFullPreviewChange = { full -> scope.launch { vm.container.settings.edit { it.copy(recordShelfFullPreview = full) } } },
            onPreviewModeChange = { mode -> scope.launch { vm.container.settings.edit { it.copy(recordShelfPreviewMode = mode) } } },
            nextPreviewRequests = preview.nextRequests,
            isSaved = { song -> when {
                song.navidrome != null -> navidromeLikes[song.id] == true
                likesUseNetease -> io.github.currencortex.music.data.song.NeteaseSongActionsRepository.songId(song) in neteaseLikes
                else -> statuses[song.id]?.liked == true
            } },
            onSave = if (localMusic != null) null else { song -> library.togglePrimaryLike(song, undoable = true) }, saveEnabled = !likeBusy,
            onClose = { scope.launch { vm.container.settings.edit { it.copy(playlistRecordShelf = false) } } })
        return
    }
    if (appearance.playlistDepthRibbon && songs.isNotEmpty()) {
        PlaylistDepthRibbon(songs, canPlay, onPlay = { index -> play(songs, index) },
            onPrepare = { index -> val job = vm.preloadForPlayback(songs[index]); { job.cancel() } },
            onClose = { scope.launch { vm.container.settings.edit { it.copy(playlistDepthRibbon = false) } } })
        return
    }
    val albumPage = vm.route.startsWith("lib/album/")
    val collectionKind = if (albumPage) "专辑" else "歌单"
    val title = state.title.ifBlank { if (state.loading) "正在加载$collectionKind" else collectionKind }
    val origin = when {
        albumPage -> state.description.lineSequence().firstOrNull { it.isNotBlank() } ?: "专辑"
        localMusic != null -> "本地曲库 · ${state.songs.size} 首歌曲"
        state.playlist?.source == "ncm" -> "网易云音乐 · 同步歌单只读"
        state.playlist?.source == "netease" -> "网易云音乐"
        editable -> account.account?.nickname.orEmpty().ifBlank { "我的歌单" }
        vm.route == "lib/likes" -> "CurrentMusic · 我喜欢"
        vm.route == "lib/daily" -> "每日为你推荐"
        vm.route == "lib/foryou" -> "根据你的音乐喜好推荐"
        vm.route == "lib/recent" -> "最近听过的音乐"
        else -> "CurrentMusic 歌单"
    }
    val heroOrigin = if (state.playlist?.source == "ncm") "网易云音乐" else origin
    val heroDescription = (if (albumPage) state.description.substringAfter('\n', "") else state.description).trim().takeUnless {
        it == "来自网易云音乐" || it == heroOrigin
    }.orEmpty()
    fun start(shuffle: Boolean) {
        if (songs.isEmpty() || !canPlay) return
        vm.container.playerController.setMode(if (shuffle) PlaybackMode.SHUFFLE else PlaybackMode.LIST)
        play(songs, if (shuffle) songs.indices.random() else 0)
    }
    val context = LocalContext.current
    // Opening only transforms the toolbar. Reveal results when filtering starts.
    LaunchedEffect(searching && query.isNotBlank()) {
        if (searching && query.isNotBlank()) list.animateScrollToItem(1)
    }
    fun share() {
        val text = buildString {
            appendLine("《$title》 · ${state.songs.size} 首歌曲")
            state.songs.take(12).forEach { appendLine("${it.name}${it.artists.takeIf(String::isNotBlank)?.let { artist -> " — $artist" }.orEmpty()}") }
            append("来自 CurrentMusic")
        }
        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text), "分享$collectionKind")) }.onFailure { library.container.errorNotices.show("暂时无法打开分享") }
    }
    PlaylistPageLayout(cover, list, heroHeightPx,
        if (localMusic != null) "local_music_screen" else "library_detail", toolbar = { progress ->
            GlassSearchToolbar(title, ((progress - .35f) / .65f).coerceIn(0f, 1f), onBack,
                searching, { searching = it }, query, { query = it },
                action = GlassToolbarAction(MusicIcons.Image, "CD 唱片架", {
                    scope.launch { vm.container.settings.edit { it.copy(playlistRecordShelf = true, playlistDepthRibbon = false) } }
                }, "playlist_record_shelf_toggle"),
                trailing = GlassToolbarAction(Icons.Default.MoreVert, "歌单操作", { menuOpen = true }, "playlist_more"),
                modifier = Modifier.testTag("playlist_top_bar"), inputTag = "playlist_query", searchTag = "playlist_search_toggle",
                placeholder = "搜索$collectionKind")
        }) { topInset ->
        Box(Modifier.fillMaxSize().testTag("playlist_refresh")) {
            LazyColumn(Modifier.fillMaxSize().clipToBounds().nestedScroll(pullMotion).testTag("playlist_tracks"),
                state = list, overscrollEffect = null,
                contentPadding = musicScrollPadding(PaddingValues(top = topInset + 8.dp, bottom = 32.dp + searchClearance))) {
                item(key = "hero") {
                    Column(Modifier.fillMaxWidth().onSizeChanged { heroHeightPx = it.height }
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp).testTag("playlist_hero")) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            StyleArtwork(cover, Modifier.size(128.dp).clip(RoundedCornerShape(16.dp)).testTag("playlist_cover"), tint,
                                pixels = 420, pending = state.loading && state.songs.isEmpty(), contentDescription = "歌单封面")
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(title, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(heroOrigin, fontSize = 12.sp, color = Color.White.copy(alpha = .72f), maxLines = 2)
                                if (heroDescription.isNotBlank()) Text(heroDescription,
                                    Modifier.clickable { infoOpen = true }, fontSize = 12.sp, color = Color.White.copy(alpha = .72f),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (localMusic != null) PlaylistPill("扫描范围", Icons.Default.Search,
                                localMusic.chooseScope, Modifier.weight(1f).testTag("local_music_scope"))
                            else PlaylistPill("分享", Icons.Default.Share, ::share, Modifier.weight(1f).testTag("playlist_share"), enabled = state.title.isNotBlank())
                            PlaylistPill("随机播放", Icons.Default.PlayArrow, { start(true) }, Modifier.weight(1f).testTag("playlist_shuffle"), enabled = canPlay && songs.isNotEmpty(), emphasized = true)
                            if (localMusic != null) PlaylistPill(if (localMusic.allowed) "扫描音乐" else "选择范围", Icons.Default.Refresh,
                                ::refresh, Modifier.weight(1f).testTag(if (localMusic.allowed) "local_music_refresh" else "local_music_permission"),
                                enabled = !state.loading && !state.refreshing)
                            else PlaylistPill("${collectionKind}信息", Icons.Default.Info, { infoOpen = true }, Modifier.weight(1f).testTag("playlist_info"))
                        }
                    }
                }
                item(key = "playback") {
                    Column(Modifier.fillMaxWidth().playlistPullSurface(pullMotion).background(surface)
                        .testTag("playlist_playback_panel")) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            MusicTransportButton("播放全部", { start(false) }, Modifier.size(48.dp).testTag("play_library_all"),
                                prominent = true, enabled = songs.isNotEmpty() && canPlay)
                            Column(Modifier.weight(1f).clickable(enabled = songs.isNotEmpty() && canPlay, role = Role.Button) { start(false) }
                                .padding(horizontal = 14.dp)) {
                                Text("播放全部", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (query.isBlank()) playlistDurationLabel(songs) else "找到 ${songs.size} 首 / 共 ${state.songs.size} 首",
                                    Modifier.padding(top = 3.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                            }
                            PlaylistIconAction(MusicIcons.Sort, "歌曲排序", { sortOpen = true }, Modifier.testTag("playlist_sort"),
                                ink = MiuixTheme.colorScheme.onSurface, background = Color.Transparent)
                        }

                    }
                }
                val resume = queue.current?.takeIf { current -> state.songs.any { it.id == current.id && it.musicSource == current.musicSource } }
                if (resume != null && !playback.showPause && queue.positionMs.coerceAtLeast(playback.positionMs) > 0 && canPlay) item(key = "resume") {
                    Row(Modifier.fillMaxWidth().playlistPullSurface(pullMotion).background(surface).clickable(role = Role.Button) { vm.container.playerController.toggle() }
                        .padding(horizontal = 20.dp, vertical = 12.dp).testTag("playlist_resume"), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.primary)
                        Text("继续播放：${resume.name}", Modifier.padding(start = 10.dp), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (state.loading && state.songs.isEmpty()) item { Box(Modifier.fillMaxWidth().playlistPullSurface(pullMotion).padding(20.dp)) { LoadingSongList(4) } }
                if (localMusic != null && !localMusic.allowed) item {
                    Column(Modifier.fillMaxWidth().playlistPullSurface(pullMotion).background(surface).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(localMusic.permissionMessage ?: "选择音乐文件夹，只导入该目录及子目录中的歌曲；也可选择扫描全机音乐。", fontSize = 14.sp)
                        TextButton("选择扫描范围", onClick = localMusic.requestPermission, modifier = Modifier.testTag("local_music_grant"))
                        if (localMusic.permissionMessage != null) TextButton("打开系统设置", onClick = localMusic.openSettings,
                            modifier = Modifier.testTag("local_music_open_app_settings"))
                    }
                }
                state.error?.let { error -> item { Column(Modifier.fillMaxWidth().playlistPullSurface(pullMotion).padding(20.dp)) { Text(error); TextButton("重试", onClick = ::refresh) } } }
                if (room != null && localMusic == null) item { Text("点击歌曲，为当前房间点歌", Modifier.playlistPullSurface(pullMotion).padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 13.sp) }
                if (!state.loading && songs.isEmpty() && state.error == null && localMusic?.allowed != false) item {
                    Text(if (query.isNotBlank()) "没有找到匹配的歌曲" else if (localMusic != null) "未找到音乐文件，下拉重新扫描" else "这里还没有歌曲，可在搜索结果中加入歌单",
                        Modifier.fillMaxWidth().playlistPullSurface(pullMotion).padding(24.dp), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
                itemsIndexed(songs, key = { index, _ -> songKeys[index] }) { index, song ->
                    Box(Modifier.fillMaxWidth().animateItem(fadeInSpec = tween(280), placementSpec = tween(300), fadeOutSpec = tween(160))
                        .playlistPullSurface(pullMotion).background(surface).padding(horizontal = 16.dp)) {
                        PlaylistTrack(song, selected = queue.current?.id == song.id && queue.current?.musicSource == song.musicSource,
                            liked = localMusic == null && (if (neteaseMain) song.id in neteaseLikes else statuses[song.id]?.liked == true), play = { play(songs, index) },
                            next = { vm.container.playerController.add(song, true) }, enqueue = { vm.container.playerController.add(song) }) {
                            LibrarySongActions(library, song, navigate)
                            val dismiss = LocalDismissSongMenu.current
                            if (editable) MusicDestinationRow("移出歌单", {
                                dismiss(); library.action("已移出歌单") { vm.remove(song) }
                            }, enabled = !busy, chevron = false)
                        }
                    }
                }
            }
        }
    }
    SettingsUiScope {
    if (sortOpen) MusicChoiceDialog("歌曲排序", onDismiss = { sortOpen = false }) {
        listOf("默认顺序", "歌名 A–Z", "歌手 A–Z").forEachIndexed { index, label ->
            MusicDialogChoiceRow(label, order == index, { order = index; sortOpen = false },
                modifier = Modifier.testTag("playlist_order_$index"))
        }
    }
    if (menuOpen) MusicChoiceDialog("${collectionKind}操作", onDismiss = { menuOpen = false }) {
        MusicDestinationRow("CD 唱片架", {
            menuOpen = false
            scope.launch { vm.container.settings.edit { it.copy(playlistRecordShelf = true, playlistDepthRibbon = false) } }
        }, modifier = Modifier.testTag("playlist_record_shelf_menu"), chevron = false)
        MusicDestinationRow("立体缎带", {
            menuOpen = false
            scope.launch { vm.container.settings.edit { it.copy(playlistDepthRibbon = true, playlistRecordShelf = false) } }
        }, modifier = Modifier.testTag("playlist_depth_ribbon_toggle"), chevron = false)
        MusicDestinationRow("${collectionKind}信息", { menuOpen = false; infoOpen = true }, modifier = Modifier.testTag("playlist_menu_info"), chevron = false)
        MusicDestinationRow(if (localMusic != null) "重新扫描本地音乐" else "刷新歌单", { menuOpen = false; refresh() }, chevron = false)
        if (state.playlist?.editable(account.account?.id ?: 0) == true) {
            MusicDestinationRow(if (importState.running) "查看网易云导入进度" else "一键导入网易云我喜欢", {
                menuOpen = false
                if (!importState.running) vm.container.playlistLikeImporter.start(vm.id, state.title)
                importOpen = true
            }, enabled = !state.loading && state.songs.isNotEmpty(),
                modifier = Modifier.testTag("playlist_import_netease_likes"), chevron = false)
        }
        if (editable) {
            MusicDestinationRow("重命名", { menuOpen = false; onRename() }, enabled = !busy, modifier = Modifier.testTag("rename_playlist"), chevron = false)
        }
        if (removable) {
            MusicDestinationRow(if (state.playlist?.source == "netease" && !state.playlist.nativeOwned) "取消收藏" else "删除歌单",
                { menuOpen = false; onDelete() }, enabled = !busy, modifier = Modifier.testTag("delete_playlist"), chevron = false)
        }
    }
    if (infoOpen) MusicDialog("${collectionKind}信息", onDismiss = { infoOpen = false }) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(origin, fontSize = 13.sp)
        Text(playlistDurationLabel(state.songs), fontSize = 13.sp)
        Text(state.description.ifBlank { "暂无歌单介绍" }, Modifier.heightIn(max = 240.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), fontSize = 14.sp)
        MusicDialogButton("关闭", onClick = { infoOpen = false })
    }
    if (importOpen) PlaylistLikeImportDialog(importState, onClose = { importOpen = false },
        onStop = vm.container.playlistLikeImporter::stop,
        onRetry = { vm.container.playlistLikeImporter.start(importState.playlistId, importState.name) })
    }
}

internal fun playlistDurationLabel(songs: List<Song>): String {
    val count = "${songs.size} 首"
    if (songs.isEmpty() || songs.any { it.durationMs <= 0 }) return count
    val minutes = songs.sumOf { it.durationMs } / 60_000
    return "$count · " + if (minutes >= 60) "${minutes / 60}小时${minutes % 60}分钟" else if (minutes > 0) "${minutes}分钟" else "不足1分钟"
}

@Composable internal fun PlaylistTrack(song: Song, selected: Boolean, liked: Boolean, play: () -> Unit,
    next: () -> Unit, enqueue: () -> Unit, extra: @Composable () -> Unit) {
    val host = LocalSongMenu.current
    var menu by remember(song.id) { mutableStateOf<SongMenu?>(null) }
    val room = LocalRoomSongRequest.current.takeUnless { song.privateLibrary || song.musicSource == io.github.currencortex.music.data.song.MusicSource.LOCAL }
    val pending = room?.pending?.invoke(song.id) == true
    val primary = { if (room != null) room.request(song) else play() }
    Row(Modifier.fillMaxWidth().testTag("playlist_song_${song.id}").clickable(enabled = !pending, role = Role.Button, onClick = primary)
        .padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        MusicCover(song.cover, Modifier.size(48.dp), cornerRadius = 10.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(song.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (liked) Image(painterResource(R.drawable.player_symbol_favorite_fill1), "已喜欢", Modifier.size(12.dp),
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary))
                Text(if (pending) "正在提交点歌…" else listOf(song.artists, song.album).filter(String::isNotBlank).joinToString(" · "),
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
            }
            if (song.mv > 0) Text("MV", fontSize = 10.sp, color = MiuixTheme.colorScheme.primary)
        }
        PlaylistIconAction(Icons.Default.MoreVert, "歌曲操作", {
            val value = SongMenu(song, primary, next, enqueue, room != null, extra)
            if (host != null) host(value) else menu = value
        }, Modifier.testTag("song_menu_${song.id}"), ink = MiuixTheme.colorScheme.onSurface.copy(alpha = .4f),
            background = Color.Transparent, enabled = !pending)
    }
    menu?.let { SongActionsSheet(it) { menu = null } }
}

@Composable private fun PlaylistPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, emphasized: Boolean = false) {
    GlassActionButton(label, onClick, modifier, primary = emphasized, enabled = enabled)
}

@Composable private fun PlaylistIconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
    onClick: () -> Unit, modifier: Modifier = Modifier, ink: Color = Color.White,
    background: Color = Color.White.copy(alpha = .08f), enabled: Boolean = true) {
    GlassIconButton(onClick, label, modifier, enabled = enabled) {
        Icon(icon, null, Modifier.size(22.dp), tint = ink)
    }
}
