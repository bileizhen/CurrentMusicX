// SPDX-License-Identifier: AGPL-3.0-only
// Native complete-word masks; motion follows AMLL, see THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.feature.lyrics.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.currencortex.music.feature.lyrics.model.LyricWord
import kotlin.math.ceil
import kotlin.math.floor

private data class AppleInkRun(val word: Int, val range: IntRange, val advance: Rect,
    val before: Float, val rtl: Boolean)
private data class AppleMaskDraft(val run: AppleInkRun, val bitmap: Bitmap, val bounds: Rect, val halo: Boolean)
private data class AppleInkPatch(val run: AppleInkRun, val bounds: Rect, val uv: Rect, val halo: Boolean)

/** Complete shaped words share one cached atlas. No selection rectangle ever cuts or erases ink. */
internal class AppleMusicWordInk private constructor(private val atlas: ImageBitmap,
    private val patches: List<AppleInkPatch>, private val widths: FloatArray,
    private val words: List<LyricWord>, private val sustained: List<Boolean>, private val fontPx: Float) {
    private val mesh = AppleWordMesh(atlas, maxOf(1536, patches.size * 48 * 12))
    private val before = FloatArray(widths.size).also { offsets ->
        for (i in 1 until widths.size) offsets[i] = offsets[i - 1] + widths[i - 1]
    }
    private val totalWidth = widths.sum()
    private val overlapping = appleWordTimingsOverlap(words)

    /** Only worker results that have never been published to a draw may be recycled. */
    internal fun discardUnpublished() { atlas.asAndroidBitmap().recycle() }

    fun draw(scope: DrawScope, now: Long, motion: Boolean, liftEnabled: Boolean, glow: Boolean,
        background: Boolean, canLift: Boolean, opacity: Float = 1f,
        animationSpeed: Float = 1f, glowIntensity: Float = 1f) {
        val feather = fontPx * .5f
        val sweep = appleLineSweep(now, words, widths, feather)
        mesh.begin()
        // Halo patches precede ink, so they cannot brighten an unsung letter's interior.
        for (patch in patches) {
            val run = patch.run
            val word = words.getOrNull(run.word)
            val last = run.word == words.lastIndex
            val emphasize = word != null && sustained[run.word] && motion && canLift
            if (patch.halo && (!glow || !emphasize || glowIntensity == 0f)) continue
            val floating = if (word != null && motion && canLift && liftEnabled)
                appleWordLiftEm(now, word.startTimeMs, word.endTimeMs, animationSpeed) * fontPx * if (background) 2f else 1f else 0f
            val strength = if (emphasize) appleEmphasisAmount(word.endTimeMs - word.startTimeMs, last) else 0f
            val bounds = patch.bounds
            val count = ceil(bounds.width / (fontPx * .3f)).toInt().coerceIn(1, 64)
            val cx = run.advance.center.x; val cy = run.advance.center.y
            fun logical(x: Float) = run.before + if (run.rtl) run.advance.right - x else x - run.advance.left
            fun wave(x: Float): Float = if (emphasize) appleSustainWave(now, word.startTimeMs,
                word.endTimeMs, logical(x) / widths[run.word], last, animationSpeed) else 0f
            fun xAt(x: Float, wave: Float) = x + (x - cx) * .1f * strength * wave
            fun yAt(y: Float, wave: Float) = cy + (y - cy) * (1f + .1f * strength * wave) - floating -
                .025f * fontPx * strength * wave * if (background) 2f else 1f
            fun alpha(x: Float, wave: Float): Float = if (patch.halo) appleGlowAlpha(wave, strength, glowIntensity)
                else if (word == null) 1f else if (overlapping) appleIndependentWordBrightness(now,
                    word.startTimeMs, word.endTimeMs, logical(x), widths[run.word], feather)
                else appleLineBrightness(sweep, (before[run.word] + logical(x)).coerceIn(0f, totalWidth), feather)
            var left = bounds.left
            var leftWave = wave(left)
            for (slice in 1..count) {
                val right = bounds.left + bounds.width * slice / count
                val rightWave = wave(right)
                val u0 = patch.uv.left + left - bounds.left
                val u1 = patch.uv.left + right - bounds.left
                mesh.quad(xAt(left, leftWave), yAt(bounds.top, leftWave), yAt(bounds.bottom, leftWave),
                    xAt(right, rightWave), yAt(bounds.top, rightWave), yAt(bounds.bottom, rightWave),
                    Rect(u0, patch.uv.top, u1, patch.uv.bottom), alpha(left, leftWave) * opacity, alpha(right, rightWave) * opacity)
                left = right; leftWave = rightWave
            }
        }
        mesh.draw(scope)
    }

    companion object {
        fun create(layout: TextLayoutResult, words: List<LyricWord>, sustained: List<Boolean>,
            measurer: TextMeasurer, density: Density, direction: LayoutDirection,
            checkActive: () -> Unit = {}): AppleMusicWordInk? {
            checkActive()
            if (words.isEmpty()) return null
            val fontPx = with(density) { layout.layoutInput.style.fontSize.toPx() }
            val runs = mutableListOf<AppleInkRun>()
            val widths = FloatArray(words.size)
            fun addRuns(word: Int, range: IntRange) {
                var start = range.first
                while (start <= range.last) {
                    val row = layout.getLineForOffset(start)
                    val rtl = layout.getBidiRunDirection(start) == ResolvedTextDirection.Rtl
                    var end = start + 1
                    while (end <= range.last && layout.getLineForOffset(end) == row &&
                        (layout.getBidiRunDirection(end) == ResolvedTextDirection.Rtl) == rtl) end++
                    val boxes = (start until end).map { layout.getBoundingBox(it) }.filter { it.width > 0f }
                    if (boxes.isNotEmpty()) {
                        val rect = boxes.reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top),
                            maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
                        val before = if (word >= 0) widths[word] else 0f
                        if (word >= 0) widths[word] += rect.width
                        if (layout.layoutInput.text.text.substring(start, end).any { !it.isWhitespace() })
                            runs += AppleInkRun(word, start until end, rect, before, rtl)
                    }
                    start = end
                }
            }
            words.forEachIndexed { index, word -> addRuns(index, word.startOffset until word.endOffset) }
            val covered = BooleanArray(layout.layoutInput.text.length)
            words.forEach { word -> for (i in word.startOffset until word.endOffset) covered[i] = true }
            var start = 0
            while (start < covered.size) {
                if (covered[start]) { start++; continue }
                var end = start + 1
                while (end < covered.size && !covered[end]) end++
                addRuns(-1, start until end)
                start = end
            }
            val drafts = mutableListOf<AppleMaskDraft>()
            try {
                var draftPixels = 0L
                val overhang = ceil(fontPx).toInt().coerceAtLeast(4)
                for (run in runs) {
                    checkActive()
                    // Color-only spans preserve the full paragraph's shaping/kerning and contextual
                    // forms. Unlike getBoundingBox clipping, every descender/italic overhang is ink.
                    val maskText = AnnotatedString.Builder(layout.layoutInput.text.text).apply {
                        addStyle(SpanStyle(color = Color.White), run.range.first, run.range.last + 1)
                    }.toAnnotatedString()
                    val shaped = measurer.measure(maskText, layout.layoutInput.style.copy(color = Color.Transparent),
                        constraints = layout.layoutInput.constraints, layoutDirection = direction, skipCache = true)
                    val left = floor(run.advance.left).toInt() - overhang
                    val top = floor(run.advance.top).toInt() - overhang
                    val width = ceil(run.advance.right).toInt() - left + overhang
                    val height = ceil(run.advance.bottom).toInt() - top + overhang
                    if (width.toLong() * height > 4_000_000 || width >= 8192 || height >= 8192) {
                        return null
                    }
                    val temporary = ImageBitmap(width, height)
                    CanvasDrawScope().draw(density, direction, Canvas(temporary), Size(width.toFloat(), height.toFloat())) {
                        drawText(shaped, topLeft = Offset(-left.toFloat(), -top.toFloat()))
                    }
                    val source = temporary.asAndroidBitmap()
                    val pixels = IntArray(width * height)
                    source.getPixels(pixels, 0, width, 0, 0, width, height)
                    var minX = width; var minY = height; var maxX = -1; var maxY = -1
                    pixels.forEachIndexed { i, pixel -> if (pixel ushr 24 != 0) {
                        val x = i % width; val y = i / width
                        minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
                    } }
                    if (maxX >= minX) {
                        val ink = Bitmap.createBitmap(source, minX, minY, maxX - minX + 1, maxY - minY + 1)
                        val bounds = Rect((left + minX).toFloat(), (top + minY).toFloat(),
                            (left + maxX + 1).toFloat(), (top + maxY + 1).toFloat())
                        drafts += AppleMaskDraft(run, ink, bounds, false)
                        draftPixels += ink.width.toLong() * ink.height
                        if (run.word >= 0 && sustained[run.word]) {
                            val offsets = IntArray(2)
                            val halo = ink.extractAlpha(Paint().apply {
                                maskFilter = BlurMaskFilter(fontPx * .28f, BlurMaskFilter.Blur.OUTER)
                            }, offsets)
                            drafts += AppleMaskDraft(run, halo, Rect(bounds.left + offsets[0], bounds.top + offsets[1],
                                bounds.left + offsets[0] + halo.width, bounds.top + offsets[1] + halo.height), true)
                            draftPixels += halo.width.toLong() * halo.height
                        }
                        if (ink !== source) source.recycle()
                    } else source.recycle()
                    if (draftPixels > 4_000_000) {
                        return null
                    }
                }
                if (drafts.isEmpty()) return null
                val atlasWidth = maxOf(layout.size.width + overhang * 2, drafts.maxOf { it.bitmap.width + 2 })
                var x = 1; var y = 1; var rowHeight = 0
                val locations = drafts.map { draft ->
                    if (x + draft.bitmap.width + 1 > atlasWidth) { x = 1; y += rowHeight + 2; rowHeight = 0 }
                    val location = x to y
                    x += draft.bitmap.width + 2; rowHeight = maxOf(rowHeight, draft.bitmap.height)
                    location
                }
                val atlasHeight = y + rowHeight + 1
                if (atlasWidth.toLong() * atlasHeight > 4_000_000 || atlasWidth >= 8192 || atlasHeight >= 8192) {
                    return null
                }
                checkActive()
                val bitmap = Bitmap.createBitmap(atlasWidth, atlasHeight, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = android.graphics.Canvas(bitmap)
                    val paint = Paint().apply { color = android.graphics.Color.WHITE }
                    val patches = drafts.mapIndexed { i, draft ->
                        checkActive()
                        val (u, v) = locations[i]
                        canvas.drawBitmap(draft.bitmap, u.toFloat(), v.toFloat(), paint)
                        val uv = Rect(u.toFloat(), v.toFloat(), (u + draft.bitmap.width).toFloat(), (v + draft.bitmap.height).toFloat())
                        draft.bitmap.recycle()
                        AppleInkPatch(draft.run, draft.bounds, uv, draft.halo)
                    }.sortedByDescending { it.halo }
                    bitmap.prepareToDraw()
                    return AppleMusicWordInk(bitmap.asImageBitmap(), patches, widths, words, sustained, fontPx)
                } catch (failure: Throwable) {
                    bitmap.recycle()
                    throw failure
                }
            } finally {
                drafts.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
            }
        }
    }
}

