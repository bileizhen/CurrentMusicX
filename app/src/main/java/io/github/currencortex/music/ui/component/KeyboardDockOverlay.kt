package io.github.currencortex.music.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

internal val KeyboardDockAlpha = SemanticsPropertyKey<Float>("KeyboardDockAlpha")

/** Retain artwork and glass while the IME covers them; move only the overlay layer. */
@Composable internal fun KeyboardDockOverlay(keyboardOpen: Boolean, modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit) {
    val reveal = animateFloatAsState(if (keyboardOpen) 0f else 1f,
        tween(240, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)), label = "keyboard dock reveal")
    val draw by remember(keyboardOpen) { derivedStateOf { !keyboardOpen || reveal.value > .001f } }
    RetainedOverlay(draw, modifier.fillMaxSize().testTag("keyboard_dock_overlay")
        .semantics { this[KeyboardDockAlpha] = reveal.value }
        .graphicsLayer {
            alpha = reveal.value
            translationY = 16.dp.toPx() * (1f - reveal.value)
        }) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()
            .then(if (keyboardOpen || reveal.value < .999f) Modifier.clearAndSetSemantics {} else Modifier), content = content)
    }
}
