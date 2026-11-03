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
import kotlin.math.floor
import kotlin.math.roundToInt

private class MonetTrack(val entering: Boolean) {
    var target by mutableFloatStateOf(Float.NaN)
    var position: State<Float>? = null
}

/** A bounded window of keyed tracks. Only layers move while the current line advances. */
@Composable internal fun MonetLyricsRail(document: LyricsDocument, target: Int, active: Set<Int>,
    modifier: Modifier = Modifier, fontSize: Float, centered: Boolean, minimal: Boolean,
    perspective: Boolean, effects: Boolean, followRequest: Int, onBrowse: (Int?) -> Unit,
    expandWindow: Boolean = false, playbackTarget: Int = target,
    content: @Composable (index: Int, browsing: Boolean, tone: MonetTone, focused: Boolean, focusIndex: Int) -> Unit) {
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
    // Sub-line finger travel rendered as a shared viewport shift: the list tracks the finger
    // continuously while whole lines advance the anchor exactly at measured row boundaries.
    var dragResidual by remember(document) { mutableFloatStateOf(0f) }
    var bouncing by remember(document) { mutableStateOf(false) }
    val scrubbing = dragging || bouncing
    val anchor = (manualAnchor ?: target).coerceIn(0, document.lines.lastIndex.coerceAtLeast(0))
    val latestPlayback = rememberUpdatedState(playbackTarget)
    val latestDragging = rememberUpdatedState(dragging)
    val latestAnchor = rememberUpdatedState(anchor)
    val browsing = manualAnchor != null
    // Emphasis (bold/glow/scale pop) only lands when the finger rests; while scrubbing the
    // brightness glides continuously through the rail tone instead of switching at boundaries.
    // Selection means "inside an active interval" only — the silence fallback stays a tone/resting
    // concern so a gap never reports a selected row.
    fun isFocusActive(index: Int) = if (browsing) !scrubbing && index == anchor else index in active
    val focusIndex = if (browsing) anchor else target
    SideEffect { onBrowse(manualAnchor) }
    val start = (anchor - radius).coerceAtLeast(0)
    val desired = if (document.lines.isEmpty()) emptyList() else
        (start..minOf(anchor + radius, document.lines.lastIndex)).toList()
    var previousWindow by remember(document) { mutableStateOf(desired) }
    var retiring by remember(document) { mutableStateOf(emptyList<Int>()) }
    SideEffect {
        if (previousWindow != desired) {
            // During the fast glide a stale retired target would hang in the wrong slot; the
            // viewport edge fade hides the instant unmount.
            retiring = if (bouncing) emptyList() else previousWindow.filter { it !in desired }
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
    // During a scrub the raw residual is read directly by placement and tone; the animation
    // layer above it reintroduced frame lag that read as row-by-row hitching. Only the settle
    // after the finger lifts is eased, and it is short so the emphasis lands immediately.
    val residualSettle = remember { Animatable(0f) }
    LaunchedEffect(dragResidual) { if (scrubbing) residualSettle.snapTo(dragResidual) }
    LaunchedEffect(scrubbing) {
        if (!scrubbing) residualSettle.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
    }
    val visualResidual = if (scrubbing) dragResidual else residualSettle.value
    val pitchFallback = fontSize * unit * fontScale * 1.3f
    fun railPitch(index: Int): Float {
        val here = tracks[index]?.target?.takeIf { it.isFinite() } ?: return pitchFallback
        val ahead = tracks[index + 1]?.target?.takeIf { it.isFinite() }
        val behind = tracks[index - 1]?.target?.takeIf { it.isFinite() }
        val aheadGap = if (ahead != null) abs(ahead - here) else 0f
        val behindGap = if (behind != null) abs(here - behind) else 0f
        return (if (aheadGap > 0f) aheadGap else behindGap).takeIf { it > 0f } ?: pitchFallback
    }
    // Gliding return: the focus position sweeps back to the sung line as one continuous
    // reverse scrub, mirroring the finger, instead of hopping anchor row by anchor row.
    suspend fun bounceToPlayback() {
        val from = manualAnchor ?: return
        bouncing = true
        try {
            val maxIndex = document.lines.lastIndex.coerceAtLeast(0)
            var to = latestPlayback.value
            val totalMs = MONET_BOUNCE_TOTAL_MS.coerceAtLeast(abs(to - from) * 24L)
            var startFrame = -1L
            while (true) {
                val frame = withFrameNanos { it }
                if (latestDragging.value) return
                if (startFrame < 0) startFrame = frame
                to = latestPlayback.value
                val t = ((frame - startFrame) / 1_000_000f / totalMs).coerceIn(0f, 1f)
                val pos = from + (to - from) * t
                // Floor keeps the residual inside the segment it measures: crossing a row of a
                // different height (wrapped lines) would otherwise pop by half the gap mismatch.
                val a = floor(pos).toInt().coerceIn(0, maxIndex)
                if (manualAnchor != a) manualAnchor = a
                dragResidual = (pos - a) * railPitch(a)
                if (t >= 1f) break
            }
            dragResidual = 0f
            manualAnchor = null
        } finally {
            bouncing = false
            if (!latestDragging.value) dragResidual = 0f
        }
    }
    LaunchedEffect(followRequest) { if (followRequest > 0) bounceToPlayback() }
    LaunchedEffect(gestureRevision, dragging, browsing) {
        if (browsing && !dragging) { delay(MONET_IDLE_RESET_MS); bounceToPlayback() }
    }
    val focusPos = if (browsing) anchor + visualResidual / railPitch(anchor) else target.toFloat()
    // Keep the existing far-line blur when extra tracks become visible in the larger area.
    val tones = indices.map {
        if (browsing) monetRailTone(it - focusPos, lift = (1f - abs(it - focusPos)).coerceIn(0f, 1f) * MONET_RAIL_LIFT)
        else monetTone(it in active || active.isEmpty() && it == target,
            if (expandWindow) (it - target).coerceIn(-4, 4) else it - target)
    }
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
            detectVerticalDragGestures(onDragStart = { dragging = true; manualAnchor = latestAnchor.value; accumulator = 0f; dragResidual = 0f },
                onDragEnd = { dragging = false; dragResidual = 0f; gestureRevision++ },
                onDragCancel = { dragging = false; dragResidual = 0f; gestureRevision++ }) { change, amount ->
                change.consume()
                if (accumulator * amount > 0) accumulator = 0f
                accumulator -= amount
                val maxIndex = document.lines.lastIndex.coerceAtLeast(0)
                while (true) {
                    val current = manualAnchor ?: latestAnchor.value
                    val dir = if (accumulator > 0) 1 else -1
                    val pitch = railPitch(current)
                    if (abs(accumulator) < pitch) break
                    val next = (current + dir).coerceIn(0, maxIndex)
                    if (next == current) { accumulator = 0f; break }
                    manualAnchor = next
                    accumulator -= dir * pitch
                }
                dragResidual = accumulator
                gestureRevision++
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
                        // Direct manipulation: the list must sit under the finger on the same frame.
                        else if (scrubbing) motion.snapTo(targetY)
                        else motion.animateTo(targetY, spring(MONET_SCROLL_DAMPING, MONET_SCROLL_STIFFNESS, .1f))
                    }
                    motion.asState()
                }
                track.position = y
                val tone = tones[slot]
                // The rail tone is already continuous while scrubbing; animating towards it would
                // reintroduce the very lag that reads as "no highlight under the finger".
                val scale = animateFloatAsState(tone.scale,
                    if (scrubbing) snap() else monetScaleSpring(), label = "Monet scale")
                val alpha = remember { Animatable(if (track.entering && !browsing) 0f else tone.opacity) }
                LaunchedEffect(tone.opacity, exiting, scrubbing) {
                    if (exiting) alpha.animateTo(0f, tween(200, easing = CubicBezierEasing(.32f, .72f, 0f, 1f)))
                    else if (scrubbing) alpha.snapTo(tone.opacity)
                    else alpha.animateTo(tone.opacity, tween(280, easing = CubicBezierEasing(.32f, .72f, 0f, 1f)))
                }
                val blur = animateFloatAsState(if (effects && !browsing) { if (exiting) 6f else tone.blur } else 0f,
                    tween(320, easing = CubicBezierEasing(.32f, .72f, 0f, 1f)), label = "Monet blur")
                val renderBlur = remember(blur, unit) { derivedStateOf {
                    if (android.os.Build.VERSION.SDK_INT >= 31 && blur.value > .01f)
                        BlurEffect(blur.value * unit, blur.value * unit, TileMode.Decal) else null
                } }
                Box(Modifier.fillMaxWidth().zIndex(if (isFocusActive(index)) 4f else -abs(index - focusPos).toFloat())
                    .layout { measurable, constraints ->
                        // Expand only the layer's drawing space, then report the original paragraph size.
                        val inset = effectInset.roundToPx()
                        val child = measurable.measure(constraints.offset(inset * 2, inset * 2))
                        layout(child.width - inset * 2, child.height - inset * 2) { child.place(-inset, -inset) }
                    }.graphicsLayer {
                    // While scrubbing, read the freshly measured target directly: the snapTo in the
                    // effect lands one frame late and would flicker at every row boundary.
                    translationY = if (scrubbing) track.target.takeIf { it.isFinite() } ?: y?.value ?: 0f
                        else y?.value ?: 0f
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
                }.padding(effectInset)) { content(index, browsing, tone, isFocusActive(index), focusIndex) }
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
            fontSize * density * fontScale, desired.mapIndexedNotNull { slot, i -> if (isFocusActive(i)) slot else null }.toSet())
        desired.forEachIndexed { slot, i -> tracks.getValue(i).target = positions[slot] }
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Viewport movement is shared placement, not a new spring target for every line.
            // Finger-up accumulates positive residual, so content moves the opposite sign of it.
            val focus = ((constraints.maxHeight * if (minimal) .5f else .46f) - visualResidual).roundToInt()
            val halo = effectInset.roundToPx()
            rows.forEachIndexed { slot, row ->
                val track = tracks.getValue(indices[slot])
                val y = if (scrubbing) track.target.takeIf { it.isFinite() } ?: track.position?.value
                    else track.position?.value
                // Expanded windows prewarm nearby rows. Do not raster/blur those outside the viewport.
                if (y == null || (focus + y < constraints.maxHeight + halo && focus + y + row.height > -halo))
                    row.placeRelative(inset, focus)
            }
        }
    }
}
