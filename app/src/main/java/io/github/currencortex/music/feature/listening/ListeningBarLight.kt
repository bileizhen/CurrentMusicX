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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.currencortex.music.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/** One clock for the visible chart; reading its phase only invalidates the bars' draw pass. */
@Composable internal fun rememberListeningBarLight(enabled: Boolean): State<Float>? {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val motion = (rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    if (!enabled || !motion || !lifecycle.isAtLeast(Lifecycle.State.RESUMED)) return null
    return rememberInfiniteTransition(label = "listening light").animateFloat(0f, 1f,
        infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "listening light travel")
}

@Composable internal fun ListeningDurationBar(primary: Color, emphasized: Boolean, light: State<Float>?, modifier: Modifier) {
    val softRed = FootprintSoftRed
    val ray = ImageBitmap.imageResource(R.drawable.listening_ray)
    Box(modifier.clip(CircleShape).drawWithCache {
        val base = Brush.verticalGradient(if (emphasized) listOf(Color(0xFFFF2222), Color(0xFFFF8080)) else listOf(softRed, softRed))
        onDrawBehind {
            drawRect(base)
            light?.let {
                val band = size.width * (146.667f / 32f)
                val top = size.height - it.value * band * 2
                drawImage(ray, dstOffset = IntOffset(0, top.toInt()), dstSize = IntSize(size.width.toInt(), band.toInt()))
            }
        }
    })
}
