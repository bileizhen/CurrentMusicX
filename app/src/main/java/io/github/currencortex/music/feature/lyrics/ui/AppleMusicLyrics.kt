// SPDX-License-Identifier: AGPL-3.0-only
// Native Apple Music presentation; AMLL reference and attribution in THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.feature.lyrics.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.lyrics.domain.LyricsInterlude
import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.ui.component.*
import kotlin.math.PI
import kotlin.math.sin

@Composable internal fun AppleMusicLyrics(document: LyricsDocument, position: State<Long>, onSeek: (Long) -> Unit,
    modifier: Modifier, canSeek: Boolean, translation: Boolean, romanization: Boolean,
    wordAnimation: Boolean, effects: Boolean, fontSize: Float, weightMode: LyricsWeight,
    lyricsOffsetMs: Long, display: LyricsDisplayOptions, mediaDurationMs: Long, playbackRunning: Boolean) {
    val motionEnabled = (rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    val timeline = remember(document, mediaDurationMs) {
        LyricsSynchronizer(if (document.metadata.durationMs == null && mediaDurationMs > 0)
            document.copy(metadata = document.metadata.copy(durationMs = mediaDurationMs)) else document)
    }
    val clock = remember(position, lyricsOffsetMs) { derivedStateOf { LyricsSynchronizer.lyricPosition(position.value, lyricsOffsetMs) } }
    val active by remember(timeline, clock) { derivedStateOf { timeline.activeLines(clock.value).toSet() } }
    val gap by remember(timeline, clock) { derivedStateOf {
        timeline.interlude(clock.value)?.takeIf { it.endTimeMs != null && it.endTimeMs - it.startTimeMs >= 7000 &&
            // The rail keeps the upcoming cue through the dots' exit fade. Returning to
            // the previous scroll target before the vocal starts creates a backward twitch.
            clock.value >= it.startTimeMs + 500 && clock.value < it.endTimeMs }
    } }
    val target by remember(timeline, clock) { derivedStateOf {
        // Keep simultaneous singers in the same reading group, rather than jumping to the last one.
        timeline.activeLines(clock.value).firstOrNull() ?: timeline.scrollTarget(clock.value)
    } }
    val gapTarget by remember(document, gap) { derivedStateOf {
        val end = gap?.endTimeMs
        if (end == null) target else document.lines.indexOfFirst { it.startTimeMs >= end }.takeIf { it >= 0 } ?: target
    } }
    val readingTarget = if (gap != null) gapTarget else target
    var browsing by remember(document) { mutableStateOf(false) }
    var followRequest by remember(document) { mutableIntStateOf(0) }
    var actionLine by remember(document) { mutableStateOf<Int?>(null) }
    Box(modifier.testTag("lyrics_panel")) {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("apple_music_lyrics")) {
            AppleMusicLyricsRail(document, readingTarget, active, Modifier.fillMaxSize(),
                fontSize, display.centered, effects && display.blur, display.stagger, motionEnabled,
                followRequest, gap != null, onBrowsing = { browsing = it }, options = display.appleMusic) { index, reading ->
                val line = document.lines[index]
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val browseSelected = reading && LocalAppleLyricsBrowseSelected.current
                val selectionShade = animateFloatAsState(if (pressed) .12f else if (browseSelected) .055f else 0f,
                    if (motionEnabled) tween(appleVisualDuration(180, display.appleMusic.animationSpeed)) else snap(),
                    label = "Apple lyric browsing selection")
                AppleMusicLyricLine(line, index, clock,
                    Modifier.fillMaxWidth().testTag("lyric_line_$index")
                        .semantics {
                            selected = index in active
                            this[AppleLyricsBrowseSelected] = browseSelected
                        }
                        .drawBehind {
                            drawRoundRect(Color.White.copy(alpha = selectionShade.value),
                                cornerRadius = CornerRadius(10.dp.toPx()))
                        }
                        .combinedClickable(interactionSource = interaction, indication = null,
                            role = Role.Button, onClickLabel = if (canSeek) "跳到此句" else "歌词操作",
                            onLongClickLabel = "复制或分享歌词", onLongClick = { actionLine = index }, onClick = {
                                if (canSeek) LyricsSynchronizer.seekPositionOrNull(line.startTimeMs, lyricsOffsetMs, mediaDurationMs)?.let {
                                    onSeek(it); followRequest++
                                } else actionLine = index
                            }),
                    fontSize = fontSize, translation = translation, romanization = romanization,
                    wordAnimation = wordAnimation, display = display, weightMode = weightMode,
                    motionEnabled = effects && motionEnabled, browsing = reading, paused = !playbackRunning,
                    held = active.isEmpty() && gap == null && index == target,
                    prepareInk = !reading && (index == readingTarget || index == readingTarget + 1))
            }
            if (gap != null && !browsing) AppleMusicInterlude(gap!!, clock, motionEnabled,
                Modifier.offset(y = maxHeight * .35f).padding(horizontal = 24.dp).fillMaxWidth().height(44.dp),
                right = document.lines.getOrNull(gapTarget)?.isDuet == true && !display.centered,
                speed = display.appleMusic.normalized().animationSpeed)
            if (browsing) BasicText("回到当前歌词", Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                .testTag("lyrics_follow").background(Color.White.copy(alpha = .17f), RoundedCornerShape(24.dp))
                .clickable(role = Role.Button) { followRequest++ }.padding(horizontal = 20.dp, vertical = 13.dp),
                style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = io.github.currencortex.music.ui.theme.LocalCustomFont.current))
        }
    }
    actionLine?.let { index -> AppleLyricsActions(document, index, translation) { actionLine = null } }
}

