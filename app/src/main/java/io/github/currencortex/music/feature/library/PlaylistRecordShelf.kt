package io.github.currencortex.music.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import io.github.currencortex.music.feature.update.UpdateGlassButton
import io.github.currencortex.music.feature.update.rememberUpdateBackdrop
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.settings.RecordShelfPreviewMode
import kotlinx.coroutines.flow.Flow
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

@Composable internal fun PlaylistRecordShelf(songs: List<Song>, canPlay: Boolean,
    onPlay: (Int) -> Unit, onClose: () -> Unit,
    isSaved: (Song) -> Boolean = { false }, onSave: ((Song) -> Unit)? = null, saveEnabled: Boolean = true,
    onPreview: (Song?) -> Unit = {},
    previewMode: RecordShelfPreviewMode = RecordShelfPreviewMode.ONCE,
    onPreviewModeChange: (RecordShelfPreviewMode) -> Unit = {},
    fullPreview: Boolean = false,
    onFullPreviewChange: (Boolean) -> Unit = {},
    nextPreviewRequests: Flow<Song>? = null) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val renderer = remember(context) { RecordShelfView(context) }
    val customTypeface = io.github.currencortex.music.ui.theme.LocalCustomTypeface.current
    val glassBackdrop = rememberUpdateBackdrop()
    val pageActive by rememberUpdatedState(LocalMusicPageActive.current)
    val refreshRate = LocalRefreshRatePreference.current
    var selection by remember { mutableIntStateOf(0) }
    var displayedSongs by remember { mutableStateOf(songs) }
    var replacingResults by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val controlsAlpha by animateFloatAsState(if (appeared) 1f else 0f, tween(500), label = "shelf controls")
    val titleAlpha by animateFloatAsState(if (picking) .4f else 1f, tween(200), label = "shelf title")
    val filtered = remember(songs, query) {
        songs.filter { query.isBlank() || listOf(it.name, it.artists, it.album).any { text -> text.contains(query.trim(), true) } }
    }
    val selected = displayedSongs.getOrNull(selection)
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density).toFloat()
    val bottom = with(density) { 75.dp.toPx() } + WindowInsets.navigationBars.getBottom(density)
    val activate by rememberUpdatedState<(Int) -> Unit>({ index ->
        if (canPlay && !replacingResults) displayedSongs.getOrNull(index)?.let { song ->
            songs.indexOfFirst { it.id == song.id && it.musicSource == song.musicSource }.takeIf { it >= 0 }?.let(onPlay)
        }
    })
    val currentSelection by rememberUpdatedState<(Int) -> Unit>({ selection = it })
    val currentPreview by rememberUpdatedState<(Int?) -> Unit>({ index -> onPreview(if (canPlay) index?.let(displayedSongs::getOrNull) else null) })
    val currentSongsChanged by rememberUpdatedState<(List<Song>) -> Unit>({ displayedSongs = it })
    val advancePreview by rememberUpdatedState<(Song) -> Unit>({ finished ->
        if (!replacingResults && previewMode in setOf(RecordShelfPreviewMode.NEXT, RecordShelfPreviewMode.RANDOM) && canPlay && pageActive &&
            owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            selected?.id == finished.id && selected.musicSource == finished.musicSource) {
            nextRecordPreviewIndex(displayedSongs, selection, previewMode == RecordShelfPreviewMode.RANDOM)?.let(renderer::pick)
        }
    })
    LaunchedEffect(nextPreviewRequests) { nextPreviewRequests?.collect { advancePreview(it) } }
    DisposableEffect(renderer, owner) {
        renderer.onSelection = { currentSelection(it) }
        renderer.onSongsChanged = { currentSongsChanged(it) }
        renderer.onResultsChangingChanged = { replacingResults = it }
        renderer.onPreview = { currentPreview(it) }
        renderer.onActivate = { activate(it) }
        renderer.onPickingChanged = { picking = it }
        val observer = LifecycleEventObserver { _, _ ->
            renderer.setRunning(pageActive && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); renderer.setRunning(false) }
    }
    DisposableEffect(renderer) { onDispose { renderer.dispose() } }
    SideEffect {
        renderer.setCustomTypeface(customTypeface)
        renderer.setRunning(pageActive && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        // Keep result changes independent of AndroidView layout updates. The renderer owns both
        // the outgoing and incoming phases, including updates delivered behind the search overlay.
        renderer.configure(filtered, searching = query.isNotBlank())
        displayedSongs = renderer.displayedSongs
        replacingResults = renderer.replacingResults
    }
    BackHandler(enabled = pageActive) { if (searchOpen) searchOpen = false else if (optionsOpen) optionsOpen = false else onClose() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF102132)).testTag("playlist_record_shelf")) {
        val screenWidth = maxWidth
        val footerSize = screenWidth * .132f
        Box(Modifier.matchParentSize().then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)) {
            androidx.compose.animation.Crossfade(selected?.cover.orEmpty(), Modifier.matchParentSize(), tween(450), label = "shelf cover background") { cover ->
                io.github.currencortex.music.feature.player.PlayerBackdrop(cover, Modifier.fillMaxSize())
            }
            AndroidView(factory = { renderer }, update = {
                it.topInset = top; it.bottomInset = bottom; applyViewRefreshRate(it, refreshRate); it.invalidate()
            }, modifier = Modifier.fillMaxSize().testTag("record_shelf_canvas").semantics {
                contentDescription = "CD 唱片架，滑动浏览歌曲，点击唱片抽出，再次点击播放，底部刻度可快速定位"
                stateDescription = if (replacingResults) "正在切换唱片" else selected?.let { "${selection + 1} / ${displayedSongs.size}，${it.name}，${it.artists}" } ?: "没有匹配的歌曲"
                if (replacingResults) disabled()
                onClick("播放当前歌曲") { if (canPlay && selected != null && !replacingResults) { activate(selection); true } else false }
                scrollBy { x, y -> renderer.travel((x - y * .55f) / 70f); true }
                scrollToIndex { index ->
                    if (!replacingResults && index in displayedSongs.indices) { renderer.select(index); true } else false
                }
                customActions = listOf(
                    CustomAccessibilityAction("上一张唱片") { renderer.travel(-1f); true },
                    CustomAccessibilityAction("下一张唱片") { renderer.travel(1f); true },
                    CustomAccessibilityAction("播放当前歌曲") { activate(selection); canPlay && selected != null },
                    CustomAccessibilityAction("返回列表") { onClose(); true },
                )
            })
        }
        Row(Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = controlsAlpha }.statusBarsPadding().padding(horizontal = screenWidth * .04f, vertical = 14.dp),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShelfCircle(glassBackdrop, "搜索歌曲", "record_shelf_search", { searchOpen = true }) {
                Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(selected?.name ?: "没有匹配的歌曲", fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = titleAlpha }.blur(if (picking) 3.dp else 0.dp).testTag("record_shelf_title"))
                Text(selected?.let { listOf(it.artists, it.album).filter(String::isNotBlank).joinToString(" · ") }.orEmpty(),
                    Modifier.padding(top = 7.dp).graphicsLayer { alpha = titleAlpha }.blur(if (picking) 3.dp else 0.dp), fontSize = 14.sp, color = Color.White.copy(alpha = .6f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            ShelfCircle(glassBackdrop, "返回歌曲列表", "record_shelf_close", onClose) {
                ShelfGlyph("collection", Modifier.size(21.dp))
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = controlsAlpha }.navigationBarsPadding()
            .padding(bottom = 22.dp).fillMaxWidth().padding(horizontal = screenWidth * .05f),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            ShelfCircle(glassBackdrop, if (selected?.let(isSaved) == true) "取消喜欢" else "喜欢当前歌曲", "record_shelf_save", {
                selected?.let { onSave?.invoke(it) }
            }, enabled = selected != null && !selected.video && onSave != null && saveEnabled && !replacingResults, size = footerSize) {
                Icon(if (selected?.let(isSaved) == true) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    null, Modifier.size(24.dp), tint = Color(0xFFFF4D6D))
            }
            RecordShelfPickButton(Modifier.width(screenWidth * .432f).height(footerSize),
                enabled = displayedSongs.isNotEmpty() && !picking && !replacingResults, onClick = {
                    val index = if (displayedSongs.size <= 1) 0 else {
                        val choice = (0 until displayedSongs.lastIndex).random()
                        if (choice >= selection) choice + 1 else choice
                    }
                    renderer.pick(index)
                }) {
                ShelfGlyph("shuffle", Modifier.size(23.dp), Color(0xFF0E1420))
                Text("帮我挑一首", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0E1420))
            }
            ShelfCircle(glassBackdrop, "唱片架选项", "record_shelf_options", { optionsOpen = true }, size = footerSize) {
                ShelfGlyph("sliders", Modifier.size(24.dp))
            }
        }
    }
    if (optionsOpen) MusicDialog("唱片架", onDismiss = { optionsOpen = false }) {
        SettingsSwitch("整首试听", fullPreview, onFullPreviewChange,
            Modifier.testTag("record_shelf_preview_full"), summary = "从头播放整首歌曲，关闭后恢复高潮片段")
        SettingsSwitch(if (fullPreview) "循环试听整首歌" else "循环试听片段", previewMode == RecordShelfPreviewMode.LOOP,
            { onPreviewModeChange(if (it) RecordShelfPreviewMode.LOOP else RecordShelfPreviewMode.ONCE) },
            Modifier.testTag("record_shelf_preview_loop"), summary = if (fullPreview) "重复播放当前唱片的整首歌曲" else "重复当前唱片的试听片段")
        SettingsSwitch("试听结束自动下一首", previewMode in setOf(RecordShelfPreviewMode.NEXT, RecordShelfPreviewMode.RANDOM),
            { onPreviewModeChange(if (it) RecordShelfPreviewMode.NEXT else RecordShelfPreviewMode.ONCE) },
            Modifier.testTag("record_shelf_preview_next"), summary = if (previewMode == RecordShelfPreviewMode.RANDOM)
                "试听结束后随机抽出另一张唱片" else "按当前列表顺序试听，最后一首后回到开头")
        SettingsSwitch("自动随机切换", previewMode == RecordShelfPreviewMode.RANDOM,
            { onPreviewModeChange(if (it) RecordShelfPreviewMode.RANDOM else RecordShelfPreviewMode.NEXT) },
            Modifier.testTag("record_shelf_preview_random"), summary = "随机抽出另一张唱片并试听",
            enabled = previewMode in setOf(RecordShelfPreviewMode.NEXT, RecordShelfPreviewMode.RANDOM))
        MusicDialogButton("返回歌曲列表", onClick = { optionsOpen = false; onClose() })
    }

    if (searchOpen) MusicDialog("搜索唱片", onDismiss = { searchOpen = false }) {
        TextField(query, { query = it }, label = "歌名、歌手或专辑", singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("record_shelf_query"))
        Text("${filtered.size} 首歌曲", fontSize = 13.sp)
        MusicDialogButton("查看唱片", onClick = { searchOpen = false })
        if (query.isNotBlank()) MusicDialogButton("清空搜索", onClick = { query = "" })
    }
}

