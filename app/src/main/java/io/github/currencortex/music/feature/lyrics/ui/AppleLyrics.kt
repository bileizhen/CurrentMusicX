package io.github.currencortex.music.feature.lyrics.ui

import android.os.Build
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.timeline.*
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
@Composable fun AppleLyrics(document: LyricsDocument, position: State<Long>, onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier, canSeek: Boolean = true, translation: Boolean = true,
    romanization: Boolean = false, wordAnimation: Boolean = true, effects: Boolean = true,
    fontSize: Float = LyricsTypography.DEFAULT_SIZE) {
    val size = LyricsTypography.normalize(fontSize)
    val timeline = remember(document) { LyricsTimeline(document) }
    val active by remember(timeline, position) { derivedStateOf { timeline.lineAt(position.value) } }
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
    LaunchedEffect(active, mode, document, size, translation, romanization) {
        if (mode != LyricsScrollMode.FOLLOWING || document.lines.isEmpty()) return@LaunchedEffect
        val target = active.coerceAtLeast(0)
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
                val focused = index == active
                val distance = if (mode == LyricsScrollMode.BROWSING) 1 else abs(index - active)
                val opacity = animateFloatAsState(if (focused) 1f else when (distance) { 1 -> .56f; 2 -> .35f; else -> .24f }, tween(350), label = "lyric focus")
                val scale = animateFloatAsState(if (focused) 1f else if (distance == 1) .97f else .95f,
                    spring(stiffness = 220f), label = "lyric scale")
                val blur = if (effects && Build.VERSION.SDK_INT >= 31 && mode == LyricsScrollMode.FOLLOWING && distance > 1) Modifier.blur(if (distance == 2) .7.dp else 1.3.dp) else Modifier
                Column(Modifier.fillMaxWidth().testTag("lyric_line_$index")
                    .semantics { selected = focused }
                    .clickable(enabled = canSeek, role = Role.Button) { onSeek(line.startTimeMs); mode = LyricsScrollMode.FOLLOWING }
                    .graphicsLayer { alpha = opacity.value; scaleX = scale.value; scaleY = scale.value; transformOrigin = TransformOrigin(0f, .5f) }
                    .then(blur)) {
                    KaraokeText(line, position, focused && wordAnimation, Modifier.fillMaxWidth(), size)
                    if (romanization && line.romanization.isNotBlank()) BasicText(line.romanization,
                        Modifier.padding(top = 6.dp), style = TextStyle(fontFamily = LyricsFontFamily, color = Color.White.copy(alpha = .5f), fontSize = (size * .53f).sp, lineHeight = (size * .75f).sp))
                    if (translation && line.translation.isNotBlank()) BasicText(line.translation,
                        Modifier.padding(top = 6.dp), style = TextStyle(fontFamily = LyricsFontFamily, color = Color.White.copy(alpha = .65f), fontSize = (size * .6f).sp, lineHeight = (size * .85f).sp))
                }
            }
        }
        if (mode == LyricsScrollMode.BROWSING) BasicText("回到当前歌词",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).testTag("lyrics_follow")
                .background(Color.White.copy(alpha = .14f), RoundedCornerShape(24.dp))
                .clickable(role = Role.Button) { mode = LyricsScrollMode.FOLLOWING }.padding(horizontal = 20.dp, vertical = 14.dp),
            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}
