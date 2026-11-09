package io.github.currencortex.music.feature.lyrics.desktop

import io.github.currencortex.music.core.media.DockAudioLevel
import io.github.currencortex.music.core.media.FluidBackgroundMotion
import io.github.currencortex.music.core.media.smoothDockEnergy
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp

/** The player background's tempo/energy clock drives a bounded spring inside the island. */
internal class DesktopIslandGlowMotion {
    var energy = 0f
        private set
    var beat = 0f
        private set
    var position = 0f
        private set
    var speed = .35f
        private set
    private var velocity = 0f
    private var direction = 1f
    private var travel = .24f
    private val rhythm = FluidBackgroundMotion()

    fun update(sample: DockAudioLevel.Sample?, seconds: Float, playbackRate: Float = 1f, playing: Boolean = true) {
        fun Float.unit() = if (isFinite()) coerceIn(0f, 1f) else 0f
        val dt = seconds.takeIf { it.isFinite() }?.coerceIn(0f, .1f) ?: 0f
        val fresh = sample.takeIf { playing }
        // Keep the player's musical target, but give horizontal travel a slower response:
        // short attacks and uncertain BPM should brighten the light, not jerk its pace.
        val targetSpeed = rhythm.update(fresh, dt, playbackRate, playing)
        speed += ((targetSpeed - speed) * (1f - exp(-dt / 1.1f)))
            .coerceIn(-.55f * dt, .55f * dt)
        energy = smoothDockEnergy(energy, fresh?.energy?.unit() ?: 0f, dt)
        beat = smoothDockEnergy(beat, maxOf(fresh?.bass?.unit() ?: 0f, fresh?.pulse?.unit() ?: 0f), dt)
        val audible = (energy + beat * .35f).coerceIn(0f, 1f)
        // Moving the turn-around boundary abruptly also looks like a speed jump.
        travel += (.24f + audible * .68f - travel) * (1f - exp(-dt / .55f))
        if (energy < .001f && beat < .001f) {
            energy = 0f; beat = 0f; velocity = 0f
            return
        }
        // Fixed maximum substep keeps rebounds stable on both 60/120 Hz screens and dropped frames.
        val motionSeconds = dt * speed / .75f
        val steps = ceil(motionSeconds / (1f / 120f)).toInt().coerceAtLeast(1)
        val step = motionSeconds / steps
        repeat(steps) {
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
