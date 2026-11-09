package io.github.currencortex.music.feature.lyrics.desktop

import io.github.currencortex.music.core.media.DockAudioLevel
import io.github.currencortex.music.core.media.smoothDockEnergy
import kotlin.math.abs
import kotlin.math.ceil

/** A bounded spring driven only by fresh PCM energy; silence and pause let the light settle. */
internal class DesktopIslandGlowMotion {
    var energy = 0f
        private set
    var beat = 0f
        private set
    var position = 0f
        private set
    private var velocity = 0f
    private var direction = 1f

    fun update(sample: DockAudioLevel.Sample?, seconds: Float) {
        fun Float.unit() = if (isFinite()) coerceIn(0f, 1f) else 0f
        val dt = seconds.takeIf { it.isFinite() }?.coerceIn(0f, .1f) ?: 0f
        energy = smoothDockEnergy(energy, sample?.energy?.unit() ?: 0f, dt)
        beat = smoothDockEnergy(beat, maxOf(sample?.bass?.unit() ?: 0f, sample?.pulse?.unit() ?: 0f), dt)
        if (energy < .001f && beat < .001f) {
            energy = 0f; beat = 0f; velocity = 0f
            return
        }
        // Fixed maximum substep keeps rebounds stable on both 60/120 Hz screens and dropped frames.
        val steps = ceil(dt / (1f / 120f)).toInt().coerceAtLeast(1)
        val step = dt / steps
        repeat(steps) {
            val audible = (energy + beat * .35f).coerceIn(0f, 1f)
            val target = if (sample == null) 0f else direction * .92f
            val stiffness = 12f + audible * 28f
            velocity += ((target - position) * stiffness - velocity * 4.8f) * step
            position += velocity * step
            if (direction * position >= .78f) direction = -direction
            if (abs(position) > 1f) {
                position = position.coerceIn(-1f, 1f)
                velocity = -velocity * .5f
            }
        }
    }
}
