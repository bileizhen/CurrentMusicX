package io.github.currencortex.music.feature.style

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Quiet actions use text and a press response, without another filled button surface. */
@Composable private fun StyleAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, active: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, tween(180), label = "style action")
    val colors = MiuixTheme.colorScheme
    Column(modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .semantics { selected = active }.clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(label, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = (if (active) colors.primary else colors.onSurface).copy(alpha = if (!enabled) .35f else if (active) 1f else .65f))
        Spacer(Modifier.height(5.dp))
        Box(Modifier.width(14.dp).height(2.dp).background(if (active) colors.primary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(1.dp)))
    }
}

@Composable private fun StyleCard(style: MusicStyle, cover: String?, modifier: Modifier, navigate: (String) -> Unit) {
    val tint = rememberStyleTint(cover.orEmpty())
    val surface = styleSurface(tint.value)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(180), label = "style card")
    Box(modifier.height(112.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(24.dp))
        .background(surface).clickable(source, indication = null, role = Role.Button) { navigate("lib/style/${style.tagId}") }
        .testTag("style_category_${style.tagId}")) {
        StyleArtwork(cover.orEmpty(), Modifier.align(Alignment.CenterEnd).offset(x = 20.dp, y = 8.dp).size(94.dp)
            .graphicsLayer { rotationZ = 12f }.shadow(6.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)), tint, 280, pending = cover == null)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(surface, surface.copy(alpha = .94f), surface.copy(alpha = 0f)))))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp, end = 54.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(style.tagName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (style.enName.isNotBlank()) Text(style.enName, fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable fun MusicStyleCategories(state: StyleCatalogState, retry: () -> Unit, navigate: (String) -> Unit,
    loadCover: (Long) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("style_categories"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("曲风分类", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (state.styles.size > 6) StyleAction(if (expanded) "收起" else "全部 ${state.styles.size}",
                { expanded = !expanded }, Modifier.testTag("style_catalog_expand"))
        }
        if (state.loading && state.styles.isEmpty()) repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { MusicPlaceholder(Modifier.weight(1f).height(112.dp)) }
            }
        }
        (if (expanded) state.styles else state.styles.take(6)).chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { style -> key(style.tagId) {
                    val cover = state.covers[style.tagId]
                    LaunchedEffect(style.tagId, cover == null, state.loading) {
                        if (cover == null && !state.loading) loadCover(style.tagId)
                    }
                    StyleCard(style, cover, Modifier.weight(1f), navigate)
                } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        state.error?.let {
            Text(it, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
            StyleAction("重试曲风分类", retry, Modifier.testTag("style_catalog_retry"), enabled = !state.loading)
        }
        if (!state.loading && state.styles.isEmpty() && state.error == null) Text("暂无曲风分类")
    }
}

@Composable private fun StyleHero(state: StyleDetailState) {
    val tint = rememberStyleTint(state.heroCover)
    val surface = styleSurface(tint.value)
    val english = state.description?.enName ?: state.style?.enName.orEmpty()
    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(28.dp)).background(surface).testTag("style_hero")) {
        StyleArtwork(state.heroCover, Modifier.align(Alignment.CenterEnd).offset(x = 12.dp, y = 8.dp).size(160.dp)
            .graphicsLayer { rotationZ = -8f }.shadow(12.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
            .testTag("style_hero_cover"), tint, 560, pending = state.loading && state.heroCover.isBlank())
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to surface, .4f to surface.copy(alpha = .94f),
            .72f to surface.copy(alpha = .15f), 1f to surface.copy(alpha = 0f))))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 22.dp, end = 142.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (english.isNotBlank()) Text(english, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
            if (state.total > 0) Text("${state.total} 首歌曲", fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun StyleSubcategories(styles: List<MusicStyle>, navigate: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        styles.forEach { style -> key(style.tagId) {
            StyleAction("${style.tagName} ›", { navigate("lib/style/${style.tagId}") }, Modifier.testTag("style_category_${style.tagId}"))
        } }
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
    val colors = MiuixTheme.colorScheme
    LaunchedEffect(state.songs) { library.refreshStatuses(state.songs) }
    PreloadMusicCovers(state.songs.take(12).map(Song::cover))
    MusicPullToRefresh(state.loading, { vm.reload(fresh = true) }, Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        LazyColumn(Modifier.fillMaxSize().testTag("style_detail"), contentPadding = musicScrollPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.clickable(role = Role.Button, onClick = onBack).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(22.dp), tint = colors.onSurface)
                    Text("返回", Modifier.padding(horizontal = 10.dp), fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                StyleHero(state)
            }
            state.description?.desc?.takeIf(String::isNotBlank)?.let { description -> item {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(description, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 22.sp, maxLines = if (fullDescription) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis, color = colors.onSurface.copy(alpha = .65f))
                    StyleAction(if (fullDescription) "收起介绍" else "展开介绍", { fullDescription = !fullDescription })
                }
            } }
            state.detailError?.let { error -> item {
                Text("曲风介绍：$error", fontSize = 13.sp)
                StyleAction("重试介绍", { vm.reload(fresh = true) })
            } }
            if (children.isNotEmpty()) item {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("细分曲风", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (children.size > 3) StyleAction(if (allChildren) "收起分类" else "全部 ${children.size}", { allChildren = !allChildren })
                    }
                    StyleSubcategories(if (allChildren) children else children.take(3), navigate)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val enabled = state.songs.isNotEmpty() && room == null && playback.mode == PlayerMode.LOCAL
                    Row(Modifier.weight(1f).heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button) {
                        vm.container.playerController.setMode(PlaybackMode.LIST); play(state.songs, 0)
                    }.testTag("style_play_all"), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp), tint = colors.primary.copy(alpha = if (enabled) 1f else .35f))
                        Text("播放全部", Modifier.padding(start = 4.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface.copy(alpha = if (enabled) 1f else .4f))
                    }
                    StyleAction("热门", { vm.sort(0) }, Modifier.testTag("style_sort_hot"), active = state.sort == 0)
                    StyleAction("最新", { vm.sort(1) }, Modifier.testTag("style_sort_new"), active = state.sort == 1)
                }
            }
            if (room != null) item { Text("点击歌曲，为当前房间点歌", fontSize = 13.sp) }
            if (state.loading && state.songs.isEmpty()) item { LoadingSongList(5) }
            state.error?.let { error -> item {
                Text(error, fontSize = 14.sp)
                StyleAction("重试歌曲", { vm.reload(more = state.songs.isNotEmpty() && state.more, fresh = true) },
                    Modifier.testTag("style_songs_retry"), enabled = !state.loading)
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
                StyleAction("加载更多歌曲", { vm.reload(more = true) }, Modifier.fillMaxWidth().testTag("style_load_more"), enabled = !state.loading)
            }
        }
    }
}
