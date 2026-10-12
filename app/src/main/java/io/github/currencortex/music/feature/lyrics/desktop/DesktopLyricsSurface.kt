package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import io.github.currencortex.music.feature.lyrics.ui.rememberLyricsPosition
import io.github.currencortex.music.feature.lyrics.ui.KaraokeText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

@Composable internal fun DesktopLyricsSurface(lyrics: StateFlow<PlaybackLyricsState>, playback: StateFlow<PlayerState>,
    preferences: StateFlow<DesktopLyricsSettings>, maxTextHeight: () -> Float,
    drag: (Float, Float) -> Unit, release: () -> Unit, lock: () -> Unit, close: () -> Unit, settings: () -> Unit, maxIslandWidth: () -> Float = { 640f }, editRequest: Int = 0,
    onEditingChanged: (Boolean) -> Unit = {}, onIslandSize: (IntSize) -> Unit = {}) {
    val content by lyrics.collectAsStateWithLifecycle()
    val player by playback.collectAsStateWithLifecycle()
    val options by preferences.collectAsStateWithLifecycle()
    val island = options.appearance == DesktopAppearance.ISLAND
    val fpsMode = island && (player.song == null || content.songId != player.song?.id ||
        !remember(content.document) { desktopIslandHasLyrics(content.document) })
    val frameRate = rememberIslandFrameRate()
    val position = rememberLyricsPosition(player, fpsMode, if (fpsMode) frameRate::onFrame else null)
    val timeline = remember(content.document) { LyricsSynchronizer(content.document) }
    val effective = remember(position, options.offsetMs) { derivedStateOf { LyricsSynchronizer.effectivePosition(position.value, options.offsetMs) } }
    val index by remember(timeline, effective, island) { derivedStateOf { if (island) desktopIslandLineIndex(content.document, timeline, effective.value) else timeline.findCurrentLine(effective.value) } }
    val line = content.document.lines.getOrNull(index).takeIf { content.songId == player.song?.id }
    val interlude by remember(timeline, effective, island) { derivedStateOf { if (island) null else timeline.interlude(effective.value)?.label } }
    val jumpTime = remember(player.timelineRevision) { android.os.SystemClock.elapsedRealtime() }
    val motion = rememberDesktopMotion()
    // Editing chrome is transient; hiding it must never change the saved touch-through lock.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var controlsWanted by remember { mutableStateOf(true) }
    var touching by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    fun revealControls() { controlsWanted = true; interaction++ }
    fun finishDrag() { release(); dragging = false; revealControls() }
    LaunchedEffect(options.locked, editRequest) { if (!options.locked) revealControls() }
    LaunchedEffect(lifecycle, options.locked, touching, dragging, interaction, controlsWanted) {
        if (options.locked || touching || dragging || !controlsWanted) return@LaunchedEffect
        // The one-shot idle timer runs only while this window is actually visible.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(3_000)
            controlsWanted = false
        }
    }
    val chromeAlpha by animateFloatAsState(if (controlsWanted && !options.locked) 1f else 0f,
        tween(if (motion) 180 else 0), label = "desktop chrome")
    val showChrome = !options.locked && (controlsWanted || chromeAlpha > 0f)
    SideEffect { onEditingChanged(showChrome) }
    val idleGestures = if (!options.locked && !showChrome) Modifier
        .pointerInput(Unit) { detectTapGestures(onTap = { revealControls() }) }
        .then(if (island) Modifier else Modifier.pointerInput(Unit) { detectDragGestures(onDragStart = { dragging = true },
            onDragEnd = { finishDrag() }, onDragCancel = { finishDrag() },
            onDrag = { change, amount -> change.consume(); drag(amount.x, amount.y) }) })
        else Modifier
    val stage = options.effect == DesktopEffect.STAGE
    val glowOpacity = remember(stage, options.glowStrength) { mutableFloatStateOf(options.glowStrength * if (stage) 1f else .5f) }
    val textColor = Color(options.textColor.toInt())
    val baseOpacity = remember(stage) { mutableFloatStateOf(if (stage) .35f else .55f) }
    val shape = RoundedCornerShape(22.dp)
    val displayFont = options.displayFontSize
    val effectPadding = desktopEffectPadding(island)
    val capsule = options.appearance == DesktopAppearance.CAPSULE
    val message = when {
        player.song == null -> "等待播放"
        content.loading || content.songId != player.song?.id -> "正在读取歌词"
        content.document.lines.isEmpty() -> "暂无歌词 · 聆听音乐"
        else -> interlude ?: "♪"
    }
    val islandSize = if (island) rememberIslandSize(player.song,
        if (fpsMode) "000.0 FPS" else line?.text?.takeIf { interlude == null } ?: message, displayFont, fpsMode, showChrome,
        line?.translation?.takeIf { options.translation && interlude == null }) else null
    val islandReveal = if (island) rememberIslandPresentation(player.song?.id,
        fpsMode || (!content.loading && content.songId == player.song?.id), motion).value else 1f
    val introWidth = ((islandSize?.metadata?.value ?: 120f) + 14f).coerceIn(if (showChrome) 240f else 48f, 640f).coerceAtMost(maxIslandWidth())
    // One destination per phase: do not chase a moving target on every intro frame.
    val desiredIslandWidth = if (islandReveal < .15f) introWidth else (islandSize?.width ?: introWidth).coerceAtMost(maxIslandWidth())
    var islandWidthTarget by remember(island) { mutableFloatStateOf(if (island) desiredIslandWidth else 640f) }
    val islandAnimatedWidth = rememberIslandWidth(islandWidthTarget, motion)
    val islandEntrance = if (island) rememberIslandEntrance(motion) else null
    SideEffect {
        if (islandReveal == 0f || fpsMode || player.song == null || (!content.loading && content.songId == player.song?.id)) islandSize?.let { islandWidthTarget = desiredIslandWidth }
    }
    val material = if (capsule) Modifier.background(Brush.linearGradient(listOf(Color(0xCC222538), Color(0xAA10131F))), shape)
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.22f), Color.White.copy(.03f))), shape) else Modifier
    val morphEnvelope = if (island && islandReveal > 0f && islandReveal < 1f) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val fontHeight = with(density) { (displayFont * 1.2f + if (options.translation && !line?.translation.isNullOrBlank()) desktopSubtitleSize(true, displayFont) * 1.2f else 0f).sp.toDp() }
        fontHeight + 28.dp + if (showChrome) 56.dp else 0.dp
    } else 0.dp
    Column(Modifier.fillMaxWidth().heightIn(min = morphEnvelope).then(material).then(idleGestures)
        .pointerInput(options.locked) {
            if (!options.locked) try {
                // Observe without consuming: buttons and lyric scrolling keep their gestures.
                while (true) awaitPointerEventScope {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    touching = event.changes.any { it.pressed }
                    if (touching) interaction++
                }
            } finally { touching = false }
        }.padding(horizontal = if (island) 0.dp else 4.dp, vertical = if (island) 0.dp else 8.dp).testTag("desktop_lyrics_window"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showChrome) Row(Modifier.fillMaxWidth().testTag("desktop_edit_toolbar")
            .graphicsLayer { alpha = chromeAlpha }
            .background(if (capsule) Color.White.copy(.06f) else Color(0xEB161A25), RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp).then(if (island) Modifier else Modifier.pointerInput(Unit) { detectDragGestures(
            onDragStart = { dragging = true }, onDragEnd = { finishDrag() }, onDragCancel = { finishDrag() }, onDrag = { change, amount -> change.consume(); drag(amount.x, amount.y) }) }),
            verticalAlignment = Alignment.CenterVertically) {
            BasicText(if (island) "灵动岛" else "⠿  拖动", Modifier.weight(1f).heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically), style = TextStyle(fontSize = 12.sp, color = Color.White.copy(.75f)))
            DesktopAction("设置", settings); DesktopAction("锁定", lock); DesktopAction("关闭", close)
        }
        val lyricBody: @Composable () -> Unit = {
            if (fpsMode) DesktopIslandFrameRate(frameRate.rate, displayFont, textColor)
            else if (island && line != null) {
                val nextIndex = remember(content.document, index) { desktopIslandNextLineIndex(content.document, index) }
                DesktopIslandCueHost(player.song?.id, index, line, nextIndex, content.document.lines.getOrNull(nextIndex), options to motion) { cue ->
                    DesktopIslandLyrics(cue, effective, options, motion, "desktop_window_translation")
                }
            }
            else if (line != null && interlude == null) key(player.song?.id, line) {
                var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                val scroll = rememberScrollState()
                val wordRow by remember(line, layout, effective) { derivedStateOf {
                    val now = effective.value
                    val word = line.words.lastOrNull { it.startTimeMs <= now }
                    layout?.let { measured -> word?.startOffset?.coerceIn(0, (line.text.length - 1).coerceAtLeast(0))?.let(measured::getLineForOffset) } ?: 0
                } }
                val revision = player.timelineRevision
                // A seek or late document goes directly to its current cue, never replaying
                // missed line entrances. Pause also freezes this player-time transition.
                val skipEntrance = remember(revision, options.offsetMs) { effective.value - line.startTimeMs > 450 || android.os.SystemClock.elapsedRealtime() - jumpTime < 450 }
                val entrance = remember(effective, line, skipEntrance, motion, stage) { derivedStateOf {
                    if (!motion || skipEntrance) 1f else ((effective.value - line.startTimeMs).toFloat() / if (stage) 320f else 200f)
                        .coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
                } }
                LaunchedEffect(layout, revision, player.playing, motion) {
                    val measured = layout ?: return@LaunchedEffect
                    snapshotFlow { wordRow }.distinctUntilChanged().collectLatest { row ->
                        val target = (measured.getLineTop(row) - (measured.getLineBottom(row) - measured.getLineTop(row)) * .25f).toInt().coerceIn(0, scroll.maxValue)
                        if (!motion || !player.playing || skipEntrance) scroll.scrollTo(target)
                        else scroll.animateScrollTo(target, tween(220))
                    }
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(desktopLineSpacing(island))) {
                    if (showChrome && !island) DesktopSongTitle(player.song?.name ?: "CurrentMusic", chromeAlpha)
                    Box(Modifier.fillMaxWidth().heightIn(max = minOf(maxTextHeight(), if (island) 160f else Float.MAX_VALUE).dp).clipToBounds()) {
                        KaraokeText(line, effective, animateWords = line.words.isNotEmpty(), effectPadding = effectPadding,
                            modifier = Modifier.fillMaxWidth().verticalScroll(scroll, enabled = showChrome)
                                .graphicsLayer {
                                    alpha = .65f + .35f * entrance.value
                                    translationY = (1f - entrance.value) * if (stage) 8.dp.toPx() else 3.dp.toPx()
                                },
                            fontSize = displayFont, textAlign = if (island) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start, fontWeight = FontWeight.Medium, reservedFontWeight = FontWeight.Medium,
                            forceLineAnimation = false, glow = motion && options.glowEnabled && options.glowStrength > 0f, wordLift = motion && stage,
                            accurateWordLift = true, glowOpacity = glowOpacity, baseOpacity = baseOpacity,
                            color = textColor, glowColor = Color(options.glowColor.toInt()), glowRadiusScale = desktopGlowSpread(island, options.glowSpread),
                            untimedLineGlow = options.glowEnabled && options.glowStrength > 0f,
                            onLayout = { if (layout != it) layout = it })
                    }
                    if (options.translation && !line.translation.isNullOrBlank()) BasicText(line.translation,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = effectPadding).testTag("desktop_window_translation"),
                        style = TextStyle(textAlign = if (island) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start, fontSize = desktopSubtitleSize(island, displayFont).sp, color = textColor.copy(.75f),
                            shadow = if (capsule) Shadow(Color.Black, blurRadius = 3f) else null), maxLines = if (island) 1 else 2, overflow = TextOverflow.Ellipsis)
                }
                if (showChrome && scroll.maxValue > 0 && line.words.isEmpty()) BasicText("长句可上下滑动",
                    Modifier.padding(horizontal = desktopHaloPadding), style = TextStyle(fontSize = 10.sp, color = Color.White.copy(.5f)))
            } else if (island) Spacer(Modifier.height(0.dp))
            else Column(Modifier.padding(horizontal = desktopHaloPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showChrome && !island) BasicText(player.song?.name ?: "CurrentMusic", Modifier.graphicsLayer { alpha = chromeAlpha }, style = TextStyle(fontSize = 11.sp, color = Color.White.copy(.55f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(message, Modifier.padding(vertical = 8.dp), style = TextStyle(fontSize = displayFont.sp, color = textColor,
                    shadow = if (capsule) Shadow(Color.Black.copy(.8f), blurRadius = 4f) else null))
            }
        }
        if (island) DesktopIslandFrame(player.song, Modifier.islandAnimatedWidth(islandAnimatedWidth).fillMaxWidth().align(Alignment.CenterHorizontally).onSizeChanged(onIslandSize)
            .graphicsLayer {
                val enter = islandEntrance?.value ?: 1f
                alpha = enter
                scaleX = .94f + .06f * enter; scaleY = scaleX
                transformOrigin = TransformOrigin(.5f, 0f)
            }, metadata = islandSize?.metadata, presentation = islandReveal, coverPosition = position, motion = motion,
            content = lyricBody)
        else lyricBody()
    }
}
@Composable private fun DesktopSongTitle(name: String, alpha: Float) {
    BasicText(name, Modifier.padding(horizontal = desktopHaloPadding).graphicsLayer { this.alpha = alpha }.testTag("desktop_window_title"),
        style = TextStyle(fontSize = 11.sp, color = Color.White.copy(.55f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
}
@Composable private fun DesktopAction(label: String, click: () -> Unit) {
    BasicText(label, Modifier.clickable(role = Role.Button, onClick = click).heightIn(min = 48.dp)
        .wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 8.dp),
        style = TextStyle(fontSize = 12.sp, color = Color.White.copy(.85f)))
}
