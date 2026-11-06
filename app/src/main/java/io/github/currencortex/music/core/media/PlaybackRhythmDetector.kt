package io.github.currencortex.music.core.media

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** Small online onset/tempo estimator over the meter's PCM windows; no track metadata or audio retention. */
internal class PlaybackRhythmDetector {
    data class Rhythm(val bpm: Float? = null, val pulse: Float = 0f)
    private var elapsed = 0.0
    private var lastOnset = -10.0
    private var previous = 0f
    private var averageFlux = 0f
    private var pulse = 0f
    private val intervals = FloatArray(5)
    private var count = 0
    private var index = 0
    private var bpm: Float? = null

    fun reset() {
        elapsed = 0.0; lastOnset = -10.0; previous = 0f; averageFlux = 0f
        pulse = 0f; count = 0; index = 0; bpm = null
    }

    fun update(energy: Float, bass: Float, seconds: Float): Rhythm {
        if (!energy.isFinite() || !bass.isFinite() || !seconds.isFinite() || seconds <= 0f || seconds > .1f) {
            reset(); return Rhythm()
        }
        elapsed += seconds
        val level = max(energy, bass * 1.4f).coerceIn(0f, 1f)
        val flux = (level - previous).coerceAtLeast(0f)
        val interval = (elapsed - lastOnset).toFloat()
        pulse *= exp(-seconds / .18f)
        // Relative flux threshold follows recording loudness; refractory time rejects one attack's tail.
        if (level > .002f && flux > max(.0015f, averageFlux * 2.2f) && interval >= .20f) {
            pulse = 1f
            if (interval in .24f..2.05f) {
                intervals[index] = interval
                index = (index + 1) % intervals.size
                count = (count + 1).coerceAtMost(intervals.size)
                if (count >= 2) {
                    val recent = intervals.copyOf(count).sorted()
                    val median = recent[count / 2]
                    val consistent = recent.count { abs(it - median) < median * .22f }
                    bpm = if (consistent >= maxOf(2, count / 2 + 1)) 60f / median else null
                }
            } else { count = 0; index = 0; bpm = null }
            lastOnset = elapsed
        } else if (interval > 2.05f) {
            count = 0; index = 0; bpm = null
        }
        averageFlux += (flux - averageFlux) * (1f - exp(-seconds / .6f))
        previous = level
        return Rhythm(bpm, pulse)
    }
}
