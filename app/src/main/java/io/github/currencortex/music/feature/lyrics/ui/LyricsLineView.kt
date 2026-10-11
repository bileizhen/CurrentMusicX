package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import io.github.currencortex.music.data.settings.LyricsWeight
import io.github.currencortex.music.data.settings.LyricsDisplayOptions
import io.github.currencortex.music.data.settings.KaraokeScope
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import kotlin.math.*

internal const val ACTIVE_LYRIC_SCALE = 1.12f

@Composable internal fun LyricsLineView(line: LyricLine, index: Int, position: State<Long>, focused: Boolean,
    resting: Boolean, distance: Int, browsing: Boolean, canSeek: Boolean, onSeek: (Long) -> Unit, translation: Boolean,
    romanization: Boolean, wordAnimation: Boolean, effects: Boolean, fontSize: Float, weightMode: LyricsWeight,
    display: LyricsDisplayOptions, scrollMotion: LyricsScrollMotion) {
    val opacity = animateFloatAsState(if (focused) 1f else if (resting) .68f else when (distance) { 1 -> .56f; 2 -> .35f; else -> .24f }, tween(350), label = "lyric focus")
    val scale = animateFloatAsState(if (focused || resting) ACTIVE_LYRIC_SCALE else if (distance == 1) .96f else .94f,
        spring(dampingRatio = 1f, stiffness = 220f), label = "lyric scale")
    val mainFocused by remember(line, position) {
        derivedStateOf { position.value >= line.startTimeMs && position.value < line.endTimeMs }
    }
    val lagDistance = with(LocalDensity.current) { 5.dp.toPx() }
    Box(Modifier.fillMaxWidth().testTag("lyric_line_$index").semantics { selected = focused }
        .clickable(enabled = canSeek, role = Role.Button) { onSeek(line.startTimeMs) }) {
        Column(Modifier.fillMaxWidth().layout { measurable, constraints ->
            // Focus emphasis cannot rewrap the cached paragraph or clip its last glyph.
            val width = (constraints.maxWidth / ACTIVE_LYRIC_SCALE).roundToInt()
            val child = measurable.measure(constraints.copy(minWidth = minOf(constraints.minWidth, width), maxWidth = width))
            val height = constraints.constrainHeight(ceil(child.height * ACTIVE_LYRIC_SCALE).toInt())
            layout(constraints.maxWidth, height) { child.placeRelative(
                if (display.centered) (constraints.maxWidth - child.width) / 2
                else if (line.isDuet) constraints.maxWidth - child.width else 0, (height - child.height) / 2) }
        }.graphicsLayer {
            alpha = opacity.value; scaleX = scale.value; scaleY = scale.value
            // Shared scroll progress starts and ends at zero: no per-row snap or delayed jobs.
            translationY = if (display.stagger && !browsing) scrollMotion.lag(distance) * lagDistance else 0f
            transformOrigin = TransformOrigin(if (display.centered) .5f else if (line.isDuet) 1f else 0f, .5f)
        }) {
            LyricsVocalView(line, position, mainFocused, resting, translation, romanization, wordAnimation,
                if (line.isBackground) fontSize * .75f else fontSize, weightMode, display, effects, distance,
                vocalModifier = Modifier.testTag("lyric_vocal_$index"))
            line.backgroundVocals.forEachIndexed { bgIndex, bg ->
                val bgFocused by remember(bg, position) { derivedStateOf { position.value >= bg.startTimeMs && position.value < bg.endTimeMs } }
                Column(Modifier.padding(top = 10.dp).testTag("lyric_bg_${index}_$bgIndex")
                    .semantics { selected = bgFocused }
                    .clickable(enabled = canSeek, role = Role.Button) { onSeek(bg.startTimeMs) }
                    .graphicsLayer { alpha = if (bgFocused) 1f else .55f }) {
                    LyricsVocalView(bg, position, bgFocused, false, translation, romanization, wordAnimation, fontSize * .75f, weightMode, display, effects, distance)
                }
            }
        }
    }
}

