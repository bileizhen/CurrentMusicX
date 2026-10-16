package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import io.github.currencortex.music.feature.lyrics.ui.rememberLyricsPosition
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable internal fun DesktopLyricsSurface(lyrics: StateFlow<PlaybackLyricsState>, playback: StateFlow<PlayerState>,
    preferences: StateFlow<DesktopLyricsSettings>, lock: () -> Unit, close: () -> Unit, settings: () -> Unit,
    maxIslandWidth: () -> Float = { 640f }, editRequest: Int = 0, onEditingChanged: (Boolean) -> Unit = {},
    onIslandSize: (IntSize) -> Unit = {}, islandTapRequest: Int = 0, onIslandModeChanged: (Boolean) -> Unit = {},
    changeSong: (Int) -> Unit = {}, onIslandTop: (Int) -> Unit = {},
    togglePlayback: () -> Unit = {}, onCoverBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    defaultFpsMode: Boolean = false,
    effectNotices: StateFlow<io.github.currencortex.music.core.media.SoundEffectNotice?> = remember { kotlinx.coroutines.flow.MutableStateFlow(null) }) {
    val effectNotice by effectNotices.collectAsStateWithLifecycle()
    val content by lyrics.collectAsStateWithLifecycle()
    val player by playback.collectAsStateWithLifecycle()
    val options by preferences.collectAsStateWithLifecycle()
    val hasLyrics = player.song != null && content.songId == player.song?.id &&
        remember(content.document) { desktopIslandHasLyrics(content.document) }
    var selectedFps by remember(player.song?.id, player.song?.musicSource, defaultFpsMode) { mutableStateOf(defaultFpsMode) }
    val fpsMode = !hasLyrics || selectedFps
    SideEffect { onIslandModeChanged(fpsMode) }
    val toggle by rememberUpdatedState({ if (hasLyrics) selectedFps = !selectedFps })
    val navigate by rememberUpdatedState({ direction: Int -> if (player.song != null && player.canControlPlayback) changeSong(direction) })
    LaunchedEffect(islandTapRequest) { if (islandTapRequest > 0) toggle() }
    val frameRate = rememberIslandFrameRate()
    val position = rememberLyricsPosition(player, fpsMode, if (fpsMode) frameRate::onFrame else null)
    val timeline = remember(content.document) { LyricsSynchronizer(content.document) }
    val effective = remember(position, options.offsetMs) { derivedStateOf { LyricsSynchronizer.effectivePosition(position.value, options.offsetMs) } }
    val index by remember(timeline, effective) { derivedStateOf { desktopIslandLineIndex(content.document, timeline, effective.value) } }
    val line = content.document.lines.getOrNull(index).takeIf { content.songId == player.song?.id }
    val motion = rememberDesktopMotion()
    val coverTransition = rememberIslandCoverTransition(player.song, motion)
    val coverFlying by remember(coverTransition) { derivedStateOf { coverTransition.progress.value < 1f } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var controlsWanted by remember { mutableStateOf(true) }
    var touching by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    fun revealControls() { controlsWanted = true; interaction++ }
    LaunchedEffect(options.locked, editRequest) { if (!options.locked) revealControls() }
    LaunchedEffect(lifecycle, options.locked, touching, interaction, controlsWanted) {
        if (options.locked || touching || !controlsWanted) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { delay(3000); controlsWanted = false }
    }
    val chromeAlpha by animateFloatAsState(if (controlsWanted && !options.locked) 1f else 0f,
        tween(if (motion) 180 else 0), label = "desktop chrome")
    val showChrome = !options.locked && (controlsWanted || chromeAlpha > 0f)
    SideEffect { onEditingChanged(showChrome) }
    val font = options.displayFontSize
    val size = rememberIslandSize(player.song, if (fpsMode) "000.0 FPS" else line?.text.orEmpty(), font, fpsMode, showChrome,
        line?.translation?.takeIf { options.translation && !fpsMode })
    val reveal = rememberIslandPresentation(player.song?.id, fpsMode || (!content.loading && content.songId == player.song?.id), motion).value
    val introWidth = (size.metadata.value + 14f).coerceIn(if (showChrome) 240f else 48f, 640f).coerceAtMost(maxIslandWidth())
    val desiredWidth = if (reveal < .15f) introWidth else size.width.coerceAtMost(maxIslandWidth())
    var widthTarget by remember { mutableFloatStateOf(desiredWidth) }
    val width = rememberIslandWidth(widthTarget, motion)
    val entrance = rememberIslandEntrance(motion)
    SideEffect { if (reveal == 0f || fpsMode || player.song == null || (!content.loading && content.songId == player.song?.id)) widthTarget = desiredWidth }
    val density = LocalDensity.current
    val morphEnvelope = if (reveal > 0f && reveal < 1f) {
        with(density) { (font * 1.2f + if (options.translation && !line?.translation.isNullOrBlank()) desktopSubtitleSize(font) * 1.2f else 0f).sp.toDp() } +
            28.dp + if (showChrome) 56.dp else 0.dp
    } else 0.dp
    val drawingEnvelope = with(density) { (if (coverFlying) coverTransition.startDrawingHeight else 0).toDp() }
    // Hold the native buffer during the flight; only the visible pill resizes.
    Column(Modifier.fillMaxWidth().heightIn(min = maxOf(morphEnvelope, drawingEnvelope))
        .onSizeChanged { coverTransition.drawingHeight = it.height }
        .pointerInput(options.locked) {
            if (!options.locked) try {
                while (true) awaitPointerEventScope {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    touching = event.changes.any { it.pressed }
                    if (touching) interaction++
                }
            } finally { touching = false }
        }.testTag("desktop_lyrics_window"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showChrome) Row(Modifier.fillMaxWidth().testTag("desktop_edit_toolbar").graphicsLayer { alpha = chromeAlpha }
            .background(Color(0xEB161A25), RoundedCornerShape(16.dp)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("灵动岛", Modifier.weight(1f).heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
                style = TextStyle(fontSize = 12.sp, color = Color.White.copy(.75f)))
            DesktopAction("设置", settings); DesktopAction("锁定", lock); DesktopAction("关闭", close)
        }
        LaunchedEffect(effectNotice?.id) { if (effectNotice != null) onCoverBounds(androidx.compose.ui.geometry.Rect.Zero) }
        DesktopIslandEffectTransition(effectNotice, Modifier.align(Alignment.CenterHorizontally)
            .widthIn(max = maxIslandWidth().dp).padding(vertical = desktopIslandDrawingSpace)
            .onSizeChanged(onIslandSize)
            .onGloballyPositioned { onIslandTop(kotlin.math.round(it.positionInRoot().y).toInt()) }
            .graphicsLayer {
                alpha = entrance.value; scaleX = .94f + .06f * entrance.value; scaleY = scaleX
                transformOrigin = TransformOrigin(.5f, 0f)
            }) {
        DesktopIslandFrame(player.song, Modifier.islandAnimatedWidth(width).fillMaxWidth()
            .pointerInput(options.locked) { detectTapGestures(onTap = { toggle() }, onLongPress = { if (!options.locked) revealControls() }) }
            .pointerInput(Unit) {
                var dx = 0f; var dy = 0f
                val threshold = 32.dp.toPx()
                detectDragGestures(onDragStart = { dx = 0f; dy = 0f }, onDragEnd = {
                    if (kotlin.math.abs(dx) >= threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f) navigate(if (dx < 0f) 1 else -1)
                }, onDrag = { change, amount -> change.consume(); dx += amount.x; dy += amount.y })
            }, metadata = size.metadata, presentation = reveal, coverPosition = position, motion = motion,
            durationMs = player.durationMs.takeIf { it > 0 } ?: player.song?.durationMs ?: 0L, transition = coverTransition,
            coverPlaying = player.showPause, onCoverBounds = onCoverBounds, material = false,
            onCoverTap = { if (player.song != null && player.canControlPlayback) togglePlayback() }) {
            DesktopIslandDisplay(fpsMode, motion, lyrics = {
                if (line != null) {
                    val next = remember(content.document, index) { desktopIslandNextLineIndex(content.document, index) }
                    DesktopIslandCueHost(player.song?.id, index, line, next, content.document.lines.getOrNull(next), options to motion) { cue ->
                        DesktopIslandLyrics(cue, effective, options, motion, "desktop_window_translation")
                    }
                } else Spacer(Modifier.height(0.dp))
            }, fps = { DesktopIslandFrameRate(frameRate.rate, font, Color(options.textColor.toInt())) })
        }
        }
    }
}

@Composable private fun DesktopAction(label: String, click: () -> Unit) {
    BasicText(label, Modifier.clickable(role = Role.Button, onClick = click).heightIn(min = 48.dp)
        .wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 8.dp),
        style = TextStyle(fontSize = 12.sp, color = Color.White.copy(.85f)))
}
