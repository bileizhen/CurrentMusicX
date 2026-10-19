package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import io.github.currencortex.music.data.settings.LyricsWeight
import io.github.currencortex.music.data.settings.LyricsDisplayOptions
import io.github.currencortex.music.data.settings.KaraokeScope
import io.github.currencortex.music.feature.lyrics.model.LyricLine

@Composable internal fun LyricsLineView(line: LyricLine, index: Int, position: State<Long>, focused: Boolean,
    resting: Boolean, distance: Int, canSeek: Boolean, onSeek: (Long) -> Unit, translation: Boolean,
    romanization: Boolean, wordAnimation: Boolean, effects: Boolean, fontSize: Float, weightMode: LyricsWeight,
    display: LyricsDisplayOptions, monetTone: MonetTone) {
    val mainFocused by remember(line, position) {
        derivedStateOf { position.value >= line.startTimeMs && position.value < line.endTimeMs }
    }
    Box(Modifier.fillMaxWidth().testTag("lyric_line_$index").semantics { selected = focused }
        .clickable(enabled = canSeek, role = Role.Button) { onSeek(line.startTimeMs) }) {
        // Keep the app's existing effective font size. Monet owns the independent track scale outside.
        Column(Modifier.fillMaxWidth().layout { measurable, constraints ->
            val width = (constraints.maxWidth / 1.12f).roundToInt()
            val child = measurable.measure(constraints.copy(minWidth = minOf(constraints.minWidth, width), maxWidth = width))
            val height = ceil(child.height * 1.12f).toInt()
            layout(constraints.maxWidth, height) {
                child.placeRelative(if (display.centered) (constraints.maxWidth - child.width) / 2
                    else if (line.isDuet) constraints.maxWidth - child.width else 0, (height - child.height) / 2)
            }
        }.graphicsLayer {
            scaleX = 1.12f; scaleY = 1.12f
            transformOrigin = TransformOrigin(if (display.centered) .5f else if (line.isDuet) 1f else 0f, .5f)
        }) {
            LyricsVocalView(line, position, mainFocused, resting, translation, romanization, wordAnimation,
                if (line.isBackground) fontSize * .75f else fontSize, weightMode, display, effects, distance,
                vocalModifier = Modifier.testTag("lyric_vocal_$index"), monetTone = monetTone)
            line.backgroundVocals.forEachIndexed { bgIndex, bg ->
                val bgFocused by remember(bg, position) { derivedStateOf { position.value >= bg.startTimeMs && position.value < bg.endTimeMs } }
                Column(Modifier.padding(top = 10.dp).testTag("lyric_bg_${index}_$bgIndex")
                    .semantics { selected = bgFocused }
                    .clickable(enabled = canSeek, role = Role.Button) { onSeek(bg.startTimeMs) }
                    .graphicsLayer { alpha = if (bgFocused) 1f else .55f }) {
                    LyricsVocalView(bg, position, bgFocused, false, translation, romanization, wordAnimation, fontSize * .75f, weightMode, display, effects, distance, monetTone = monetTone)
                }
            }
        }
    }
}

@Composable private fun LyricsVocalView(line: LyricLine, position: State<Long>, focused: Boolean,
    resting: Boolean, translation: Boolean, romanization: Boolean, wordAnimation: Boolean, size: Float, weightMode: LyricsWeight,
    display: LyricsDisplayOptions, effects: Boolean, distance: Int, vocalModifier: Modifier = Modifier,
    monetTone: MonetTone) {
    val weight = lyricsFontWeight(weightMode, focused || resting, display.fontWeight)
    val reservedWeight = lyricsFontWeight(LyricsWeight.ALL, true, display.fontWeight)
    val auxiliaryWeight = lyricsFontWeight(weightMode, true, display.fontWeight)
    val align = if (display.centered) TextAlign.Center else if (line.isDuet) TextAlign.End else TextAlign.Start
    val beforeStart by remember(line, position) { derivedStateOf { position.value < line.startTimeMs } }
    val preview = resting && beforeStart
    // The intro preview eases into normal unsung ink at the first word, rather
    // than abruptly replacing fully white text with the karaoke base layer.
    val baseOpacity = animateFloatAsState(if (preview) .55f else monetTone.baseAlpha, tween(220), label = "lyric preview ink")
    val glowOpacity = animateFloatAsState(if (display.glow && focused) 1f else 0f,
        tween(220), label = "lyric glow")
    val glowing by remember(glowOpacity) { derivedStateOf { glowOpacity.value > .001f } }
    // Blur each vocal independently so an active background vocal never inherits main-vocal blur.
    // Silence keeps one readable paragraph; it never counts as an active vocal
    // for translation, glow or karaoke progress.
    val blur = if ((monetTone.blur == 0f) && effects && android.os.Build.VERSION.SDK_INT >= 31 && !focused && !resting)
        Modifier.blur(if (distance <= 1) 1.4.dp else 2.2.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded) else Modifier
    Column(Modifier.fillMaxWidth().then(blur)) {
    if (line.text.isNotBlank()) KaraokeText(line, position, wordAnimation && (focused || preview || (!resting && display.karaokeScope != KaraokeScope.CURRENT)),
        Modifier.fillMaxWidth().then(vocalModifier), size, weight, align, forceLineAnimation = display.karaokeScope == KaraokeScope.ALWAYS,
        reservedFontWeight = reservedWeight, glow = display.glow && (focused || glowing || (!beforeStart && distance <= 4)),
        wordLift = false, monet = wordAnimation, glowOpacity = glowOpacity, baseOpacity = baseOpacity,
        prepareDrawResources = true, prewarmGlow = display.glow && beforeStart && distance == 1,
        )
    LyricsAuxiliaryText(line.translation, position, translation && focused, size * .6f,
        auxiliaryWeight, align, "lyric_translation", .65f, animateHeight = false)
    LyricsAuxiliaryText(line.romanization, position, romanization && focused, size * .53f,
        auxiliaryWeight, align, "lyric_romanization", .5f, animateHeight = false)
    }
}
