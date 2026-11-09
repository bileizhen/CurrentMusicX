package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.currencortex.music.core.media.DockAudioLevel
import io.github.currencortex.music.ui.component.rememberArtworkGlowPalette

/** Draw behind the content without clipping the flying cover or changing the island's hit bounds. */
@Composable internal fun rememberDesktopIslandGlow(cover: String, songKey: String?, playing: Boolean,
    meter: DockAudioLevel, motion: Boolean, active: Boolean = true, playbackRate: Float = 1f): Modifier {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    // Overlay WindowOwner is STARTED while visible; it has no Activity resume event.
    val visible = active && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val running = visible && motion
    val palette by rememberArtworkGlowPalette(cover)
    val primary = animateColorAsState(Color(palette.primary), tween(if (motion) 900 else 0), label = "island glow primary")
    val secondary = animateColorAsState(Color(palette.secondary), tween(if (motion) 900 else 0), label = "island glow secondary")
    val position = remember { mutableFloatStateOf(0f) }
    val energy = remember { mutableFloatStateOf(0f) }
    val beat = remember { mutableFloatStateOf(0f) }
    val rate by rememberUpdatedState(playbackRate)
    DisposableEffect(meter, running, playing) {
        val subscription = if (running && playing) meter.acquire() else null
        onDispose { subscription?.close() }
    }
    val dynamics = remember(meter, songKey, running) { DesktopIslandGlowMotion() }
    LaunchedEffect(meter, dynamics, running, playing) {
        if (!running) {
            energy.floatValue = 0f; beat.floatValue = 0f; position.floatValue = 0f
            return@LaunchedEffect
        }
        val started = System.nanoTime()
        var previous = 0L
        do {
            withInfiniteAnimationFrameNanos { now ->
                val seconds = if (previous == 0L) 0f else (now - previous) / 1e9f
                previous = now
                val sample = meter.sample.takeIf {
                    playing && it.timestampNanos >= started && System.nanoTime() - it.timestampNanos in 0L..500_000_000L
                }
                dynamics.update(sample, seconds, rate, playing)
                position.floatValue = dynamics.position
                energy.floatValue = dynamics.energy
                beat.floatValue = dynamics.beat
            }
        } while (playing || dynamics.energy > 0f || dynamics.beat > 0f)
    }
    return Modifier.drawWithCache {
        val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2f))) }
        val left = islandGlowBrush(primary.value)
        val right = islandGlowBrush(secondary.value)
        onDrawBehind {
            if (!visible || size.width <= 0f || size.height <= 0f) return@onDrawBehind
            val audio = (energy.floatValue * .72f + beat.floatValue * .28f).coerceIn(0f, 1f)
            // Reduced motion keeps a quiet, stationary cover tint. Silence has no synthetic beat.
            val strength = .09f + audio * .38f
            val travel = position.floatValue * .31f
            clipPath(outline) {
                islandGlowPool(left, .5f + travel, .86f, .34f + beat.floatValue * .09f, 1.12f, strength)
                islandGlowPool(right, .5f - travel, .94f, .39f, .92f + audio * .38f, strength * .8f)
            }
        }
    }
}

private fun islandGlowBrush(color: Color) = Brush.radialGradient(
    0f to color, .25f to color.copy(alpha = .78f), .60f to color.copy(alpha = .24f),
    1f to color.copy(alpha = 0f), center = Offset.Zero, radius = 1f)

private fun DrawScope.islandGlowPool(brush: Brush, x: Float, y: Float, width: Float, height: Float, opacity: Float) {
    withTransform({
        translate(size.width * x, size.height * y)
        scale(size.width * width, size.height * height, pivot = Offset.Zero)
    }) { drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = opacity) }
}
