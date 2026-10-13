package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.ui.component.rememberArtworkGlowPalette
import kotlin.math.*

@Immutable data class LyricsSmokeColors(val primary: Color, val secondary: Color)
@Immutable data class LyricsSmokeFlight(val sourceTop: Float, val sourceScale: Float = .62f)

@Composable internal fun rememberLyricsSmokeColors(cover: String): LyricsSmokeColors? {
    val motion = (rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    if (!motion) return null
    val palette by rememberArtworkGlowPalette(cover)
    val primary by animateColorAsState(Color(palette.primary), tween(700), label = "smoke cover primary")
    val secondary by animateColorAsState(Color(palette.secondary), tween(700), label = "smoke cover secondary")
    return LyricsSmokeColors(primary, secondary)
}

internal data class SmokeGlyph(val box: Rect, val start: Long, val duration: Long)

private fun smokeStarts(line: LyricLine, clusters: List<IntRange>): List<Long> {
    val entrance = minOf(1400L, (line.endTimeMs - line.startTimeMs).coerceAtLeast(1) / 2)
    return clusters.mapIndexed { index, range ->
        val word = line.words.firstOrNull { range.first >= it.startOffset && range.first < it.endOffset }
        val wordClusters = word?.let { cue -> clusters.filter { it.first in cue.startOffset until cue.endOffset } }
        if (word != null && !wordClusters.isNullOrEmpty())
            word.startTimeMs + (word.endTimeMs - word.startTimeMs) * wordClusters.indexOf(range) / wordClusters.size
        else line.startTimeMs + index * entrance / clusters.size.coerceAtLeast(1)
    }
}

/** Wait until the final outgoing character has left the bottom staging area. */
internal fun smokePreviewRevealAt(line: LyricLine): Long {
    val clusters = lyricsClusters(line.text).filter { range -> line.text.substring(range).any { !it.isWhitespace() } }
    val last = smokeStarts(line, clusters).maxOrNull() ?: line.startTimeMs
    return last + minOf(900L, (line.endTimeMs - last).coerceAtLeast(1)) / 3
}

/** Shape once using grapheme clusters, keeping surrogate pairs, emoji and wrapped lines intact. */
internal fun smokeGlyphs(line: LyricLine, layout: TextLayoutResult): List<SmokeGlyph> {
    val clusters = lyricsClusters(line.text).filter { range -> line.text.substring(range).any { !it.isWhitespace() } }
    if (clusters.size > 160 || !lyricsCanLift(line.text)) return emptyList()
    val starts = smokeStarts(line, clusters)
    return clusters.mapIndexedNotNull { index, range ->
        val boxes = range.map { layout.getBoundingBox(it) }.filter { it.width > 0 && it.height > 0 }
        if (boxes.isEmpty()) return@mapIndexedNotNull null
        val box = boxes.reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
        val start = starts[index]
        val duration = minOf(900L, (line.endTimeMs - start).coerceAtLeast(1))
        SmokeGlyph(box, start, duration)
    }
}

/** Blur the actual glyph silhouette once. Palette animation never rebuilds these masks. */
internal class LyricsSmokeInk(private val glyphs: List<SmokeGlyph>, private val ink: ImageBitmap) {
    private data class Mask(val bitmap: android.graphics.Bitmap, val origin: Offset)
    private data class GlyphInk(val origin: IntOffset, val size: IntSize, val masks: List<Mask>)
    private val shapes = glyphs.map { glyph ->
        val box = glyph.box
        val left = floor(box.left).toInt().coerceIn(0, ink.width - 1)
        val top = floor(box.top).toInt().coerceIn(0, ink.height - 1)
        val width = (ceil(box.right).toInt().coerceAtMost(ink.width) - left).coerceAtLeast(1)
        val height = (ceil(box.bottom).toInt().coerceAtMost(ink.height) - top).coerceAtLeast(1)
        val glyphBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(glyphBitmap).drawBitmap(ink.asAndroidBitmap(), -left.toFloat(), -top.toFloat(), null)
        val masks = listOf(0f, .025f, .07f, .15f).map { radius ->
            val offsets = IntArray(2)
            val paint = android.graphics.Paint().apply {
                if (radius > 0f) maskFilter = android.graphics.BlurMaskFilter(height * radius, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            val alpha = glyphBitmap.extractAlpha(paint, offsets)
            Mask(alpha, Offset((left + offsets[0]).toFloat(), (top + offsets[1]).toFloat()))
        }
        glyphBitmap.recycle()
        GlyphInk(IntOffset(left, top), IntSize(width, height), masks)
    }
    private val vertices = FloatArray(7 * 7 * 2)
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)

    fun DrawScope.draw(now: Long, colors: LyricsSmokeColors, color: Color, flight: LyricsSmokeFlight? = null) {
        val textFilter = if (color == Color.White) null else ColorFilter.tint(color)
        glyphs.forEachIndexed { index, glyph ->
            val p = ((now - glyph.start).toFloat() / glyph.duration).coerceIn(0f, 1f)
            if (p <= 0f && flight == null) return@forEachIndexed
            val shape = shapes[index]
            val travel = 1f - (1f - p).pow(3)
            val glyphScale = flight?.let { it.sourceScale + (1f - it.sourceScale) * travel } ?: 1f
            withTransform({
                if (flight != null) {
                    translate(left = ink.width * .5f * (1f - glyphScale), top = flight.sourceTop * (1f - travel))
                    scale(glyphScale, glyphScale, pivot = Offset.Zero)
                }
            }) {
            // Waiting characters remain in the bottom preview; each leaves on its own cue.
            if (flight != null && p < .15f) {
                drawImage(ink, srcOffset = shape.origin, srcSize = shape.size,
                    dstOffset = shape.origin, dstSize = shape.size,
                    alpha = .48f * (1f - p / .15f), colorFilter = textFilter)
            }
            if (p >= 1f) {
                drawImage(ink, srcOffset = shape.origin, srcSize = shape.size,
                    dstOffset = shape.origin, dstSize = shape.size, colorFilter = textFilter)
            } else {
            val drift = (1f - p).pow(2)
            val opacity = smoothSmoke((p / .22f).coerceIn(0f, 1f))
            val smokeColor = lerp(colors.primary, colors.secondary, .25f)
            val tone = lerp(smokeColor, color, smoothSmoke(((p - .25f) / .6f).coerceIn(0f, 1f)))
            // A trailing, soft copy of the *same strokes*, not a separate cloud sprite.
            drawMask(shape.masks[2], glyph, index, p, drift, opacity * drift * .32f, smokeColor, .16f)
            val blur = 3f * (1f - p).pow(1.5f)
            val low = floor(blur).toInt().coerceIn(0, 2)
            val blend = blur - low
            drawMask(shape.masks[low + 1], glyph, index, p, drift, opacity * blend, tone, 0f)
            drawMask(shape.masks[low], glyph, index, p, drift, opacity * (1f - blend), tone, 0f)
            }
            }
        }
    }

    private fun DrawScope.drawMask(mask: Mask, glyph: SmokeGlyph, index: Int, p: Float,
        drift: Float, alpha: Float, color: Color, trail: Float) {
        if (alpha < .002f) return
        val height = glyph.box.height
        // A small continuous mesh bends the blurred strokes, then collapses exactly
        // onto the original glyph. Reused vertices avoid per-frame texture allocation.
        for (y in 0..6) for (x in 0..6) {
            val at = (y * 7 + x) * 2
            val localY = y / 6f
            vertices[at] = mask.origin.x + mask.bitmap.width * x / 6f + height * drift *
                (.22f + trail + sin(localY * 5f + index * 1.7f + p * 4f) * .055f)
            vertices[at + 1] = mask.origin.y + mask.bitmap.height * localY + height * drift *
                (.55f + trail + sin(x / 6f * 4f + index + p * 3f) * .045f)
        }
        paint.colorFilter = android.graphics.PorterDuffColorFilter(color.toArgb(), android.graphics.PorterDuff.Mode.SRC_IN)
        paint.alpha = (alpha * color.alpha * 255).roundToInt().coerceIn(0, 255)
        drawContext.canvas.nativeCanvas.drawBitmapMesh(mask.bitmap, 6, 6, vertices, 0, null, 0, paint)
    }
}

private fun smoothSmoke(value: Float) = value * value * (3f - 2f * value)
