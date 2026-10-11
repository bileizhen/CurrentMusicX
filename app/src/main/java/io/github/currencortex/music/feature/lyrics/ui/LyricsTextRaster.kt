package io.github.currencortex.music.feature.lyrics.ui

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** One paragraph raster, with cached isolated halos only for eligible sung glyphs. */
internal class LyricsTextRaster(private val layout: TextLayoutResult, private val density: Density,
    private val direction: LayoutDirection) {
    private val glyphHalos = mutableMapOf<Rect, Pair<ImageBitmap, Offset>>()
    private val maskPaint by lazy { Paint().apply {
        // Keep bloom outside the source ink, so a partially sung glyph's halo
        // cannot paint its unsung interior white before the karaoke sweep arrives.
        maskFilter = BlurMaskFilter(6f * density.density, BlurMaskFilter.Blur.OUTER)
    } }
    val ink: ImageBitmap by lazy {
        ImageBitmap(layout.size.width.coerceAtLeast(1), layout.size.height.coerceAtLeast(1)).also { bitmap ->
            CanvasDrawScope().draw(density, direction, Canvas(bitmap), Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
                drawText(layout, color = Color.White)
            }
        }
    }
    fun glyphHalo(box: Rect): Pair<ImageBitmap, Offset> = glyphHalos.getOrPut(box) {
        val left = kotlin.math.floor(box.left).toInt()
        val top = kotlin.math.floor(box.top).toInt()
        val glyph = android.graphics.Bitmap.createBitmap(
            (kotlin.math.ceil(box.right).toInt() - left).coerceAtLeast(1),
            (kotlin.math.ceil(box.bottom).toInt() - top).coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(glyph).apply {
            clipRect(box.left - left, box.top - top, box.right - left, box.bottom - top)
            drawBitmap(ink.asAndroidBitmap(), -left.toFloat(), -top.toFloat(), null)
        }
        val offsets = IntArray(2)
        val alpha = glyph.extractAlpha(maskPaint, offsets)
        glyph.recycle()
        alpha.asImageBitmap() to Offset((left + offsets[0]).toFloat(), (top + offsets[1]).toFloat())
    }
}
