package io.github.currencortex.music.feature.listening

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/** One clock for the visible chart; reading its phase only invalidates the bars' draw pass. */
@Composable internal fun rememberListeningBarLight(enabled: Boolean): State<Float>? {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val motion = (rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    if (!enabled || !motion || !lifecycle.isAtLeast(Lifecycle.State.RESUMED)) return null
    return rememberInfiniteTransition(label = "listening light").animateFloat(0f, 1f,
        infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "listening light travel")
}

@Composable internal fun ListeningDurationBar(primary: Color, emphasized: Boolean, light: State<Float>?, modifier: Modifier) {
    Box(modifier.clip(CircleShape).drawWithCache {
        val base = Brush.verticalGradient(listOf(primary.copy(alpha = if (emphasized) .95f else .40f), primary.copy(alpha = .16f)))
        onDrawBehind {
            drawRect(base)
            light?.let {
                val band = size.height * .48f
                val center = size.height + band - it.value * (size.height + band * 2)
                // A soft diagonal highlight travels up the capsule, fully outside it at both
                // ends of the loop. No flashing reset, bitmap allocation or offscreen blur.
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .32f), Color.Transparent),
                    start = Offset(-size.width, center + band / 2), end = Offset(size.width * 2, center - band / 2)))
            }
        }
    })
}
