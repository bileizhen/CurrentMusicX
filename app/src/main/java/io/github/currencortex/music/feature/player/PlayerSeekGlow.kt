package io.github.currencortex.music.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.rememberArtworkGlowPalette

@Composable internal fun rememberPlayerSeekTint(cover: String): State<Color> {
    val palette by rememberArtworkGlowPalette(cover)
    // Keep the cover's hue, with enough light for the thin track on a dark backdrop.
    return animateColorAsState(lerp(Color(palette.primary), Color.White, .25f), tween(600), label = "seek artwork tint")
}

/** Reuse unit-space brushes as the playhead moves; no blur layer or animation loop is needed. */
internal class PlayerSeekGlow(tint: Color) {
    private val halo = Brush.radialGradient(
        0f to tint.copy(alpha = .38f),
        .3f to tint.copy(alpha = .26f),
        .6f to tint.copy(alpha = .10f),
        1f to tint.copy(alpha = 0f),
        center = Offset.Zero, radius = 1f,
    )
    private val highlight = Brush.horizontalGradient(
        0f to Color.White.copy(alpha = 0f),
        .45f to Color.White.copy(alpha = .18f),
        .8f to Color.White.copy(alpha = .72f),
        1f to Color.White,
        startX = 0f, endX = 1f,
    )

    fun DrawScope.drawHalo(end: Float, stroke: Float) {
        if (end <= 0f) return
        val length = minOf(end, 72.dp.toPx())
        val radiusY = (stroke * .5f + 9.dp.toPx()).coerceAtMost(size.height * .5f)
        // Keep the haze inside the touch area, including at either end of the track.
        clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
            withTransform({
                translate(end - length * .42f, size.height * .5f)
                scale(length * .65f, radiusY, pivot = Offset.Zero)
            }) {
                drawCircle(halo, radius = 1f, center = Offset.Zero)
            }
        }
    }

    fun DrawScope.drawHighlight(end: Float, stroke: Float) {
        if (end <= 0f) return
        val length = minOf(end, 72.dp.toPx())
        withTransform({
            translate(end - length, size.height * .5f)
            scale(length, 1f, pivot = Offset.Zero)
        }) {
            drawLine(highlight, Offset.Zero, Offset(1f, 0f), stroke, StrokeCap.Butt)
        }
    }
}
