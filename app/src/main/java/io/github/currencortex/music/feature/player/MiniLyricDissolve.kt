package io.github.currencortex.music.feature.player

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private class LyricInk(val text: String, visible: Boolean) {
    val opacity = Animatable(if (visible) 1f else 0f)
    var layout: TextLayoutResult? = null
}

/** The live Text owns semantics and shared-element geometry. Only the outgoing ink is cached. */
@Composable internal fun miniLyricDissolve(
    trackKey: String?, text: String, color: Color, snapshot: PlayerSharedTextSnapshot,
): Modifier {
    if (trackKey == null) return Modifier
    val scale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    if (scale?.scaleFactor == 0f) return Modifier

    val lines = remember(trackKey) { mutableStateListOf(LyricInk(text, visible = true)) }
    val current = remember(trackKey, text) {
        // Text is about to be remeasured. Keep the layout that was actually on screen.
        snapshot.layout?.let { measured ->
            lines.firstOrNull { it.text == measured.layoutInput.text.text }?.layout = measured
        }
        lines.firstOrNull { it.text == text }
            ?: LyricInk(text, visible = lines.none { it.layout != null }).also { lines.add(it) }
    }
    for (line in lines) key(line) {
        val isCurrent = line === current
        LaunchedEffect(isCurrent) {
            val target = if (isCurrent) 1f else 0f
            if (line.opacity.value != target) line.opacity.animateTo(target, tween(190))
            if (!isCurrent) lines.remove(line)
        }
    }
    val fullyVisible by remember(current) { derivedStateOf { current.opacity.value == 1f } }
    if (lines.size == 1 && fullyVisible) return Modifier

    val maxRadius = with(LocalDensity.current) { 3.dp.toPx() }
    val blurSupported = Build.VERSION.SDK_INT >= 31 && LocalView.current.isHardwareAccelerated
    val blur = remember(maxRadius, blurSupported) {
        Array(9) { step ->
            if (blurSupported && step > 0) {
                val radius = maxRadius * step / 8f
                BlurEffect(radius, radius, TileMode.Decal)
            } else null
        }
    }
    return Modifier.drawWithCache {
        val outgoing = lines.mapNotNull { line ->
            if (line === current) return@mapNotNull null
            val layout = line.layout ?: return@mapNotNull null
            line to obtainGraphicsLayer().apply { record { drawText(layout, color = color) } }
        }
        onDrawWithContent {
            for ((line, layer) in outgoing) {
                val opacity = line.opacity.value
                layer.alpha = opacity
                layer.renderEffect = blur[((1f - opacity) * blur.lastIndex).roundToInt()]
                drawLayer(layer)
            }
            drawContent()
        }
    }.graphicsLayer {
        val opacity = current.opacity.value
        alpha = opacity
        renderEffect = blur[((1f - opacity) * blur.lastIndex).roundToInt()]
    }
}
