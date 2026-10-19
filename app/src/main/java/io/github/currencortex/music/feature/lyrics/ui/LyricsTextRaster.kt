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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** One paragraph raster, with cached isolated halos only for eligible sung glyphs. */
internal class LyricsTextRaster(private val layout: TextLayoutResult, private val density: Density,
    private val direction: LayoutDirection, private val glowRadiusScale: Float = 1f,
    private val monet: Boolean = false) {
    private val glyphHalos = mutableMapOf<Rect, Pair<ImageBitmap, Offset>>()
    private val wideHalos = mutableMapOf<Rect, Pair<ImageBitmap, Offset>>()
    private val untimedParagraphs = mutableMapOf<List<Rect>, ImageBitmap>()
    private val prepared = mutableSetOf<ImageBitmap>()
    /** Optional desktop prefetch: each immutable finished bitmap is uploaded at most once. */
    fun prepareForDrawing(bitmap: ImageBitmap) {
        if (prepared.add(bitmap)) bitmap.asAndroidBitmap().prepareToDraw()
    }
    private val maskPaint by lazy { Paint().apply {
        // Keep bloom outside the source ink, so a partially sung glyph's halo
        // cannot paint its unsung interior white before the karaoke sweep arrives.
        maskFilter = BlurMaskFilter(if (monet) layout.layoutInput.style.fontSize.value * density.density * density.fontScale * .28f
            else 6f * density.density * glowRadiusScale, BlurMaskFilter.Blur.OUTER)
    } }
    private val widePaint by lazy { Paint().apply {
        maskFilter = BlurMaskFilter(layout.layoutInput.style.fontSize.value * density.density * density.fontScale * .65f,
            BlurMaskFilter.Blur.OUTER)
    } }
    val ink: ImageBitmap by lazy {
        ImageBitmap(layout.size.width.coerceAtLeast(1), layout.size.height.coerceAtLeast(1)).also { bitmap ->
            CanvasDrawScope().draw(density, direction, Canvas(bitmap), Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
                drawText(layout, color = Color.White, drawStyle = Fill)
            }
        }
    }
    /** Static ink outside timed glyph boxes: punctuation/credits need no per-frame difference mask. */
    fun untimedInk(boxes: List<Rect>): ImageBitmap = untimedParagraphs.getOrPut(boxes) {
        ink.asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, true).also { bitmap ->
            val clear = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) }
            val canvas = android.graphics.Canvas(bitmap)
            boxes.forEach { canvas.drawRect(it.left, it.top, it.right, it.bottom, clear) }
        }.asImageBitmap()
    }
    /** Static line-level bloom for untimed captions; it carries no invented word cues. */
    val lineHalo: Pair<ImageBitmap, Offset> by lazy {
        val offsets = IntArray(2)
        val alpha = ink.asAndroidBitmap().extractAlpha(maskPaint, offsets)
        alpha.asImageBitmap() to Offset(offsets[0].toFloat(), offsets[1].toFloat())
    }
    fun glyphHalo(box: Rect): Pair<ImageBitmap, Offset> = glyphHalos.getOrPut(box) { makeHalo(box, maskPaint) }
    fun wideGlyphHalo(box: Rect): Pair<ImageBitmap, Offset> = wideHalos.getOrPut(box) { makeHalo(box, widePaint) }
    private fun makeHalo(box: Rect, paint: Paint): Pair<ImageBitmap, Offset> {
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
        val alpha = glyph.extractAlpha(paint, offsets)
        glyph.recycle()
        return alpha.asImageBitmap() to Offset((left + offsets[0]).toFloat(), (top + offsets[1]).toFloat())
    }
}