/** Three media-clock-driven dots; pause, rate changes and backward seeks need no animation reset. */
@Composable private fun AppleMusicInterlude(interval: LyricsInterlude, clock: State<Long>, motion: Boolean,
    modifier: Modifier, right: Boolean, speed: Float) {
    Canvas(modifier.testTag("lyrics_interlude").semantics { contentDescription = interval.label }) {
        val end = interval.endTimeMs ?: return@Canvas
        val now = clock.value
        val duration = (end - interval.startTimeMs).coerceAtLeast(1)
        val progress = ((now - interval.startTimeMs).toFloat() / duration).coerceIn(0f, 1f)
        // Only visual envelopes and breathing change speed. The real interval, its dot
        // progress and all vocal/seek timestamps stay anchored to the media clock.
        val exit = ((end - now) * speed / 650f).coerceIn(0f, 1f)
        val start = if (right) size.width - 54.dp.toPx() else 0f
        repeat(3) { dot ->
            val elapsed = now - interval.startTimeMs - 500
            val entrance = ((elapsed * speed - dot * 80) / 750f).coerceIn(0f, 1f)
            val lit = ((progress * 3 - dot) * 2).coerceIn(0f, 1f)
            val breath = if (motion) (.93f + .07f * sin(elapsed * speed / 1000.0 * PI * 1.4 - dot * .6).toFloat()) else 1f
            drawCircle(Color.White.copy(alpha = (.22f + .68f * lit) * entrance * exit),
                radius = 4.5.dp.toPx() * breath * if (motion) (.8f + .2f * entrance) else 1f,
                center = Offset(start + (dot * 18 + 6).dp.toPx(), size.height / 2))
        }
    }
}

@Composable private fun AppleLyricsActions(document: LyricsDocument, initial: Int, translation: Boolean, dismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember(document, initial) { mutableStateOf(setOf(initial)) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (initial - 1).coerceAtLeast(0))
    fun selectedText() = selected.sorted().joinToString("\n") { index ->
        document.lines[index].let { if (translation && it.translation.isNotBlank()) "${it.text}\n${it.translation}" else it.text }
    }
    MusicDialog("选择歌词", dismiss) {
        Column(Modifier.testTag("apple_lyrics_actions")) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp), state = list) {
                itemsIndexed(document.lines) { index, line ->
                    MusicDialogChoiceRow(line.text, index in selected, {
                        selected = if (index in selected && selected.size > 1) selected - index else selected + index
                    })
                }
            }
            MusicDialogButton("复制歌词", {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("歌词", selectedText()))
                dismiss()
            }, Modifier.fillMaxWidth())
            MusicDialogButton("分享歌词", {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, selectedText())
                }, "分享歌词"))
                dismiss()
            }, Modifier.fillMaxWidth())
        }
    }
}
