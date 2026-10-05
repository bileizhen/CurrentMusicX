package io.github.currencortex.music.feature.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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

private enum class PlayerContent { COVER, LYRICS }
private enum class PlayerOverlay { NONE, QUEUE, COMMENTS, OPTIONS, QUALITY, ACTIONS, MODE, LYRICS, WEIGHT, KARAOKE }
internal val PlayerPagePosition = SemanticsPropertyKey<Float>("PlayerPagePosition")

@Composable fun PlayerScreen(vm: PlayerViewModel, onBack: () -> Unit, onToggle: () -> Unit,
    actions: (@Composable (io.github.currencortex.music.data.song.Song) -> Unit)? = null,
    onCast: (() -> Unit)? = null, onRoom: (() -> Unit)? = null, onDialogActive: (Boolean) -> Unit = {},
    onNetwork: (() -> Unit)? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val songActions by vm.actions.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val heartLoading by vm.heartLoading.collectAsStateWithLifecycle()
    var content by rememberSaveable { mutableStateOf(PlayerContent.COVER) }
    val pager = rememberPagerState(initialPage = content.ordinal) { 2 }
    val pagerArtwork = remember(pager) { PlayerPagerArtworkTransition { pager.currentPage + pager.currentPageOffsetFraction } }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.settledPage) { content = PlayerContent.entries[pager.settledPage] }
    var overlay by rememberSaveable { mutableStateOf(PlayerOverlay.NONE) }
    val queueMotion = remember(overlay == PlayerOverlay.QUEUE) { QueuePageMotion() }
    var controlsRevealed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.lyricsDisplay.hideControls) { controlsRevealed = false }
    val menuHost = LocalSongMenu.current
    val dismiss = { overlay = PlayerOverlay.NONE }
    LaunchedEffect(overlay) { onDialogActive(overlay != PlayerOverlay.NONE) }
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
    BoxWithConstraints(Modifier.fillMaxSize().testTag("player_screen")
        .then(if (overlay == PlayerOverlay.NONE) Modifier.playerSheetDrag(fromMini = false) {
            listOfNotNull(lyricsCoordinates, previewCoordinates)
        } else Modifier)) {
        val immersive = settings.lyricsDisplay.hideControls && !controlsRevealed &&
            (content == PlayerContent.LYRICS || maxWidth >= 648.dp)
        PlayerBackdrop(queue.current?.cover.orEmpty(), Modifier.matchParentSize())
        // The artwork viewport uses light ink; dialogs below inherit the app appearance.
        MiuixTheme(controller = remember { ThemeController(colorSchemeMode = ColorSchemeMode.Dark, isDark = true, darkColors = colors) }) {
        CompositionLocalProvider(LocalIndication provides indication) {
        Column(Modifier.fillMaxSize().graphicsLayer { translationY = -size.height * queueMotion.progress }
            .then(if (overlay == PlayerOverlay.QUEUE) Modifier.semantics { hideFromAccessibility() } else Modifier)
            .statusBarsPadding().navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 24.dp).testTag("player_safe_content")
            .graphicsLayer { alpha = playerSheetContentAlpha(sheetProgress()) }) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerIconButton(PlayerIcon.COLLAPSE, "收起播放器", onBack, Modifier.testTag("navigate_back"))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(settings.quality.label, Modifier.testTag("open_quality_sheet").clickable(enabled = state.mode == PlayerMode.LOCAL, role = Role.Button) { overlay = PlayerOverlay.QUALITY }
                        .padding(horizontal = 16.dp, vertical = 14.dp), color = Color.White.copy(alpha = .55f), fontSize = 12.sp)
                }
                PlayerIconButton(PlayerIcon.MORE, "播放与歌词设置", { overlay = PlayerOverlay.OPTIONS }, Modifier.testTag("lyrics_options"))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth >= 600.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        CoverContent(vm, Modifier.weight(1f), onPreviewCoordinates = { previewCoordinates = it })
                        if (!immersive) PlayerTransport(vm, onToggle)
                    }
                    LyricsPanel(vm, Modifier.weight(1.15f).fillMaxHeight().onGloballyPositioned { lyricsCoordinates = it })
                } else Column(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalPlayerPagerArtwork provides pagerArtwork) {
                    Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { pagerArtwork.container = it }) {
                    HorizontalPager(pager, Modifier.fillMaxSize().testTag("player_content_pager")
                        .semantics { this[PlayerPagePosition] = pager.currentPage + pager.currentPageOffsetFraction },
                        beyondViewportPageCount = 1,
                        userScrollEnabled = expanded && sheetDrag?.state?.dragging != true,
                        flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = tween(240, easing = LinearOutSlowInEasing))) { page ->
                        Box(Modifier.fillMaxSize().onGloballyPositioned {
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
            if (immersive) Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                Text("显示控制面板", color = Color.White.copy(alpha = .65f), fontSize = 12.sp,
                    modifier = Modifier.testTag("lyrics_reveal_controls").clickable(role = Role.Button) { controlsRevealed = true }
                        .padding(horizontal = 24.dp, vertical = 14.dp))
            } else PlayerSongActionsBar(vm, { overlay = PlayerOverlay.COMMENTS }, { overlay = PlayerOverlay.QUEUE })
        }
        }
        }
        when (overlay) {
            PlayerOverlay.NONE -> Unit
            PlayerOverlay.COMMENTS -> PlayerCommentsDialog(comments, dismiss, { vm.loadComments() }, { vm.loadComments(more = true) })
            PlayerOverlay.QUEUE -> PlaybackQueuePage(vm, queueMotion, false, "player_queue_sheet", dismiss)
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
                MusicDestinationRow(if (content == PlayerContent.LYRICS) "显示封面" else "显示歌词",
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
            PlayerOverlay.LYRICS -> MusicDialog("歌词显示", dismiss) {
                LyricsDisplaySettings(settings.lyricsFontSize, vm::lyricsFontSize, settings.lyricsWeight, { overlay = PlayerOverlay.WEIGHT },
                    settings.lyricsDisplay, vm::lyricsDisplay, { overlay = PlayerOverlay.KARAOKE })
            }
        }
    }
}

