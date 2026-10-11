package io.github.currencortex.music.feature.player

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.feature.lyrics.ui.*
import io.github.currencortex.music.data.settings.LyricsWeight
import io.github.currencortex.music.data.settings.KaraokeScope
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

private enum class PlayerContent { COVER, LYRICS }
private enum class PlayerOverlay { NONE, QUEUE, COMMENTS, OPTIONS, QUALITY, ACTIONS, MODE, LYRICS, WEIGHT, KARAOKE, SLEEP }
internal val PlayerPagePosition = SemanticsPropertyKey<Float>("PlayerPagePosition")

@Composable fun PlayerScreen(vm: PlayerViewModel, onBack: () -> Unit, onToggle: () -> Unit,
    actions: (@Composable (io.github.currencortex.music.data.song.Song) -> Unit)? = null,
    onCast: (() -> Unit)? = null, onRoom: (() -> Unit)? = null, onDialogActive: (Boolean) -> Unit = {},
    onNetwork: (() -> Unit)? = null, onLike: (() -> Unit)? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val songActions by vm.actions.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val heartLoading by vm.heartLoading.collectAsStateWithLifecycle()
    val sleepState by vm.sleepTimer.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The timer can expire while the sheet is closed; report it once, on the next composition.
    LaunchedEffect(sleepState.notice) {
        sleepState.notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.sleepTimer.consumeNotice()
        }
    }
    var content by rememberSaveable { mutableStateOf(PlayerContent.COVER) }
    val pager = rememberPagerState(initialPage = content.ordinal) { 2 }
    val pagerArtwork = remember(pager) { PlayerPagerArtworkTransition { pager.currentPage + pager.currentPageOffsetFraction } }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.settledPage) { content = PlayerContent.entries[pager.settledPage] }
    var overlay by rememberSaveable { mutableStateOf(PlayerOverlay.NONE) }
    val queueMotion = remember { QueuePageMotion() }
    var queueGesture by remember { mutableStateOf(false) }
    var queueGestureJob by remember { mutableStateOf<Job?>(null) }
    var controlsRevealed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.lyricsDisplay.hideControls) { controlsRevealed = false }
    val menuHost = LocalSongMenu.current
    val dismiss = {
        queueGestureJob?.cancel()
        queueGestureJob = null
        queueGesture = false
        overlay = PlayerOverlay.NONE
    }
    LaunchedEffect(overlay, queueGesture) { onDialogActive(overlay != PlayerOverlay.NONE || queueGesture) }
    LaunchedEffect(overlay, songActions.songId) { if (overlay == PlayerOverlay.COMMENTS) vm.loadComments() }
    DisposableEffect(Unit) { onDispose { onDialogActive(false) } }
    val indication = LocalIndication.current
    var lyricsCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var previewCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val sheetProgress = LocalPlayerSheetProgress.current
    val expanded by remember(sheetProgress) { derivedStateOf { sheetProgress() >= .995f } }
    val sheetDrag = LocalPlayerSheetDrag.current
    val colors = remember { darkColorScheme(primary = Color.White, onPrimary = Color(0xFF282629),
        background = Color(0xFF262428), surface = Color(0xFF262428)) }
    val queueFling = with(LocalDensity.current) { 700.dp.toPx() }
    var queueHeight by remember { mutableFloatStateOf(1f) }
    val upDrag = PlayerUpDrag(begin = {
            queueMotion.dragged = 0f
            queueGesture = true
        }, drag = { delta ->
            queueMotion.dragged?.let { queueMotion.dragged = (it - delta / queueHeight).coerceIn(0f, 1f) }
        }, end = end@{ velocity, cancelled ->
            // Back or the queue's close gesture may already have taken over this drag.
            if (!queueGesture || queueMotion.dragged == null) return@end
            val start = queueMotion.progress
            val open = !cancelled && (if (kotlin.math.abs(velocity) > queueFling) velocity < 0 else start >= .14f)
            queueGestureJob = scope.launch {
                queueMotion.animation.snapTo(start)
                queueMotion.dragged = null
                queueMotion.animation.animateTo(if (open) 1f else 0f,
                    tween((300 * (if (open) 1 - start else start)).toInt().coerceAtLeast(120),
                        easing = CubicBezierEasing(.2f, 0f, .2f, 1f)))
                if (open) overlay = PlayerOverlay.QUEUE
                queueGesture = false
                queueGestureJob = null
            }
    })
    BoxWithConstraints(Modifier.fillMaxSize().testTag("player_screen")
        .onSizeChanged { queueHeight = it.height.toFloat().coerceAtLeast(1f) }
        .then(if (overlay == PlayerOverlay.NONE) Modifier.playerSheetDrag(fromMini = false,
            upDrag = if (expanded) upDrag else null, enabled = !queueGesture) {
                listOfNotNull(lyricsCoordinates, previewCoordinates)
            } else Modifier)) {
        val widePlayer = maxWidth > maxHeight
        PlayerSystemBars(immersive = widePlayer)
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val wideSafeInset = with(density) { maxOf(WindowInsets.safeDrawing.getLeft(density, layoutDirection),
            WindowInsets.safeDrawing.getRight(density, layoutDirection)).toDp() }
        val wideVerticalInset = with(density) { maxOf(WindowInsets.safeDrawing.getTop(density),
            WindowInsets.safeDrawing.getBottom(density)).toDp() }
        LaunchedEffect(widePlayer) { if (widePlayer) pager.scrollToPage(PlayerContent.LYRICS.ordinal) }
        val immersive = settings.lyricsDisplay.hideControls && !controlsRevealed && content == PlayerContent.LYRICS
        val functions: @Composable () -> Unit = {
            PlayerSongActionsBar(vm, { overlay = PlayerOverlay.COMMENTS }, {
                scope.launch { queueMotion.animation.snapTo(0f); overlay = PlayerOverlay.QUEUE }
            }, { overlay = PlayerOverlay.SLEEP }, onLike)
        }
        val header: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().height(if (widePlayer) 48.dp else 52.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerIconButton(PlayerIcon.COLLAPSE, "收起播放器", onBack, Modifier.testTag("navigate_back"))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(settings.quality.label, Modifier.testTag("open_quality_sheet").clickable(enabled = state.mode == PlayerMode.LOCAL, role = Role.Button) { overlay = PlayerOverlay.QUALITY }
                        .padding(horizontal = 16.dp, vertical = 14.dp), color = Color.White.copy(alpha = .55f), fontSize = 12.sp)
                }
                PlayerIconButton(PlayerIcon.MORE, "播放与歌词设置", { overlay = PlayerOverlay.OPTIONS }, Modifier.testTag("lyrics_options"))
            }
        }
        PlayerBackdrop(queue.current?.cover.orEmpty(), Modifier.matchParentSize())
        // The artwork viewport uses light ink; dialogs below inherit the app appearance.
        MiuixTheme(controller = remember { ThemeController(colorSchemeMode = ColorSchemeMode.Dark, isDark = true, darkColors = colors) }) {
        CompositionLocalProvider(LocalIndication provides indication) {
        Column(Modifier.fillMaxSize().graphicsLayer { translationY = -size.height * queueMotion.progress }
            .then(if (overlay == PlayerOverlay.QUEUE || queueGesture) Modifier.semantics { hideFromAccessibility() } else Modifier)
            .then(if (widePlayer) Modifier.padding(horizontal = wideSafeInset, vertical = wideVerticalInset)
                else Modifier.statusBarsPadding().navigationBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
            .padding(horizontal = 24.dp).testTag("player_safe_content")
            .graphicsLayer { alpha = playerSheetContentAlpha(sheetProgress()) }) {
            if (!widePlayer) header()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (widePlayer) {
                    val stageHeight = (maxHeight - 24.dp).coerceAtLeast(0.dp)
                    val stageWidth = minOf(maxWidth, stageHeight * 2.7f, 1000.dp)
                    Box(Modifier.fillMaxSize().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Row(Modifier.size(stageWidth, stageHeight).testTag("player_wide_stage"),
                            horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                            WideCoverContent(vm, Modifier.weight(1f).fillMaxHeight())
                            HorizontalPager(pager, Modifier.weight(1f).fillMaxHeight().testTag("player_wide_pager")
                                .semantics { this[PlayerPagePosition] = pager.currentPage + pager.currentPageOffsetFraction },
                                beyondViewportPageCount = 1,
                                userScrollEnabled = expanded && sheetDrag?.state?.dragging != true,
                                flingBehavior = PagerDefaults.flingBehavior(pager,
                                    snapAnimationSpec = tween(240, easing = LinearOutSlowInEasing))) { page ->
                                Box(Modifier.fillMaxSize().then(if (pager.currentPage != page && !pager.isScrollInProgress)
                                    Modifier.clearAndSetSemantics {} else Modifier)) {
                                    if (page == 0) WideControlsContent(vm, onToggle, functions)
                                    else LyricsPanel(vm, Modifier.fillMaxSize()
                                        .onGloballyPositioned { lyricsCoordinates = it },
                                        active = pager.currentPage == 1 || pager.isScrollInProgress, minimal = true)
                                }
                            }
                        }
                    }
                    PlayerIconButton(PlayerIcon.MORE, "播放与歌词设置", { overlay = PlayerOverlay.OPTIONS },
                        Modifier.align(Alignment.TopEnd).testTag("lyrics_options"))
                } else Column(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalPlayerPagerArtwork provides pagerArtwork) {
                    Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { pagerArtwork.container = it }) {
                    HorizontalPager(pager, Modifier.fillMaxSize().testTag("player_content_pager")
                        .semantics { this[PlayerPagePosition] = pager.currentPage + pager.currentPageOffsetFraction },
                        beyondViewportPageCount = 1,
                        userScrollEnabled = expanded && sheetDrag?.state?.dragging != true,
                        flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = tween(240, easing = LinearOutSlowInEasing))) { page ->
                        Box(Modifier.fillMaxSize().then(if (pager.currentPage != page && !pager.isScrollInProgress)
                            Modifier.clearAndSetSemantics {} else Modifier).onGloballyPositioned {
                            if (page == 0) pagerArtwork.coverPage = it else pagerArtwork.lyricsPage = it
                        }) {
                        if (page == 1) Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                                .testTag("player_lyrics_header"), verticalAlignment = Alignment.CenterVertically) {
                                PlayerArtwork(queue.current?.cover.orEmpty(), Modifier.size(46.dp).testTag("player_lyrics_cover"), pixels = 800,
                                    pagerRole = PlayerPagerArtworkRole.LYRICS)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(queue.current?.name ?: "还没有选择歌曲", color = Color.White,
                                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(queue.current?.artists.orEmpty(), fontSize = 13.sp, color = Color.White.copy(alpha = .55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            LyricsPanel(vm, Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { lyricsCoordinates = it },
                                active = pager.currentPage == 1 || pager.isScrollInProgress)
                        } else CoverContent(vm, Modifier.fillMaxSize(), transitionTarget = pager.currentPage == 0,
                            active = pager.currentPage == 0 || pager.isScrollInProgress,
                            pagerRole = PlayerPagerArtworkRole.COVER,
                            onPreviewCoordinates = { previewCoordinates = it })
                        }
                    }
                    PlayerPagerArtwork(pagerArtwork, queue.current?.cover.orEmpty())
                    }
                    }
                    if (!immersive) PlayerTransport(vm, onToggle)
                }
            }
            if (!widePlayer && immersive) Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                Text("显示控制面板", color = Color.White.copy(alpha = .65f), fontSize = 12.sp,
                    modifier = Modifier.testTag("lyrics_reveal_controls").clickable(role = Role.Button) { controlsRevealed = true }
                        .padding(horizontal = 24.dp, vertical = 14.dp))
            } else if (!widePlayer) functions()
        }
        }
        }
        if (queueGesture || overlay == PlayerOverlay.QUEUE)
            PlaybackQueuePage(vm, queueMotion, false, "player_queue_sheet", dismiss)
        when (overlay) {
            PlayerOverlay.NONE -> Unit
            PlayerOverlay.COMMENTS -> PlayerCommentsDialog(comments, dismiss, { vm.loadComments() }, { vm.loadComments(more = true) })
            PlayerOverlay.QUEUE -> Unit
            PlayerOverlay.MODE -> MusicDialog("播放模式", dismiss) {
                PlaybackMode.entries.forEach { mode -> TextButton((if (mode == queue.mode) "✓ " else "") + mode.label,
                    onClick = { vm.setPlaybackMode(mode); overlay = PlayerOverlay.QUEUE },
                    enabled = state.mode == PlayerMode.LOCAL && (mode != PlaybackMode.HEART ||
                        (!heartLoading && io.github.currencortex.music.data.song.NeteaseSongActionsRepository.songId(queue.current) != null)),
                    modifier = Modifier.testTag("playback_mode_${mode.name}").semantics { selected = mode == queue.mode }) }
            }
            PlayerOverlay.QUALITY -> AudioQualitySheet(settings.quality, vm::quality, dismiss, state.mode == PlayerMode.LOCAL,
                onNetwork = onNetwork?.let { { dismiss(); it() } })
            PlayerOverlay.ACTIONS -> if (queue.current != null) MusicDialog("歌曲操作", dismiss) { actions?.invoke(queue.current!!) }
            PlayerOverlay.OPTIONS -> MusicDialog("播放与歌词", dismiss) {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                MusicDestinationRow(if (content == PlayerContent.LYRICS) {
                    if (widePlayer) "显示控制栏" else "显示封面"
                } else "显示歌词",
                    modifier = Modifier.testTag("open_lyrics"), onClick = {
                        dismiss()
                        scope.launch { pager.animateScrollToPage(if (pager.targetPage == 0) 1 else 0,
                            animationSpec = tween(260, easing = LinearOutSlowInEasing)) }
                    })
                if (onCast != null) MusicDestinationRow(if (state.mode == PlayerMode.CAST) "投屏控制" else "投屏",
                    modifier = Modifier.testTag("open_player_cast"), onClick = { dismiss(); onCast() })
                if (controlsRevealed && settings.lyricsDisplay.hideControls && content == PlayerContent.LYRICS)
                    MusicDestinationRow("隐藏控制面板", modifier = Modifier.testTag("lyrics_conceal_controls"), onClick = { controlsRevealed = false; dismiss() })
                if (actions != null && queue.current != null) MusicDestinationRow("歌曲操作", onClick = {
                    val song = queue.current ?: return@MusicDestinationRow
                    if (menuHost == null) overlay = PlayerOverlay.ACTIONS
                    else { dismiss(); menuHost(SongMenu(song, {}, {}, {}, extra = { actions(song) }, transport = false)) }
                })
                MusicDestinationRow("播放音质", summary = settings.quality.label, onClick = { overlay = PlayerOverlay.QUALITY }, enabled = state.mode == PlayerMode.LOCAL)
                MusicDestinationRow("播放模式", summary = queue.mode.label, onClick = { overlay = PlayerOverlay.MODE }, enabled = state.mode == PlayerMode.LOCAL)
                MusicDestinationRow("定时关闭", summary = sleepSummary(sleepState), modifier = Modifier.testTag("open_sleep_timer"),
                    onClick = { overlay = PlayerOverlay.SLEEP })
                MusicDestinationRow("歌词显示", summary = "霞鹜文楷 · 字号 ${settings.lyricsFontSize.toInt()}",
                    modifier = Modifier.testTag("open_lyrics_display"), onClick = { overlay = PlayerOverlay.LYRICS })
                if (onRoom != null) MusicDestinationRow(if (state.mode == PlayerMode.ROOM) "房间控制" else "一起听", onClick = { dismiss(); onRoom() })
                }
            }
            PlayerOverlay.WEIGHT -> MusicDialog("歌词字重", { overlay = PlayerOverlay.LYRICS }) {
                LyricsWeight.entries.forEach { weight ->
                    MusicDestinationRow((if (weight == settings.lyricsWeight) "✓ " else "") + weight.label,
                        modifier = Modifier.testTag("lyrics_weight_${weight.name}"), chevron = false,
                        onClick = { vm.lyricsWeight(weight); overlay = PlayerOverlay.LYRICS })
                }
            }
            PlayerOverlay.KARAOKE -> MusicDialog("卡拉OK（逐字）歌词动画兼容策略", { overlay = PlayerOverlay.LYRICS }) {
                KaraokeScope.entries.forEach { scope ->
                    MusicDestinationRow((if (scope == settings.lyricsDisplay.karaokeScope) "✓ " else "") + scope.label,
                        summary = when (scope) {
                            KaraokeScope.CURRENT -> "只对正在播放的行逐字高亮"
                            KaraokeScope.ALL -> "所有含逐字时间的可见行跟随进度高亮"
                            KaraokeScope.ALWAYS -> "全部行启用；普通歌词按行时长近似扫亮"
                        },
                        modifier = Modifier.testTag("karaoke_scope_${scope.name}"), chevron = false,
                        onClick = { vm.lyricsDisplay { it.copy(karaokeScope = scope) }; overlay = PlayerOverlay.LYRICS })
                }
            }
            PlayerOverlay.SLEEP -> SleepTimerSheet(vm.sleepTimer, dismiss)
            PlayerOverlay.LYRICS -> MusicDialog("歌词显示", dismiss) {
                LyricsDisplaySettings(settings.lyricsFontSize, vm::lyricsFontSize, settings.lyricsWeight, { overlay = PlayerOverlay.WEIGHT },
                    settings.lyricsDisplay, vm::lyricsDisplay, { overlay = PlayerOverlay.KARAOKE })
            }
        }
    }
}

