package io.github.currencortex.music.feature.lyrics.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import io.github.currencortex.music.feature.lyrics.domain.withDetectedChorus
import io.github.currencortex.music.feature.lyrics.data.SongLyricsStore

internal val LyricsPerspectiveAngle = SemanticsPropertyKey<Float>("LyricsPerspectiveAngle")

@Composable fun rememberLyricsPosition(player: PlayerState, sampleDisplayFrames: Boolean = false,
    onFrameNanos: ((Long) -> Unit)? = null): State<Long> {
    val frameObserver = rememberUpdatedState(onFrameNanos)
    // Like Folia's displayed-song read-head, reset exactly when the displayed playback changes.
    // Platform-local ids may coincide, and two Navidrome libraries can expose the same track id.
    val songKey = remember(player.song) { player.song?.let(SongLyricsStore::identity) }
    val identity = remember(songKey, player.mode) { songKey to player.mode }
    val anchor = remember(identity, player.positionMs, player.playing, player.loading, player.durationMs, player.playbackSpeed, player.anchorRealtimeMs, player.timelineRevision) {
        PlaybackAnchor(player.positionMs, player.anchorRealtimeMs.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            player.playing && !player.loading, player.durationMs, player.playbackSpeed, player.timelineRevision)
    }
    val position = remember { mutableLongStateOf(player.positionMs) }
    val clock = remember { LyricsPlaybackClock() }
    SideEffect {
        clock.update(identity, anchor)
        position.longValue = clock.positionAt(SystemClock.elapsedRealtime())
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(identity, anchor.playing, lifecycle, sampleDisplayFrames) {
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
    cover: String = "", mediaDurationMs: Long = 0, playbackRunning: Boolean = true,
    rhythmGlow: State<Float>? = null) {
    // Resolve the quality once, then render with the reduced options; the saved preference is untouched.
    val context = LocalContext.current
    val lowRam = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }
    val renderQuality = remember(display.renderQuality, lowRam) {
        effectiveLyricsRenderQuality(display.renderQuality, lowRam, android.os.Build.VERSION.SDK_INT)
    }
    val renderDisplay = remember(display, renderQuality) { display.withRenderQuality(renderQuality) }
    // Blur is the expensive part; a POWER_SAVER device must not keep drawing it just because the
    // saved preference asked for it.
    val renderEffects = effects && renderDisplay.blur
    // Chorus detection runs once per document; it only tags roles, it never rewrites the text.
    val renderedDocument = remember(document) { document.withDetectedChorus() }
    if (renderDisplay.style == io.github.currencortex.music.data.settings.LyricsStyle.APPLE_MUSIC) {
        AppleMusicLyrics(renderedDocument, position, onSeek, modifier, canSeek, translation, romanization,
            wordAnimation, renderEffects, LyricsTypography.normalize(fontSize), weightMode,
            lyricsOffsetMs, renderDisplay, mediaDurationMs, playbackRunning)
        return
    }
    // The saved mode picks the stage: flipping it to power saver must not move the lyrics layout.
    val smokeStage = display.smoke || display.particles
    val smokeEffects = renderDisplay.smoke || renderDisplay.particles
    val smokeColors = if (smokeStage) rememberLyricsSmokeColors(cover).takeIf { smokeEffects } else null
    val size = LyricsTypography.normalize(fontSize)
    val timeline = remember(document) { LyricsSynchronizer(document) }
    val effectivePosition = remember(position, lyricsOffsetMs) { derivedStateOf { LyricsSynchronizer.lyricPosition(position.value, lyricsOffsetMs) } }
    // While the reader holds a manual anchor, every highlight derives from that line, not the playing position.
    var browseAnchor by remember(document) { mutableStateOf<Int?>(null) }
    val visualPosition = remember(document, effectivePosition) { derivedStateOf {
        val anchor = browseAnchor
        if (anchor != null && anchor in document.lines.indices) document.lines[anchor].startTimeMs + 1
        else effectivePosition.value
    } }
    val activeLines by remember(timeline, visualPosition) { derivedStateOf { timeline.activeLines(visualPosition.value) } }
    val target by remember(timeline, visualPosition) { derivedStateOf { timeline.scrollTarget(visualPosition.value) } }
    val playbackTarget by remember(timeline, effectivePosition) { derivedStateOf { timeline.scrollTarget(effectivePosition.value) } }
    val interlude by remember(timeline, effectivePosition) { derivedStateOf { timeline.interlude(effectivePosition.value) } }
    if (smokeStage) {
        SmokeLyricsStage(renderedDocument, target, effectivePosition, onSeek = {
            LyricsSynchronizer.seekPositionOrNull(it, lyricsOffsetMs, mediaDurationMs)?.let(onSeek)
        },
            modifier = modifier, canSeek = canSeek, translation = translation, romanization = romanization,
            fontSize = size, weightMode = weightMode, display = renderDisplay.normalized(), colors = smokeColors,
            particles = renderDisplay.particles)
        return
    }
    var followRequest by remember(document) { mutableIntStateOf(0) }
    val browsingOffStage = remember { mutableLongStateOf(-1L) }
    Box(modifier.testTag("lyrics_panel")) {
        MonetLyricsRail(renderedDocument, target, activeLines.toSet(), Modifier.fillMaxSize(), size,
            renderDisplay.centered, minimal, perspective, renderEffects, followRequest,
            onBrowse = { browseAnchor = it },
            expandWindow = renderDisplay.hideControls && !minimal, playbackTarget = playbackTarget) { index, browsing, tone, focused, focusIndex ->
            val line = renderedDocument.lines[index]
            // Browsing rows never self-match: bold, glow, karaoke fill and the translation slot
            // belong to the sung line alone. The selection reads through the rail tone lift only.
            LyricsLineView(line, index, if (browsing) browsingOffStage else visualPosition, focused,
                !browsing && activeLines.isEmpty() && index == target, kotlin.math.abs(index - focusIndex),
                canSeek, {
                    LyricsSynchronizer.seekPositionOrNull(it, lyricsOffsetMs, mediaDurationMs)?.let { target ->
                        onSeek(target); followRequest++
                    }
                }, translation, romanization, wordAnimation, renderEffects, size, weightMode, renderDisplay, monetTone = tone,
                rhythmGlow = rhythmGlow,
                browsing = browsing)
        }
        if (!minimal && interlude != null && browseAnchor == null)
            LyricsInterludeView(interlude!!, effectivePosition, Modifier.align(Alignment.TopStart).padding(16.dp))
        if (!minimal && browseAnchor != null) BasicText("回到当前歌词",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).testTag("lyrics_follow")
                .background(Color.White.copy(alpha = .14f), RoundedCornerShape(24.dp))
                .clickable(role = Role.Button) { followRequest++ }.padding(horizontal = 20.dp, vertical = 14.dp),
            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = io.github.currencortex.music.ui.theme.LocalCustomFont.current))
    }
}
