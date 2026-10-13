package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument

private const val PREVIEW_SCALE = .62f
private class SmokeStageGeometry {
    var current = -1
    var preview = -1
    var previewTopFraction = 1f
    var height = 0
    var mainY = 0
}
private data class SmokeFlight(val from: Int, val topFraction: Float)

/** Current ink stays at the viewport center; auxiliary visibility never moves that anchor. */
@Composable internal fun SmokeLyricsStage(document: LyricsDocument, target: Int, position: State<Long>,
    onSeek: (Long) -> Unit, modifier: Modifier, canSeek: Boolean, translation: Boolean, romanization: Boolean,
    fontSize: Float, weightMode: LyricsWeight, display: LyricsDisplayOptions, colors: LyricsSmokeColors?) {
    val line = document.lines.getOrNull(target) ?: return
    val next = document.lines.getOrNull(target + 1)
    val geometry = remember(document) { SmokeStageGeometry() }
    val flight = remember(document, target) {
        if (geometry.preview == target && geometry.current == target - 1)
            SmokeFlight(geometry.current, geometry.previewTopFraction) else null
    }
    val rise = remember(document, target) { Animatable(if (flight == null) 1f else 0f) }
    LaunchedEffect(rise) { rise.animateTo(1f, tween(620, easing = CubicBezierEasing(.22f, 0f, .18f, 1f))) }
    val inFlight by remember(rise) { derivedStateOf { rise.value < 1f } }
    val oldLine = if (inFlight) flight?.from?.let(document.lines::getOrNull) else null
    val oldPosition = remember(document, target) { mutableLongStateOf(oldLine?.endTimeMs?.minus(1) ?: 0) }
    val focused by remember(line, position) { derivedStateOf { position.value in line.startTimeMs until line.endTimeMs } }
    val weight = lyricsFontWeight(weightMode, true, display.fontWeight)
    val previewRevealAt = remember(line) { smokePreviewRevealAt(line) }
    val auxiliaryWeight = weight
    Layout(modifier = modifier.testTag("lyrics_panel").semantics { this[LyricsPerspectiveAngle] = 0f }
        .padding(horizontal = 16.dp).clipToBounds(), content = {
        // Keep the five measured slots attached while keyed text/subcomposition changes.
        Box(Modifier.fillMaxWidth()) { key(target) {
            KaraokeText(line, position, display.wordAnimation,
                Modifier.fillMaxWidth().testTag("lyric_line_$target").semantics { selected = focused }
                    .clickable(enabled = canSeek, role = Role.Button) { onSeek(line.startTimeMs) },
                fontSize = fontSize, fontWeight = weight, reservedFontWeight = auxiliaryWeight,
                textAlign = TextAlign.Center, smokeColors = colors,
                smokeFlight = { flight?.let { LyricsSmokeFlight(it.topFraction * geometry.height - geometry.mainY) } })
        } }
        Column(Modifier.fillMaxWidth().clipToBounds()) {
            key(target) {
                LyricsAuxiliaryText(line.romanization, position, romanization && focused, fontSize * .53f,
                    auxiliaryWeight, TextAlign.Center, "lyric_romanization", .5f, reserveSpace = true)
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clipToBounds()) {
            key(target) {
                LyricsAuxiliaryText(line.translation, position, translation && focused, fontSize * .6f,
                    auxiliaryWeight, TextAlign.Center, "lyric_translation", .65f, reserveSpace = true)
            }
        }
        Box(Modifier.fillMaxWidth()) { if (next != null) key(target + 1) {
            // Same paragraph and font geometry as the current line. Layer scaling
            // makes the preview smaller without rewrapping it during promotion.
            KaraokeText(next, position, false,
                Modifier.fillMaxWidth().testTag("smoke_next_line")
                    .clickable(enabled = canSeek, role = Role.Button) { onSeek(next.startTimeMs) },
                fontSize = fontSize, fontWeight = weight, reservedFontWeight = auxiliaryWeight,
                textAlign = TextAlign.Center)
        } }
        Box(Modifier.fillMaxWidth()) { if (oldLine != null) key(flight!!.from) {
            KaraokeText(oldLine, oldPosition, false, Modifier.fillMaxWidth().clearAndSetSemantics {},
                fontSize = fontSize, fontWeight = weight, reservedFontWeight = auxiliaryWeight,
                textAlign = TextAlign.Center, exposeText = false)
        } }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val nextPlaceable = measurables[3].measure(loose)
        val bottomGap = 16.dp.roundToPx()
        val previewHeight = nextPlaceable.height * PREVIEW_SCALE
        val nextY = height - bottomGap - previewHeight
        val main = measurables[0].measure(loose)
        val mainY = ((height - main.height) / 2).coerceAtLeast(0)
        val gap = 10.dp.roundToPx()
        val translatedY = minOf(16.dp.roundToPx(), mainY)
        val translated = measurables[2].measure(loose.copy(maxHeight = (mainY - translatedY - gap).coerceAtLeast(0)))
        val auxiliary = measurables[1].measure(loose.copy(maxHeight = (nextY.toInt() - mainY - main.height - gap * 2).coerceAtLeast(0)))
        val old = measurables[4].measure(loose)
        geometry.current = target
        geometry.preview = if (next != null) target + 1 else -1
        geometry.previewTopFraction = nextY / height.coerceAtLeast(1)
        geometry.height = height
        geometry.mainY = mainY
        layout(width, height) {
            // Retire the previous center line gently while the preview rises into it.
            old.placeRelativeWithLayer(0, (height - old.height) / 2) {
                alpha = (1f - rise.value * 2.5f).coerceIn(0f, 1f)
                translationY = -16.dp.toPx() * rise.value
            }
            main.placeRelative(0, mainY)
            auxiliary.placeRelativeWithLayer(0, mainY + main.height + gap) { alpha = rise.value }
            translated.placeRelativeWithLayer(0, translatedY) { alpha = rise.value }
            nextPlaceable.placeRelativeWithLayer(0, nextY.toInt()) {
                scaleX = PREVIEW_SCALE; scaleY = PREVIEW_SCALE
                transformOrigin = TransformOrigin(.5f, 0f)
                alpha = .48f * if (flight != null && colors != null)
                    ((position.value - previewRevealAt) / 180f).coerceIn(0f, 1f)
                else ((rise.value - .25f) / .75f).coerceIn(0f, 1f)
            }
        }
    }
}
