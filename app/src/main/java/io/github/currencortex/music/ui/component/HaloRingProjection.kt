package io.github.currencortex.music.ui.component

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** The two edge-on passages use a finite shutter, rather than a sharp, stroked ellipse. */
internal object HaloRingProjection {
    fun strength(timeMs: Float): Float = maxOf(
        exp(-((timeMs - 1000f) / 80f).pow(4)),
        exp(-((timeMs - 1800f) / 95f).pow(4)))

    fun heightScale(timeMs: Float): Float = (1f - .995f * maxOf(
        exp(-((timeMs - 1000f) / 24f).pow(2)),
        exp(-((timeMs - 1800f) / 24f).pow(2)))) *
        if (timeMs < 1000f || timeMs >= 1800f) 1f else -1f

    fun depth(timeMs: Float, aspect: Float): Float = .22f * strength(timeMs) *
        sqrt((1f - aspect * aspect).coerceIn(0f, 1f)) * if (timeMs < 1400f) 1f else -1f

    /** Normalised perspective keeps the sampled ellipse's centre and bounding box stable. */
    fun x(radius: Float, cosine: Float, sine: Float, radialScale: Float, depth: Float): Float =
        radius * sqrt(1f - depth * depth) * radialScale * cosine / (1f + depth * radialScale * sine)

    fun y(height: Float, sine: Float, radialScale: Float, depth: Float): Float =
        height * (radialScale * sine + depth) / (1f + depth * radialScale * sine)
}
