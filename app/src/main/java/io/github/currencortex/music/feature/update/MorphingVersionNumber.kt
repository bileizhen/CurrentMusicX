package io.github.currencortex.music.feature.update

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import kotlin.math.abs
import kotlin.math.min

/** Font outlines share an arc-length sampling grid, including counters (0/6/8/9).
 * Missing contours collapse to a point, so holes and additional digits grow in-place.
 * Only retargeting samples a font or allocates a snapshot; frames reuse one native path. */
@Composable
internal fun MorphingVersionNumber(version: String, initialVersion: String, fontSize: TextUnit,
    color: Color, modifier: Modifier = Modifier) {
    val glyphs = remember { VersionGlyphs() }
    var from by remember { mutableStateOf(glyphs.outline(initialVersion)) }
    var to by remember { mutableStateOf(from) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(version) {
        if (to.version != version) {
            // Cancellation leaves progress at the currently drawn pose, rather than an endpoint.
            val visible = interpolateVersionOutline(from, to, progress.value)
            progress.snapTo(0f)
            from = visible
            to = glyphs.outline(version)
            progress.animateTo(1f, tween(960, easing = FastOutSlowInEasing))
        }
    }
    val density = LocalDensity.current
    val textPixels = with(density) { fontSize.toPx() }
    val height = with(density) { (textPixels * glyphs.lineHeight / VersionGlyphs.EM).toDp() }
    val path = remember { Path().apply { fillType = Path.FillType.EVEN_ODD } }
    val outer = remember { Path() }
    val counter = remember { Path() }
    val components = remember { VersionPathComponents() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }
    Canvas(modifier.fillMaxWidth().height(height).semantics { text = AnnotatedString(version) }) {
        val amount = progress.value
        val width = mix(from.width, to.width, amount)
        val scale = min(textPixels / VersionGlyphs.EM, (size.width - 24).coerceAtLeast(1f) / width.coerceAtLeast(1f))
        versionOutlinePath(from, to, amount, path, outer, counter, components)
        paint.color = color.toArgb()
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(size.width / 2f, (size.height - glyphs.lineHeight * scale) / 2f - glyphs.top * scale)
            native.scale(scale, scale)
            native.drawPath(path, paint)
            native.restore()
        }
    }
}

internal data class VersionDigitOutline(val x: Float, val contours: List<FloatArray>)
internal data class VersionOutline(val version: String, val width: Float, val digits: List<VersionDigitOutline>)

internal class VersionGlyphs {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = EM
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    val top = paint.fontMetrics.top
    val lineHeight = paint.fontMetrics.bottom - top
    private val cached = mutableMapOf<Char, List<FloatArray>>()

    fun outline(version: String): VersionOutline {
        val width = paint.measureText(version)
        var x = -width / 2
        val digits = version.map { char ->
            val digit = VersionDigitOutline(x, cached.getOrPut(char) { sample(char) })
            x += paint.measureText(char.toString())
            digit
        }
        return VersionOutline(version, width, digits)
    }

    private fun sample(char: Char): List<FloatArray> {
        val path = Path()
        paint.getTextPath(char.toString(), 0, 1, 0f, 0f, path)
        val measure = PathMeasure(path, false)
        val point = FloatArray(2)
        val contours = mutableListOf<FloatArray>()
        do {
            if (measure.length <= .01f) continue
            val points = FloatArray(SAMPLES * 2)
            repeat(SAMPLES) { index ->
                measure.getPosTan(index * measure.length / SAMPLES, point, null)
                points[index * 2] = point[0]; points[index * 2 + 1] = point[1]
            }
            contours += canonicalContour(points)
        } while (measure.nextContour())
        // Outer contour first. Counter order follows vertical position so 8's holes don't cross.
        return contours.sortedByDescending { abs(area(it)) }.let { sorted ->
            sorted.take(1) + sorted.drop(1).sortedBy { center(it, 1) }
        }
    }

    companion object { const val EM = 100f; private const val SAMPLES = 128 }
}

private fun area(points: FloatArray): Float {
    var sum = 0f
    for (index in points.indices step 2) {
        val next = (index + 2) % points.size
        sum += points[index] * points[next + 1] - points[next] * points[index + 1]
    }
    return sum / 2
}

private fun canonicalContour(points: FloatArray): FloatArray {
    val count = points.size / 2
    val start = (0 until count).minBy { points[it * 2 + 1] }
    val direction = if (area(points) >= 0f) 1 else -1
    return FloatArray(points.size) { index ->
        val vertex = ((start + direction * (index / 2)) % count + count) % count
        points[vertex * 2 + index % 2]
    }
}

private fun center(points: FloatArray, axis: Int): Float {
    var sum = 0f
    for (index in axis until points.size step 2) sum += points[index]
    return sum / (points.size / 2)
}

private fun collapsePoint(points: FloatArray) = Offset(center(points, 0), center(points, 1))

// Close a disappearing counter before its outer boundary moves away. Open a new counter
// after the filled body has formed, so a moving hole cannot cut the stroke into fragments.
private fun contourProgress(index: Int, from: FloatArray?, to: FloatArray?, progress: Float) = when {
    index == 0 || from != null && to != null -> progress
    to == null -> (progress * 3f).coerceAtMost(1f)
    else -> ((progress - .6f) / .4f).coerceIn(0f, 1f)
}