@Composable private fun CoverContent(vm: PlayerViewModel, modifier: Modifier, transitionTarget: Boolean = true,
    active: Boolean = true, pagerRole: PlayerPagerArtworkRole? = null, showPreview: Boolean = true,
    onPreviewCoordinates: (LayoutCoordinates) -> Unit = {}) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val song = queue.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val coverSize = minOf(maxWidth * .92f, (maxHeight - if (showPreview) 200.dp else 128.dp).coerceAtLeast(72.dp), 360.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PlayerArtwork(song?.cover.orEmpty(), Modifier.size(coverSize).testTag("player_cover"),
                transitionTarget = transitionTarget, pagerRole = pagerRole)
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(song?.name ?: "还没有选择歌曲", Modifier.testTag("player_song_title"), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(song?.artists.orEmpty(), fontSize = 18.sp, color = Color.White.copy(alpha = .55f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (showPreview) CoverLyricPreview(vm, Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp)
                .onGloballyPositioned(onPreviewCoordinates), active)
        }
    }
}

/** Artwork stays at the center of its pane while controls and lyrics slide beside it. */
@Composable private fun WideCoverContent(vm: PlayerViewModel, modifier: Modifier) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    BoxWithConstraints(modifier.fillMaxWidth().testTag("player_wide_cover_content")) {
        val coverSize = minOf(maxWidth * .96f, (maxHeight - 8.dp).coerceAtLeast(0.dp), 360.dp)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PlayerArtwork(queue.current?.cover.orEmpty(), Modifier.size(coverSize).testTag("player_cover"))
        }
    }
}

