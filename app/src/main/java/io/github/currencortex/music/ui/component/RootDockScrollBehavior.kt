package io.github.currencortex.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Observe the list after it scrolls; never take distance from lists, pagers or pull-to-refresh. */
internal class RootDockScrollBehavior(
    private val collapseDistance: Float,
    private val revealDistance: Float,
    private val enabled: () -> Boolean = { true },
) : NestedScrollConnection {
    var collapsed by mutableStateOf(false)
        private set
    private var travel = 0f

    fun resetGesture() { travel = 0f }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!enabled()) { resetGesture(); return Offset.Zero }
        // Fling/automatic positioning and horizontal carousel movement do not change the dock.
        if (source != NestedScrollSource.UserInput || abs(consumed.x) > abs(consumed.y)) return Offset.Zero
        val delta = consumed.y
        if (delta == 0f) {
            // A short pull at the top also restores navigation, including a short/empty page.
            if (available.y > 0f && abs(available.y) > abs(available.x)) {
                collapsed = false
                resetGesture()
            }
            return Offset.Zero
        }
        if (travel * delta < 0f) travel = 0f
        travel += delta
        if (travel <= -collapseDistance) collapsed = true
        if (travel >= revealDistance) collapsed = false
        travel = travel.coerceIn(-collapseDistance, revealDistance)
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        resetGesture()
        return Velocity.Zero
    }
}

@Composable internal fun rememberRootDockScrollBehavior(page: Int, miniPresent: Boolean,
    enabled: Boolean): RootDockScrollBehavior {
    val currentEnabled by rememberUpdatedState(enabled)
    val density = LocalDensity.current
    val behavior = remember(page, miniPresent, density) {
        with(density) { RootDockScrollBehavior(12.dp.toPx(), 24.dp.toPx()) { currentEnabled } }
    }
    LaunchedEffect(behavior, enabled) { behavior.resetGesture() }
    return behavior
}

/** Standard navigation uses the same duration as the glass dock, keeping page layout fixed. */
@Composable internal fun ScrollCollapsingNavigation(expanded: Boolean, modifier: Modifier = Modifier,
    content: @Composable () -> Unit) {
    val progress = animateFloatAsState(if (expanded) 1f else 0f, tween(320), label = "bottom navigation scroll")
    RetainedOverlay(progress.value > .001f, modifier
        .then(if (expanded) Modifier else Modifier.clearAndSetSemantics {})
        .pointerInput(expanded) {
            if (!expanded) awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
        .graphicsLayer {
            translationY = size.height * (1f - progress.value)
            alpha = progress.value
        }) { Box { content() } }
}
