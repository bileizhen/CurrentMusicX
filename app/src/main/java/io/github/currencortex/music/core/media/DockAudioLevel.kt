package io.github.currencortex.music.core.media

import kotlin.math.exp
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Only numeric energy leaves the playback thread. No audio is retained or captured from a mic. */
class DockAudioLevel {
    @Volatile private var directlyEnabled = false
    private val consumers = AtomicInteger()
    var enabled: Boolean
        get() = directlyEnabled || consumers.get() > 0
        set(value) { directlyEnabled = value; if (!enabled) reset() }
    @Volatile var sample = Sample()
        private set
    data class Sample(val energy: Float = 0f, val bass: Float = 0f, val timestampNanos: Long = 0L,
        val bpm: Float? = null, val pulse: Float = 0f)
    /** Dock and player/preview can subscribe together without disabling each other. */
    fun acquire(): java.io.Closeable {
        consumers.incrementAndGet()
        val closed = AtomicBoolean()
        return java.io.Closeable {
            if (closed.compareAndSet(false, true)) {
                consumers.decrementAndGet()
                if (!enabled) reset()
            }
        }
    }
    fun publish(energy: Float, bass: Float, timestampNanos: Long = System.nanoTime(), bpm: Float? = null, pulse: Float = 0f) {
        if (enabled) sample = Sample(energy.coerceIn(0f, 1f), bass.coerceIn(0f, 1f), timestampNanos, bpm, pulse)
    }
    fun reset() { sample = Sample() }
}

/** Fast attack / slow release, independent of screen refresh rate; silence settles to zero. */
internal fun smoothDockEnergy(current: Float, target: Float, seconds: Float): Float {
    val tau = if (target > current) .085f else .32f
    return current + (target - current) * (1f - exp(-seconds.coerceIn(0f, .1f) / tau))
}
