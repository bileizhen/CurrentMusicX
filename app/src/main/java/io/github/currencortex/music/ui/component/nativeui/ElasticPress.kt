// SPDX-License-Identifier: GPL-3.0-only
// ElasticPress / _RenderPressTransform adapted from chen08209's upstream project,
// lib/ui/navigation_dock.dart @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7.
// Flutter pointer/render APIs are mapped to Compose. See THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.min

@Immutable data class ElasticPressFrame(val lift: Float, val pull: Offset, val scaleX: Float, val scaleY: Float)
val ElasticPressGeometry = SemanticsPropertyKey<ElasticPressFrame>("ElasticPressGeometry")
val GlassPressLight = SemanticsPropertyKey<Float>("GlassPressLight")

/** The upstream rubber band: asymptotically approaches limit rather than stopping the finger. */
internal fun elasticRubberBand(distance: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    return limit * (1f - 1f / (abs(distance) * .55f / limit + 1f)) * kotlin.math.sign(distance)
}

@Stable class ElasticPressState internal constructor(private val scope: CoroutineScope) {
    private val lift = Animatable(0f, visibilityThreshold = .001f)
    private val pullX = Animatable(0f, visibilityThreshold = .01f)
    private val pullY = Animatable(0f, visibilityThreshold = .01f)
    private var releaseJob: Job? = null
    private var released = false
    private var origin = Offset.Zero
    var size by mutableStateOf(Size.Zero)
        internal set
    val amount get() = lift.value
    fun press(position: Offset) {
        origin = position
        released = false
        releaseJob?.cancel()
        scope.launch { lift.animateTo(1f, spring(.593f, 311f)) }
    }
    fun drag(position: Offset, pastEdges: Boolean = false) {
        val limit = size.minDimension * 7f / 32f
        val delta = if (pastEdges) position - Offset(position.x.coerceIn(0f, size.width), position.y.coerceIn(0f, size.height))
            else position - origin
        scope.launch { pullX.animateTo(elasticRubberBand(delta.x, limit), spring(1f, 2741.5568f)) }
        scope.launch { pullY.animateTo(elasticRubberBand(delta.y, limit), spring(1f, 2741.5568f)) }
    }
    fun release() {
        if (released) return
        released = true
        scope.launch { pullX.animateTo(0f, spring(.593f, 311f)) }
        scope.launch { pullY.animateTo(0f, spring(.593f, 311f)) }
        // Keep the rise of an early release; the following coroutine owns its fall.
        releaseJob = scope.launch {
            snapshotFlow { lift.value }.first { it >= .975f || lift.targetValue == 0f }
            if (released) {
                lift.animateTo(0f, spring(.593f, 311f))
            }
        }
    }

    internal fun frame(maxGrowth: Float): ElasticPressFrame {
        val width = size.width.coerceAtLeast(1f); val height = size.height.coerceAtLeast(1f)
        val swell = 1f + lift.value * min(.25f, maxGrowth / maxOf(width, height))
        return ElasticPressFrame(lift.value, Offset(pullX.value, pullY.value),
            swell * (1 + abs(pullX.value) / width * .5f), swell * (1 + abs(pullY.value) / height * .5f))
    }
}

@Composable fun rememberElasticPressState(): ElasticPressState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ElasticPressState(scope) }
}

/** Layout and hit targets stay at rest. Only the child's painted layer stretches and follows. */
@Composable fun ElasticPress(modifier: Modifier = Modifier, enabled: Boolean = true,
    state: ElasticPressState = rememberElasticPressState(), pullPastEdges: Boolean = false,
    observePointer: Boolean = true, paintedHeight: Dp? = null,
    content: @Composable () -> Unit) {
    val growth = with(LocalDensity.current) { 16.dp.toPx() }
    val heightPx = with(LocalDensity.current) { paintedHeight?.toPx() }
    val currentHeight by rememberUpdatedState(heightPx)
    SideEffect { if (heightPx != null) state.size = state.size.copy(height = heightPx) }
    val rowPaint = LocalElasticRowPaint.current
    DisposableEffect(rowPaint, state) {
        rowPaint?.put(state, Unit)
        onDispose { rowPaint?.remove(state) }
    }
    val currentEnabled by rememberUpdatedState(enabled)
    LaunchedEffect(enabled) { if (!enabled) state.release() }
    Box(modifier.onSizeChanged { state.size = Size(it.width.toFloat(), currentHeight ?: it.height.toFloat()) }
        .semantics { this[ElasticPressGeometry] = state.frame(growth); this[GlassPressLight] = state.amount.coerceIn(0f, 1f) }
        .then(if (observePointer) Modifier.pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!currentEnabled || currentHeight?.let { down.position.y !in 0f..it } == true) return@awaitEachGesture
                state.press(down.position)
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.changes.count { it.pressed } > 1) break
                        if (change.pressed) state.drag(change.position, pullPastEdges)
                    } while (change.pressed)
                } finally { state.release() }
            }
        } else Modifier)) {
        Box(Modifier.graphicsLayer {
            val frame = state.frame(growth)
            transformOrigin = TransformOrigin(.5f, if (size.height > 0) (heightPx ?: size.height) / (2 * size.height) else .5f)
            scaleX = frame.scaleX; scaleY = frame.scaleY
            translationX = frame.pull.x; translationY = frame.pull.y
        }) { content() }
    }
}
