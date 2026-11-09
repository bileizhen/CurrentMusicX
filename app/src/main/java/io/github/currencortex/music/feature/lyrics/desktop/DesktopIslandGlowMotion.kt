package io.github.currencortex.music.feature.lyrics.desktop

import io.github.currencortex.music.core.media.DockAudioLevel
import io.github.currencortex.music.core.media.FluidBackgroundMotion
import io.github.currencortex.music.core.media.smoothDockEnergy
import kotlin.math.abs
import kotlin.math.ceil

/** The player background's tempo/energy clock drives a bounded spring inside the island. */
internal class DesktopIslandGlowMotion {
    var energy = 0f
        private set
    var beat = 0f
        private set
    var position = 0f
        private set
    private var velocity = 0f
    private var direction = 1f
    private val rhythm = FluidBackgroundMotion()

    fun update(sample: DockAudioLevel.Sample?, seconds: Float, playbackRate: Float = 1f, playing: Boolean = true) {
        fun Float.unit() = if (isFinite()) coerceIn(0f, 1f) else 0f
        val dt = seconds.takeIf { it.isFinite() }?.coerceIn(0f, .1f) ?: 0f
        val fresh = sample.takeIf { playing }
        // Use the same BPM + playback rate + energy + onset response and acceleration/release
        // smoothing as the player. Only the geometry differs: horizontal rebound instead of flow.
        val speed = rhythm.update(fresh, dt, playbackRate, playing)
        energy = smoothDockEnergy(energy, fresh?.energy?.unit() ?: 0f, dt)
        beat = smoothDockEnergy(beat, maxOf(fresh?.bass?.unit() ?: 0f, fresh?.pulse?.unit() ?: 0f), dt)
        if (energy < .001f && beat < .001f) {
            energy = 0f; beat = 0f; velocity = 0f
            return
        }
        // Fixed maximum substep keeps rebounds stable on both 60/120 Hz screens and dropped frames.
        val motionSeconds = dt * speed / .75f
        val steps = ceil(motionSeconds / (1f / 120f)).toInt().coerceAtLeast(1)
        val step = motionSeconds / steps
        repeat(steps) {
            val audible = (energy + beat * .35f).coerceIn(0f, 1f)
            val travel = .24f + audible * .68f
            val target = if (fresh == null) 0f else direction * travel
            velocity += ((target - position) * 22f - velocity * 4.8f) * step
            position += velocity * step
            if (direction * position >= travel * .85f) direction = -direction
            if (abs(position) > 1f) {
                position = position.coerceIn(-1f, 1f)
                velocity = -velocity * .5f
            }
        }
    }
}
