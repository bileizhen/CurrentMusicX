// SPDX-License-Identifier: AGPL-3.0-only
// Native presentation informed by AMLL f322858; see THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.feature.lyrics.ui

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.settings.AppleMusicLyricsOptions
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.pow

internal val AppleLyricsFollowing = SemanticsPropertyKey<Boolean>("AppleLyricsFollowing")
internal val AppleLyricsAnchor = SemanticsPropertyKey<Int>("AppleLyricsAnchor")
internal val AppleLyricsBrowseFocus = SemanticsPropertyKey<Int>("AppleLyricsBrowseFocus")
internal val AppleLyricsBrowseSelected = SemanticsPropertyKey<Boolean>("AppleLyricsBrowseSelected")
internal val LocalAppleLyricsBrowseSelected = compositionLocalOf { false }

private class AppleLyricsTrack {
    var target by mutableFloatStateOf(Float.NaN)
    var position: State<Float>? = null
    var height = 0f
    var staggerMs by mutableLongStateOf(0L)
    var resumePosition by mutableFloatStateOf(Float.NaN)
    fun visualPosition() = resumePosition.takeIf(Float::isFinite) ?: position?.value ?: target
}

private data class AppleRailMotionRequest(val y: Float, val focus: Int, val manual: Boolean,
    val enabled: Boolean, val stagger: Boolean, val delayMs: Long, val follow: Int,
    val intervalMs: Long, val interlude: Boolean, val last: Boolean, val width: Float, val height: Float,
    val speed: Float)

/** A delayed target never stops the spring already in flight. Retargeting preserves velocity. */
@Composable private fun appleRailMotion(track: AppleLyricsTrack, request: AppleRailMotionRequest): State<Float>? {
    if (!request.y.isFinite()) return null
    val motion = remember { Animatable(track.resumePosition.takeIf(Float::isFinite) ?: request.y) }
    val latest = rememberUpdatedState(request)
    LaunchedEffect(motion) {
        coroutineScope {
            val animations = this
            var queued: Job? = null
            var queuedTarget = Float.NaN
            var due = Long.MAX_VALUE
            var previous = latest.value
            snapshotFlow { latest.value }.collect { next ->
                if (next.manual || !next.enabled) {
                    queued?.cancel(); due = Long.MAX_VALUE
                    motion.snapTo(next.y)
                    // A follow click first emits the new command while the old manual request
                    // is still queued. That stale request must not erase its newly captured seed.
                    if (!next.manual) track.resumePosition = Float.NaN
                    previous = next
                } else {
                    val resumed = track.resumePosition.isFinite()
                    if (resumed) {
                        queued?.cancel(); due = Long.MAX_VALUE
                        motion.snapTo(track.resumePosition)
                        track.resumePosition = Float.NaN
                    }
                    if (!resumed && next.y == motion.targetValue && queued?.isActive != true &&
                        (next.speed == previous.speed || !motion.isRunning)) {
                        // Keep command/viewport metadata fresh even when no movement is needed.
                        // A newly composed focus may precede its measured y by one pass, though.
                        previous = next.copy(focus = previous.focus)
                        return@collect
                    }
                    // A cue's focus can arrive one composition before its measured y.
                    // Keep the pending wave and its old focus until the new y arrives;
                    // consuming that focus here would make the next request skip its delay.
                    if (!resumed && queued?.isActive == true && next.y == queuedTarget &&
                        next.follow == previous.follow &&
                        next.width == previous.width && next.height == previous.height &&
                        next.stagger == previous.stagger && next.interlude == previous.interlude &&
                        next.speed == previous.speed) return@collect
                    val seek = resumed || next.follow != previous.follow || abs(next.focus - previous.focus) > 1
                    val step = next.focus != previous.focus && !seek &&
                        next.width == previous.width && next.height == previous.height
                    val waitMs = if (next.stagger && step && !next.interlude)
                        (next.delayMs / next.speed).roundToLong() else 0L
                    val now = SystemClock.uptimeMillis()
                    val deadline = minOf(if (queued?.isActive == true) due else Long.MAX_VALUE, now + waitMs)
                    queued?.cancel()
                    due = deadline
                    queuedTarget = next.y
                    previous = next
                    queued = launch {
                        delay((deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L))
                        due = Long.MAX_VALUE
                        val stiffness: Float
                        val damping: Float
                        when {
                            seek || next.interlude || next.focus == 0 -> { stiffness = 100f; damping = 5f / 6f }
                            next.last -> { stiffness = 140f / .9f; damping = 22f / (2f * kotlin.math.sqrt(140f * .9f)) }
                            else -> {
                                val interval = (next.intervalMs.coerceIn(100L, 800L) - 100L) / 700f
                                stiffness = (170f + 50f * (1f - interval).pow(.2f)) / .9f
                                // AMLL's solver treats its overdamped branch as critically damped.
                                damping = 1f
                            }
                        }
                        // This is a sibling of the pending job. A new pending target must not
                        // cancel it. animateTo captures velocity before superseding the old run.
                        animations.launch {
                            // Scaling stiffness by speed squared changes the time scale while
                            // preserving damping shape and the velocity at a live retarget.
                            motion.animateTo(next.y, spring(damping, stiffness * next.speed * next.speed, .25f),
                                initialVelocity = motion.velocity)
                        }
                    }
                }
            }
        }
    }
    return motion.asState()
}

