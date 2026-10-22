package io.github.currencortex.music.feature.search

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.currencortex.music.core.media.PlayerController
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun SearchScreen(vm: SearchViewModel, player: PlayerController,
    actions: (@Composable (Song) -> Unit)? = null, roomName: String? = null, onBack: (() -> Unit)? = null,
    navigate: (String) -> Unit = {}, sheet: SearchSheetState? = null, autoFocus: Boolean = false, onFocused: () -> Unit = {},
    recommendations: List<Song> = emptyList(), recent: List<Song> = emptyList(),
    quickActions: (@Composable (Song) -> Unit)? = null, onResults: (List<Song>) -> Unit = {},
    onPlayAll: ((List<Song>) -> Unit)? = null,
    onPlay: (Song) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val landing by vm.landing.collectAsStateWithLifecycle()
    val charts by vm.charts.collectAsStateWithLifecycle()
    val playing by remember(player) { player.state.map { it.song?.id to it.playing }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(initialValue = null to false)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    val bottomInset = LocalMusicBottomInset.current
    val categories = if (roomName != null) listOf(SearchCategory.SONG, SearchCategory.LYRIC) else SearchCategory.entries
    val category = state.category.takeIf { it in categories } ?: SearchCategory.SONG
    val page = state.pages[category] ?: SearchCategoryPage()
    val lyricScroll = rememberLazyListState()
    val songScroll = rememberLazyListState()
    val artistScroll = rememberLazyListState()
    val albumScroll = rememberLazyListState()
    val playlistScroll = rememberLazyListState()
    val scroll = when (category) {
        SearchCategory.LYRIC -> lyricScroll
        SearchCategory.SONG -> songScroll
        SearchCategory.ARTIST -> artistScroll
        SearchCategory.ALBUM -> albumScroll
        SearchCategory.PLAYLIST -> playlistScroll
    }
    var inputFocused by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(roomName != null) { if (roomName != null && state.category !in categories) vm.select(SearchCategory.SONG) else if (roomName == null) vm.loadLanding() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) { withFrameNanos { }; requester.requestFocus(); keyboard?.show(); onFocused() }
    }
    LaunchedEffect(state.submitted) { lyricScroll.scrollToItem(0); songScroll.scrollToItem(0); artistScroll.scrollToItem(0); albumScroll.scrollToItem(0); playlistScroll.scrollToItem(0) }
    PreloadMusicCovers((page.songs.take(8).map { it.cover } + page.entries.take(8).map { it.cover }).distinct())
    LaunchedEffect(page.songs) { if (page.songs.isNotEmpty()) onResults(page.songs) }
    fun submit(keyword: String = state.query) { focus.clearFocus(); keyboard?.hide(); vm.search(keyword) }
    fun back() { focus.clearFocus(); keyboard?.hide(); onBack?.invoke() }
    val colors = MiuixTheme.colorScheme
    val showSuggestions = inputFocused && state.query.isNotBlank() && state.query.trim() != state.submitted && suggestions.query == state.query.trim()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = with(LocalDensity.current) { maxHeight.toPx() }
        Column(Modifier.fillMaxSize().then(if (sheet != null) Modifier.statusBarsPadding().imePadding() else Modifier)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = if (sheet == null) 12.dp else 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null && sheet == null) Box(Modifier.size(40.dp).graphicsLayer { alpha = sheet?.progress ?: 1f }
                    .clickable(role = Role.Button) { back() }.testTag(if (roomName != null) "room_search_back" else "search_back"), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", Modifier.size(24.dp), tint = colors.onSurface)
                }
                if (sheet == null) Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f).height(48.dp).onGloballyPositioned { target = it.boundsInRoot() }) {
                    MusicSearchBar(state.query, { if (state.query.isBlank()) { requester.requestFocus(); keyboard?.show() } else submit() },
                        Modifier.fillMaxSize().graphicsLayer {
                            val source = sheet?.source; val end = target; val p = sheet?.progress ?: 1f
                            if (source != null && end != null && end.width > 0 && end.height > 0) {
                                translationX = (source.left - end.left) * (1f - p)
                                translationY = (source.top - end.top) * (1f - p)
                            }
                        }.testTag("search_header_field"), onInput = vm::input, requester = requester, onInputFocus = { inputFocused = it })
                }
            }
            MusicCategoryTabs(categories.map { it.route to it.label }, category.route,
                { key -> focus.clearFocus(); keyboard?.hide(); vm.select(SearchCategory.entries.first { it.route == key }) }, "search_category",
                Modifier.padding(horizontal = 20.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!showSuggestions) LazyColumn(Modifier.fillMaxSize().testTag("search_screen").graphicsLayer {
                val p = sheet?.progress ?: 1f
                translationY = height * (1f - p); alpha = (p / .65f).coerceIn(0f, 1f)
            }, state = scroll, contentPadding = PaddingValues(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 20.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(if (state.searched) 2.dp else 16.dp)) {
                if (roomName != null) item { Text("$roomName · 点击歌曲提交点歌", fontSize = 13.sp) }
                if (!state.searched) item {
                    SearchLandingContent(history, landing, charts, recommendations, recent,
                        onSearch = ::submit, onClearHistory = { vm.clearHistory() }, onDeleteHistory = { vm.deleteHistory(it) },
                        onRetryHot = { vm.loadLanding(true) }, onRetryCharts = { vm.loadCharts(true) },
                        onLoadChart = { id, retry -> vm.loadChart(id, retry) },
                        roomSearch = roomName != null)
                }
        page.error?.let { error -> item {
            Text(error)
            MusicTextAction("重试", vm::retry, Modifier.testTag("search_retry"), enabled = !page.loading)
        } }
        if (page.loading && page.count == 0) item { LoadingSongList(4) }
        if (state.searched && page.loaded) item {
            val count = when (category) {
                SearchCategory.SONG -> "${page.total} 首歌曲"
                SearchCategory.LYRIC -> "${page.total} 首歌词匹配歌曲"
                SearchCategory.ARTIST -> "${page.total} 位作者"
                SearchCategory.ALBUM -> "${page.total} 张专辑"
                SearchCategory.PLAYLIST -> "${page.total} 个歌单"
            }
            if (roomName == null && page.songs.isNotEmpty() && onPlayAll != null)
                SearchResultsToolbar(page.songs.size, page.total, { onPlayAll(page.songs) }, { page.songs.forEach { player.add(it) } })
            else Text("找到 $count", Modifier.padding(vertical = 12.dp), fontSize = 13.sp, color = colors.onSurface.copy(alpha = .45f))
        }
        if (state.searched && page.loaded && !page.loading && page.error == null && page.count == 0) item {
            Text("没有找到${category.label}，试试其他关键词", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
        }
        itemsIndexed(page.entries, key = { _, entry -> "${category.route}-${entry.id}" }) { _, entry ->
            Row(Modifier.fillMaxWidth().clickable(enabled = entry.id > 0, role = Role.Button) {
                navigate("lib/${if (category == SearchCategory.PLAYLIST) "ncmplaylist" else category.route}/${entry.id}")
            }.padding(vertical = 8.dp).testTag("search_${category.route}_${entry.id}"), verticalAlignment = Alignment.CenterVertically) {
                MusicCover(entry.cover, Modifier.size(52.dp), 160, cornerRadius = if (category == SearchCategory.ARTIST) 26.dp else 14.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(searchMatchText(entry.name, state.submitted, colors.primary), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(entry.subtitle.ifBlank { when (category) {
                        SearchCategory.ARTIST -> "艺人"
                        SearchCategory.PLAYLIST -> "网易云歌单"
                        else -> "专辑"
                    } }, fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("›", fontSize = 22.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .35f))
            }
        }
        itemsIndexed(page.songs, key = { _, song -> song.id }) { _, song ->
            Column(Modifier.testTag("search_song_${song.id}")) {
                SearchResultSongRow(song, state.submitted, playing.first == song.id && playing.second,
                    { onPlay(song) }, { player.add(song, true) }, { player.add(song) },
                    lyric = page.matchedLyrics[song.id].takeIf { category == SearchCategory.LYRIC },
                    quickActions = { quickActions?.invoke(song) },
                    extraActions = { actions?.invoke(song) })
            }
        }
        if (page.loading && page.count > 0) item { LoadingSongList(2) }
        if (page.more && page.error == null) item {
            MusicTextAction("加载更多", { vm.search(more = true) }, Modifier.fillMaxWidth().testTag("search_load_more"), enabled = !page.loading)
        }
            }
                if (showSuggestions) SearchSuggestionsContent(state.query.trim(), suggestions, ::submit,
                    Modifier.fillMaxSize().background(colors.background).graphicsLayer {
                        val p = sheet?.progress ?: 1f
                        translationY = height * (1f - p); alpha = (p / .65f).coerceIn(0f, 1f)
                    })

            }
        }
    }
}
