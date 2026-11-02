package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max

internal val LocalElasticRowPaint = staticCompositionLocalOf<MutableMap<ElasticPressState, Unit>?> { null }

private data class VerticalOverflow(val top: Int, val bottom: Int)

/** Both the lazy row and its wrapper read these insets during measurement. */
private class ElasticRowPadding(presses: Map<ElasticPressState, Unit>, private val density: Density) : PaddingValues {
    private val growth = with(density) { 16.dp.toPx() }
    val pixels by derivedStateOf(structuralEqualityPolicy()) {
        var top = 0f
        var bottom = 0f
        for (press in presses.keys) {
            val frame = press.frame(growth)
            val swell = press.size.height * (frame.scaleY - 1f) / 2f
            top = max(top, swell - frame.pull.y)
            bottom = max(bottom, swell + frame.pull.y)
        }
        VerticalOverflow(ceil(top).toInt(), ceil(bottom).toInt())
    }

    override fun calculateTopPadding() = with(density) { pixels.top.toDp() }
    override fun calculateBottomPadding() = with(density) { pixels.bottom.toDp() }
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
}

/** Expand the cross-axis viewport to the painted bounds, without moving the resting items
 * or reserving animation space in the surrounding list. Horizontal scrolling stays clipped. */
@Composable internal fun ElasticLazyRow(modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(16.dp),
    content: LazyListScope.() -> Unit) {
    val presses = remember { mutableStateMapOf<ElasticPressState, Unit>() }
    val density = LocalDensity.current
    val padding = remember(presses, density) { ElasticRowPadding(presses, density) }
    CompositionLocalProvider(LocalElasticRowPaint provides presses) {
        LazyRow(modifier.layout { measurable, constraints ->
            val (topPx, bottomPx) = padding.pixels
            val overflow = topPx + bottomPx
            val expanded = if (constraints.hasBoundedHeight) constraints.copy(
                maxHeight = (constraints.maxHeight.toLong() + overflow).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            ) else constraints
            val child = measurable.measure(expanded)
            layout(child.width, (child.height - overflow).coerceAtLeast(0)) {
                child.place(0, -topPx)
            }
        }, contentPadding = padding, horizontalArrangement = horizontalArrangement, content = content)
    }
}
