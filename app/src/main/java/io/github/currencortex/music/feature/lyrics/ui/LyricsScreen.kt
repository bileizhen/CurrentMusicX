package io.github.currencortex.music.feature.lyrics.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

enum class LyricsScrollMode { FOLLOWING, BROWSING }

internal val LyricsPerspectiveAngle = SemanticsPropertyKey<Float>("LyricsPerspectiveAngle")

@Composable fun rememberLyricsPosition(player: PlayerState, sampleDisplayFrames: Boolean = false,
    onFrameNanos: ((Long) -> Unit)? = null): State<Long> {
    val frameObserver = rememberUpdatedState(onFrameNanos)
    val anchor = remember(player.song?.id, player.positionMs, player.playing, player.loading, player.durationMs, player.playbackSpeed, player.anchorRealtimeMs, player.timelineRevision) {
        PlaybackAnchor(player.positionMs, player.anchorRealtimeMs.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            player.playing && !player.loading, player.durationMs, player.playbackSpeed, player.timelineRevision)
    }
    val position = remember { mutableLongStateOf(player.positionMs) }
    val clock = remember { LyricsPlaybackClock() }
    SideEffect {
        clock.update(player.song?.id, anchor)
        position.longValue = clock.positionAt(SystemClock.elapsedRealtime())
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(player.song?.id, anchor.playing, lifecycle, sampleDisplayFrames) {
        position.longValue = clock.positionAt(SystemClock.elapsedRealtime())
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            frameObserver.value?.invoke(0L)
            if (anchor.playing || sampleDisplayFrames) while (true) {
                // FPS-only mode needs real display callbacks even while paused.
                // It uses this same clock loop, not a second animation driver.
                val frame = if (sampleDisplayFrames) awaitLyricsDisplayFrame() else withFrameNanos { it }
                position.longValue = clock.positionAt(SystemClock.elapsedRealtime())
                frameObserver.value?.invoke(frame)
            }
        }
    }
    return position
}

private suspend fun awaitLyricsDisplayFrame(): Long = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
    val choreographer = android.view.Choreographer.getInstance()
    val callback = android.view.Choreographer.FrameCallback { time ->
        if (continuation.isActive) continuation.resumeWith(Result.success(time))
    }
    continuation.invokeOnCancellation { choreographer.removeFrameCallback(callback) }
    choreographer.postFrameCallback(callback)
}

/** Foundation-only lyric viewport; playback and network ownership stay outside the renderer. */
@Composable fun LyricsScreen(document: LyricsDocument, position: State<Long>, onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier, canSeek: Boolean = true, translation: Boolean = true,
    romanization: Boolean = false, wordAnimation: Boolean = true, effects: Boolean = true,
    fontSize: Float = LyricsTypography.DEFAULT_SIZE, weightMode: LyricsWeight = LyricsWeight.CURRENT, lyricsOffsetMs: Long = 0,
    display: LyricsDisplayOptions = LyricsDisplayOptions(), minimal: Boolean = false, perspective: Boolean = false,
    cover: String = "") {
    val smokeColors = if (display.smoke || display.particles) rememberLyricsSmokeColors(cover) else null
    val size = LyricsTypography.normalize(fontSize)
    val timeline = remember(document) { LyricsSynchronizer(document) }
    val effectivePosition = remember(position, lyricsOffsetMs) { derivedStateOf { LyricsSynchronizer.effectivePosition(position.value, lyricsOffsetMs) } }
    val activeLines by remember(timeline, effectivePosition) { derivedStateOf { timeline.activeLines(effectivePosition.value) } }
    val target by remember(timeline, effectivePosition) { derivedStateOf { timeline.scrollTarget(effectivePosition.value) } }
    val interlude by remember(timeline, effectivePosition) { derivedStateOf { timeline.interlude(effectivePosition.value) } }
    if (display.smoke || display.particles) {
        SmokeLyricsStage(document, target, effectivePosition, onSeek = { onSeek(LyricsSynchronizer.seekPosition(it, lyricsOffsetMs)) },
            modifier = modifier, canSeek = canSeek, translation = translation, romanization = romanization,
            fontSize = size, weightMode = weightMode, display = display.normalized(), colors = smokeColors,
            particles = display.particles)
        return
    }
    var mode by remember(document) { mutableStateOf(LyricsScrollMode.FOLLOWING) }
    var followRequest by remember(document) { mutableIntStateOf(0) }
    Box(modifier.testTag("lyrics_panel")) {
        MonetLyricsRail(document, target, activeLines.toSet(), Modifier.fillMaxSize(), size,
            display.centered, minimal, perspective, effects, followRequest,
            onBrowsing = { mode = if (it) LyricsScrollMode.BROWSING else LyricsScrollMode.FOLLOWING },
            expandWindow = display.hideControls && !minimal) { index, browsing, tone ->
            val line = document.lines[index]
            LyricsLineView(line, index, effectivePosition, index in activeLines,
                !browsing && activeLines.isEmpty() && index == target, kotlin.math.abs(index - target),
                canSeek, {
                    onSeek(LyricsSynchronizer.seekPosition(it, lyricsOffsetMs)); followRequest++
                }, translation, romanization, wordAnimation, effects, size, weightMode, display, monetTone = tone)
        }
        if (!minimal && interlude != null && mode == LyricsScrollMode.FOLLOWING)
            LyricsInterludeView(interlude!!, effectivePosition, Modifier.align(Alignment.TopStart).padding(16.dp))
        if (!minimal && mode == LyricsScrollMode.BROWSING) BasicText("回到当前歌词",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).testTag("lyrics_follow")
                .background(Color.White.copy(alpha = .14f), RoundedCornerShape(24.dp))
                .clickable(role = Role.Button) { followRequest++ }.padding(horizontal = 20.dp, vertical = 14.dp),
            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}
