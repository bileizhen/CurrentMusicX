package io.github.currencortex.music.feature.lyrics.ui

import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.settings.LyricsTypography
import io.github.currencortex.music.data.settings.LyricsWeight
import io.github.currencortex.music.data.settings.LyricsDisplayOptions
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.timeline.*
import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.abs
import kotlin.math.sin

enum class LyricsScrollMode { FOLLOWING, BROWSING }

internal class LyricsScrollMotion {
    var progress by mutableFloatStateOf(1f)
    var direction = 1f
    fun lag(distance: Int): Float =
        sin(progress.coerceIn(0f, 1f) * Math.PI).toFloat() * distance.coerceIn(0, 4) * direction
}
internal val LyricsPerspectiveAngle = SemanticsPropertyKey<Float>("LyricsPerspectiveAngle")

@Composable fun rememberLyricsPosition(player: PlayerState): State<Long> {
    val anchor = remember(player.song?.id, player.positionMs, player.playing, player.loading, player.durationMs, player.playbackSpeed) {
        PlaybackAnchor(player.positionMs, SystemClock.elapsedRealtime(), player.playing && !player.loading, player.durationMs, player.playbackSpeed)
    }
    val position = remember { mutableLongStateOf(player.positionMs) }
    val clock = remember { LyricsPlaybackClock() }
    SideEffect {
        clock.update(player.song?.id, anchor)
        if (!anchor.playing) position.longValue = clock.positionAt(SystemClock.elapsedRealtime())
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(player.song?.id, anchor.playing, lifecycle) {
        position.longValue = clock.positionAt(SystemClock.elapsedRealtime())
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (anchor.playing) while (true) withFrameNanos {
                position.longValue = clock.positionAt(SystemClock.elapsedRealtime())
            }
        }
    }
    return position
}