/** Shared vertices make the whole-word deformation continuous, including italic ink outside advances. */
private class AppleWordMesh(ink: ImageBitmap, capacity: Int) {
    private var vertices = FloatArray(capacity)
    private var texture = FloatArray(capacity)
    private var colors = IntArray(capacity / 2)
    private var count = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(ink.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    fun begin() { count = 0 }
    fun quad(left: Float, leftTop: Float, leftBottom: Float, right: Float, rightTop: Float, rightBottom: Float,
        uv: Rect, leftAlpha: Float, rightAlpha: Float) {
        if (leftAlpha <= 0 && rightAlpha <= 0) return
        if (count + 12 > vertices.size) {
            vertices = vertices.copyOf(vertices.size * 2); texture = texture.copyOf(vertices.size); colors = colors.copyOf(vertices.size / 2)
        }
        fun vertex(x: Float, y: Float, u: Float, v: Float, alpha: Float) {
            vertices[count] = x; texture[count++] = u
            vertices[count] = y; texture[count++] = v
            colors[count / 2 - 1] = ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or 0x00FFFFFF
        }
        vertex(left, leftTop, uv.left, uv.top, leftAlpha); vertex(right, rightTop, uv.right, uv.top, rightAlpha)
        vertex(left, leftBottom, uv.left, uv.bottom, leftAlpha); vertex(left, leftBottom, uv.left, uv.bottom, leftAlpha)
        vertex(right, rightTop, uv.right, uv.top, rightAlpha); vertex(right, rightBottom, uv.right, uv.bottom, rightAlpha)
    }
    fun draw(scope: DrawScope) { if (count > 0) scope.drawContext.canvas.nativeCanvas.drawVertices(
        android.graphics.Canvas.VertexMode.TRIANGLES, count, vertices, 0, texture, 0, colors, 0, null, 0, 0, paint) }
}
