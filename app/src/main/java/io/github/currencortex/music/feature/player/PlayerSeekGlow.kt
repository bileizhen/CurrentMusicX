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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.rememberArtworkGlowPalette
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

private const val SeekBeamLengthDp = 100f
private const val SeekBeamRadiusDp = 24f

private fun smoothMaskEdge(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

// A small shared alpha mask gives the beam continuous Gaussian falloff on every supported API.
// Rasterize once, not on each position/colour update, and let the GPU tint and scale it.
private val seekBeamMask by lazy {
    val width = 384
    val height = 192
    val pixels = IntArray(width * height) { index ->
        val x = (index % width).toFloat() / (width - 1)
        val y = (index / width).toFloat() / (height - 1) * 2f - 1f
        // Measured against the reference with a 4dp track: a long falloff behind a narrow tip.
        // Linear widening is essential; sqrt widening makes the end swell into a round blob.
        val behindDp = (1f - x) * SeekBeamLengthDp
        val spreadDp = 1.1f + .13f * behindDp
        val distance = behindDp / 28.7f
        val vertical = y * SeekBeamRadiusDp / spreadDp
        val falloff = exp(-.5f * (distance * distance + vertical * vertical))
        // A Gaussian alone is still visible at the texture bounds. Bring both opacity and its
        // slope to zero before the image/track clip so no straight rectangle edge can appear.
        val edgeFade = smoothMaskEdge((1f - abs(y)) / .16f) *
            smoothMaskEdge(x / .08f) * smoothMaskEdge(behindDp / 4f)
        val alpha = (falloff * edgeFade * .93f * 255).toInt().coerceIn(0, 255)
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
    private val glowTint = ColorFilter.tint(tint)
    private val highlight = Brush.horizontalGradient(
        *(0..32).map { step ->
            val position = step / 32f
            val distance = (1f - position) * SeekBeamLengthDp / 27.3f
            position to Color.White.copy(alpha = if (step == 0) 0f else exp(-.5f * distance * distance))
        }.toTypedArray(),
        startX = 0f, endX = 1f,
    )
    private val trackClip = Path()
    private val edgeLayerPaint = Paint()
    private var edgeFadeWidth = 0f
    private var edgeFade: Brush? = null

    fun DrawScope.drawHalo(end: Float, stroke: Float, alpha: Float) {
        if (end <= 0f) return
        val fullLength = minOf(size.width, SeekBeamLengthDp.dp.toPx())
        val tip = end + stroke * .5f
        // Early frames expand and brighten, then reach their full profile before 60dp played.
        val growth = sin((end / 56.dp.toPx()).coerceIn(0f, 1f) * (Math.PI.toFloat() * .5f))
        val length = fullLength * growth
        val strength = tanh(end / 26.7f.dp.toPx())
        val radiusY = stroke * .5f + (SeekBeamRadiusDp - 2f).dp.toPx() * growth
        val y = size.height * .5f
        val fadeWidth = 8.dp.toPx()
        val fadeAtStart = tip - length < fadeWidth
        if (fadeAtStart && edgeFadeWidth != fadeWidth) {
            edgeFadeWidth = fadeWidth
            edgeFade = Brush.horizontalGradient(Color.White.copy(alpha = 0f).let { clear ->
                listOf(clear, clear, Color.White.copy(alpha = .16f), Color.White.copy(alpha = .5f),
                    Color.White.copy(alpha = .84f), Color.White)
            }, startX = 0f, endX = fadeWidth)
        }
        // Allow the faint outer falloff outside the layout's hit area; never crop its soft edges.
        // The tip aligns with the rounded track's end, not a vertical cut through its cap.
        clipRect(left = 0f, top = y - radiusY, right = tip, bottom = y + radiusY) {
            val mask = seekBeamMask
            // Only the beginning needs a small alpha layer, so the left track boundary stays soft.
            if (fadeAtStart) drawContext.canvas.saveLayer(Rect(0f, y - radiusY, tip, y + radiusY), edgeLayerPaint)
            withTransform({
                translate(tip - length, y - radiusY)
                scale(length / mask.width, radiusY * 2f / mask.height, pivot = Offset.Zero)
            }) {
                drawImage(mask, alpha = alpha * strength, colorFilter = glowTint)
            }
            if (fadeAtStart) {
                drawRect(edgeFade!!, topLeft = Offset(0f, y - radiusY), size = Size(tip, radiusY * 2f), blendMode = BlendMode.DstIn)
                drawContext.canvas.restore()
            }
        }
    }

    fun DrawScope.drawHighlight(end: Float, stroke: Float) {
        if (end <= 0f) return
        val radius = stroke * .5f
        val tip = end + radius
        val length = minOf(tip, size.width, SeekBeamLengthDp.dp.toPx())
        val y = size.height * .5f
        trackClip.reset()
        trackClip.addRoundRect(RoundRect(-radius, y - radius, end + radius, y + radius, CornerRadius(radius)))
        // Paint one continuous gradient through the rounded cap; never overlay a circular thumb.
        clipPath(trackClip) {
            withTransform({
                translate(tip - length, y)
                scale(length, 1f, pivot = Offset.Zero)
            }) {
                drawRect(highlight, topLeft = Offset(0f, -radius), size = Size(1f, stroke))
            }
        }
    }
}