/** Snapshot the visible geometry for interruption without snapping back to a digit. */
internal fun interpolateVersionOutline(from: VersionOutline, to: VersionOutline, amount: Float): VersionOutline {
    val digits = (0 until maxOf(from.digits.size, to.digits.size)).map { index ->
        val old = from.digits.getOrNull(index)
        val next = to.digits.getOrNull(index)
        val contours = (0 until maxOf(old?.contours?.size ?: 0, next?.contours?.size ?: 0)).map { contour ->
            val a = old?.contours?.getOrNull(contour)
            val b = next?.contours?.getOrNull(contour)
            val t = contourProgress(contour, a, b, amount)
            val oldPoint = if (a == null) collapsePoint(b!!) else Offset.Zero
            val newPoint = if (b == null) collapsePoint(a!!) else Offset.Zero
            FloatArray((a ?: b!!).size) { point -> mix(a?.get(point) ?: if (point % 2 == 0) oldPoint.x else oldPoint.y,
                b?.get(point) ?: if (point % 2 == 0) newPoint.x else newPoint.y, t) }
        }
        VersionDigitOutline(mix(old?.x ?: next!!.x, next?.x ?: old!!.x, amount), contours)
    }
    return VersionOutline("", mix(from.width, to.width, amount), digits)
}

/** No alpha/old-text overlay: interpolate the filled glyph contour itself. */
internal fun versionOutlinePath(from: VersionOutline, to: VersionOutline, amount: Float, path: Path,
    outer: Path = Path(), counter: Path = Path(), components: VersionPathComponents = VersionPathComponents()) {
    path.rewind()
    // Path.op returns even-odd contours; keep that fill rule when combining glyphs.
    path.fillType = Path.FillType.EVEN_ODD
    repeat(maxOf(from.digits.size, to.digits.size)) { digit ->
        val old = from.digits.getOrNull(digit)
        val next = to.digits.getOrNull(digit)
        val x = mix(old?.x ?: next!!.x, next?.x ?: old!!.x, amount)
        outer.rewind()
        repeat(maxOf(old?.contours?.size ?: 0, next?.contours?.size ?: 0)) { contour ->
            val a = old?.contours?.getOrNull(contour)
            val b = next?.contours?.getOrNull(contour)
            val t = contourProgress(contour, a, b, amount)
            val oldPoint = if (a == null) collapsePoint(b!!) else Offset.Zero
            val newPoint = if (b == null) collapsePoint(a!!) else Offset.Zero
            val target = if (contour == 0) outer else counter.also { it.rewind() }
            for (point in (a ?: b!!).indices step 2) {
                val px = x + mix(a?.get(point) ?: oldPoint.x, b?.get(point) ?: newPoint.x, t)
                val py = mix(a?.get(point + 1) ?: oldPoint.y, b?.get(point + 1) ?: newPoint.y, t)
                if (point == 0) target.moveTo(px, py) else target.lineTo(px, py)
            }
            target.close()
            // A counter outside the moving outer contour must never become a filled island.
            if (contour > 0) outer.op(counter, Path.Op.DIFFERENCE)
        }
        components.appendBody(path, outer)
    }
}

/** A concave outline can briefly split while its topology changes. Keep its connected body
 * and enclosed counters, rather than drawing detached fragments. Native paths/buffers are reused. */
internal class VersionPathComponents {
    private val resolved = Path()
    private val empty = Path()
    private val measure = PathMeasure()
    private val paths = mutableListOf<Path>()
    private val polygons = mutableListOf<FloatArray>()
    private val point = FloatArray(2)

    fun appendBody(destination: Path, glyph: Path) {
        resolved.set(glyph)
        resolved.fillType = Path.FillType.EVEN_ODD
        resolved.op(empty, Path.Op.UNION)
        measure.setPath(resolved, false)
        var count = 0
        var largest = 0
        var largestArea = 0f
        do {
            val length = measure.length
            if (length < .01f) continue
            if (count == paths.size) { paths.add(Path()); polygons.add(FloatArray(128)) }
            val segment = paths[count]
            segment.rewind()
            measure.getSegment(0f, length, segment, true)
            segment.close()
            val polygon = polygons[count]
            repeat(polygon.size / 2) { index ->
                measure.getPosTan(index * length / (polygon.size / 2), point, null)
                polygon[index * 2] = point[0]; polygon[index * 2 + 1] = point[1]
            }
            val size = abs(area(polygon))
            if (size > largestArea) { largestArea = size; largest = count }
            count++
        } while (measure.nextContour())
        if (count == 0) return
        destination.addPath(paths[largest])
        for (index in 0 until count) {
            if (index == largest) continue
            val polygon = polygons[index]
            if (contains(polygons[largest], polygon[0], polygon[1])) destination.addPath(paths[index])
        }
    }

    private fun contains(polygon: FloatArray, x: Float, y: Float): Boolean {
        var inside = false
        var previous = polygon.size - 2
        for (index in polygon.indices step 2) {
            val ax = polygon[index]; val ay = polygon[index + 1]
            val bx = polygon[previous]; val by = polygon[previous + 1]
            if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) inside = !inside
            previous = index
        }
        return inside
    }
}

private fun mix(from: Float, to: Float, amount: Float) = from + (to - from) * amount
