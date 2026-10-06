package io.github.currencortex.music.feature.search

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.PlayerController
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SearchScreen(vm: SearchViewModel, player: PlayerController,
    actions: (@Composable (Song) -> Unit)? = null, roomName: String? = null, onBack: (() -> Unit)? = null,
    navigate: (String) -> Unit = {}, sheet: SearchSheetState? = null, autoFocus: Boolean = false, onFocused: () -> Unit = {},
    recommendations: List<Song> = emptyList(), recent: List<Song> = emptyList(),
    onPlay: (List<Song>, Int) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val landing by vm.landing.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    val bottomInset = LocalMusicBottomInset.current
    val category = if (roomName != null) SearchCategory.SONG else state.category
    val page = state.pages[category] ?: SearchCategoryPage()
    val songScroll = rememberLazyListState()
    val artistScroll = rememberLazyListState()
    val albumScroll = rememberLazyListState()
    val scroll = when (category) {
        SearchCategory.SONG -> songScroll
        SearchCategory.ARTIST -> artistScroll
        SearchCategory.ALBUM -> albumScroll
    }
    var target by remember { mutableStateOf<Rect?>(null) }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var recommendationOffset by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(roomName != null) { if (roomName != null) vm.select(SearchCategory.SONG) else vm.loadLanding() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) { withFrameNanos { }; requester.requestFocus(); keyboard?.show(); onFocused() }
    }
    LaunchedEffect(state.submitted) { songScroll.scrollToItem(0); artistScroll.scrollToItem(0); albumScroll.scrollToItem(0) }
    PreloadMusicCovers(page.songs.take(12).map { it.cover } + page.entries.take(8).map { it.cover })
    fun submit(keyword: String = state.query) { focus.clearFocus(); keyboard?.hide(); vm.search(keyword) }
    fun back() { focus.clearFocus(); keyboard?.hide(); onBack?.invoke() }
    val colors = MiuixTheme.colorScheme
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
                        }.testTag("search_header_field"), onInput = vm::input, requester = requester)
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("search_screen").graphicsLayer {
                val p = sheet?.progress ?: 1f
                translationY = height * (1f - p); alpha = (p / .65f).coerceIn(0f, 1f)
            }, state = scroll, contentPadding = PaddingValues(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 20.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (roomName != null) item { Text("$roomName · 点击歌曲提交点歌", fontSize = 13.sp) }
                if (roomName == null && state.searched) item {
                    MusicCategoryTabs(SearchCategory.entries.map { it.route to it.label }, category.route,
                        { key -> vm.select(SearchCategory.entries.first { it.route == key }) }, "search_category")
                }
                if (!state.searched) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("搜索历史", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Box(Modifier.size(40.dp).clickable(enabled = history.isNotEmpty()) { vm.clearHistory() }.testTag("search_clear_history"), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Delete, "清空搜索历史", Modifier.size(20.dp), tint = colors.onSurface.copy(alpha = .4f))
                            }
                        }
                        if (history.isEmpty()) Text("搜索过的音乐会留在这里", fontSize = 13.sp, color = colors.onSurface.copy(alpha = .4f), modifier = Modifier.padding(vertical = 12.dp))
                        else FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            (if (historyExpanded) history else history.take(4)).forEach { h ->
                                Box(Modifier.background(colors.onSurface.copy(alpha = .045f), RoundedCornerShape(20.dp))
                                    .combinedClickable(onClick = { submit(h.keyword) }, onLongClick = { vm.deleteHistory(h.keyword) }, onLongClickLabel = "删除这条搜索记录", role = Role.Button).padding(horizontal = 14.dp, vertical = 9.dp)) {
                                    Text(h.keyword, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (history.size > 4) MusicTextAction(if (historyExpanded) "收起" else "展开", { historyExpanded = !historyExpanded })
                        }
                    }
                    if (roomName == null && recommendations.isNotEmpty()) item {
                        val unique = recommendations.distinctBy { it.name }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("猜你喜欢", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Box(Modifier.size(40.dp).clickable { recommendationOffset = (recommendationOffset + 6) % unique.size }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Refresh, "换一批", Modifier.size(20.dp), tint = colors.onSurface.copy(alpha = .4f))
                            }
                        }
                        val words = (unique.drop(recommendationOffset) + unique.take(recommendationOffset)).take(6)
                        words.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth()) { pair.forEachIndexed { index, song ->
                            Text(song.name, Modifier.weight(1f).clickable(role = Role.Button) { submit(song.name) }.padding(vertical = 10.dp, horizontal = 2.dp),
                                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (index == 0 && song == words.first()) colors.primary else colors.onSurface.copy(alpha = .75f))
                        }; if (pair.size == 1) Spacer(Modifier.weight(1f)) } }
                    }
                    if (roomName == null) item {
                        LazyRow(Modifier.fillMaxWidth().testTag("search_rankings"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                Card(Modifier.width(280.dp)) { Column(Modifier.padding(18.dp)) {
                                    Text("热搜榜", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(16.dp))
                                    if (landing.loading && landing.words.isEmpty()) LoadingSongList(3)
                                    else if (landing.error != null) {
                                        Text("热搜暂时不可用", fontSize = 13.sp, color = colors.onSurface.copy(alpha = .5f))
                                        MusicTextAction("重新加载", { vm.loadLanding(true) }, Modifier.testTag("search_hot_retry"))
                                    } else if (landing.words.isEmpty()) Text("暂无热搜", fontSize = 13.sp, color = colors.onSurface.copy(alpha = .5f))
                                    landing.words.take(10).forEachIndexed { index, word ->
                                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { submit(word.text) }.padding(vertical = 12.dp)
                                            .testTag("search_hot_$index"), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${index + 1}", Modifier.width(30.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                                color = if (index < 3) colors.primary else colors.onSurface.copy(alpha = .4f))
                                            Column(Modifier.weight(1f)) {
                                                Text(word.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                if (word.description.isNotBlank()) Text(word.description, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                    color = colors.onSurface.copy(alpha = .4f))
                                            }
                                        }
                                    }
                                } }
                            }
                            if (recent.isNotEmpty()) item {
                                Card(Modifier.width(280.dp)) { Column(Modifier.padding(18.dp)) {
                                    Text("最近播放", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(16.dp))
                                    recent.take(10).forEach { song -> Row(Modifier.fillMaxWidth().clickable { submit(song.name) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        MusicCover(song.cover, Modifier.size(36.dp), 160)
                                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                            Text(song.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song.artists, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurface.copy(alpha = .4f))
                                        }
                                    } }
                                } }
                            }
                        }
                    }
                }
        page.error?.let { error -> item {
            Text(error)
            MusicTextAction("重试", vm::retry, Modifier.testTag("search_retry"), enabled = !page.loading)
        } }
        if (page.loading && page.count == 0) item { LoadingSongList(4) }
        if (state.searched && page.loaded) item {
            val count = when (category) {
                SearchCategory.SONG -> "${page.total} 首歌曲"
                SearchCategory.ARTIST -> "${page.total} 位作者"
                SearchCategory.ALBUM -> "${page.total} 张专辑"
            }
            Text("找到 $count", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
        }
        if (state.searched && page.loaded && !page.loading && page.error == null && page.count == 0) item {
            Text("没有找到${category.label}，试试其他关键词", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
        }
        itemsIndexed(page.entries, key = { _, entry -> "${category.route}-${entry.id}" }) { _, entry ->
            Row(Modifier.fillMaxWidth().clickable(enabled = entry.id > 0, role = Role.Button) {
                navigate("lib/${category.route}/${entry.id}")
            }.padding(vertical = 8.dp).testTag("search_${category.route}_${entry.id}"), verticalAlignment = Alignment.CenterVertically) {
                MusicCover(entry.cover, Modifier.size(64.dp), 240, cornerRadius = if (category == SearchCategory.ARTIST) 32.dp else 18.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(entry.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(entry.subtitle.ifBlank { if (category == SearchCategory.ARTIST) "艺人" else "专辑" }, fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("›", fontSize = 22.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .35f))
            }
        }
        itemsIndexed(page.songs, key = { _, song -> song.id }) { index, song ->
            Box(Modifier.testTag("search_song_${song.id}")) {
                SongRow(SongRowUi(song), { onPlay(page.songs, index) }, { player.add(song, true) }, { player.add(song) }) {
                    actions?.invoke(song)
                }
            }
        }
        if (page.loading && page.count > 0) item { LoadingSongList(2) }
        if (page.more && page.error == null) item {
            MusicTextAction("加载更多", { vm.search(more = true) }, Modifier.fillMaxWidth().testTag("search_load_more"), enabled = !page.loading)
        }
            }
        }
    }
}
