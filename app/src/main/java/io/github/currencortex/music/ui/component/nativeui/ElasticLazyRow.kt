package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max

internal val LocalElasticRowPaint = staticCompositionLocalOf<MutableMap<ElasticPressState, Unit>?> { null }

/** Expand the cross-axis viewport to the painted bounds, without moving the resting items
 * or reserving animation space in the surrounding list. Horizontal scrolling stays clipped. */
@Composable internal fun ElasticLazyRow(modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(16.dp),
    content: LazyListScope.() -> Unit) {
    val presses = remember { mutableStateMapOf<ElasticPressState, Unit>() }
    val density = LocalDensity.current
    val growth = with(density) { 16.dp.toPx() }
    var top = 0f
    var bottom = 0f
    for (press in presses.keys) {
        val frame = press.frame(growth)
        val swell = press.size.height * (frame.scaleY - 1f) / 2f
        top = max(top, swell - frame.pull.y)
        bottom = max(bottom, swell + frame.pull.y)
    }
    val topPx = ceil(top).toInt()
    val bottomPx = ceil(bottom).toInt()
    CompositionLocalProvider(LocalElasticRowPaint provides presses) {
        LazyRow(modifier.layout { measurable, constraints ->
            val overflow = topPx + bottomPx
            val expanded = if (constraints.hasBoundedHeight) constraints.copy(
                maxHeight = (constraints.maxHeight.toLong() + overflow).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            ) else constraints
            val child = measurable.measure(expanded)
            layout(child.width, (child.height - overflow).coerceAtLeast(0)) {
                child.place(0, -topPx)
            }
        }, contentPadding = with(density) {
            PaddingValues(top = topPx.toDp(), bottom = bottomPx.toDp())
        }, horizontalArrangement = horizontalArrangement, content = content)
    }
}