@Composable private fun LyricsVocalView(line: LyricLine, position: State<Long>, focused: Boolean,
    resting: Boolean, translation: Boolean, romanization: Boolean, wordAnimation: Boolean, size: Float, weightMode: LyricsWeight,
    display: LyricsDisplayOptions, effects: Boolean, distance: Int, vocalModifier: Modifier = Modifier) {
    val weight = lyricsFontWeight(weightMode, focused || resting, display.fontWeight)
    val reservedWeight = lyricsFontWeight(LyricsWeight.ALL, true, display.fontWeight)
    val align = if (display.centered) TextAlign.Center else if (line.isDuet) TextAlign.End else TextAlign.Start
    val beforeStart by remember(line, position) { derivedStateOf { position.value < line.startTimeMs } }
    val preview = resting && beforeStart
    // The intro preview eases into normal unsung ink at the first word, rather
    // than abruptly replacing fully white text with the karaoke base layer.
    val baseOpacity = animateFloatAsState(if (preview) .55f else .32f, tween(220), label = "lyric preview ink")
    val glowOpacity = animateFloatAsState(if (display.glow && focused) 1f else 0f,
        tween(220), label = "lyric glow")
    val glowing by remember(glowOpacity) { derivedStateOf { glowOpacity.value > .001f } }
    // Blur each vocal independently so an active background vocal never inherits main-vocal blur.
    // Silence keeps one readable paragraph; it never counts as an active vocal
    // for translation, glow or karaoke progress.
    val blur = if (effects && android.os.Build.VERSION.SDK_INT >= 31 && !focused && !resting)
        Modifier.blur(if (distance <= 1) 1.4.dp else 2.2.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded) else Modifier
    Column(Modifier.fillMaxWidth().then(blur)) {
    if (line.text.isNotBlank()) KaraokeText(line, position, wordAnimation && (focused || preview || (!resting && display.karaokeScope != KaraokeScope.CURRENT)),
        Modifier.fillMaxWidth().then(vocalModifier), size, weight, align, forceLineAnimation = display.karaokeScope == KaraokeScope.ALWAYS,
        reservedFontWeight = reservedWeight, glow = display.glow && (focused || glowing),
        wordLift = display.wordLift && wordAnimation, glowOpacity = glowOpacity, baseOpacity = baseOpacity)
    if (line.translation.isNotBlank()) {
        val visible = translation && focused
        val reveal = animateFloatAsState(if (visible) 1f else 0f,
            tween(260, easing = CubicBezierEasing(.22f, 0f, .18f, 1f)), label = "current translation")
        // Measure the full paragraph once, then animate only its allocated height and
        // opacity. Fully hidden translations occupy zero space, including their padding.
        val auxiliary = remember(line.translation) { line.copy(text = line.translation, words = emptyList()) }
        KaraokeText(auxiliary, position, false, Modifier.fillMaxWidth()
            .graphicsLayer { alpha = reveal.value; clip = true }
            .layout { measurable, constraints ->
                val child = measurable.measure(constraints)
                layout(child.width, (child.height * reveal.value).roundToInt()) { child.placeRelative(0, 0) }
            }
            .padding(top = 4.dp)
            .then(if (visible) Modifier.testTag("lyric_translation") else Modifier.clearAndSetSemantics {}),
            fontSize = size * .6f, fontWeight = weight, textAlign = align, reservedFontWeight = reservedWeight,
            color = Color.White.copy(alpha = .65f), lineHeightRatio = 1.2f, exposeText = visible)
    }
    if (romanization && line.romanization.isNotBlank()) {
        val auxiliary = remember(line.romanization) { line.copy(text = line.romanization, words = emptyList()) }
        KaraokeText(auxiliary, position, false, Modifier.fillMaxWidth().padding(top = 4.dp),
            fontSize = size * .53f, fontWeight = weight, textAlign = align, reservedFontWeight = reservedWeight,
            color = Color.White.copy(alpha = .5f), lineHeightRatio = 1.2f)
    }
    }
}
