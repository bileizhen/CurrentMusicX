package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import kotlin.math.ceil
import kotlin.math.max
import io.github.currencortex.music.ui.component.rootMotionItems

internal val LocalElasticRowPaint = staticCompositionLocalOf<MutableMap<ElasticPressState, Unit>?> { null }

private data class VerticalOverflow(val top: Int, val bottom: Int)

private fun verticalOverflow(presses: Map<ElasticPressState, Unit>, growth: Float): VerticalOverflow {
    var top = 0f
    var bottom = 0f
    for (press in presses.keys) {
        val frame = press.frame(growth)
        val swell = press.size.height * (frame.scaleY - 1f) / 2f
        top = max(top, swell - frame.pull.y)
        bottom = max(bottom, swell + frame.pull.y)
    }
    return VerticalOverflow(ceil(top).toInt(), ceil(bottom).toInt())
}

/** Expand the cross-axis viewport to the painted bounds, without moving the resting items
 * or reserving animation space in the surrounding list. Horizontal scrolling stays clipped. */
@Composable internal fun ElasticLazyRow(modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(16.dp),
    edgeToEdgeInset: Dp = 0.dp,
    content: LazyListScope.() -> Unit) {
    val presses = remember { mutableStateMapOf<ElasticPressState, Unit>() }
    val density = LocalDensity.current
    val inset = edgeToEdgeInset.coerceAtLeast(0.dp)
    val growth = with(density) { 16.dp.toPx() }
    val pixels by remember(presses, growth) {
        derivedStateOf(structuralEqualityPolicy()) { verticalOverflow(presses, growth) }
    }
    // Keep the wrapper and LazyRow on the same immutable padding snapshot. Changing
    // contentPadding invalidates the row's measure policy even while scrolling is idle.
    val (topPx, bottomPx) = pixels
    val padding = with(density) {
        PaddingValues(start = inset, top = topPx.toDp(), end = inset, bottom = bottomPx.toDp())
    }
    CompositionLocalProvider(LocalElasticRowPaint provides presses) {
        LazyRow(modifier.layout { measurable, constraints ->
            val overflow = topPx + bottomPx
            // Let cards scroll through the parent's side gutters, while the first/last
            // card still rests on the same inset as the surrounding page content.
            val sidePx = inset.roundToPx()
            val wider = constraints.offset(horizontal = sidePx * 2)
            val expanded = if (constraints.hasBoundedHeight) wider.copy(
                maxHeight = (constraints.maxHeight.toLong() + overflow).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            ) else wider
            val child = measurable.measure(expanded)
            layout((child.width - sidePx * 2).coerceAtLeast(0), (child.height - overflow).coerceAtLeast(0)) {
                child.place(-sidePx, -topPx)
            }
        }, contentPadding = padding, horizontalArrangement = horizontalArrangement) { rootMotionItems(content = content) }
    }
}
