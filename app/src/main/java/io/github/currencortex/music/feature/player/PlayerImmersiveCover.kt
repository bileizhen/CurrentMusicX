package io.github.currencortex.music.feature.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import io.github.currencortex.music.ui.component.coverRequestUrl
import io.github.currencortex.music.ui.component.rememberArtworkGlowPalette
import kotlin.math.max
import kotlin.math.min

// Match the floating header's ease-in/out fade. Sampling once avoids a sharp
// slope change halfway through the artwork and a visible cutoff at its tail.
private val ImmersiveCoverFadeStops = CubicBezierEasing(.42f, 0f, .58f, 1f).let { easing ->
    Array(33) { index ->
        val fraction = index / 32f
        fraction to Color.White.copy(alpha = 1 - easing.transform(fraction))
    }
}

/** Crops at draw time, so the tall cover can morph into a square without stretching its pixels. */
@Composable internal fun PlayerImmersiveCover(url: String, modifier: Modifier,
    bounds: () -> Rect?, radius: () -> Float, immersion: () -> Float,
    alpha: () -> Float = { 1f }, wideViewport: Boolean = false,
    contentBoundary: () -> Float? = { null }) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(remember(context, url) {
        ImageRequest.Builder(context).data(coverRequestUrl(url, 1200)).size(1200).build()
    })
    val palette by rememberArtworkGlowPalette(url)
    val base = lerp(Color(palette.primary), Color(0xFF101114), .7f)
    val path = remember { Path() }
    val layerPaint = remember { Paint() }
    Canvas(modifier) {
        val rect = if (wideViewport) Rect(Offset.Zero, size) else bounds() ?: return@Canvas
        if (rect.isEmpty || !rect.width.isFinite() || !rect.height.isFinite()) return@Canvas
        val amount = immersion().coerceIn(0f, 1f)
        val opacity = alpha().coerceIn(0f, 1f)
        if (opacity <= 0f) return@Canvas
        val r = radius().coerceIn(0f, rect.size.minDimension / 2)
        path.reset(); path.addRoundRect(RoundRect(rect, CornerRadius(r)))
        // Mask only the artwork. Applying DstIn directly to the parent canvas would
        // also erase the animated backdrop that should show through the fading edge.
        val needsLayer = amount > 0f || opacity < 1f
        clipPath(path) {
            // Establish the layer AFTER the AA clip so the rounded edge is applied
            // once, to the composited artwork and mask. Clipping each draw separately
            // leaves partially covered pixels behind when DstIn clears the lower edge.
            if (needsLayer) {
                layerPaint.alpha = opacity
                drawContext.canvas.saveLayer(rect, layerPaint)
            }
            try {
                drawRect(base, rect.topLeft, rect.size)
                val imageHeight = rect.height * (1 - amount) + min(rect.height, rect.width * 1.55f) * amount
                // Keep landscape artwork on the left instead of cropping a square cover
                // against the entire screen width. Its right edge is already transparent.
                val imageWidth = if (wideViewport)
                    rect.width * (1 - amount) + min(rect.width * .7f, rect.height * 1.4f) * amount
                    else rect.width
                val intrinsic = painter.intrinsicSize
                if (intrinsic.width.isFinite() && intrinsic.height.isFinite() && intrinsic.width > 0 && intrinsic.height > 0) {
                    val scale = max(imageWidth / intrinsic.width, imageHeight / intrinsic.height)
                    val target = intrinsic * scale
                    clipRect(rect.left, rect.top, rect.left + imageWidth, rect.top + imageHeight) {
                        translate(rect.left + (imageWidth - target.width) / 2, rect.top + (imageHeight - target.height) / 2) {
                            with(painter) { draw(target) }
                        }
                    }
                }
                if (amount > 0f) {
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .18f * amount),
                        Color.Transparent), rect.top, rect.top + imageHeight * .4f), rect.topLeft, rect.size)
                    // The fade narrows to nothing at the compact-cover endpoint. Its
                    // outer edge stays clear even mid-flight, so the cropped image never
                    // ends in a visible strip of the placeholder colour below/beside it.
                    // The landscape controls and lyrics share the right-hand pane. Clear
                    // the cover by the centre gutter; portrait follows the measured title
                    // so large text and shorter windows cannot move controls onto artwork.
                    val fadeEnd = if (wideViewport) min(imageWidth, rect.width * (1 - .5f * amount))
                        else contentBoundary()?.takeIf { it.isFinite() }?.let { boundary ->
                            min(imageHeight, rect.height * (1 - amount + boundary.coerceIn(0f, 1f) * amount))
                        } ?: imageHeight
                    val brush = if (wideViewport) Brush.horizontalGradient(*ImmersiveCoverFadeStops,
                        startX = rect.left + fadeEnd * (1 - .6f * amount), endX = rect.left + fadeEnd)
                    else Brush.verticalGradient(*ImmersiveCoverFadeStops,
                        startY = rect.top + fadeEnd * (1 - .52f * amount), endY = rect.top + fadeEnd)
                    // Cover fractional rect edges fully; the outer clip defines the shape.
                    val maskBounds = rect.inflate(1f)
                    drawRect(brush, maskBounds.topLeft, maskBounds.size, blendMode = BlendMode.DstIn)
                }
            } finally {
                if (needsLayer) drawContext.canvas.restore()
            }
        }
    }
}
