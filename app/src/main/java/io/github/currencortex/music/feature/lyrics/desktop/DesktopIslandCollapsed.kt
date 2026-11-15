package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class DesktopIslandGesture { NONE, PREVIOUS, NEXT, COLLAPSE, EXPAND }

internal fun desktopIslandGesture(dx: Float, dy: Float, threshold: Float, collapsed: Boolean): DesktopIslandGesture {
    if (!dx.isFinite() || !dy.isFinite() || !threshold.isFinite() || threshold <= 0f) return DesktopIslandGesture.NONE
    val horizontal = abs(dx)
    val vertical = abs(dy)
    if (!collapsed && horizontal >= threshold && horizontal > vertical * 1.25f)
        return if (dx < 0f) DesktopIslandGesture.NEXT else DesktopIslandGesture.PREVIOUS
    if (vertical < threshold || vertical <= horizontal * 1.25f) return DesktopIslandGesture.NONE
    return when {
        dy < 0f && !collapsed -> DesktopIslandGesture.COLLAPSE
        dy > 0f && collapsed -> DesktopIslandGesture.EXPAND
        else -> DesktopIslandGesture.NONE
    }
}

internal fun desktopIslandTouchBounds(surfaceX: Int, surfaceY: Int, surfaceWidth: Int, contentTop: Int,
    contentSize: androidx.compose.ui.unit.IntSize, rootHeight: Int, minimumHeight: Int, density: Float): android.graphics.Rect {
    val width = contentSize.width.coerceIn(1, surfaceWidth.coerceAtLeast(1))
    val visualHeight = contentSize.height.coerceIn(1, rootHeight.coerceAtLeast(1))
    val height = if (minimumHeight > 0 && density > 0f)
        visualHeight.coerceAtLeast((minimumHeight * density).roundToInt()) else visualHeight
    val visualY = surfaceY + contentTop.coerceIn(0, (rootHeight - visualHeight).coerceAtLeast(0))
    return android.graphics.Rect(surfaceX + (surfaceWidth - width) / 2, visualY + (visualHeight - height) / 2,
        surfaceX + (surfaceWidth + width) / 2, visualY + (visualHeight + height) / 2)
}

@Composable internal fun rememberIslandCollapseProgress(collapsed: Boolean, motion: Boolean,
    onTransition: (Boolean) -> Unit): State<Float> {
    val progress = remember { Animatable(if (collapsed) 1f else 0f) }
    LaunchedEffect(collapsed, motion) {
        onTransition(true)
        try {
            if (motion) progress.animateTo(if (collapsed) 1f else 0f, tween(320, easing = FastOutSlowInEasing))
            else progress.snapTo(if (collapsed) 1f else 0f)
        } finally {
            onTransition(false)
        }
    }
    return progress.asState()
}

/** A single container shrinks to a two-pixel capsule; its content is clipped and contracted with it. */
@Composable internal fun DesktopIslandCollapseTransition(progress: State<Float>, compactWidth: Float,
    modifier: Modifier = Modifier, expanded: @Composable () -> Unit) {
    Layout(modifier = modifier.testTag("desktop_island_collapse_transition"), content = {
        Box(Modifier.clearAndSetSemantics {}) { expanded() }
    }) { children, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val full = children[0].measure(loose)
        val fraction = progress.value
        val width = constraints.constrainWidth((full.width + (compactWidth.dp.roundToPx() - full.width) * fraction).roundToInt())
        val height = constraints.constrainHeight((full.height + (2.dp.roundToPx() - full.height) * fraction).roundToInt())
        layout(width, height) {
            full.placeWithLayer((width - full.width) / 2, (height - full.height) / 2) {
                alpha = 1f - (fraction / .72f).coerceIn(0f, 1f)
                scaleX = 1f - fraction * .2f
                scaleY = 1f - fraction * .8f
            }
        }
    }
}