/** Only the bounded reading window owns text, blur layers and springs. */
@Composable internal fun AppleMusicLyricsRail(document: LyricsDocument, target: Int, active: Set<Int>,
    modifier: Modifier, fontSize: Float, centered: Boolean, blur: Boolean, stagger: Boolean,
    motionEnabled: Boolean, followRequest: Int, interlude: Boolean,
    onBrowsing: (Boolean) -> Unit, options: AppleMusicLyricsOptions = AppleMusicLyricsOptions(),
    content: @Composable (Int, Boolean) -> Unit) {
    val tuning = options.normalized()
    val speed = tuning.animationSpeed
    val density = LocalDensity.current
    val unit = density.density
    val textPixels = fontSize * unit * density.fontScale
    var viewportHeight by remember { mutableFloatStateOf(0f) }
    var viewportWidth by remember { mutableFloatStateOf(0f) }
    // Small spacing can fit more rows, but the retained reading window remains bounded.
    val pitchFallback = textPixels * (tuning.lineHeight + .8f * tuning.lineSpacing)
    val radius = (ceil(viewportHeight / pitchFallback.coerceAtLeast(1f)).toInt() + 2).coerceIn(5, 24)
    val tracks = remember(document) { mutableMapOf<Int, AppleLyricsTrack>() }
    val windowIdentity = remember(document) { Any() }
    // Heights are cheap measurements, never retained text or bitmap caches.
    val heights = remember(document, textPixels, viewportWidth, tuning.lineSpacing, tuning.lineHeight) {
        mutableStateMapOf<Int, Float>()
    }
    var manualAnchor by remember(document) { mutableStateOf<Int?>(null) }
    var residual by remember(document) { mutableFloatStateOf(0f) }
    var browseStartedTarget by remember(document) { mutableIntStateOf(target) }
    var lastInputAt by remember(document) { mutableLongStateOf(0L) }
    var fingerDown by remember(document) { mutableStateOf(false) }
    var settling by remember(document) { mutableStateOf(false) }
    var browseInputVersion by remember(document) { mutableIntStateOf(0) }
    val currentTarget = rememberUpdatedState(target)
    val browsing = manualAnchor != null
    val anchor = (manualAnchor ?: target).coerceIn(0, document.lines.lastIndex.coerceAtLeast(0))
    val currentAnchor = rememberUpdatedState(anchor)
    fun pitch(index: Int) = (heights[index] ?: pitchFallback).coerceAtLeast(1f)
    fun readingOffset(index: Int) = minOf(pitch(index) * .5f, viewportHeight * .25f)
    // A tall paragraph owns its interior: snapping to its next edge must not hide the
    // lines the user just scrolled into view in a short landscape pane.
    fun tall(index: Int) = viewportHeight > 0f && pitch(index) > viewportHeight * .75f
    fun nearestRow(index: Int, offset: Float): Int {
        if (tall(index)) return index
        var closest = index
        var distance = abs(offset + readingOffset(index))
        if (index > 0) {
            val previousDistance = abs(offset - pitch(index - 1) + readingOffset(index - 1))
            if (previousDistance < distance) { closest = index - 1; distance = previousDistance }
        }
        if (index < document.lines.lastIndex && abs(offset + pitch(index) + readingOffset(index + 1)) < distance)
            closest = index + 1
        return closest
    }
    val browseFocus by remember(document, heights, pitchFallback, viewportHeight) { derivedStateOf {
        val index = manualAnchor ?: return@derivedStateOf -1
        nearestRow(index, residual)
    } }
    fun startBrowsing() {
        if (manualAnchor == null) {
            val from = currentTarget.value.coerceIn(0, document.lines.lastIndex.coerceAtLeast(0))
            // Grabbing a spring in flight starts at the currently displayed position.
            residual = tracks[from]?.visualPosition()?.takeIf(Float::isFinite) ?: 0f
            tracks.values.forEach { it.resumePosition = Float.NaN }
            manualAnchor = from
            browseStartedTarget = currentTarget.value
        }
    }
    fun scrollPixels(delta: Float): Float {
        if (document.lines.isEmpty() || delta == 0f) return 0f
        startBrowsing()
        var index = manualAnchor ?: currentAnchor.value
        var offset = residual + delta
        while (offset < -pitch(index) && index < document.lines.lastIndex) {
            offset += pitch(index); index++
        }
        while (offset > 0f && index > 0) {
            index--; offset -= pitch(index)
        }
        // Bound edge travel while keeping the middle of a long paragraph freely readable.
        if (index == 0 && offset > 0f) offset = offset.coerceAtMost(pitch(index) * .35f)
        if (index == document.lines.lastIndex) offset = offset.coerceAtLeast(-pitch(index))
        manualAnchor = index
        residual = offset
        lastInputAt = SystemClock.uptimeMillis()
        return delta
    }
    val scroll = rememberScrollableState { scrollPixels(it) }
    fun followPlayback() {
        if (manualAnchor != null) {
            // Carry the finger's sub-row displacement into every spring, before changing its
            // coordinate origin. Otherwise even a short drag snaps back by the whole residual.
            tracks.values.forEach { it.resumePosition = it.target + residual }
        }
        manualAnchor = null
        residual = 0f
    }
    LaunchedEffect(scroll.isScrollInProgress, fingerDown, browsing, browseInputVersion, motionEnabled, speed,
        viewportWidth, viewportHeight, tuning.lineSpacing, tuning.lineHeight) {
        if (!scroll.isScrollInProgress && !fingerDown && browsing) {
            lastInputAt = SystemClock.uptimeMillis()
            val index = manualAnchor ?: return@LaunchedEffect
            val selected = nearestRow(index, residual)
            if (tall(index) && abs(residual + readingOffset(index)) > textPixels * 1.2f)
                return@LaunchedEffect
            // Changing the origin and residual together retains every row's on-screen position.
            if (selected > index) { manualAnchor = selected; residual += pitch(index) }
            if (selected < index) { manualAnchor = selected; residual -= pitch(selected) }
            val destination = -readingOffset(selected)
            if (!motionEnabled) residual = destination else try {
                settling = abs(residual - destination) > .25f
                animate(residual, destination,
                    animationSpec = spring(dampingRatio = .92f, stiffness = 180f * speed * speed,
                        visibilityThreshold = .25f)) { value, _ ->
                    if (manualAnchor != null && !fingerDown && !scroll.isScrollInProgress) residual = value
                }
            } finally {
                settling = false
            }
            if (manualAnchor != null && !fingerDown && !scroll.isScrollInProgress) {
                residual = destination
            }
        }
    }
    SideEffect { onBrowsing(browsing) }
    LaunchedEffect(followRequest) {
        if (followRequest > 0) followPlayback()
    }
    // Browsing a distant verse is never interrupted by an unconditional timeout.
    // Resume only when a newly sung line has actually entered the reader's viewport.
    LaunchedEffect(target, browsing, scroll.isScrollInProgress, fingerDown) {
        if (browsing && !scroll.isScrollInProgress && !fingerDown && target != browseStartedTarget) {
            delay((500L - (SystemClock.uptimeMillis() - lastInputAt)).coerceAtLeast(0))
            val track = tracks[target]
            val top = viewportHeight * .35f + residual + (track?.target ?: Float.NaN)
            if (top.isFinite() && top >= viewportHeight * .08f &&
                top + (track?.height ?: 0f).coerceAtMost(viewportHeight * .5f) <= viewportHeight * .92f) {
                followPlayback()
            }
        }
    }
    val wanted = if (document.lines.isEmpty()) emptyList() else
        ((anchor - radius).coerceAtLeast(0)..minOf(anchor + radius, document.lines.lastIndex)).toList()
    var previous by remember(document) { mutableStateOf(wanted) }
    var departing by remember(document) { mutableStateOf(emptyList<Int>()) }
    // Retain leaving rows in this composition. Adding them only in SideEffect first
    // disposes their keyed content and spring, then recreates an unpositioned row.
    val retainedDeparting = if (previous != wanted) {
        if (browsing || !motionEnabled) emptyList() else previous.filter { it !in wanted }
    } else departing
    SideEffect {
        if (previous != wanted) {
            departing = retainedDeparting
            previous = wanted
        }
    }
    LaunchedEffect(departing, speed) {
        if (departing.isNotEmpty()) { delay((450f / speed).roundToLong()); departing = emptyList() }
    }
    val indices = (wanted + retainedDeparting).distinct().sorted()
    SideEffect { tracks.keys.retainAll(indices.toSet()) }
    val held = active.isEmpty() && !interlude && document.lines.getOrNull(target)?.let {
        // Prelude is a readable preview, not a falsely selected vocal.
        it.startTimeMs >= 0
    } == true
    val inset = 12.dp
    // Reserve the largest allowed halo once. Resizing this inset on every blur frame would
    // repeatedly resize offscreen layers and clip the tail when intensity is reduced.
    val effectInset = 48.dp
    val rowPadding = with(density) { (fontSize * .4f * tuning.lineSpacing).sp.toDp() }
    val ease = remember { CubicBezierEasing(.25f, .1f, .25f, 1f) }
    val interval = document.lines.getOrNull(target)?.startTimeMs?.minus(
        document.lines.getOrNull(target - 1)?.startTimeMs ?: 0L) ?: 800L
    Layout(modifier = modifier.testTag("lyrics_list").onSizeChanged {
        viewportHeight = it.height.toFloat(); viewportWidth = it.width.toFloat()
    }
        .semantics {
            this[AppleLyricsFollowing] = !browsing
            this[AppleLyricsAnchor] = anchor
            this[AppleLyricsBrowseFocus] = browseFocus
            verticalScrollAxisRange = ScrollAxisRange({ anchor.toFloat() }, { document.lines.lastIndex.coerceAtLeast(0).toFloat() })
            scrollBy { _, y -> if (y == 0f) false else { scrollPixels(-y); browseInputVersion++; true } }
            scrollToIndex { index ->
                if (index !in document.lines.indices) false else {
                    startBrowsing(); manualAnchor = index; residual = 0f
                    browseInputVersion++
                    lastInputAt = SystemClock.uptimeMillis(); true
                }
            }
        }.pointerInput(document) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // A press that catches the settling spring is a grab, not a lyric seek.
                // Consume only its down; scrollable can still take over a subsequent drag.
                if (settling) down.consume()
                fingerDown = true
                try {
                    do { val event = awaitPointerEvent(PointerEventPass.Final) }
                    while (event.changes.any { it.pressed })
                } finally { fingerDown = false }
            }
        }.scrollable(scroll, Orientation.Vertical)
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val fade = Brush.verticalGradient(0f to Color.Transparent, .075f to Color.Black,
                .90f to Color.Black, 1f to Color.Transparent)
            onDrawWithContent { drawContent(); drawRect(fade, blendMode = BlendMode.DstIn) }
        }, content = {
            indices.forEach { index -> key(windowIdentity, index) {
                val track = tracks.getOrPut(index) { AppleLyricsTrack() }
                val targetY = track.target
                val exiting = index !in wanted
                val browseSelected = browsing && index == browseFocus
                val focused = if (browsing) browseSelected else index in active || held && index == target
                val motion = appleRailMotion(track, AppleRailMotionRequest(targetY, target, browsing, motionEnabled,
                    stagger, track.staggerMs, followRequest, interval, interlude, target == document.lines.lastIndex,
                    viewportWidth, viewportHeight, speed))
                track.position = motion
                val alpha = animateFloatAsState(if (exiting) 0f else if (browsing) {
                    if (browseSelected) 1f else .85f
                } else if (focused) .85f else 1f,
                    if (motionEnabled) tween((400f / speed).roundToInt(), easing = ease) else snap(), label = "Apple lyric ink")
                val scale = animateFloatAsState(if (focused) 1f else .97f,
                    // Float's default .01 cutoff is a whole percent of the row width:
                    // it visibly snaps the last part of this small .03 scale change.
                    if (motionEnabled) spring(.8838835f, 50f * speed * speed, .0001f) else snap(), label = "Apple lyric emphasis")
                val distance = if (browsing) abs(index - browseFocus) else
                    if (index < target) target - index + 1 else abs(index - (active.maxOrNull() ?: target))
                // AMLL's asymmetric row-distance gradient, strengthened as requested. Logical
                // CSS pixels correspond to dp; font scaling must not multiply the blur again.
                val depth = (1f + distance) * (if (viewportWidth / unit <= 1024f) .8f else 1f)
                val blurRadius = animateFloatAsState(if (blur && !focused)
                    (depth * 1.25f).coerceAtMost(6f) * tuning.blurStrength else 0f,
                    if (motionEnabled) tween((400f / speed).roundToInt(), easing = ease) else snap(), label = "Apple lyric depth")
                val blurEffect by remember(blurRadius, unit) { derivedStateOf {
                    if (Build.VERSION.SDK_INT >= 31 && blurRadius.value > .05f)
                        BlurEffect(blurRadius.value * unit, blurRadius.value * unit, TileMode.Decal) else null
                } }
                Box(Modifier.fillMaxWidth().semantics { this[AppleLyricsBrowseSelected] = browseSelected }
                    .layout { measurable, constraints ->
                    val bleed = effectInset.roundToPx()
                    val child = measurable.measure(constraints.offset(bleed * 2, bleed * 2))
                    layout(child.width - bleed * 2, child.height - bleed * 2) { child.place(-bleed, -bleed) }
                }.graphicsLayer {
                    // The pending seed also wins for the last frame of the old manual layer:
                    // placement may observe residual=0 before its captured browsing flag updates.
                    translationY = track.resumePosition.takeIf(Float::isFinite) ?: if (browsing)
                        track.target.takeIf { it.isFinite() } ?: 0f
                        else track.visualPosition().takeIf(Float::isFinite) ?: 0f
                    val bleed = effectInset.toPx()
                    val pivot = if (centered) .5f else if (document.lines[index].isDuet) 1f else 0f
                    transformOrigin = TransformOrigin((bleed + (size.width - bleed * 2) * pivot) / size.width,
                        bleed / size.height)
                    scaleX = scale.value; scaleY = scale.value
                    this.alpha = if (motion == null) 0f else alpha.value
                    renderEffect = blurEffect
                }.padding(effectInset).padding(vertical = rowPadding)) {
                    CompositionLocalProvider(LocalAppleLyricsBrowseSelected provides browseSelected) { content(index, browsing) }
                }
            } }
        }) { measurables, constraints ->
        val width = (constraints.maxWidth - inset.roundToPx() * 2).coerceAtLeast(1)
        val rows = measurables.map { it.measure(Constraints.fixedWidth(width)) }
        indices.forEachIndexed { slot, index ->
            heights[index] = rows[slot].height.toFloat()
            tracks.getValue(index).height = rows[slot].height.toFloat()
        }
        val groupEnd = (if (browsing) anchor else active.maxOrNull() ?: anchor).coerceAtLeast(anchor)
        val focusHeight = (anchor..groupEnd).sumOf { (heights[it] ?: 0f).toDouble() }.toFloat()
        // Center normal groups like AMLL, but retain the beginning of an oversized paragraph
        // in a short landscape pane. The reader can scroll through its remaining lines.
        val groupTop = if (browsing) 0f else -minOf(focusHeight * .5f, constraints.maxHeight * .25f)
        var top = if (interlude && !browsing) 54.dp.toPx() else groupTop
        for (index in anchor..(wanted.lastOrNull() ?: -1)) {
            tracks[index]?.target = top
            top += heights[index] ?: pitchFallback
        }
        top = if (interlude && !browsing) 0f else groupTop
        for (index in (anchor - 1) downTo (wanted.firstOrNull() ?: anchor)) {
            top -= heights[index] ?: pitchFallback
            tracks[index]?.target = top
        }
        var waveDelay = 0f
        var waveStep = 50f
        indices.forEach { index ->
            val track = tracks.getValue(index)
            track.staggerMs = waveDelay.toLong()
            if (constraints.maxHeight * .35f + track.target + track.height >= 0f) {
                waveDelay += waveStep
                if (index >= target) waveStep /= 1.05f
            }
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val focus = constraints.maxHeight * .35f + if (browsing) residual else 0f
            rows.forEachIndexed { slot, row ->
                val track = tracks.getValue(indices[slot])
                val y = track.resumePosition.takeIf(Float::isFinite) ?: if (browsing) track.target else track.visualPosition()
                if (!y.isFinite() || focus + y < constraints.maxHeight + effectInset.toPx() && focus + y + row.height > -effectInset.toPx())
                    row.placeRelative(inset.roundToPx(), focus.roundToInt())
            }
        }
    }
}
