package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.*

internal const val PARTICLE_EXIT_MS = 1000L
private const val COLOR_BUCKETS = 8
private const val EDGE_BUCKETS = 16

/** Sample the actual text ink once; bounded buffers are reused for every media-clock frame. */
internal class LyricsParticleInk(private val glyphs: List<SmokeGlyph>, private val ink: ImageBitmap) {
    private data class Shape(val origin: IntOffset, val size: IntSize, val points: FloatArray)
    private val step = maxOf(1.4f, (glyphs.firstOrNull()?.box?.height ?: 60f) / 65f,
        sqrt(glyphs.sumOf { (it.box.width * it.box.height).toDouble() }.toFloat() / 24000f))
    private val shapes = glyphs.mapIndexed { index, glyph ->
        val box = glyph.box
        val left = floor(box.left).toInt().coerceIn(0, ink.width - 1)
        val top = floor(box.top).toInt().coerceIn(0, ink.height - 1)
        val width = (ceil(box.right).toInt().coerceAtMost(ink.width) - left).coerceAtLeast(1)
        val height = (ceil(box.bottom).toInt().coerceAtMost(ink.height) - top).coerceAtLeast(1)
        val pixels = IntArray(width * height)
        ink.asAndroidBitmap().getPixels(pixels, 0, width, left, top, width, height)
        val points = ArrayList<Float>()
        var y = step * .5f
        while (y < height) {
            var x = step * .5f
            while (x < width) {
                if ((pixels[y.toInt() * width + x.toInt()] ushr 24) > 72) {
                    // Deterministic jitter: seeks, pause and palette changes never reseed the cloud.
                    val hash = (x.toInt() * 73856093 xor y.toInt() * 19349663 xor index * 83492791)
                    val seed = (hash and 65535) / 65535f
                    points.add(left + x); points.add(top + y); points.add(seed)
                }
                x += step
            }
            y += step
        }
        Shape(IntOffset(left, top), IntSize(width, height), points.toFloatArray())
    }
    // Bound each color buffer by its actual seed population, then reuse it for edge opacity.
    private val capacities = IntArray(COLOR_BUCKETS).apply {
        shapes.forEach { shape ->
            val population = IntArray(COLOR_BUCKETS)
            var at = 2
            while (at < shape.points.size) {
                population[(shape.points[at] * 7.99f).toInt()]++
                at += 3
            }
            indices.forEach { this[it] = maxOf(this[it], population[it]) }
        }
    }
    private val buffers = Array(COLOR_BUCKETS * EDGE_BUCKETS) { FloatArray(capacities[it % COLOR_BUCKETS] * 2) }
    private val counts = IntArray(buffers.size)
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    fun DrawScope.draw(now: Long, colors: LyricsSmokeColors, color: Color, exitAt: Long? = null) {
        val tint = if (color == Color.White) null else ColorFilter.tint(color)
        // Fade before the stage's horizontal clip. Keep settled glyph ink untouched.
        val edgeWidth = minOf(32.dp.toPx(), ink.width * .2f).coerceAtLeast(1f)
        glyphs.forEachIndexed { index, glyph ->
            val outgoing = exitAt != null
            val start = exitAt?.plus(index * 400L / glyphs.size.coerceAtLeast(1)) ?: glyph.start
            val duration = if (outgoing) 600L else glyph.duration
            val p = ((now - start).toFloat() / duration).coerceIn(0f, 1f)
            val shape = shapes[index]
            if ((!outgoing && p >= 1f) || (outgoing && p <= 0f)) {
                drawImage(ink, srcOffset = shape.origin, srcSize = shape.size,
                    dstOffset = shape.origin, dstSize = shape.size, colorFilter = tint)
                return@forEachIndexed
            }
            if ((!outgoing && p <= 0f) || (outgoing && p >= 1f)) return@forEachIndexed
            val scatter = if (outgoing) p else 1f - p
            val solid = smoothParticle(((1f - scatter - .66f) / .34f).coerceIn(0f, 1f))
            if (solid > 0f) drawImage(ink, srcOffset = shape.origin, srcSize = shape.size,
                dstOffset = shape.origin, dstSize = shape.size, colorFilter = tint, alpha = solid)
            counts.fill(0)
            val height = glyph.box.height
            var at = 0
            while (at < shape.points.size) {
                val seed = shape.points[at + 2]
                val drift = scatter * scatter
                val curl = seed * 6.283185f + scatter * 5f + index * .37f
                val x = shape.points[at] - height * drift * (.45f + seed * 1.55f) +
                    sin(curl) * height * scatter * .30f
                val y = shape.points[at + 1] - height * drift * (.35f + seed * 1.15f) +
                    cos(curl * 1.3f) * height * scatter * .28f
                val edgeAlpha = smoothParticle((minOf(x, ink.width - x) / edgeWidth).coerceIn(0f, 1f))
                val opacity = (edgeAlpha * (EDGE_BUCKETS - 1)).roundToInt()
                if (opacity == 0) { at += 3; continue }
                val bucket = opacity * COLOR_BUCKETS + (seed * 7.99f).toInt()
                val n = counts[bucket]
                buffers[bucket][n] = x; buffers[bucket][n + 1] = y
                counts[bucket] = n + 2
                at += 3
            }
            val cloudAlpha = (1f - solid) * smoothParticle(((1f - scatter) / .22f).coerceIn(0f, 1f))
            for (bucket in buffers.indices) {
                if (counts[bucket] == 0) continue
                val colorBucket = bucket % COLOR_BUCKETS
                val edgeAlpha = (bucket / COLOR_BUCKETS).toFloat() / (EDGE_BUCKETS - 1)
                val shade = lerp(colors.primary, colors.secondary, colorBucket / 7f)
                paint.color = lerp(shade, color, (1f - scatter).pow(3)).toArgb()
                paint.alpha = (255 * color.alpha * cloudAlpha * edgeAlpha * (.48f + colorBucket * .065f)).toInt().coerceIn(0, 255)
                paint.strokeWidth = step * (.50f + colorBucket * .045f)
                drawContext.canvas.nativeCanvas.drawPoints(buffers[bucket], 0, counts[bucket], paint)
            }
        }
    }
}

private fun smoothParticle(p: Float) = p * p * (3f - 2f * p)
