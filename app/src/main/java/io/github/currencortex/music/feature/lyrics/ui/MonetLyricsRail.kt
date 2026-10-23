// SPDX-License-Identifier: AGPL-3.0-only
// Compose port of Folia's MonetLyricsRail / MonetRailLine / monetRailLayout, commit 26c57f9b.
// Copyright Folia contributors. Android adaptations: cached paragraphs, duet alignment and accessibility.
package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.delay
import kotlin.math.abs

private class MonetTrack(val entering: Boolean) {
    var target by mutableFloatStateOf(Float.NaN)
    var position: State<Float>? = null
}

/** A bounded window of keyed tracks. Only layers move while the current line advances. */
@Composable internal fun MonetLyricsRail(document: LyricsDocument, target: Int, active: Set<Int>,
    modifier: Modifier = Modifier, fontSize: Float, centered: Boolean, minimal: Boolean,
    perspective: Boolean, effects: Boolean, followRequest: Int, onBrowsing: (Boolean) -> Unit,
    expandWindow: Boolean = false,
    content: @Composable (index: Int, browsing: Boolean, tone: MonetTone) -> Unit) {
    val unit = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    var viewportHeight by remember { mutableFloatStateOf(0f) }
    // Pixel-by-pixel viewport changes must not recompose every lyric track during a pager drag.
    val radius by remember(expandWindow, fontSize, unit, fontScale) { derivedStateOf {
        if (expandWindow) monetViewportRadius(viewportHeight, fontSize * unit * fontScale) else 4
    } }
    val effectInset = (fontSize * fontScale * 2f + 6f).dp
    var manualAnchor by remember(document) { mutableStateOf<Int?>(null) }
    var gestureRevision by remember(document) { mutableIntStateOf(0) }
    var dragging by remember(document) { mutableStateOf(false) }
    val anchor = (manualAnchor ?: target).coerceIn(0, document.lines.lastIndex.coerceAtLeast(0))
    LaunchedEffect(followRequest) { manualAnchor = null }
    val latestAnchor = rememberUpdatedState(anchor)
    val browsing = manualAnchor != null
    SideEffect { onBrowsing(browsing) }
    LaunchedEffect(gestureRevision, dragging, browsing) {
        if (browsing && !dragging) { delay(MONET_IDLE_RESET_MS); manualAnchor = null }
    }
    val start = (anchor - radius).coerceAtLeast(0)
    val desired = if (document.lines.isEmpty()) emptyList() else
        (start..minOf(anchor + radius, document.lines.lastIndex)).toList()
    var previousWindow by remember(document) { mutableStateOf(desired) }
    var retiring by remember(document) { mutableStateOf(emptyList<Int>()) }
    SideEffect {
        if (previousWindow != desired) {
            retiring = previousWindow.filter { it !in desired }
            previousWindow = desired
        }
    }
    LaunchedEffect(retiring) { if (retiring.isNotEmpty()) { delay(200); retiring = emptyList() } }
    val indices = (desired + retiring).distinct().sorted()
    val firstWindow = remember(document) { desired.toSet() }
    val tracks = remember(document) { mutableMapOf<Int, MonetTrack>() }
    // Retain only the virtual window; a long song cannot accumulate animation or raster objects.
    val keepKeys = (indices + previousWindow).toSet()
    SideEffect { tracks.keys.retainAll(keepKeys) }
    // Keep the existing far-line blur when extra tracks become visible in the larger area.
    val tones = indices.map { monetTone(it in active || (!browsing && active.isEmpty() && it == target),
        if (expandWindow) (it - target).coerceIn(-4, 4) else it - target) }
    Layout(modifier = modifier.testTag("lyrics_list").onSizeChanged { viewportHeight = it.height.toFloat() }
        .semantics {
            this[LyricsPerspectiveAngle] = if (perspective) LYRIC_PERSPECTIVE_ANGLE else 0f
            verticalScrollAxisRange = ScrollAxisRange({ anchor.toFloat() }, { document.lines.lastIndex.coerceAtLeast(0).toFloat() })
            scrollBy { _, y ->
                if (y == 0f || document.lines.isEmpty()) false else {
                    manualAnchor = (anchor + if (y > 0f) 1 else -1).coerceIn(0, document.lines.lastIndex)
                    gestureRevision++; true
                }
            }
            scrollToIndex { index ->
                if (index !in document.lines.indices) false else { manualAnchor = index; gestureRevision++; true }
            }
        }
        .pointerInput(document) {
            var accumulator = 0f
            detectVerticalDragGestures(onDragStart = { dragging = true; manualAnchor = latestAnchor.value; accumulator = 0f },
                onDragEnd = { dragging = false; gestureRevision++ },
                onDragCancel = { dragging = false; gestureRevision++ }) { change, amount ->
                change.consume()
                if (accumulator * amount > 0) accumulator = 0f
                accumulator -= amount
                if (abs(accumulator) >= 52.dp.toPx()) {
                    manualAnchor = (latestAnchor.value + if (accumulator > 0) 1 else -1).coerceIn(0, document.lines.lastIndex.coerceAtLeast(0))
                    accumulator = 0f; gestureRevision++
                }
            }
        }
        .graphicsLayer {
            rotationY = if (perspective) LYRIC_PERSPECTIVE_ANGLE else 0f
            cameraDistance = (size.width / 48f).coerceAtLeast(1f)
            transformOrigin = TransformOrigin(0f, if (minimal) .5f else
                (.46f + fontSize * density * fontScale * .1f / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f))
        }.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
            val verticalFade = Brush.verticalGradient(0f to Color.Transparent, .11f to Color.Black,
                .88f to Color.Black, 1f to Color.Transparent)
            // Fade only the existing gutter: glyph positions and wrapping stay unchanged.
            val edge = (8.dp.toPx() / size.width.coerceAtLeast(1f)).coerceAtMost(.49f)
            val horizontalFade = Brush.horizontalGradient(0f to Color.Transparent, edge to Color.Black,
                (1f - edge) to Color.Black, 1f to Color.Transparent)
            onDrawWithContent {
                drawContent()
                drawRect(verticalFade, blendMode = BlendMode.DstIn)
                drawRect(horizontalFade, blendMode = BlendMode.DstIn)
            }
        }, content = {
            indices.forEachIndexed { slot, index -> key(index, document.lines[index].startTimeMs) {
                val track = tracks.getOrPut(index) { MonetTrack(index !in firstWindow) }
                val exiting = index !in desired
                val targetY = track.target
                val y = if (targetY.isNaN()) null else {
                    val motion = remember { Animatable(targetY + if (track.entering && !browsing) (if (index >= anchor) 34f else -34f) * unit else 0f) }
                    LaunchedEffect(targetY, exiting) {
                        if (exiting) motion.animateTo(targetY + (if (index < anchor) -38f else 38f) * unit,
                            tween(200, easing = CubicBezierEasing(.32f, .72f, 0f, 1f)))
                        else motion.animateTo(targetY, spring(MONET_SCROLL_DAMPING, MONET_SCROLL_STIFFNESS, .1f))
                    }
                    motion.asState()
                }
                track.position = y
                val tone = tones[slot]
                val scale = animateFloatAsState(tone.scale,
                    monetScaleSpring(), label = "Monet scale")
                val alpha = remember { Animatable(if (track.entering && !browsing) 0f else tone.opacity) }
                LaunchedEffect(tone.opacity, exiting) {
                    alpha.animateTo(if (exiting) 0f else tone.opacity,
                        tween(if (exiting) 200 else 280, easing = CubicBezierEasing(.32f, .72f, 0f, 1f)))
                }
                val blur = animateFloatAsState(if (effects) { if (exiting) 6f else tone.blur } else 0f,
                    tween(320, easing = CubicBezierEasing(.32f, .72f, 0f, 1f)), label = "Monet blur")
                val renderBlur = remember(blur, unit) { derivedStateOf {
                    if (android.os.Build.VERSION.SDK_INT >= 31 && blur.value > .01f)
                        BlurEffect(blur.value * unit, blur.value * unit, TileMode.Decal) else null
                } }
                Box(Modifier.fillMaxWidth().zIndex(if (index in active) 4f else -abs(index - target).toFloat())
                    .layout { measurable, constraints ->
                        // Expand only the layer's drawing space, then report the original paragraph size.
                        val inset = effectInset.roundToPx()
                        val child = measurable.measure(constraints.offset(inset * 2, inset * 2))
                        layout(child.width - inset * 2, child.height - inset * 2) { child.place(-inset, -inset) }
                    }.graphicsLayer {
                    translationY = y?.value ?: 0f
                    val right = document.lines[index].isDuet
                    val pivot = if (centered) .5f else if (right) 1f else 0f
                    // A top-edge pivot keeps y equal to the visual top of the scaled paragraph.
                    val inset = effectInset.roundToPx().toFloat()
                    transformOrigin = TransformOrigin((inset + (size.width - inset * 2) * pivot) / size.width,
                        inset / size.height)
                    scaleX = scale.value; scaleY = scale.value
                    this.alpha = if (y == null) 0f else alpha.value
                    // Alpha and RenderEffect create offscreen layers even with clip=false.
                    // Reserve the raster halo outside the paragraph instead of cutting at its box.
                    renderEffect = renderBlur.value
                }.padding(effectInset)) { content(index, browsing, tone) }
            } }
        }) { measurables, constraints ->
        val inset = 8.dp.roundToPx()
        val extra = if (perspective) (constraints.maxWidth * .16f).toInt() else 0
        val rowWidth = (constraints.maxWidth - inset * 2 - extra).coerceAtLeast(1)
        val rows = measurables.map { it.measure(Constraints.fixedWidth(rowWidth)) }
        val desiredSlots = desired.map { indices.indexOf(it) }
        val positions = monetTrackPositions(desiredSlots.map { rows[it].height.toFloat() },
            desiredSlots.map { tones[it].scale }, desired.indexOf(anchor),
            0f,
            fontSize * density * fontScale, desired.mapIndexedNotNull { slot, i -> if (i in active) slot else null }.toSet())
        desired.forEachIndexed { slot, i -> tracks.getValue(i).target = positions[slot] }
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Viewport movement is shared placement, not a new spring target for every line.
            val focus = (constraints.maxHeight * if (minimal) .5f else .46f).toInt()
            val halo = effectInset.roundToPx()
            rows.forEachIndexed { slot, row ->
                val y = tracks.getValue(indices[slot]).position?.value
                // Expanded windows prewarm nearby rows. Do not raster/blur those outside the viewport.
                if (y == null || (focus + y < constraints.maxHeight + halo && focus + y + row.height > -halo))
                    row.placeRelative(inset, focus)
            }
        }
    }
}
