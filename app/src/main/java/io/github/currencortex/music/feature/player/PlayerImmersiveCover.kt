package io.github.currencortex.music.feature.player

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
        if (needsLayer) {
            layerPaint.alpha = opacity
            drawContext.canvas.saveLayer(rect, layerPaint)
        }
        try {
            clipPath(path) {
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
                    val mask = listOf(Color.White, Color.White.copy(alpha = 1 - .2f * amount),
                        Color.Transparent)
                    // The landscape controls and lyrics share the right-hand pane. Clear
                    // the cover by the centre gutter; portrait follows the measured title
                    // so large text and shorter windows cannot move controls onto artwork.
                    val fadeEnd = if (wideViewport) min(imageWidth, rect.width * (1 - .5f * amount))
                        else contentBoundary()?.takeIf { it.isFinite() }?.let { boundary ->
                            min(imageHeight, rect.height * (1 - amount + boundary.coerceIn(0f, 1f) * amount))
                        } ?: imageHeight
                    val brush = if (wideViewport) Brush.horizontalGradient(mask,
                        rect.left + fadeEnd * (1 - .6f * amount), rect.left + fadeEnd)
                    else Brush.verticalGradient(mask,
                        rect.top + fadeEnd * (1 - .52f * amount), rect.top + fadeEnd)
                    drawRect(brush, rect.topLeft, rect.size, blendMode = BlendMode.DstIn)
                }
            }
        } finally {
            if (needsLayer) drawContext.canvas.restore()
        }
    }
}
