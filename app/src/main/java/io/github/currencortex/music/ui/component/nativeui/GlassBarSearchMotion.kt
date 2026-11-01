// SPDX-License-Identifier: GPL-3.0-only
// CommonScaffold._SearchMove (group return), scaffold.dart blob
// 9ae9c661fe9597dd5400aee8a0c8971416635321, chen08209's upstream project.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.*
import kotlin.math.*

@Immutable internal data class BarSearchFrame(
    val growth: Float = 0f, val collapse: Float = 0f, val surface: Float = 0f,
    val title: Float = 1f, val actions: Float = 1f, val flank: Float = 0f,
    val content: Float = 0f, val glyph: Float = 1f, val swell: Float = 0f,
    val leading: Float = 1f, val towardLeading: Float = 0f, val lift: Float = 0f,
) {
    companion object { val Open = BarSearchFrame(1f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 0f) }
}

private val barEaseOut = CubicBezierEasing(0f, 0f, .58f, 1f)
private val barEaseInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)

/** Independent channels retain the upstream timings; reversing preserves spring velocity. */
private class BarSearchMove(val open: Boolean, val from: BarSearchFrame, val start: Float,
    val velocity: Float, val delay: Float) {
    private val stiffness = if (open) 390f else 600f
    fun growth(t: Float) = glassSpring(max(0f, t - delay), stiffness, .75f, start, if (open) 1f else 0f, velocity)
    fun speed(t: Float): Float {
        if (t < delay) return 0f
        val s = t - delay; val omega = sqrt(stiffness); val decay = .75f * omega
        val frequency = omega * sqrt(1 - .75f * .75f)
        val a = start - if (open) 1f else 0f; val b = (velocity + decay * a) / frequency
        return exp(-decay * s) * ((-decay * a + b * frequency) * cos(frequency * s) +
            (-decay * b - a * frequency) * sin(frequency * s))
    }
    fun settled(t: Float) = abs(growth(t) - if (open) 1f else 0f) < .001f && abs(speed(t)) < .001f
    fun done(t: Float) = t >= (if (open) .29f else .55f) && settled(t)
    fun frame(t: Float): BarSearchFrame {
        if (done(t)) return if (open) BarSearchFrame.Open else BarSearchFrame()
        fun to(a: Float, b: Float, begin: Float, end: Float, curve: Int = 0): Float {
            val p = ((t - begin) / (end - begin)).coerceIn(0f, 1f)
            return glassLerp(a, b, when (curve) { 1 -> barEaseOut.transform(p); 2 -> barEaseInOut.transform(p); else -> p })
        }
        return if (open) BarSearchFrame(
            growth(t), to(from.collapse, 1f, 0f, .08f, 1), to(from.surface, 1f, 0f, .05f),
            to(from.title, 0f, 0f, .08f), to(from.actions, 0f, .02f, .08f),
            to(from.flank, 1f, .08f, .2f), to(from.content, 1f, .07f, .22f),
            to(from.glyph, 0f, 0f, .03f), to(from.swell, 0f, 0f, .1f),
            to(from.leading, 0f, 0f, .02f), to(from.towardLeading, 0f, 0f, .08f),
            to(to(from.lift, 1f, .02f, .12f, 1), 0f, .16f, .29f, 2),
        ) else BarSearchFrame(
            growth(t), to(from.collapse, 0f, .17f, .33f, 2), to(from.surface, 0f, .19f, .29f),
            to(from.title, 1f, .08f, .19f), to(from.actions, 1f, .18f, .26f),
            to(from.flank, 0f, .11f, .2f), to(from.content, 0f, 0f, .04f),
            to(from.glyph, 1f, .3f, .46f),
            to(to(from.swell, 1f, .27f, .36f, 1), 0f, .36f, .55f, 2),
            to(from.leading, 1f, .16f, .19f),
            to(to(from.towardLeading, 1f, 0f, .08f), 0f, .4f, .41f),
            to(to(from.lift, 1f, .06f, .16f, 1), 0f, .21f, .32f, 2),
        )
    }
}

@Stable internal class BarSearchMotion(initial: Boolean) {
    var frame by mutableStateOf(if (initial) BarSearchFrame.Open else BarSearchFrame())
        private set
    private var move: BarSearchMove? = null
    private var elapsed = 0f
    private var target = initial
    suspend fun animate(open: Boolean) {
        if (target == open) return
        target = open
        val last = move
        val next = BarSearchMove(open, frame, frame.growth, last?.speed(elapsed) ?: 0f,
            if (last != null && !last.settled(elapsed)) 0f else if (open) .05f else .04f)
        move = next; elapsed = 0f
        val start = withFrameNanos { it }
        do {
            elapsed = withFrameNanos { (it - start) / 1_000_000_000f }
            frame = next.frame(elapsed)
        } while (!next.done(elapsed))
    }
}
