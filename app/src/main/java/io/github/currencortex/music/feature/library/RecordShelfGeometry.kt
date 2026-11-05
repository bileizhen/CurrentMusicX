package io.github.currencortex.music.feature.library

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/** A bounded strip of a potentially very large collection. Each song has one sleeve. */
internal fun shelfWindow(position: Float, count: Int, nearSlots: Int = 10): IntRange {
    if (count <= 0) return IntRange.EMPTY
    val center = position.roundToInt()
    val first = center - minOf(nearSlots, (count - 1) / 2)
    return first until first + minOf(count, 29)
}

/** Keep the cases at their height-limited size while a wide viewport extends the rack. */
internal class ShelfGeometry(val viewportWidth: Float, val top: Float, val height: Float,
    val viewportHeight: Float = top + height) {
    val caseScale = minOf(viewportWidth, height * 1.12f)
    val wide = viewportWidth > caseScale
    private val originX = (viewportWidth - caseScale) * .5f
    private val spacing = if (wide) maxOf(caseScale * .069f, viewportWidth / 24f) else caseScale * .069f
    private val verticalRoom = (height - caseScale * .677f).coerceAtLeast(0f)
    private val rackX = originX + caseScale * .120f
    private val rackY = top + verticalRoom * .5f
    private val caseWidth = caseScale * .435f
    // The rack follows the screen's lower-left to upper-right diagonal. Only the
    // selected case uses the central stage; outer cases may continue into the corners.
    // At either screen edge, leave part of a case visible even when the rack's central
    // anchor sits high or low. Include its skew and up to one slot of alignment error.
    private val edgeReveal = caseScale * .1f
    private val edgeSkew = spacing * (.132f / .435f)
    private val rightSlope = (rackY + caseScale * .582f - edgeReveal - edgeSkew) /
        (viewportWidth - caseWidth - rackX + spacing).coerceAtLeast(1f)
    private val leftSlope = (viewportHeight - edgeReveal - rackY - edgeSkew) /
        (rackX + spacing).coerceAtLeast(1f)
    private val rise = if (wide) spacing * minOf(.5773503f, .92f * viewportHeight / viewportWidth,
        rightSlope, leftSlope).coerceAtLeast(0f) else caseScale * .046f

    fun window(position: Float, count: Int) = shelfWindow(position, count, if (wide) 14 else 10)

    /** Global coordinates are shared by rendering, hit testing, and search-result motion. */
    fun quad(distance: Float, extraction: Float, out: FloatArray) {
        if (!wide) {
            shelfQuad(distance, caseScale, top, height, extraction, out)
            return
        }
        val x = rackX + caseScale * extraction * .367f + distance * spacing
        val y = rackY - distance * rise + caseScale * extraction * .095f
        val w = caseWidth
        val h = caseScale * .45f
        val skew = caseScale * .132f
        out[0] = x; out[1] = y
        out[2] = x + w; out[3] = y + skew
        out[4] = x + w; out[5] = y + skew + h
        out[6] = x; out[7] = y + h
    }

    fun entryOffset(seconds: Float, distance: Float, restingX: Float) =
        shelfEntryOffsetX(seconds, distance, viewportWidth, restingX)

    fun exitOffset(seconds: Float, distance: Float, restingX: Float) =
        shelfResultsExitOffsetX(seconds, distance, viewportWidth, restingX, 0f)

    fun entryRise(offset: Float) = shelfEntryOffsetY(offset, viewportWidth)
}

/** Measured from the reference's 724 x 1574 screen: a case is .435 W wide, .45 W tall,
 * with a .132 W edge rise. Its front slides (.367 W, .095 W) out of its slot, without turning. */
internal fun shelfQuad(distance: Float, width: Float, top: Float, height: Float,
    extraction: Float, out: FloatArray) {
    val x = width * (.120f + distance * .069f + extraction * .367f)
    val y = top + height * .404f + width * (-distance * .046f + extraction * .095f)
    val scale = 1f
    val w = width * .435f * scale
    val h = width * .45f * scale
    val skew = width * .132f * scale
    out[0] = x; out[1] = y
    out[2] = x + w; out[3] = y + skew
    out[4] = x + w; out[5] = y + skew + h
    out[6] = x; out[7] = y + h
}

/** Exact exponential integration makes settling independent of the display's refresh rate. */
internal fun shelfApproach(value: Float, target: Float, seconds: Float, speed: Float = 12f): Float {
    val result = target + (value - target) * exp(-speed * seconds.coerceIn(0f, .05f))
    return if (abs(result - target) < .0005f) target else result
}

/** Tracked from the reference entrance: each case starts just beyond the right edge.
 * Rear cases settle first; the nearer cases travel farther and take longer to unfold. */
internal fun shelfEntryOffsetX(seconds: Float, distance: Float, width: Float, restingX: Float): Float {
    val front = (-distance).coerceIn(0f, 10f)
    val start = .412f + front * .0111f
    val duration = if (distance >= 0f) (.632f - distance * .030f).coerceAtLeast(.22f)
        else .632f + front * .0123f
    val progress = ((seconds - start) / duration).coerceIn(0f, 1f)
    return (width * 1.010f - restingX).coerceAtLeast(0f) * (1f - progress).pow(2.7f + front * .0158f)
}

internal fun shelfEntryOffsetY(offsetX: Float, width: Float): Float =
    1.1f * offsetX * offsetX / width - .48f * offsetX

/** Search results leave along the entrance path, after the selected case has slid back in. */
internal fun shelfResultsExitOffsetX(seconds: Float, distance: Float, width: Float,
    restingX: Float, initialOffset: Float): Float {
    val delay = .08f + (-distance).coerceIn(0f, 10f) * .004f
    val t = ((seconds - delay) / .26f).coerceIn(0f, 1f)
    val eased = t * t * (3f - 2f * t)
    return initialOffset + ((width * 1.010f - restingX).coerceAtLeast(0f) - initialOffset) * eased
}