@Composable private fun WideControlsContent(vm: PlayerViewModel, onToggle: () -> Unit,
    functions: @Composable () -> Unit) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize().testTag("player_wide_controls")) {
        val compact = maxHeight < 360.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 24.dp, Alignment.CenterVertically)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(queue.current?.name ?: "还没有选择歌曲", Modifier.testTag("player_song_title"),
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(queue.current?.artists.orEmpty(), Modifier.padding(top = 6.dp).testTag("player_song_artist"),
                    color = Color.White.copy(alpha = .55f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PlayerTransport(vm, onToggle, compact = compact)
            functions()
        }
    }
}

@Composable private fun LyricsPanel(vm: PlayerViewModel, modifier: Modifier, active: Boolean = true, minimal: Boolean = false) {
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val player by vm.state.collectAsStateWithLifecycle()
    val position = rememberLyricsPosition(if (active) player else player.copy(playing = false))
    if (lyrics.document.lines.isNotEmpty()) key(player.song?.id, lyrics.document) {
        val settings by vm.settings.collectAsStateWithLifecycle()
        LyricsScreen(lyrics.document, position, vm.player::seek, modifier, player.canControlPlayback,
            settings.lyricsDisplay.translation, settings.lyricsDisplay.romanization, settings.lyricsDisplay.wordAnimation,
            settings.lyricsDisplay.blur, if (minimal) settings.lyricsFontSize * .75f else settings.lyricsFontSize,
            settings.lyricsWeight, settings.lyricsOffsetMs, settings.lyricsDisplay,
            minimal = minimal, perspective = settings.lyricsDisplay.perspective)
    } else Box(modifier.testTag("lyrics_panel"), contentAlignment = Alignment.Center) {
        if (lyrics.loading) Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            MusicPlaceholder(Modifier.fillMaxWidth(.8f).height(30.dp))
            MusicPlaceholder(Modifier.fillMaxWidth().height(30.dp))
            MusicPlaceholder(Modifier.fillMaxWidth(.6f).height(30.dp))
            Text("正在加载歌词", fontSize = 13.sp, color = Color.White.copy(alpha = .45f))
        } else Text(lyrics.error ?: "暂无歌词", Modifier.padding(28.dp), color = Color.White.copy(alpha = .5f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}
