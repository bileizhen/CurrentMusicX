package io.github.currencortex.music.ui.component

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.icu.text.BreakIterator
import org.json.JSONObject
import kotlin.math.*

/** Bundled Tegaki 1.1.0 centre lines, not font outlines. See legal/NOTICES.md. */
internal object SignatureGlyphs {
    private var latin: JSONObject? = null
    private var chinese: JSONObject? = null

    @Synchronized fun load(assets: AssetManager, name: String): Pair<JSONObject, JSONObject?> {
        fun read(file: String) = assets.open("signature/$file.json").bufferedReader().use { JSONObject(it.readText()) }
        val first = latin ?: read("parisienne").also { latin = it }
        val second = if (name.any { it.code > 127 }) chinese ?: read("wenkai").also { chinese = it } else null
        return first to second
    }
}

internal fun signatureName(username: String): String = username
    .replace(Regex("[\\p{Cc}\\p{Zl}\\p{Zp}]+"), " ").trim().ifEmpty { "欢迎聆听" }

/** Prepared on a worker; draw() only samples the active stroke and paints cached paths. */
internal class SignatureLaunchRenderer(
    username: String,
    latin: JSONObject,
    chinese: JSONObject?,
    fallbackTypeface: Typeface = Typeface.DEFAULT,
) {
    private data class Point(val x: Float, val y: Float, val width: Float)
    private class Stroke(val points: List<Point>, val start: Float, val duration: Float) {
        val distances = FloatArray(points.size)
        val full = Path()
        init {
            for (i in 1 until points.size) distances[i] = distances[i - 1] +
                hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
            ink(full, 1f)
        }
        // A filled, pressure-sensitive ribbon with round joins. No text-outline tracing.
        fun ink(path: Path, fraction: Float): Point? {
            path.rewind()
            if (points.isEmpty() || fraction <= 0f) return null
            val target = distances.last() * fraction
            var head = points.first()
            val visible = ArrayList<Point>()
            visible.add(head)
            for (i in 1 until points.size) {
                if (distances[i - 1] > target) break
                val a = points[i - 1]
                val b = points[i]
                val f = ((target - distances[i - 1]) / (distances[i] - distances[i - 1]).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
                head = Point(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.width + (b.width - a.width) * f)
                visible.add(head)
                if (f < 1f) break
            }
            fun edge(i: Int, side: Float, move: Boolean = false) {
                val a = visible[max(0, i - 1)]
                val b = visible[min(visible.lastIndex, i + 1)]
                val p = visible[i]
                val length = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(.0001f)
                val x = p.x - (b.y - a.y) / length * p.width * .5f * side
                val y = p.y + (b.x - a.x) / length * p.width * .5f * side
                if (move) path.moveTo(x, y) else path.lineTo(x, y)
            }
            for (i in visible.indices) edge(i, 1f, i == 0)
            for (i in visible.lastIndex downTo 0) edge(i, -1f)
            path.close()
            path.addCircle(visible.first().x, visible.first().y, visible.first().width / 2f, Path.Direction.CCW)
            path.addCircle(head.x, head.y, head.width / 2f, Path.Direction.CCW)
            return head
        }
    }
    private data class Fallback(val text: String, val x: Float, val y: Float, val start: Float, val duration: Float)
    private val strokes = mutableListOf<Stroke>()
    private val fallback = mutableListOf<Fallback>()
    private val partial = Path()
    private val bounds = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fallbackTypeface; textSize = 78f }
    private var duration = 0f
    private var lastWidth = 0f
    private var lastHeight = 0f
    private var ambient: Shader? = null
    private val gradient: Shader
    internal val strokeCount get() = strokes.size
    internal val fallbackCount get() = fallback.size

    init {
        val name = signatureName(username)
        val iterator = BreakIterator.getCharacterInstance().apply { setText(name) }
        val clusters = buildList {
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                add(name.substring(start, end))
                start = end
                end = iterator.next()
            }
        }
        var x = 0f
        var y = 0f
        var maxX = 0f
        // Bound hostile/abnormally long account names without splitting surrogate pairs or emoji.
        val display = if (clusters.size > 64) clusters.take(63) + "…" else clusters
        for (cluster in display) {
            val primary = latin.optJSONObject(cluster)
            val glyph = primary ?: chinese?.optJSONObject(cluster)
            val scale = if (primary != null) 100f / 2048f else 100f / 1000f
            val width = glyph?.optDouble("w")?.toFloat()?.times(scale)
                ?: textPaint.measureText(cluster).coerceAtLeast(24f)
            if (x > 0f && x + width > 1050f) { maxX = max(maxX, x); x = 0f; y += 145f }
            if (glyph == null) {
                fallback += Fallback(cluster, x, y, duration, .35f)
                duration += .35f
            } else {
                val data = glyph.getJSONArray("s")
                for (index in 0 until data.length()) {
                    val s = data.getJSONObject(index)
                    val points = s.getJSONArray("p")
                    val raw = List(points.length()) { j ->
                        val p = points.getJSONArray(j)
                        Point(x + p.getDouble(0).toFloat() * scale, y + p.getDouble(1).toFloat() * scale,
                            p.getDouble(2).toFloat() * scale)
                    }
                    if (raw.isNotEmpty()) strokes += Stroke(smooth(raw), duration + s.getDouble("d").toFloat(),
                        s.getDouble("a").toFloat().coerceAtLeast(.01f))
                }
                duration += glyph.getDouble("t").toFloat() + .025f
            }
            x += width
        }
        maxX = max(maxX, x)
        bounds.set(0f, -100f, maxX.coerceAtLeast(1f), y + 30f)
        val temp = RectF()
        for (stroke in strokes) { stroke.full.computeBounds(temp, true); bounds.union(temp) }
        gradient = LinearGradient(bounds.left, 0f, bounds.right, 0f,
            intArrayOf(0xFFFA65DA.toInt(), 0xFFF49CFF.toInt(), 0xFFB5A3FF.toInt(), 0xFF7DD5FF.toInt(), 0xFFFFC17C.toInt(), 0xFFF38FBF.toInt()),
            null, Shader.TileMode.CLAMP)
    }

    /** Catmull–Rom interpolation through the supplied pen centres, with interpolated pressure. */
    private fun smooth(raw: List<Point>): List<Point> = buildList {
        add(raw.first())
        for (i in 0 until raw.lastIndex) {
            val a = raw[max(0, i - 1)]
            val b = raw[i]
            val c = raw[i + 1]
            val d = raw[min(raw.lastIndex, i + 2)]
            val steps = ceil(hypot(c.x - b.x, c.y - b.y) / 1.2f).toInt().coerceIn(1, 80)
            for (j in 1..steps) {
                val t = j.toFloat() / steps
                fun channel(p0: Float, p1: Float, p2: Float, p3: Float) = .5f *
                    (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t * t +
                        (-p0 + 3f * p1 - 3f * p2 + p3) * t * t * t)
                add(Point(channel(a.x, b.x, c.x, d.x), channel(a.y, b.y, c.y, d.y),
                    (b.width + (c.width - b.width) * t).coerceAtLeast(.3f)))
            }
        }
    }

    fun draw(canvas: Canvas, width: Float, height: Float, timeMs: Float) {
        if (width <= 0f || height <= 0f) return
        canvas.drawColor(Color.rgb(5, 7, 12))
        if (lastWidth != width || lastHeight != height) {
            lastWidth = width; lastHeight = height
            ambient = RadialGradient(width * .5f, height * .48f, min(width * .7f, height * .55f),
                intArrayOf(0xFF29172F.toInt(), 0xFF10101E.toInt(), 0xFF05070C.toInt()), null, Shader.TileMode.CLAMP)
        }
        paint.style = Paint.Style.FILL
        paint.shader = ambient
        paint.alpha = (ease(timeMs / 250f) * 255).toInt()
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
        paint.alpha = 255
        if (timeMs <= 250f) return
        val clock = ((timeMs - 250f) / 950f).coerceIn(0f, 1f) * duration
        val highlight = sin(((timeMs - 1200f) / 400f).coerceIn(0f, 1f) * PI).toFloat()
        val fit = min(width * .80f / (bounds.width() + 22f), height * .25f / (bounds.height() + 22f))
        canvas.save()
        canvas.translate(width / 2f - bounds.centerX() * fit, height * .49f - bounds.centerY() * fit)
        canvas.scale(fit, fit)
        for (stroke in strokes) {
            val progress = ((clock - stroke.start) / stroke.duration).coerceIn(0f, 1f)
            if (progress <= 0f) continue
            val head = if (progress < 1f) stroke.ink(partial, 1f - (1f - progress).pow(2)) else null
            val path = if (progress >= 1f) stroke.full else partial
            paint.style = Paint.Style.FILL
            paint.shader = gradient
            paint.alpha = 255
            // Android 9+ supports non-text shadows on the accelerated canvas; older
            // devices retain crisp ink. Software captures use the same soft halo.
            if (!canvas.isHardwareAccelerated || android.os.Build.VERSION.SDK_INT >= 28)
                paint.setShadowLayer(5f + highlight * 2f, 0f, 0f,
                    Color.argb((66 + highlight * 24).toInt(), 192, 113, 252))
            canvas.drawPath(path, paint)
            paint.clearShadowLayer()
            paint.shader = null
            if (highlight > 0f) {
                paint.color = Color.WHITE
                paint.alpha = (highlight * 52).toInt()
                canvas.drawPath(path, paint)
            }
            if (head != null) {
                paint.color = 0xFFFFE7FF.toInt(); paint.alpha = 180
                canvas.drawCircle(head.x, head.y, head.width * .3f, paint)
            }
        }
        // Unsupported clusters retain their original glyph (including emoji), never a fake outline trace.
        for (glyph in fallback) {
            textPaint.color = 0xFFF3C6F5.toInt()
            textPaint.alpha = (ease((clock - glyph.start) / glyph.duration) * 255).toInt()
            canvas.drawText(glyph.text, glyph.x, glyph.y, textPaint)
        }
        canvas.restore()
        paint.alpha = 255
    }

    private fun ease(value: Float) = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}
