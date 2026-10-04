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
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.timeline.*
import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import kotlinx.coroutines.delay
import kotlin.math.abs

enum class LyricsScrollMode { FOLLOWING, BROWSING }

@Composable fun rememberLyricsPosition(player: PlayerState): State<Long> {
    val anchor = remember(player.song?.id, player.positionMs, player.playing, player.loading, player.durationMs, player.playbackSpeed) {
        PlaybackAnchor(player.positionMs, SystemClock.elapsedRealtime(), player.playing && !player.loading, player.durationMs, player.playbackSpeed)
    }
    val position = remember { mutableLongStateOf(player.positionMs) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(anchor, lifecycle) {
        position.longValue = anchor.positionMs
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (anchor.playing) while (true) withFrameNanos {
                position.longValue = anchor.positionAt(SystemClock.elapsedRealtime())
            }
        }
    }
    return position
}

/** Foundation-only lyric viewport; playback and network ownership stay outside the renderer. */
@Composable fun LyricsScreen(document: LyricsDocument, position: State<Long>, onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier, canSeek: Boolean = true, translation: Boolean = true,
    romanization: Boolean = false, wordAnimation: Boolean = true, effects: Boolean = true,
    fontSize: Float = LyricsTypography.DEFAULT_SIZE, weightMode: LyricsWeight = LyricsWeight.CURRENT, lyricsOffsetMs: Long = 0) {
    val size = LyricsTypography.normalize(fontSize)
    val timeline = remember(document) { LyricsSynchronizer(document) }
    val effectivePosition = remember(position, lyricsOffsetMs) { derivedStateOf { LyricsSynchronizer.effectivePosition(position.value, lyricsOffsetMs) } }
    val activeLines by remember(timeline, effectivePosition) { derivedStateOf { timeline.activeLines(effectivePosition.value) } }
    val target by remember(timeline, effectivePosition) { derivedStateOf { timeline.scrollTarget(effectivePosition.value) } }
    val interlude by remember(timeline, effectivePosition) { derivedStateOf { timeline.interlude(effectivePosition.value) } }
    val list = rememberLazyListState()
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
    LaunchedEffect(target, mode, document, size, translation, romanization, weightMode, lyricsOffsetMs) {
        if (mode != LyricsScrollMode.FOLLOWING || document.lines.isEmpty()) return@LaunchedEffect
        // Allow a changed font or auxiliary line to reflow before calculating the anchor.
        withFrameNanos { }; withFrameNanos { }
        val visible = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
        if (visible == null) list.animateScrollToItem(target)
        else list.scroll {
            var previous = 0f
            animate(0f, visible.offset.toFloat(), animationSpec = spring(dampingRatio = .88f, stiffness = 160f)) { value, _ ->
                scrollBy(value - previous)
                previous = value
            }
        }
    }
    BoxWithConstraints(modifier.testTag("lyrics_panel")) {
        val anchorPadding = maxHeight * .40f
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("lyrics_list")
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0f to Color.Transparent, .10f to Color.Black,
                    .87f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            }, contentPadding = PaddingValues(top = anchorPadding, bottom = maxHeight * .6f,
                start = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            itemsIndexed(document.lines, key = { index, line -> "$index:${line.startTimeMs}" }) { index, line ->
                val distance = if (mode == LyricsScrollMode.BROWSING) 1 else abs(index - target)
                LyricsLineView(line, index, effectivePosition, index in activeLines, distance,
                    mode == LyricsScrollMode.BROWSING, canSeek, {
                        onSeek(LyricsSynchronizer.seekPosition(it, lyricsOffsetMs)); mode = LyricsScrollMode.FOLLOWING
                    }, translation, romanization, wordAnimation, effects, size, weightMode)
            }
        }
        if (interlude != null && mode == LyricsScrollMode.FOLLOWING)
            LyricsInterludeView(interlude!!, effectivePosition, Modifier.align(Alignment.TopStart).padding(16.dp))
        if (mode == LyricsScrollMode.BROWSING) BasicText("回到当前歌词",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).testTag("lyrics_follow")
                .background(Color.White.copy(alpha = .14f), RoundedCornerShape(24.dp))
                .clickable(role = Role.Button) { mode = LyricsScrollMode.FOLLOWING }.padding(horizontal = 20.dp, vertical = 14.dp),
            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}