/** Foundation-only lyric viewport; playback and network ownership stay outside the renderer. */
@Composable fun LyricsScreen(document: LyricsDocument, position: State<Long>, onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier, canSeek: Boolean = true, translation: Boolean = true,
    romanization: Boolean = false, wordAnimation: Boolean = true, effects: Boolean = true,
    fontSize: Float = LyricsTypography.DEFAULT_SIZE, weightMode: LyricsWeight = LyricsWeight.CURRENT, lyricsOffsetMs: Long = 0,
    display: LyricsDisplayOptions = LyricsDisplayOptions(), minimal: Boolean = false, perspective: Boolean = false) {
    val size = LyricsTypography.normalize(fontSize)
    val timeline = remember(document) { LyricsSynchronizer(document) }
    val effectivePosition = remember(position, lyricsOffsetMs) { derivedStateOf { LyricsSynchronizer.effectivePosition(position.value, lyricsOffsetMs) } }
    val activeLines by remember(timeline, effectivePosition) { derivedStateOf { timeline.activeLines(effectivePosition.value) } }
    val target by remember(timeline, effectivePosition) { derivedStateOf { timeline.scrollTarget(effectivePosition.value) } }
    val interlude by remember(timeline, effectivePosition) { derivedStateOf { timeline.interlude(effectivePosition.value) } }
    val list = rememberLazyListState()
    val scrollMotion = remember { LyricsScrollMotion() }
    var mode by remember(document) { mutableStateOf(LyricsScrollMode.FOLLOWING) }
    LaunchedEffect(list, document) {
        list.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) mode = LyricsScrollMode.BROWSING
        }
    }
    LaunchedEffect(mode, list.isScrollInProgress) {
        if (mode == LyricsScrollMode.BROWSING && !list.isScrollInProgress) {
            delay(4000)
            mode = LyricsScrollMode.FOLLOWING
        }
    }
    BoxWithConstraints(modifier.testTag("lyrics_panel")) {
    LaunchedEffect(target, mode, document, size, romanization, weightMode, lyricsOffsetMs,
        display.centered, display.fontWeight, minimal, maxWidth, maxHeight) {
        if (mode != LyricsScrollMode.FOLLOWING || document.lines.isEmpty()) return@LaunchedEffect
        // Wait for the first layout only. Normal line changes start on the next animation frame.
        snapshotFlow { list.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        try {
            // Seeking outside the viewport needs one relocation, not two competing scrolls.
            if (list.layoutInfo.visibleItemsInfo.none { it.index == target }) list.scrollToItem(target)
            var entering = true
            // Translation height changes are coalesced while a line entrance is running.
            // Afterwards, resizing follows the same height animation without restarting
            // the scroll tween each frame or leaving a hidden translation at the anchor.
            snapshotFlow { list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }?.size }
                .filterNotNull().collect {
                    val entrance = entering
                    entering = false
                    val visible = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target } ?: return@collect
                    val travel = visible.offset + if (minimal) visible.size / 2f else 0f
                    if (abs(travel) < .5f) return@collect
                    list.scroll {
                        if (!entrance) {
                            scrollBy(travel)
                            return@scroll
                        }
                        scrollMotion.direction = if (travel < 0) -1f else 1f
                        scrollMotion.progress = 0f
                        var previous = 0f
                        animate(0f, 1f, animationSpec = tween(460, easing = CubicBezierEasing(.22f, 0f, .18f, 1f))) { progress, _ ->
                            val row = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
                            if (row != null) {
                                val remaining = row.offset + if (minimal) row.size / 2f else 0f
                                // Use current geometry as old/new translations resize during
                                // the entrance; the final frame lands on the actual paragraph.
                                val fraction = ((progress - previous) / (1f - previous).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
                                scrollBy(remaining * fraction)
                            }
                            previous = progress
                            scrollMotion.progress = progress
                        }
                        scrollMotion.progress = 1f
                    }
                }
        } finally { scrollMotion.progress = 1f }
    }
        val anchorPadding = maxHeight * if (minimal) .5f else .4f
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("lyrics_list")
            .semantics { this[LyricsPerspectiveAngle] = if (perspective) LYRIC_PERSPECTIVE_ANGLE else 0f }
            .graphicsLayer {
                // One rigid plane and one camera. Every glyph shares this 3D
                // projection, with no per-row roll, pitch or artificial depth scale.
                rotationY = if (perspective) LYRIC_PERSPECTIVE_ANGLE else 0f
                cameraDistance = (this.size.width / 48f).coerceAtLeast(1f)
                // A fixed camera at the portrait reading line. Wrapped paragraphs,
                // translation resizing and browsing never move its vanishing point.
                transformOrigin = TransformOrigin(0f,
                    if (minimal) .5f else (.4f + size.sp.toPx() * ACTIVE_LYRIC_SCALE * .6f /
                        this.size.height.coerceAtLeast(1f)).coerceIn(0f, 1f))
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0f to Color.Transparent, .10f to Color.Black,
                    .87f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            }, contentPadding = PaddingValues(top = anchorPadding, bottom = maxHeight - anchorPadding,
                // Reserve the near edge for the whole plane, including centered
                // paragraphs and right-aligned duet vocals, not just left-aligned text.
                start = 8.dp, end = 8.dp + if (perspective) maxWidth * .16f else 0.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            itemsIndexed(document.lines, key = { index, line -> "$index:${line.startTimeMs}" }) { index, line ->
                val distance = if (mode == LyricsScrollMode.BROWSING) 1 else abs(index - target)
                val resting = mode == LyricsScrollMode.FOLLOWING && activeLines.isEmpty() && index == target
                LyricsLineView(line, index, effectivePosition, index in activeLines, resting, distance,
                    mode == LyricsScrollMode.BROWSING, canSeek, {
                        onSeek(LyricsSynchronizer.seekPosition(it, lyricsOffsetMs)); mode = LyricsScrollMode.FOLLOWING
                    }, translation, romanization, wordAnimation, effects, size, weightMode, display, scrollMotion)
            }
        }
        if (!minimal && interlude != null && mode == LyricsScrollMode.FOLLOWING)
            LyricsInterludeView(interlude!!, effectivePosition, Modifier.align(Alignment.TopStart).padding(16.dp))
        if (!minimal && mode == LyricsScrollMode.BROWSING) BasicText("回到当前歌词",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).testTag("lyrics_follow")
                .background(Color.White.copy(alpha = .14f), RoundedCornerShape(24.dp))
                .clickable(role = Role.Button) { mode = LyricsScrollMode.FOLLOWING }.padding(horizontal = 20.dp, vertical = 14.dp),
            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}
