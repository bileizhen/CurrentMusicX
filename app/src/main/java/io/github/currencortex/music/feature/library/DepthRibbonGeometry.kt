package io.github.currencortex.music.feature.library

import kotlin.math.*

internal data class RibbonVector(val x: Float, val y: Float, val z: Float) {
    operator fun plus(v: RibbonVector) = RibbonVector(x + v.x, y + v.y, z + v.z)
    operator fun minus(v: RibbonVector) = RibbonVector(x - v.x, y - v.y, z - v.z)
    operator fun times(s: Float) = RibbonVector(x * s, y * s, z * s)
    fun dot(v: RibbonVector) = x * v.x + y * v.y + z * v.z
    fun cross(v: RibbonVector) = RibbonVector(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x)
    fun length() = sqrt(dot(this))
    fun unit() = this * (1f / length().coerceAtLeast(.0001f))
    fun mix(v: RibbonVector, amount: Float) = this + (v - this) * amount
}

/** Arc-length lookup keeps dragging speed constant through both lobes of the loop. */
internal class DepthRibbonPath(val count: Int) {
    private val samples = Array(2049) { i ->
        val t = i.toFloat() / 2048 * (2 * PI).toFloat()
        RibbonVector(sin(t), cos(t) * .28f, sin(2 * t) * .62f)
    }
    private val lengths = FloatArray(samples.size)
    val radius: Float
    private val points: Array<RibbonVector>
    init {
        require(count in 1..72)
        for (i in 1..samples.lastIndex) lengths[i] = lengths[i - 1] + (samples[i] - samples[i - 1]).length()
        radius = max(3f, count / lengths.last())
        points = Array(2048) { i ->
            val distance = lengths.last() * i / 2048
            val upper = lengths.binarySearch(distance).let { if (it >= 0) it else -it - 1 }.coerceIn(1, lengths.lastIndex)
            val mix = (distance - lengths[upper - 1]) / (lengths[upper] - lengths[upper - 1])
            samples[upper - 1].mix(samples[upper], mix) * radius
        }
    }
    fun point(position: Float): RibbonVector {
        val p = ((position % count + count) % count) / count * points.size
        val i = floor(p).toInt().coerceAtMost(points.lastIndex)
        return points[i].mix(points[(i + 1) % points.size], p - i)
    }
    fun tangent(position: Float) = (point(position + .01f) - point(position - .01f)).unit()
}

internal fun ribbonSongIndex(logical: Int, songCount: Int): Int = Math.floorMod(logical, songCount)

/** A critically damped camera transition preserves momentum when a hold is interrupted. */
internal class RibbonRevealTransition {
    var value = 0f
        private set
    private var velocity = 0f
    private var target = 0f
    val moving get() = abs(value - target) > .001f || abs(velocity) > .005f
    fun reveal(show: Boolean) { target = if (show) 1f else 0f }
    fun snap() { value = target; velocity = 0f }
    fun advance(seconds: Float) {
        if (!moving) { snap(); return }
        val dt = seconds.coerceIn(0f, .05f)
        val offset = value - target
        val momentum = velocity + 5f * offset
        val decay = exp(-5f * dt)
        value = target + (offset + momentum * dt) * decay
        velocity = (velocity - 5f * momentum * dt) * decay
    }
}
