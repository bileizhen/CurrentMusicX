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
    alpha: () -> Float = { 1f }, wideViewport: Boolean = false) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(remember(context, url) {
        ImageRequest.Builder(context).data(coverRequestUrl(url, 1200)).size(1200).build()
    })
    val palette by rememberArtworkGlowPalette(url)
    val base = lerp(Color(palette.primary), Color(0xFF101114), .7f)
    val path = remember { Path() }
    Canvas(modifier) {
        val rect = if (wideViewport) Rect(Offset.Zero, size) else bounds() ?: return@Canvas
        if (rect.isEmpty || !rect.width.isFinite() || !rect.height.isFinite()) return@Canvas
        val amount = immersion().coerceIn(0f, 1f)
        val opacity = alpha().coerceIn(0f, 1f)
        val r = radius().coerceIn(0f, rect.size.minDimension / 2)
        path.reset(); path.addRoundRect(RoundRect(rect, CornerRadius(r)))
        clipPath(path) {
            drawRect(base.copy(alpha = opacity), rect.topLeft, rect.size)
            val imageHeight = rect.height * (1 - amount) + min(rect.height, rect.width * 1.55f) * amount
            val intrinsic = painter.intrinsicSize
            if (intrinsic.width.isFinite() && intrinsic.height.isFinite() && intrinsic.width > 0 && intrinsic.height > 0) {
                val scale = max(rect.width / intrinsic.width, imageHeight / intrinsic.height)
                val target = intrinsic * scale
                clipRect(rect.left, rect.top, rect.right, rect.top + imageHeight) {
                    translate(rect.left + (rect.width - target.width) / 2, rect.top + (imageHeight - target.height) / 2) {
                        with(painter) { draw(target, alpha = opacity) }
                    }
                }
            }
            if (amount > 0f) {
                // Keep the art clear above the title, with a long, continuous fade into its own colour.
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .18f * amount),
                    Color.Transparent), rect.top, rect.top + imageHeight * .4f), rect.topLeft, rect.size,
                    alpha = opacity)
                if (wideViewport) {
                    // The right pane contains lyrics/transport in both landscape pages.
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, base.copy(alpha = .92f)),
                        rect.left + rect.width * .3f, rect.right), rect.topLeft, rect.size, alpha = opacity)
                }
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, base.copy(alpha = .2f), base),
                    rect.top + imageHeight * (1 - .52f * amount), rect.top + imageHeight), rect.topLeft, rect.size,
                    alpha = opacity)
            }
        }
    }
}
