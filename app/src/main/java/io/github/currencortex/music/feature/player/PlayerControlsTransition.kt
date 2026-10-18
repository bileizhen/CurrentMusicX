package io.github.currencortex.music.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt

internal val PlayerControlsVisibility = SemanticsPropertyKey<Float>("PlayerControlsVisibility")

/** Keep layout space and opacity in step with the pager, including a reversed drag. */
@Composable internal fun PlayerControlsRegion(visibility: Float, modifier: Modifier = Modifier,
    content: @Composable () -> Unit) {
    val fraction = visibility.coerceIn(0f, 1f)
    if (fraction <= 0f) return
    Box(modifier.semantics { this[PlayerControlsVisibility] = fraction }
        .clipToBounds().graphicsLayer { alpha = fraction }
        .layout { measurable, constraints ->
            val child = measurable.measure(constraints.copy(minHeight = 0))
            layout(child.width, (child.height * fraction).roundToInt()) { child.place(0, 0) }
        }) { content() }
}
