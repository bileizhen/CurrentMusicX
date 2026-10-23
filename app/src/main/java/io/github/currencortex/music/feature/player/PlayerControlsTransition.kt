package io.github.currencortex.music.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt

internal val PlayerControlsVisibility = SemanticsPropertyKey<Float>("PlayerControlsVisibility")

/** Keep layout space and opacity in step with the pager, including a reversed drag. */
@Composable internal fun PlayerControlsRegion(visibility: () -> Float, modifier: Modifier = Modifier,
    content: @Composable () -> Unit) {
    val current = rememberUpdatedState(visibility)
    val visible by remember { derivedStateOf { current.value() > 0f } }
    if (!visible) return
    // Swipe progress changes layout/layers, not the composition of the whole player.
    Box(modifier.semantics { this[PlayerControlsVisibility] = visibility().coerceIn(0f, 1f) }
        .clipToBounds().graphicsLayer { alpha = visibility().coerceIn(0f, 1f) }
        .layout { measurable, constraints ->
            val child = measurable.measure(constraints.copy(minHeight = 0))
            layout(child.width, (child.height * visibility().coerceIn(0f, 1f)).roundToInt()) { child.place(0, 0) }
        }) { content() }
}
