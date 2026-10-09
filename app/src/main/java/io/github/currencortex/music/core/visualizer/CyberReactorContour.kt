package io.github.currencortex.music.core.visualizer

import kotlin.math.*

/** Reusable polar contour: actual waveform/FFT and pulse, no synthetic beat source. */
class CyberReactorContour(val count: Int) {
    init { require(count in 32..256) }
    val x = FloatArray(count) { cos(-PI.toFloat() + it * 2f * PI.toFloat() / count) }
    val y = FloatArray(count) { sin(-PI.toFloat() + it * 2f * PI.toFloat() / count) }
    val radii = FloatArray(count) { 1.025f }
    private val horns = FloatArray(count) {
        val a = -PI.toFloat() + it * 2f * PI.toFloat() / count
        maxOf((1f - abs(a + PI.toFloat() * .75f) / .24f).coerceAtLeast(0f),
            (1f - abs(a + PI.toFloat() * .25f) / .24f).coerceAtLeast(0f)).pow(2)
    }
    fun update(frame: VisualizerInterpolatedFrame, config: VisualizerEffectConfig, reduced: Boolean) {
        val gain = config.spectrumSensitivity * config.globalIntensity
        val bass = VisualizerEffectState.energy(frame.bass, config.bassSensitivity * config.globalIntensity)
        val treble = VisualizerEffectState.energy(frame.treble, config.globalIntensity)
        val pulse = VisualizerEffectState.safe(frame.pulse) * config.globalIntensity.coerceAtMost(1f)
        val motion = if (reduced) .2f else 1f
        val waveGain = waveformGain(frame.rms)
        for (i in 0 until count) {
            val position = abs(i.toFloat() / count * 2f - 1f)
            val level = VisualizerEffectState.spectrum(frame.spectrum, position, gain)
            val sample = if (frame.waveform.isEmpty()) 0f else
                displayWaveform(frame.waveform[i * frame.waveform.size / count], waveGain) * config.globalIntensity.coerceAtMost(1f)
            radii[i] = 1.025f + motion * (level * .07f + abs(sample) * .10f + pulse * .055f +
                horns[i] * (.34f * (bass * .45f + treble * .55f) + pulse * .18f))
        }
    }
    fun reset() { radii.fill(1.025f) }
    companion object {
        // AS_PLAYED is volume dependent: normalize only drawing, with a finite noise-floor cap.
        fun waveformGain(rms: Float) = minOf(48f, .18f / maxOf(.0025f, VisualizerEffectState.safe(rms)))
        fun displayWaveform(value: Float, gain: Float) =
            ((value.takeIf(Float::isFinite)?.coerceIn(-1f,1f) ?: 0f) * gain).coerceIn(-1f,1f)
    }
}
