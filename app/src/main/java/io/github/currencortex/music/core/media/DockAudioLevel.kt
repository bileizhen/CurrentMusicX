package io.github.currencortex.music.core.media

import kotlin.math.exp

/** Only numeric energy leaves the playback thread. No audio is retained or captured from a mic. */
class DockAudioLevel {
    @Volatile var enabled = false
    @Volatile var sample = Sample()
        private set
    data class Sample(val energy: Float = 0f, val bass: Float = 0f, val timestampNanos: Long = 0L)
    fun publish(energy: Float, bass: Float, timestampNanos: Long = System.nanoTime()) {
        if (enabled) sample = Sample(energy.coerceIn(0f, 1f), bass.coerceIn(0f, 1f), timestampNanos)
    }
    fun reset() { sample = Sample() }
}

/** Fast attack / slow release, independent of screen refresh rate; silence settles to zero. */
internal fun smoothDockEnergy(current: Float, target: Float, seconds: Float): Float {
    val tau = if (target > current) .085f else .32f
    return current + (target - current) * (1f - exp(-seconds.coerceIn(0f, .1f) / tau))
}
