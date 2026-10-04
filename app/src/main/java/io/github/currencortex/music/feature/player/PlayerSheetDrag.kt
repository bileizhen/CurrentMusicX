package io.github.currencortex.music.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

internal class PlayerSheetState {
    val animation = Animatable(0f)
    var draggedProgress by mutableStateOf<Float?>(null)
    val dragging get() = draggedProgress != null
    val value get() = draggedProgress ?: animation.value
}

internal class PlayerSheetDragController(val state: PlayerSheetState, private val scope: CoroutineScope) {
    var onOpen: () -> Unit = {}
    var onClose: () -> Unit = {}
    var onSettled: () -> Unit = {}
    var distance: () -> Float = { 2000f }
    var flingThreshold = 2000f
    private var opening = false
    private var travel = 1f
    private var settle: Job? = null

    fun begin(fromMini: Boolean) {
        settle?.cancel()
        opening = fromMini
        state.draggedProgress = state.value
        scope.launch { state.animation.stop() }
        if (fromMini) onOpen()
        travel = distance().coerceAtLeast(1f)
    }
    fun drag(delta: Float) {
        state.draggedProgress?.let { state.draggedProgress = (it - delta / travel).coerceIn(0f, 1f) }
    }
    fun end(velocity: Float, cancelled: Boolean = false) {
        val p = state.draggedProgress ?: return
        val expand = if (cancelled) !opening else if (abs(velocity) > flingThreshold) velocity < 0
            else if (opening) p >= .14f else p > .82f
        settle = scope.launch {
            state.animation.snapTo(p)
            state.draggedProgress = null
            if (expand) {
                state.animation.animateTo(1f, tween(((1 - p) * PLAYER_SHEET_DURATION).toInt().coerceAtLeast(120),
                    easing = CubicBezierEasing(.2f, 0f, 0f, 1f)))
                onSettled()
            } else onClose()
        }
    }
}
internal val LocalPlayerSheetDrag = staticCompositionLocalOf<PlayerSheetDragController?> { null }

/** Direction locking leaves the mini player's horizontal song gesture and lyric scrolling intact. */
@Composable internal fun Modifier.playerSheetDrag(fromMini: Boolean,
    excludedArea: () -> List<LayoutCoordinates> = { emptyList() }): Modifier {
    val controller = LocalPlayerSheetDrag.current ?: return this
    val exclusion by rememberUpdatedState(excludedArea)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return onGloballyPositioned { coordinates = it }.pointerInput(controller, fromMini) {
        val velocity = VelocityTracker()
        if (!fromMini) {
            // Claim vertical movement before the cover's scroll container, but leave the
            // entire lyric viewport and horizontal controls untouched from pointer down.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val rootDown = coordinates?.localToRoot(down.position) ?: down.position
                val root = coordinates ?: return@awaitEachGesture
                if (exclusion().any { excluded ->
                    if (!excluded.isAttached) false else {
                        val point = excluded.localPositionOf(root, down.position)
                        point.x >= 0 && point.y >= 0 && point.x < excluded.size.width && point.y < excluded.size.height
                    }
                }) return@awaitEachGesture
                velocity.resetTracking()
                velocity.addPosition(down.uptimeMillis, rootDown)
                var travel = Offset.Zero
                var started = false
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                            ?: break
                        velocity.addPosition(change.uptimeMillis, coordinates?.localToRoot(change.position) ?: change.position)
                        if (change.isConsumed) break
                        if (!change.pressed) {
                            if (started) { controller.end(velocity.calculateVelocity().y); started = false }
                            break
                        }
                        val delta = change.positionChange()
                        if (!started) {
                            travel += delta
                            if (abs(travel.x) > viewConfiguration.touchSlop && abs(travel.x) >= abs(travel.y)) break
                            if (abs(travel.y) <= viewConfiguration.touchSlop) continue
                            controller.begin(fromMini = false)
                            started = true
                            controller.drag(travel.y - sign(travel.y) * viewConfiguration.touchSlop)
                        } else controller.drag(delta.y)
                        change.consume()
                    }
                } finally {
                    if (started) controller.end(0f, cancelled = true)
                }
            }
            return@pointerInput
        }
        detectDragGestures(orientationLock = Orientation.Vertical,
            onDragStart = { down, trigger, _ ->
                velocity.resetTracking()
                velocity.addPosition(down.uptimeMillis, coordinates?.localToRoot(down.position) ?: down.position)
                velocity.addPosition(trigger.uptimeMillis, coordinates?.localToRoot(trigger.position) ?: trigger.position)
                controller.begin(fromMini)
            },
            onDrag = { change, amount ->
                velocity.addPosition(change.uptimeMillis, coordinates?.localToRoot(change.position) ?: change.position)
                change.consume()
                controller.drag(amount.y)
            },
            onDragEnd = { change ->
                velocity.addPosition(change.uptimeMillis, coordinates?.localToRoot(change.position) ?: change.position)
                controller.end(velocity.calculateVelocity().y)
            },
            onDragCancel = { controller.end(0f, cancelled = true) })
    }
}
