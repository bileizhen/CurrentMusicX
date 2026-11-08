package io.github.currencortex.music.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

/** Preview locally; commit once on release so cancelled gestures never change playback order. */
internal class QueueReorderState(val keys: List<String>, private val list: LazyListState) {
    private val sourceByKey = keys.withIndex().associate { it.value to it.index }
    var order by mutableStateOf(keys.indices.toList())
        private set
    var dragged by mutableStateOf<Int?>(null)
        private set
    var returning by mutableStateOf<Int?>(null)
        private set
    val returnOffset = Animatable(0f)
    private var top by mutableFloatStateOf(0f)
    private var finger by mutableFloatStateOf(0f)
    private var rowHeight = 0
    private var grabOffset = 0f
    private var gesture = 0
    val active get() = dragged != null || returning != null

    fun start(y: Float): Boolean {
        if (active) return false
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size } ?: return false
        val source = sourceByKey[item.key] ?: return false
        gesture++
        dragged = source
        finger = y
        grabOffset = y - item.offset
        rowHeight = item.size
        top = item.offset.toFloat()
        return true
    }

    fun drag(delta: Float) {
        if (dragged == null) return
        finger += delta
        top = finger - grabOffset
        updateTarget()
    }

    fun translation(source: Int): Float {
        if (returning == source) return returnOffset.value
        if (dragged != source) return 0f
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == keys[source] } ?: return 0f
        return top - item.offset
    }

    fun updateTarget() {
        val source = dragged ?: return
        val current = order.indexOf(source)
        val center = top + rowHeight / 2f
        val target = list.layoutInfo.visibleItemsInfo.filter {
            val index = it.index
            // Ignore stale layout information until the preview has been measured again.
            order.getOrNull(index) == sourceByKey[it.key] && index != current &&
                if (index > current) center >= it.offset + it.size / 2f
                else center <= it.offset + it.size / 2f
        }.minByOrNull { kotlin.math.abs(center - (it.offset + it.size / 2f)) } ?: return
        list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        order = order.toMutableList().apply { add(target.index, removeAt(current)) }
    }

    fun scrollSpeed(edge: Float, maximum: Float): Float {
        if (dragged == null) return 0f
        val layout = list.layoutInfo
        val start = layout.viewportStartOffset + layout.beforeContentPadding
        val end = layout.viewportEndOffset - layout.afterContentPadding
        val fraction = when {
            finger < start + edge -> -((start + edge - finger) / edge).coerceIn(0f, 1f)
            finger > end - edge -> ((finger - end + edge) / edge).coerceIn(0f, 1f)
            else -> 0f
        }
        return fraction * kotlin.math.abs(fraction) * maximum
    }

    suspend fun scrollBy(delta: Float) {
        list.scrollBy(delta)
        updateTarget()
    }

    suspend fun finish(onMove: (Int, Int) -> Unit) {
        val source = dragged ?: return
        val finishingGesture = gesture
        val offset = translation(source)
        returnOffset.snapTo(offset)
        returning = source
        dragged = null
        try {
            returnOffset.animateTo(0f, spring(dampingRatio = .86f, stiffness = 500f))
            if (gesture == finishingGesture && returning == source) onMove(source, order.indexOf(source))
        } finally {
            if (gesture == finishingGesture) {
                returning = null
                order = keys.indices.toList()
            }
        }
    }

    fun cancel() {
        gesture++
        dragged = null
        returning = null
        order = keys.indices.toList()
    }
}

@Composable
internal fun queueReorderGesture(state: QueueReorderState, enabled: Boolean,
    onMove: (Int, Int) -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edge = with(density) { 64.dp.toPx() }
    val maximumSpeed = with(density) { 650.dp.toPx() }
    val removeWidth = with(density) { 48.dp.toPx() }
    val move by rememberUpdatedState(onMove)
    var released by remember(state) { mutableIntStateOf(0) }
    LaunchedEffect(state, enabled, released) {
        if (!enabled) state.cancel()
        else if (released > 0) state.finish(move)
    }
    LaunchedEffect(state, enabled, state.dragged) {
        if (!enabled || state.dragged == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(.032f)
            previous = now
            val speed = state.scrollSpeed(edge, maximumSpeed)
            if (speed != 0f) {
                state.scrollBy(speed * seconds)
            }
        }
    }
    DisposableEffect(state) { onDispose { state.cancel() } }
    return if (!enabled) Modifier else Modifier.pointerInput(state) {
        detectDragGesturesAfterLongPress(
            onDragStart = { point ->
                if (point.x < size.width - removeWidth && state.start(point.y))
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDrag = { change, amount -> if (state.dragged != null) { change.consume(); state.drag(amount.y) } },
            onDragEnd = { if (state.dragged != null) released++ },
            onDragCancel = { state.cancel() },
        )
    }
}
