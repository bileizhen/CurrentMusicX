package io.github.currencortex.music.core.visualizer

import kotlin.math.exp

/** Main-thread-owned reusable output; never publish these arrays to the analysis worker. */
class VisualizerInterpolatedFrame(val spectrum: FloatArray = FloatArray(48),
    val rawSpectrum: FloatArray = FloatArray(48), val waveform: FloatArray = FloatArray(128)) {
    var rms = 0f; var bass = 0f; var mid = 0f; var treble = 0f
    var pulse = 0f; var transientPulse = 0f
    var audioTimestampNanos = 0L
    var captureIntervalNanos = 52_000_000L
    var kickCount = 0L
    var transientCount = 0L
    var generation = 0L
    fun clear() {
        spectrum.fill(0f); rawSpectrum.fill(0f); waveform.fill(0f)
        rms = 0f; bass = 0f; mid = 0f; treble = 0f; pulse = 0f; transientPulse = 0f
        audioTimestampNanos = 0; captureIntervalNanos = 52_000_000L; kickCount = 0; transientCount = 0
    }
}

class VisualizerFrameInterpolator {
    val output = VisualizerInterpolatedFrame()
    private var target: AudioAnalysisFrame? = null
    private var generation = Long.MIN_VALUE
    private var lastTime = 0L
    private var consumedKick = 0L
    private var consumedTransient = 0L
    private var paused = false

    fun accept(frame: AudioAnalysisFrame) {
        if (frame.timestampNanos <= 0) return
        if (generation != frame.generation) { reset(); generation = frame.generation }
        if (frame.timestampNanos <= (target?.timestampNanos ?: 0)) return
        target?.let {
            val interval = frame.timestampNanos - it.timestampNanos
            if (interval in 5_000_000L..500_000_000L)
                output.captureIntervalNanos = (output.captureIntervalNanos * .8 + interval * .2).toLong()
        }
        target = frame; paused = false; output.audioTimestampNanos = frame.timestampNanos; output.generation = frame.generation
        for (i in output.rawSpectrum.indices) output.rawSpectrum[i] = safe(frame.rawSpectrum.getOrElse(i) { 0f })
    }

    fun pause() { paused = true; target = null }
    fun step(timeNanos: Long, deltaSeconds: Float): VisualizerInterpolatedFrame {
        if (timeNanos <= 0 || timeNanos <= lastTime) return output
        lastTime = timeNanos
        val dt = if (deltaSeconds.isFinite()) deltaSeconds.coerceIn(0f, .05f) else 0f
        val frame = target
        val age = (timeNanos - output.audioTimestampNanos).coerceAtLeast(0L)
        val tolerance = (output.captureIntervalNanos * 4).coerceIn(160_000_000L, 400_000_000L)
        val fresh = !paused && frame != null && age <= tolerance
        output.pulse *= exp(-dt / .22f)
        output.transientPulse *= exp(-dt / .08f)
        if (fresh && frame != null) {
            val kickId = if (frame.kickSequence > 0) frame.kickSequence else if (frame.kick) frame.timestampNanos else 0
            val transientId = if (frame.transientSequence > 0) frame.transientSequence else if (frame.transient) frame.timestampNanos else 0
            if (kickId > consumedKick && age <= tolerance) {
                consumedKick = kickId
                val eventAge = (timeNanos - frame.kickTimestampNanos.takeIf { it > 0 }.let { it ?: frame.timestampNanos }).coerceAtLeast(0L)
                if (eventAge <= tolerance) { output.kickCount++; output.pulse = exp(-eventAge / 1e9f / .22f) }
            }
            if (transientId > consumedTransient) {
                consumedTransient = transientId
                val eventAge = (timeNanos - frame.transientTimestampNanos.takeIf { it > 0 }.let { it ?: frame.timestampNanos }).coerceAtLeast(0L)
                if (eventAge <= tolerance) { output.transientCount++; output.transientPulse = exp(-eventAge / 1e9f / .08f) }
            }
        }
        output.rms = smooth(output.rms, if (fresh) safe(frame!!.rms) else 0f, dt)
        output.bass = smooth(output.bass, if (fresh) safe(frame!!.bass) else 0f, dt)
        output.mid = smooth(output.mid, if (fresh) safe(frame!!.mid) else 0f, dt)
        output.treble = smooth(output.treble, if (fresh) safe(frame!!.treble) else 0f, dt)
        for (i in output.spectrum.indices) {
            val value = if (fresh) safe(frame!!.spectrum.getOrElse(i) { 0f }).takeIf { it >= .015f } ?: 0f else 0f
            output.spectrum[i] = smooth(output.spectrum[i], value, dt)
        }
        val waveAlpha = 1f - exp(-dt / if (fresh) .018f else .1f)
        for (i in output.waveform.indices) {
            val value = if (fresh) frame!!.waveform.getOrElse(i) { 0f }.let { if (it.isFinite()) it.coerceIn(-1f, 1f) else 0f } else 0f
            output.waveform[i] += (value - output.waveform[i]) * waveAlpha
        }
        if (!fresh) {
            output.rawSpectrum.fill(0f)
        }
        return output
    }
    fun reset() {
        target = null; lastTime = 0; consumedKick = 0; consumedTransient = 0
        paused = false; output.clear()
    }
    private fun safe(value: Float) = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    private fun smooth(current: Float, value: Float, dt: Float): Float =
        current + (value - current) * (1f - exp(-dt / if (value > current) .018f else .13f))
}
