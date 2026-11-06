package io.github.currencortex.music.core.media

import kotlin.math.exp

/** Continuously combines recent beat cadence, attack strength and energy, not fixed BPM presets. */
internal class FluidBackgroundMotion {
    var speed = .35f
        private set

    fun update(sample: DockAudioLevel.Sample?, seconds: Float, playbackRate: Float = 1f, playing: Boolean = true): Float {
        fun Float.unit() = if (isFinite()) coerceIn(0f, 1f) else 0f
        val rate = playbackRate.takeIf { it.isFinite() && it > 0f }?.coerceIn(.25f, 4f) ?: 1f
        val target = if (!playing) .12f else if (sample == null) .35f else {
            val cadence = sample.bpm?.takeIf { it.isFinite() && it in 29f..250f }?.let { it / 60f * rate } ?: 0f
            (.18f + sample.energy.unit() * .45f + cadence * .4f + sample.pulse.unit() * .3f).coerceAtMost(2.8f)
        }
        val dt = seconds.takeIf { it.isFinite() }?.coerceIn(0f, .1f) ?: 0f
        speed += (target - speed) * (1f - exp(-dt / if (target > speed) .25f else .65f))
        return speed
    }
}
