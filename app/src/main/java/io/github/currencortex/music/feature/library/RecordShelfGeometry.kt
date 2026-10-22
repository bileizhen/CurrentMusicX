package io.github.currencortex.music.feature.library

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/** A bounded strip of a potentially very large collection. Each song has one sleeve. */
internal fun shelfWindow(position: Float, count: Int): IntRange {
    if (count <= 0) return IntRange.EMPTY
    val center = position.roundToInt()
    val first = center - minOf(10, (count - 1) / 2)
    return first until first + minOf(count, 29)
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
