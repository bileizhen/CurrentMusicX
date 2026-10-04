package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import io.github.currencortex.music.data.settings.LyricsWeight
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import kotlin.math.*

internal const val ACTIVE_LYRIC_SCALE = 1.12f

@Composable internal fun LyricsLineView(line: LyricLine, index: Int, position: State<Long>, focused: Boolean,
    distance: Int, browsing: Boolean, canSeek: Boolean, onSeek: (Long) -> Unit, translation: Boolean,
    romanization: Boolean, wordAnimation: Boolean, effects: Boolean, fontSize: Float, weightMode: LyricsWeight) {
    val opacity = animateFloatAsState(if (focused) 1f else when (distance) { 1 -> .56f; 2 -> .35f; else -> .24f }, tween(350), label = "lyric focus")
    val scale = animateFloatAsState(if (focused) ACTIVE_LYRIC_SCALE else if (distance == 1) .96f else .94f,
        spring(stiffness = 220f), label = "lyric scale")
    val blur = if (effects && android.os.Build.VERSION.SDK_INT >= 31 && !browsing && distance > 1)
        Modifier.blur(if (distance == 2) .7.dp else 1.3.dp) else Modifier
    Box(Modifier.fillMaxWidth().testTag("lyric_line_$index").semantics { selected = focused }
        .clickable(enabled = canSeek, role = Role.Button) { onSeek(line.startTimeMs) }) {
        Column(Modifier.fillMaxWidth().layout { measurable, constraints ->
            val width = (constraints.maxWidth / ACTIVE_LYRIC_SCALE).roundToInt()
            val child = measurable.measure(constraints.copy(minWidth = minOf(constraints.minWidth, width), maxWidth = width))
            val height = constraints.constrainHeight(ceil(child.height * ACTIVE_LYRIC_SCALE).toInt())
            layout(constraints.maxWidth, height) { child.placeRelative(if (line.isDuet) constraints.maxWidth - child.width else 0, (height - child.height) / 2) }
        }.graphicsLayer {
            alpha = opacity.value; scaleX = scale.value; scaleY = scale.value
            transformOrigin = TransformOrigin(if (line.isDuet) 1f else 0f, .5f)
        }.then(blur)) {
            LyricsVocalView(line, position, focused, translation, romanization, wordAnimation,
                if (line.isBackground) fontSize * .75f else fontSize, weightMode)
            line.backgroundVocals.forEachIndexed { bgIndex, bg ->
                val bgFocused by remember(bg, position) { derivedStateOf { position.value >= bg.startTimeMs && position.value < bg.endTimeMs } }
                Column(Modifier.padding(top = 10.dp).testTag("lyric_bg_${index}_$bgIndex")
                    .semantics { selected = bgFocused }
                    .clickable(enabled = canSeek, role = Role.Button) { onSeek(bg.startTimeMs) }
                    .graphicsLayer { alpha = if (bgFocused) 1f else .55f }) {
                    LyricsVocalView(bg, position, bgFocused, translation, romanization, wordAnimation, fontSize * .75f, weightMode)
                }
            }
        }
    }
}

@Composable private fun LyricsVocalView(line: LyricLine, position: State<Long>, focused: Boolean,
    translation: Boolean, romanization: Boolean, wordAnimation: Boolean, size: Float, weightMode: LyricsWeight) {
    val weight = lyricsFontWeight(weightMode, focused)
    val align = if (line.isDuet) TextAlign.End else TextAlign.Start
    if (line.text.isNotBlank()) KaraokeText(line, position, focused && wordAnimation, Modifier.fillMaxWidth(), size, weight, align)
    if (translation && line.translation.isNotBlank()) BasicText(line.translation, Modifier.fillMaxWidth().padding(top = 6.dp),
        style = TextStyle(fontFamily = LyricsFontFamily, fontWeight = weight, color = Color.White.copy(alpha = .65f),
            fontSize = (size * .6f).sp, lineHeight = (size * .85f).sp, textAlign = align))
    if (romanization && line.romanization.isNotBlank()) BasicText(line.romanization, Modifier.fillMaxWidth().padding(top = 6.dp),
        style = TextStyle(fontFamily = LyricsFontFamily, fontWeight = weight, color = Color.White.copy(alpha = .5f),
            fontSize = (size * .53f).sp, lineHeight = (size * .75f).sp, textAlign = align))
}
