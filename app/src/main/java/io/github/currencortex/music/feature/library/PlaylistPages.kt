package io.github.currencortex.music.feature.library

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Cover-first library entrance; the existing create dialog and server ownership stay in the host. */
@Composable internal fun PlaylistIndexBody(vm: LibraryViewModel,
    navigate: (String) -> Unit, onCreate: () -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val neteaseMain by vm.container.primaryLibrary.usesNetease.collectAsStateWithLifecycle()
    val nativeAccount by vm.container.neteaseSessions.state.collectAsStateWithLifecycle()
    MusicPullToRefresh(home.loading, vm::refresh, Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), showIndicator = false) {
        LazyVerticalGrid(GridCells.Adaptive(148.dp), Modifier.fillMaxSize().testTag("playlist_index"),
            contentPadding = musicScrollPadding(PaddingValues(20.dp, 8.dp, 20.dp, 24.dp)),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("我的歌单", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                            Text("${home.playlists.size} 个歌单 · 你的音乐收藏", Modifier.padding(top = 6.dp),
                                fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                        }
                        if ((!neteaseMain && account.account != null) || (neteaseMain && vm.container.nativeNetease != null && nativeAccount.loggedIn && !nativeAccount.stale)) PlaylistIconAction(Icons.Default.Add, "新建歌单", onCreate,
                            Modifier.testTag("create_playlist"), ink = MiuixTheme.colorScheme.primary,
                            background = MiuixTheme.colorScheme.primary.copy(alpha = .1f))
                    }
                }
            }
            home.errors["我的歌单"]?.let { error -> item(span = { GridItemSpan(maxLineSpan) }) {
                Column { Text(error); TextButton("重试", onClick = vm::refresh) }
            } }
            if ((home.loading || account.loading) && home.playlists.isEmpty()) items(4) {
                Column { MusicPlaceholder(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)))
                    MusicPlaceholder(Modifier.padding(top = 10.dp).fillMaxWidth(.7f).height(18.dp)) }
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
            items(home.playlists, key = { it.id }) { playlist ->
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

@Composable internal fun PlaylistDetailBody(state: LibraryDetailState, vm: LibraryDetailViewModel,
    library: LibraryViewModel, onBack: () -> Unit, navigate: (String) -> Unit,
    play: (List<Song>, Int) -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val statuses by library.statuses.collectAsStateWithLifecycle()
    val neteaseMain by vm.container.primaryLibrary.usesNetease.collectAsStateWithLifecycle()
    val neteaseLikes by vm.container.neteaseSongActions.likedState.collectAsStateWithLifecycle()
    val playback by vm.container.playerController.state.collectAsStateWithLifecycle()
    val queue by vm.container.playbackQueue.state.collectAsStateWithLifecycle()
    val busy by library.busy.collectAsStateWithLifecycle()
    val room = LocalRoomSongRequest.current
    val editable = vm.editable(state.playlist, account.account?.id ?: 0)
    val cover = state.cover.ifBlank { state.songs.firstOrNull { it.cover.isNotBlank() }?.cover.orEmpty() }
    val tint = rememberStyleTint(cover)
    val backdrop by animateColorAsState(lerp(Color(0xFF222831), tint.value ?: Color(0xFF69798A), .30f), tween(280), label = "playlist cover tint")
    val surface = MiuixTheme.colorScheme.background
    val headerBottom = lerp(backdrop, Color.White, .04f)
    val list = rememberLazyListState()
    val panelDensity = LocalDensity.current
    val pullMotion = rememberPlaylistPullMotion(state.loading || state.refreshing) { vm.reload(force = true) }
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable(vm.route) { mutableStateOf(false) }
    var query by rememberSaveable(vm.route) { mutableStateOf("") }
    var order by rememberSaveable(vm.route) { mutableIntStateOf(0) }
    var sortOpen by rememberSaveable(vm.route) { mutableStateOf(false) }
    var menuOpen by rememberSaveable(vm.route) { mutableStateOf(false) }
    var infoOpen by rememberSaveable(vm.route) { mutableStateOf(false) }
    val songs = remember(state.songs, query, order) {
        val needle = query.trim()
        val filtered = state.songs.filter { needle.isBlank() || listOf(it.name, it.artists, it.album).any { value -> value.contains(needle, ignoreCase = true) } }
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
    val title = state.title.ifBlank { if (state.loading) "正在加载歌单" else "歌单" }
    val origin = when {
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
    val heroDescription = state.description.trim().takeUnless {
        it == "来自网易云音乐" || it == heroOrigin
    }.orEmpty()
    fun start(shuffle: Boolean) {
        if (songs.isEmpty() || !canPlay) return
        vm.container.playerController.setMode(if (shuffle) PlaybackMode.SHUFFLE else PlaybackMode.LIST)
        play(songs, if (shuffle) songs.indices.random() else 0)
    }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun closeSearch() { query = ""; searching = false; focus.clearFocus(); keyboard?.hide() }
    fun share() {
        val text = buildString {
            appendLine("《$title》 · ${state.songs.size} 首歌曲")
            state.songs.take(12).forEach { appendLine("${it.name}${it.artists.takeIf(String::isNotBlank)?.let { artist -> " — $artist" }.orEmpty()}") }
            append("来自 CurrentMusic")
        }
        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text), "分享歌单")) }.onFailure { library.message.value = "暂时无法打开分享" }
    }
    Column(Modifier.fillMaxSize().background(backdrop).statusBarsPadding().navigationBarsPadding().testTag("library_detail")) {
        Row(Modifier.fillMaxWidth().background(backdrop).height(52.dp).padding(horizontal = 12.dp).testTag("playlist_top_bar"), verticalAlignment = Alignment.CenterVertically) {
            PlaylistIconAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack, Modifier.testTag("navigate_back"), background = Color.Transparent)
            val compact by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
            Text(if (compact) title else "歌单", Modifier.weight(1f).padding(horizontal = 12.dp), fontSize = 17.sp,
                fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PlaylistIconAction(Icons.Default.Search, "搜索歌单内歌曲", {
                if (searching) closeSearch() else searching = true
                scope.launch { list.animateScrollToItem(1) }
            }, Modifier.testTag("playlist_search_toggle"), background = Color.Transparent)
            PlaylistIconAction(Icons.Default.MoreVert, "歌单操作", { menuOpen = true }, Modifier.testTag("playlist_more"), background = Color.Transparent)
        }
        Box(Modifier.weight(1f).background(backdrop).testTag("playlist_refresh")) {
            LazyColumn(Modifier.fillMaxSize().clipToBounds().nestedScroll(pullMotion).drawBehind {
                // Paint the moving body from below its rounded toolbar, rather than
                // stretching a full white list background above the fixed artwork.
                val toolbar = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "playback" }
                val panelTop = toolbar?.offset?.toFloat() ?: size.height
                if (panelTop < size.height) drawRect(headerBottom, topLeft = Offset(0f, panelTop.coerceAtLeast(0f)),
                    size = Size(size.width, size.height - panelTop.coerceAtLeast(0f)))
                val bodyTop = panelTop + playlistPanelCornerPx(list, pullMotion.offset, panelDensity) + pullMotion.offset
                if (bodyTop < size.height) drawRect(surface, topLeft = Offset(0f, bodyTop.coerceAtLeast(0f)),
                    size = Size(size.width, size.height - bodyTop.coerceAtLeast(0f)))
            }.testTag("playlist_tracks"), state = list, overscrollEffect = null,
                contentPadding = PaddingValues(bottom = musicScrollPadding(PaddingValues(bottom = 24.dp)).calculateBottomPadding())) {
                item(key = "hero") {
                    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(backdrop, headerBottom)))
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp).testTag("playlist_hero")) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            StyleArtwork(cover, Modifier.size(112.dp).clip(RoundedCornerShape(16.dp)).testTag("playlist_cover"), tint,
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
                            PlaylistPill("分享", Icons.Default.Share, ::share, Modifier.weight(1f).testTag("playlist_share"), enabled = state.title.isNotBlank())
                            PlaylistPill("随机播放", Icons.Default.PlayArrow, { start(true) }, Modifier.weight(1f).testTag("playlist_shuffle"), enabled = canPlay && songs.isNotEmpty(), emphasized = true)
                            PlaylistPill("歌单信息", Icons.Default.Info, { infoOpen = true }, Modifier.weight(1f).testTag("playlist_info"))
                        }
                    }
                }
                stickyHeader(key = "playback") {
                    Box(Modifier.fillMaxWidth().graphicsLayer {
                        translationY = pullMotion.offset
                        val corner = (playlistPanelCornerPx(list, pullMotion.offset, panelDensity) / panelDensity.density).dp
                        shape = RoundedCornerShape(topStart = corner, topEnd = corner)
                        clip = true
                    }.testTag("playlist_playback_panel").semantics {
                        this[PlaylistPanelCornerRadius] = playlistPanelCornerPx(list, pullMotion.offset, panelDensity)
                    }) {
                    Column(Modifier.fillMaxWidth().background(surface)) {
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
                        if (searching) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val inputFocus = remember { FocusRequester() }
                            LaunchedEffect(Unit) { inputFocus.requestFocus() }
                            TextField(query, { query = it }, singleLine = true, label = "搜索歌名、歌手或专辑",
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.weight(1f).focusRequester(inputFocus).testTag("playlist_query"))
                            PlaylistIconAction(Icons.Default.Close, "关闭歌单搜索", ::closeSearch, ink = MiuixTheme.colorScheme.onSurface, background = Color.Transparent)
                        }
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
                state.error?.let { error -> item { Column(Modifier.fillMaxWidth().playlistPullSurface(pullMotion).padding(20.dp)) { Text(error); TextButton("重试", onClick = { vm.reload(force = true) }) } } }
                if (room != null) item { Text("点击歌曲，为当前房间点歌", Modifier.playlistPullSurface(pullMotion).padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 13.sp) }
                if (!state.loading && songs.isEmpty() && state.error == null) item {
                    Text(if (query.isNotBlank()) "没有找到匹配的歌曲" else "这里还没有歌曲，可在搜索结果中加入歌单",
                        Modifier.fillMaxWidth().playlistPullSurface(pullMotion).padding(24.dp), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
                itemsIndexed(songs, key = { index, _ -> songKeys[index] }) { index, song ->
                    Box(Modifier.fillMaxWidth().animateItem(fadeInSpec = tween(280), placementSpec = tween(300), fadeOutSpec = tween(160))
                        .playlistPullSurface(pullMotion).background(surface).padding(horizontal = 16.dp)) {
                        PlaylistTrack(song, selected = queue.current?.id == song.id && queue.current?.musicSource == song.musicSource,
                            liked = if (neteaseMain) song.id in neteaseLikes else statuses[song.id]?.liked == true, play = { play(songs, index) },
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
    if (sortOpen) MusicDialog("歌曲排序", onDismiss = { sortOpen = false }) {
        listOf("默认顺序", "歌名 A–Z", "歌手 A–Z").forEachIndexed { index, label ->
            MusicDestinationRow(label, { order = index; sortOpen = false }, summary = if (order == index) "当前排序" else null,
                modifier = Modifier.testTag("playlist_order_$index"), chevron = false)
        }
    }
    if (menuOpen) MusicDialog("歌单操作", onDismiss = { menuOpen = false }) {
        MusicDestinationRow("歌单信息", { menuOpen = false; infoOpen = true }, modifier = Modifier.testTag("playlist_menu_info"), chevron = false)
        MusicDestinationRow("刷新歌单", { menuOpen = false; vm.reload(force = true) }, chevron = false)
        if (editable) {
            MusicDestinationRow("重命名", { menuOpen = false; onRename() }, enabled = !busy, modifier = Modifier.testTag("rename_playlist"), chevron = false)
            MusicDestinationRow("删除歌单", { menuOpen = false; onDelete() }, enabled = !busy, modifier = Modifier.testTag("delete_playlist"), chevron = false)
        }
    }
    if (infoOpen) MusicDialog("歌单信息", onDismiss = { infoOpen = false }) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(origin, fontSize = 13.sp)
        Text(playlistDurationLabel(state.songs), fontSize = 13.sp)
        Text(state.description.ifBlank { "暂无歌单介绍" }, Modifier.heightIn(max = 240.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), fontSize = 14.sp)
        TextButton("关闭", onClick = { infoOpen = false })
    }
}

internal fun playlistDurationLabel(songs: List<Song>): String {
    val count = "${songs.size} 首"
    if (songs.isEmpty() || songs.any { it.durationMs <= 0 }) return count
    val minutes = songs.sumOf { it.durationMs } / 60_000
    return "$count · " + if (minutes >= 60) "${minutes / 60}小时${minutes % 60}分钟" else if (minutes > 0) "${minutes}分钟" else "不足1分钟"
}

@Composable private fun PlaylistTrack(song: Song, selected: Boolean, liked: Boolean, play: () -> Unit,
    next: () -> Unit, enqueue: () -> Unit, extra: @Composable () -> Unit) {
    val host = LocalSongMenu.current
    var menu by remember(song.id) { mutableStateOf<SongMenu?>(null) }
    val room = LocalRoomSongRequest.current
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
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, tween(130), label = "playlist pill press")
    Box(modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else .38f }
        .clickable(enabled = enabled, role = Role.Button, interactionSource = interactions, indication = null, onClick = onClick)
        .semantics { if (!enabled) disabled() }, contentAlignment = Alignment.Center) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = if (emphasized) .17f else .10f))
        .heightIn(min = 40.dp).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
        Text(label, fontSize = 12.sp, color = Color.White, maxLines = 1)
    }
    }
}

@Composable private fun PlaylistIconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
    onClick: () -> Unit, modifier: Modifier = Modifier, ink: Color = Color.White,
    background: Color = Color.White.copy(alpha = .08f), enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(48.dp).clip(CircleShape).background(background)
        .semantics { contentDescription = label }) { Icon(icon, null, Modifier.size(22.dp), tint = ink) }
}
