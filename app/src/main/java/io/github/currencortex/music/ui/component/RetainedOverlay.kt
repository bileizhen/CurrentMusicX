package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Keep the cover and glass resources warm, without drawing or hit-testing a hidden overlay. */
@Composable internal fun RetainedOverlay(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier
        .then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
        .layout { measurable, constraints ->
            val child = measurable.measure(constraints)
            layout(child.width, child.height) { if (visible) child.placeRelative(0, 0) }
        }) { content() }
}
