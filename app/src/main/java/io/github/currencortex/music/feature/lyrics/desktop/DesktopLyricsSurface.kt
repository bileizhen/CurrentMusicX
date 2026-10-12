package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import io.github.currencortex.music.feature.lyrics.ui.rememberLyricsPosition
import io.github.currencortex.music.feature.lyrics.ui.KaraokeText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow

@Composable internal fun DesktopLyricsSurface(lyrics: StateFlow<PlaybackLyricsState>, playback: StateFlow<PlayerState>,
    preferences: StateFlow<DesktopLyricsSettings>, maxTextHeight: () -> Float,
    drag: (Float, Float) -> Unit, release: () -> Unit, lock: () -> Unit, close: () -> Unit, settings: () -> Unit) {
    val content by lyrics.collectAsStateWithLifecycle()
    val player by playback.collectAsStateWithLifecycle()
    val options by preferences.collectAsStateWithLifecycle()
    val position = rememberLyricsPosition(player)
    val timeline = remember(content.document) { LyricsSynchronizer(content.document) }
    val effective = remember(position, options.offsetMs) { derivedStateOf { LyricsSynchronizer.effectivePosition(position.value, options.offsetMs) } }
    val index by remember(timeline, effective) { derivedStateOf { timeline.findCurrentLine(effective.value) } }
    val line = content.document.lines.getOrNull(index).takeIf { content.songId == player.song?.id }
    val interlude by remember(timeline, effective) { derivedStateOf { timeline.interlude(effective.value)?.label } }
    val jumpTime = remember(player.timelineRevision) { android.os.SystemClock.elapsedRealtime() }
    val motion = rememberDesktopMotion()
    val stage = options.effect == DesktopEffect.STAGE
    val glowOpacity = remember(stage) { mutableFloatStateOf(if (stage) 1f else .5f) }
    val baseOpacity = remember(stage) { mutableFloatStateOf(if (stage) .35f else .55f) }
    val shape = RoundedCornerShape(22.dp)
    val capsule = options.appearance == DesktopAppearance.CAPSULE
    val material = if (capsule) Modifier.background(Brush.linearGradient(listOf(Color(0xCC222538), Color(0xAA10131F))), shape)
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.22f), Color.White.copy(.03f))), shape) else Modifier
    Column(Modifier.fillMaxWidth().then(material).padding(14.dp).testTag("desktop_lyrics_window"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!options.locked) Row(Modifier.fillMaxWidth().pointerInput(Unit) { detectDragGestures(
            onDragEnd = release, onDragCancel = release, onDrag = { change, amount -> change.consume(); drag(amount.x, amount.y) }) },
            verticalAlignment = Alignment.CenterVertically) {
            BasicText("⠿  拖动", Modifier.weight(1f).padding(vertical = 8.dp), style = TextStyle(fontSize = 12.sp, color = Color.White.copy(.65f)))
            DesktopAction("设置", settings); DesktopAction("锁定", lock); DesktopAction("关闭", close)
        }
        BasicText(player.song?.name ?: "CurrentMusic", style = TextStyle(fontSize = 11.sp, color = Color.White.copy(.55f)), maxLines = 1)
        if (line != null && interlude == null) key(player.song?.id, line) {
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
            Box(Modifier.fillMaxWidth().heightIn(max = maxTextHeight().dp).clipToBounds()) {
                KaraokeText(line, effective, animateWords = line.words.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().verticalScroll(scroll, enabled = !options.locked).padding(vertical = 8.dp)
                        .graphicsLayer {
                            alpha = .65f + .35f * entrance.value
                            translationY = (1f - entrance.value) * if (stage) 8.dp.toPx() else 3.dp.toPx()
                        },
                    fontSize = options.fontSize, fontWeight = FontWeight.Medium, reservedFontWeight = FontWeight.Medium,
                    forceLineAnimation = false, glow = motion, wordLift = motion && stage,
                    accurateWordLift = true, glowOpacity = glowOpacity, baseOpacity = baseOpacity,
                    outline = !capsule, onLayout = { if (layout != it) layout = it })
            }
            if (options.translation && !line.translation.isNullOrBlank()) BasicText(line.translation,
                style = TextStyle(fontSize = (options.fontSize * .58f).sp, color = Color.White.copy(.75f),
                    shadow = Shadow(Color.Black, blurRadius = 3f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!options.locked && scroll.maxValue > 0 && line.words.isEmpty()) BasicText("长句可上下滑动",
                style = TextStyle(fontSize = 10.sp, color = Color.White.copy(.5f)))
        } else BasicText(when {
            player.song == null -> "等待播放"
            content.loading || content.songId != player.song?.id -> "正在读取歌词"
            content.document.lines.isEmpty() -> "暂无歌词 · 聆听音乐"
            else -> interlude ?: "♪"
        }, style = TextStyle(fontSize = options.fontSize.sp, color = Color.White,
            shadow = Shadow(Color.Black.copy(.8f), blurRadius = 4f)))
    }
}
@Composable private fun DesktopAction(label: String, click: () -> Unit) {
    BasicText(label, Modifier.clickable(role = Role.Button, onClick = click).heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 10.dp),
        style = TextStyle(fontSize = 12.sp, color = Color.White.copy(.85f)))
}
