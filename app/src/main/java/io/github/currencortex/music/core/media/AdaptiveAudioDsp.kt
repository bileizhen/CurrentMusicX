package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.settings.AdaptiveAudioSettings
import kotlin.math.*

/** Local, bounded energy-based EQ. No model, network access or song-level analysis. */
internal class AdaptiveAudioDsp(private val sampleRate: Int, private val channels: Int) {
    private val detectors = Array(10) { band -> Array(channels) { Biquad().apply {
        bandPass(AdaptiveAudioSettings.FREQUENCIES[band].toDouble(), sampleRate)
    } } }
    private val filters = Array(10) { Array(channels) { Biquad() } }
    private val powers = DoubleArray(10)
    private val currentGains = DoubleArray(10)
    private val low = DoubleArray(channels)
    private val low2 = DoubleArray(channels)
    private val dc = DoubleArray(channels)
    private val harmonic = DoubleArray(channels)
    private val bassAlpha = 1 - exp(-2 * PI * 130 / sampleRate)
    private val dcAlpha = 1 - exp(-2 * PI * 35 / sampleRate)
    private val harmonicAlpha = 1 - exp(-2 * PI * 600 / sampleRate)
    private val smoothing = 1 - exp(-1.0 / (.008 * sampleRate))
    private var sampleCount = 0
    private var energy = 0.0
    private var mix = 0.0
    private var bassMix = 0.0
    var bassPower = 0.0; private set
    var fullPower = 0.0; private set
    val gains: List<Float> get() = currentGains.map { it.toFloat() }

    /** One interleaved frame, in-place. All state and scratch memory stay on the audio thread. */
    fun process(frame: DoubleArray, settings: AdaptiveAudioSettings) {
        mix += smoothing * ((if (settings.enabled) 1.0 else 0.0) - mix)
        bassMix += smoothing * ((if (settings.virtualBass) settings.bassStrength / 100.0 else 0.0) - bassMix)
        fullPower = 0.0; bassPower = 0.0
        for (channel in 0 until channels) {
            val input = frame[channel].let { if (it.isFinite()) it.coerceIn(-1.0, 1.0) else 0.0 }
            fullPower += input * input / channels
            low[channel] += bassAlpha * (input - low[channel])
            low2[channel] += bassAlpha * (low[channel] - low2[channel])
            bassPower += low2[channel] * low2[channel] / channels
            var processed = input
            for (band in 0..9) {
                if (settings.dynamicEq) {
                    val detected = detectors[band][channel].process(input)
                    powers[band] += detected * detected / channels
                }
                processed = filters[band][channel].process(processed)
            }
            // Full-wave rectification creates harmonics of the missing fundamental.
            // Remove its DC and constrain its bandwidth instead of simply boosting sub-bass.
            val rectified = abs(low2[channel])
            dc[channel] += dcAlpha * (rectified - dc[channel])
            harmonic[channel] += harmonicAlpha * (rectified - dc[channel] - harmonic[channel])
            processed += harmonic[channel] * bassMix * 1.6
            // Reserve 6 dB before the output knee; never send an out-of-range sample to the sink.
            val wet = processed * .5011872336
            val bounded = if (abs(wet) <= .9) wet else sign(wet) * (.9 + .1 * tanh((abs(wet) - .9) / .1))
            frame[channel] = input + mix * (bounded - input)
        }
        energy += fullPower
        sampleCount++
        val interval = (sampleRate / (20 * AdaptiveAudioSettings.SPEEDS[settings.speed])).toInt().coerceAtLeast(1)
        if (sampleCount >= interval) {
            val average = energy / sampleCount
            val response = 1 - exp(-sampleCount.toDouble() / (sampleRate * .3))
            val amount = listOf(.35, .6, .9, 1.2, 1.5)[settings.strength]
            for (band in 0..9) {
                val frequency = AdaptiveAudioSettings.FREQUENCIES[band]
                // Silence and bands beyond Nyquist must not acquire runaway makeup gain.
                val correction = if (settings.dynamicEq && average > 1e-7 && frequency < sampleRate * .45) {
                    val relativeDb = 10 * log10((powers[band] / sampleCount + 1e-12) / (average + 1e-12))
                    ((settings.curve.tilt[band] + (-10 - relativeDb) * .12) * amount).coerceIn(-3.0, 3.0)
                } else 0.0
                val desired = if (frequency < sampleRate * .45) settings.gains[band] + correction else 0.0
                currentGains[band] += response * (desired - currentGains[band])
                filters[band].forEach { it.peak(frequency.toDouble(), sampleRate, currentGains[band]) }
                powers[band] = 0.0
            }
            sampleCount = 0; energy = 0.0
        }
    }
    val settledBypass get() = mix < .00001

    // RBJ biquads, normalized by a0: https://www.w3.org/TR/audio-eq-cookbook/
    private class Biquad {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        var z1 = 0.0; var z2 = 0.0
        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y
            return y
        }
        fun bandPass(frequency: Double, rate: Int) {
            if (frequency >= rate * .45) { b0 = 0.0; return }
            val w = 2 * PI * frequency / rate; val alpha = sin(w) / 2
            b0 = alpha / (1 + alpha); b1 = 0.0; b2 = -b0
            a1 = -2 * cos(w) / (1 + alpha); a2 = (1 - alpha) / (1 + alpha)
        }
        fun peak(frequency: Double, rate: Int, gain: Double) {
            if (frequency >= rate * .45) return
            val w = 2 * PI * frequency / rate; val alpha = sin(w) / (2 * 1.1)
            val a = 10.0.pow(gain / 40); val denominator = 1 + alpha / a
            b0 = (1 + alpha * a) / denominator; b1 = -2 * cos(w) / denominator
            b2 = (1 - alpha * a) / denominator; a1 = b1; a2 = (1 - alpha / a) / denominator
        }
    }
}
