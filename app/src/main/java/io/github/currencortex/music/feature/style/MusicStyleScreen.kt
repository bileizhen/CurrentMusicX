package io.github.currencortex.music.feature.style

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.core.media.PlaybackMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.style.MusicStyle
import io.github.currencortex.music.feature.library.LibrarySongActions
import io.github.currencortex.music.feature.library.LibraryViewModel
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.util.collectAsPageState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun StyleChips(styles: List<MusicStyle>, navigate: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        styles.forEach { style -> key(style.tagId) {
            TextButton(style.tagName, onClick = { navigate("lib/style/${style.tagId}") },
                modifier = Modifier.testTag("style_category_${style.tagId}"))
        } }
    }
}

@Composable fun MusicStyleCategories(state: StyleCatalogState, retry: () -> Unit, navigate: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("style_categories"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("曲风分类", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        if (state.loading && state.styles.isEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { MusicPlaceholder(Modifier.weight(1f).height(42.dp)) }
        }
        if (state.styles.isNotEmpty()) StyleChips(state.styles, navigate)
        state.error?.let {
            Text(it, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
            TextButton("重试曲风分类", onClick = retry, enabled = !state.loading, modifier = Modifier.testTag("style_catalog_retry"))
        }
        if (!state.loading && state.styles.isEmpty() && state.error == null) Text("暂无曲风分类")
    }
}

@Composable fun MusicStyleScreen(vm: MusicStyleViewModel, library: LibraryViewModel, onBack: () -> Unit,
    navigate: (String) -> Unit, play: (List<Song>, Int) -> Unit) {
    val state by vm.state.collectAsPageState()
    val playback by vm.container.playerController.state.collectAsPageState()
    val room = LocalRoomSongRequest.current
    var allChildren by rememberSaveable(vm.id) { mutableStateOf(false) }
    var fullDescription by rememberSaveable(vm.id) { mutableStateOf(false) }
    val children = state.style?.childrenTags.orEmpty()
    LaunchedEffect(state.songs) { library.refreshStatuses(state.songs) }
    PreloadMusicCovers(state.songs.take(12).map(Song::cover))
    MusicPullToRefresh(state.loading, { vm.reload(fresh = true) }, Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        LazyColumn(Modifier.fillMaxSize().testTag("style_detail"), contentPadding = musicScrollPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                TextButton("返回", onClick = onBack)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    state.description?.cover?.firstOrNull()?.let { MusicCover(it.replace("http:", "https:"), Modifier.size(72.dp), 240) }
                    Column(Modifier.weight(1f)) {
                        Text(state.title, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                        val english = state.description?.enName ?: state.style?.enName.orEmpty()
                        if (english.isNotBlank()) Text(english, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
                    }
                }
            }
            state.description?.desc?.takeIf(String::isNotBlank)?.let { description -> item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(description, fontSize = 14.sp, maxLines = if (fullDescription) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .65f))
                    MusicTextAction(if (fullDescription) "收起介绍" else "展开介绍", { fullDescription = !fullDescription })
                }
            } }
            state.detailError?.let { error -> item {
                Text("曲风介绍：$error", fontSize = 13.sp)
                MusicTextAction("重试介绍", { vm.reload(fresh = true) })
            } }
            if (children.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("细分曲风", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    StyleChips(if (allChildren) children else children.take(3), navigate)
                    if (children.size > 3) MusicTextAction(if (allChildren) "收起分类" else "全部 ${children.size} 种子分类",
                        { allChildren = !allChildren })
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.total > 0) "歌曲 · ${state.total} 首" else "歌曲", Modifier.weight(1f), fontSize = 20.sp)
                    TextButton(if (state.sort == 0) "✓ 热门" else "热门", onClick = { vm.sort(0) }, modifier = Modifier.testTag("style_sort_hot"))
                    TextButton(if (state.sort == 1) "✓ 最新" else "最新", onClick = { vm.sort(1) }, modifier = Modifier.testTag("style_sort_new"))
                }
            }
            if (state.songs.isNotEmpty()) item {
                TextButton("播放全部", onClick = {
                    vm.container.playerController.setMode(PlaybackMode.LIST); play(state.songs, 0)
                }, enabled = room == null && playback.mode == PlayerMode.LOCAL, modifier = Modifier.testTag("style_play_all"))
            }
            if (room != null) item { Text("点击歌曲，为当前房间点歌", fontSize = 13.sp) }
            if (state.loading && state.songs.isEmpty()) item { LoadingSongList(5) }
            state.error?.let { error -> item {
                Text(error, fontSize = 14.sp)
                TextButton("重试歌曲", onClick = { vm.reload(more = state.songs.isNotEmpty() && state.more, fresh = true) },
                    enabled = !state.loading, modifier = Modifier.testTag("style_songs_retry"))
            } }
            if (!state.loading && state.songs.isEmpty() && state.error == null) item { Text("这个曲风暂时没有歌曲") }
            itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                Box(Modifier.testTag("style_song_${song.id}")) {
                    SongRow(SongRowUi(song), { play(state.songs, index) }, { vm.container.playerController.add(song, true) },
                        { vm.container.playerController.add(song) }) { LibrarySongActions(library, song, navigate) }
                }
            }
            if (state.loading && state.songs.isNotEmpty()) item { LoadingSongList(2) }
            if (state.more && state.error == null) item {
                TextButton("加载更多歌曲", onClick = { vm.reload(more = true) }, enabled = !state.loading,
                    modifier = Modifier.testTag("style_load_more"))
            }
        }
    }
}
