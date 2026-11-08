package io.github.currencortex.music.feature.player

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.rememberArtworkGlowPalette
import kotlin.math.exp
import kotlin.math.sqrt

// A small shared alpha mask gives the beam continuous Gaussian falloff on every supported API.
// Rasterize once, not on each position/colour update, and let the GPU tint and scale it.
private val seekBeamMask by lazy {
    val width = 256
    val height = 96
    val pixels = IntArray(width * height) { index ->
        val x = (index % width + .5f) / width
        val y = ((index / width + .5f) / height) * 2f - 1f
        val behindTip = 1f - x
        val spread = .08f + .55f * sqrt(behindTip)
        val distance = (behindTip - .14f) / .26f
        val falloff = exp(-.5f * (distance * distance + y * y / (spread * spread)))
        val alpha = (falloff * minOf(1f, x / .12f) * .72f * 255).toInt().coerceIn(0, 255)
        (alpha shl 24) or 0x00FFFFFF
    }
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable internal fun rememberPlayerSeekTint(cover: String): State<Color> {
    val palette by rememberArtworkGlowPalette(cover)
    // Keep the cover's hue, with enough light for the thin track on a dark backdrop.
    return animateColorAsState(lerp(Color(palette.primary), Color.White, .25f), tween(600), label = "seek artwork tint")
}

/** Reuse unit-space brushes as the playhead moves; no blur layer or animation loop is needed. */
internal class PlayerSeekGlow(tint: Color) {
    private val glowTint = ColorFilter.tint(lerp(tint, Color.White, .45f))
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
        val fullLength = size.width * .25f
        val length = minOf(end, fullLength)
        val growth = (end / fullLength).coerceIn(0f, 1f)
        val radiusY = ((stroke * .5f + 14.dp.toPx()) * growth).coerceAtMost(size.height * .5f)
        // The beam ends at the playhead rather than spilling into the unplayed track.
        clipRect(left = 0f, top = 0f, right = end, bottom = size.height) {
            val mask = seekBeamMask
            withTransform({
                translate(end - length, size.height * .5f - radiusY)
                scale(length / mask.width, radiusY * 2f / mask.height, pivot = Offset.Zero)
            }) {
                drawImage(mask, alpha = alpha, colorFilter = glowTint)
            }
        }
    }

    fun DrawScope.drawHighlight(end: Float, stroke: Float) {
        if (end <= 0f) return
        val length = minOf(end, size.width * .25f)
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