@Composable private fun ShelfCircle(backdrop: Backdrop?, label: String, tag: String, onClick: () -> Unit,
    enabled: Boolean = true, size: androidx.compose.ui.unit.Dp = 48.dp, content: @Composable () -> Unit) {
    UpdateGlassButton(backdrop, tag, label, onClick, enabled = enabled, buttonSize = size, content = content)
}

/** The four control silhouettes follow the reference, with native actions behind each one. */
@Composable private fun ShelfGlyph(kind: String, modifier: Modifier, color: Color = Color.White, filled: Boolean = false) {
    Canvas(modifier) {
        val u = size.width / 24f
        val stroke = 1.7f * u
        fun line(x: Float, y: Float, xx: Float, yy: Float) =
            drawLine(color, Offset(x * u, y * u), Offset(xx * u, yy * u), stroke, StrokeCap.Round)
        fun path(vararg p: Float, close: Boolean = false) {
            val shape = Path().apply {
                moveTo(p[0] * u, p[1] * u)
                for (i in 2 until p.size step 2) lineTo(p[i] * u, p[i + 1] * u)
                if (close) close()
            }
            drawPath(shape, color, style = Stroke(stroke, cap = StrokeCap.Round))
            if (filled && close) drawPath(shape, color.copy(alpha = .35f))
        }
        when (kind) {
            "bookmark" -> path(6f, 3f, 18f, 3f, 18f, 22f, 12f, 17f, 6f, 22f, close = true)
            "collection" -> {
                path(3f, 8f, 21f, 8f, 21f, 22f, 3f, 22f, close = true)
                line(5f, 5f, 19f, 5f); line(7f, 2f, 17f, 2f)
                line(7f, 8f, 7f, 22f); line(17f, 8f, 17f, 22f)
                for (y in listOf(12f, 17f)) { line(3f, y, 7f, y); line(17f, y, 21f, y) }
            }
            "sliders" -> for ((y, x) in listOf(5f to 15f, 12f to 8f, 19f to 16f)) {
                line(2f, y, x - 2.5f, y); line(x + 2.5f, y, 22f, y)
                drawCircle(color, 2.4f * u, Offset(x * u, y * u), style = Stroke(stroke))
            }
            "shuffle" -> {
                path(2f, 6f, 6f, 6f, 17f, 18f, 22f, 18f)
                path(2f, 18f, 6f, 18f, 17f, 6f, 22f, 6f)
                path(19f, 3f, 22f, 6f, 19f, 9f); path(19f, 15f, 22f, 18f, 19f, 21f)
            }
        }
    }
}
