package io.github.currencortex.music.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.rememberArtworkGlowPalette
import kotlin.math.exp

@Composable internal fun rememberPlayerSeekTint(cover: String): State<Color> {
    val palette by rememberArtworkGlowPalette(cover)
    // Keep the cover's hue, with enough light for the thin track on a dark backdrop.
    return animateColorAsState(lerp(Color(palette.primary), Color.White, .25f), tween(600), label = "seek artwork tint")
}

/** Reuse unit-space brushes as the playhead moves; no blur layer or animation loop is needed. */
internal class PlayerSeekGlow(tint: Color) {
    private val halo = Brush.horizontalGradient(
        0f to tint.copy(alpha = 0f),
        .2f to tint.copy(alpha = .08f),
        .5f to lerp(tint, Color.White, .25f).copy(alpha = .38f),
        .8f to lerp(tint, Color.White, .65f).copy(alpha = .65f),
        1f to Color.White.copy(alpha = .8f),
        startX = 0f, endX = 1f,
    )
    // A beam narrows into the playhead. Nested contours feather its edges without a blur layer.
    private val beam = Path().apply {
        moveTo(0f, -1f)
        cubicTo(.38f, -1f, .78f, -.5f, 1f, 0f)
        cubicTo(.78f, .5f, .38f, 1f, 0f, 1f)
        close()
    }
    private val feather = List(24) { layer ->
        val radius = 1f - layer / 24f
        val previousRadius = radius + 1f / 24f
        val previous = if (layer == 0) 0f else exp(-4.5f * previousRadius * previousRadius)
        radius to (exp(-4.5f * radius * radius) - previous) * 1.5f
    }
    private val highlight = Brush.horizontalGradient(
        0f to Color.White.copy(alpha = 0f),
        .45f to Color.White.copy(alpha = .18f),
        .8f to Color.White.copy(alpha = .72f),
        .92f to Color.White,
        1f to Color.White,
        startX = 0f, endX = 1f,
    )
    private val trackClip = Path()

    fun DrawScope.drawHalo(end: Float, stroke: Float, alpha: Float) {
        if (end <= 0f) return
        val length = minOf(end, 88.dp.toPx())
        val radiusY = (stroke * .5f + 14.dp.toPx()).coerceAtMost(size.height * .5f)
        // The beam ends at the playhead rather than spilling into the unplayed track.
        clipRect(left = 0f, top = 0f, right = end, bottom = size.height) {
            feather.forEach { (radius, opacity) ->
                withTransform({
                    translate(end - length, size.height * .5f)
                    scale(length, radiusY * radius, pivot = Offset.Zero)
                }) {
                    drawPath(beam, halo, alpha = alpha * opacity)
                }
            }
        }
    }

    fun DrawScope.drawHighlight(end: Float, stroke: Float) {
        if (end <= 0f) return
        val length = minOf(end, 72.dp.toPx())
        val radius = stroke * .5f
        val y = size.height * .5f
        trackClip.reset()
        trackClip.addRoundRect(RoundRect(-radius, y - radius, end + radius, y + radius, CornerRadius(radius)))
        // Paint one continuous gradient through the rounded cap; never overlay a circular thumb.
        clipPath(trackClip) {
            withTransform({
                translate(end - length, y)
                scale(length, 1f, pivot = Offset.Zero)
            }) {
                drawRect(highlight, topLeft = Offset(0f, -radius), size = Size(1f + radius / length, stroke))
            }
        }
    }
}