@Composable private fun CoverContent(vm: PlayerViewModel, modifier: Modifier, transitionTarget: Boolean = true,
    active: Boolean = true, pagerRole: PlayerPagerArtworkRole? = null,
    onPreviewCoordinates: (LayoutCoordinates) -> Unit = {}) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val song = queue.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val coverSize = minOf(maxWidth * .92f, (maxHeight - 200.dp).coerceAtLeast(72.dp), 360.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PlayerArtwork(song?.cover.orEmpty(), Modifier.size(coverSize).testTag("player_cover"),
                transitionTarget = transitionTarget, pagerRole = pagerRole)
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(song?.name ?: "还没有选择歌曲", Modifier.testTag("player_song_title"), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(song?.artists.orEmpty(), fontSize = 18.sp, color = Color.White.copy(alpha = .55f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            CoverLyricPreview(vm, Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp)
                .onGloballyPositioned(onPreviewCoordinates), active)
        }
    }
}

@Composable private fun LyricsPanel(vm: PlayerViewModel, modifier: Modifier, active: Boolean = true) {
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val player by vm.state.collectAsStateWithLifecycle()
    val position = rememberLyricsPosition(if (active) player else player.copy(playing = false))
    if (lyrics.document.lines.isNotEmpty()) key(player.song?.id, lyrics.document) {
        val settings by vm.settings.collectAsStateWithLifecycle()
        LyricsScreen(lyrics.document, position, vm.player::seek, modifier, player.canControlPlayback,
            settings.lyricsDisplay.translation, settings.lyricsDisplay.romanization, settings.lyricsDisplay.wordAnimation,
            settings.lyricsDisplay.blur, settings.lyricsFontSize, settings.lyricsWeight, settings.lyricsOffsetMs, settings.lyricsDisplay)
    } else Box(modifier.testTag("lyrics_panel"), contentAlignment = Alignment.Center) {
        if (lyrics.loading) Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            MusicPlaceholder(Modifier.fillMaxWidth(.8f).height(30.dp))
            MusicPlaceholder(Modifier.fillMaxWidth().height(30.dp))
            MusicPlaceholder(Modifier.fillMaxWidth(.6f).height(30.dp))
            Text("正在加载歌词", fontSize = 13.sp, color = Color.White.copy(alpha = .45f))
        } else Text(lyrics.error ?: "暂无歌词", Modifier.padding(28.dp), color = Color.White.copy(alpha = .5f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}
