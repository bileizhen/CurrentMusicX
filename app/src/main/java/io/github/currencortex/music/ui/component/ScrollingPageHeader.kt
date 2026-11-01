package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A page strip spends the first scroll distance leaving the page, then yields to its list.
 * Downward overscroll at the beginning restores it. The fixed title can still fade over content.
 * It observes the whole page, so pager children and reversed chat lists share the same behavior.
 */
@Composable internal fun ScrollingPageHeader(topInset: Dp, resetKey: Any?,
    modifier: Modifier = Modifier, header: @Composable () -> Unit, content: @Composable (Dp) -> Unit) {
    val density = LocalDensity.current
    val estimate = with(density) { 56.dp.toPx() }
    var height by remember(density) { mutableFloatStateOf(estimate) }
    var hidden by remember(resetKey, density) { mutableFloatStateOf(0f) }
    val connection = remember(resetKey, density) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                val previous = hidden
                hidden = (previous - available.y).coerceIn(0f, height)
                return Offset(0f, previous - hidden)
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val previous = hidden
                hidden = (previous - available.y).coerceIn(0f, height)
                return Offset(0f, previous - hidden)
            }
        }
    }
    Box(modifier.fillMaxSize().nestedScroll(connection)) {
        content(with(density) { (height - hidden).coerceAtLeast(0f).toDp() })
        Box(Modifier.fillMaxWidth().offset(y = topInset).clipToBounds().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val clipped = hidden.coerceAtMost(placeable.height.toFloat())
            layout(placeable.width, (placeable.height - clipped).roundToInt()) {
                placeable.placeRelative(0, -clipped.roundToInt())
            }
        }.onSizeChanged { height = it.height.toFloat() }) { header() }
    }
}
