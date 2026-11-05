package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.settings.AdaptiveAudioSettings
import kotlin.math.*

/** Virtual bass, windowed spectral analysis, smoothed EQ, then shared peak protection. */
internal class AdaptiveAudioDsp(private val sampleRate: Int, private val channels: Int) {
    private val virtualBass = AdaptiveVirtualBass(sampleRate, channels)
    private val dynamicEq = AdaptiveDynamicEq(sampleRate, channels)
    private val filters = Array(10) { band ->
        EqBand(AdaptiveAudioSettings.FREQUENCIES[band].toDouble(), sampleRate, channels)
    }
    private val currentGains = DoubleArray(10)
    private val dry = DoubleArray(channels)
    private val low = DoubleArray(channels)
    private val low2 = DoubleArray(channels)
    private val bassAlpha = 1 - exp(-2 * PI * 130 / sampleRate)
    private val switchSmoothing = 1 - exp(-1.0 / (.008 * sampleRate))
    private val gainSmoothing = 1 - exp(-COEFFICIENT_FRAMES.toDouble() / (.024 * sampleRate))
    private val limiterRelease = 1 - exp(-1.0 / (.100 * sampleRate))
    private var coefficientFrames = 0
    private var mix = 0.0
    private var limiterGain = 1.0
    var bassPower = 0.0; private set
    var fullPower = 0.0; private set
    val gains: List<Float> get() = currentGains.map { it.toFloat() }

    /** One interleaved frame, in-place. Scratch memory and channel history stay on the audio thread. */
    fun process(frame: DoubleArray, settings: AdaptiveAudioSettings) {
        mix += switchSmoothing * ((if (settings.enabled) 1.0 else 0.0) - mix)
        fullPower = 0.0; bassPower = 0.0
        for (channel in 0 until channels) {
            val input = frame[channel].let { if (it.isFinite()) it.coerceIn(-1.0, 1.0) else 0.0 }
            dry[channel] = input
            frame[channel] = input
            fullPower += input * input / channels
            low[channel] += bassAlpha * (input - low[channel])
            low2[channel] += bassAlpha * (low[channel] - low2[channel])
            bassPower += low2[channel] * low2[channel] / channels
        }

        // Keep floating-point headroom through the whole chain. A software endpoint
        // needs no device-dependent system gain to undo an intermediate attenuation.
        virtualBass.process(frame, settings.enabled && settings.virtualBass, settings.bassStrength)
        dynamicEq.process(frame, settings)
        if (coefficientFrames == 0) updateGains(settings)
        coefficientFrames = (coefficientFrames + 1) % COEFFICIENT_FRAMES
        var peak = 0.0
        for (channel in 0 until channels) {
            var value = frame[channel]
            for (band in filters) value = band.process(value, channel)
            frame[channel] = value
            peak = max(peak, abs(value))
        }

        // Link gain across all channels: limiting must not move the stereo/surround image.
        // Instant peak capture prevents clipping; slower release avoids sample-by-sample pumping.
        val targetGain = if (peak > .95) .95 / peak else 1.0
        limiterGain = if (targetGain < limiterGain) targetGain
            else limiterGain + limiterRelease * (targetGain - limiterGain)
        for (channel in 0 until channels) {
            val wet = frame[channel] * limiterGain
            frame[channel] = (dry[channel] + mix * (wet - dry[channel])).coerceIn(-1.0, 1.0)
        }
    }

    private fun updateGains(settings: AdaptiveAudioSettings) {
        for (band in 0..9) {
            // Manual settings remain the baseline. Runtime correction never writes preferences.
            val correction = if (settings.dynamicEq) dynamicEq.gains[band] else 0.0
            val desired = if (AdaptiveAudioSettings.FREQUENCIES[band] < sampleRate * .45)
                (settings.gains[band] + correction).coerceIn(-9.0, 9.0) else 0.0
            currentGains[band] += gainSmoothing * (desired - currentGains[band])
            if (abs(desired - currentGains[band]) < 1e-6) currentGains[band] = desired
            filters[band].gain(currentGains[band])
        }
    }

    val settledBypass get() = mix < .00001

    /** Shared RBJ coefficients, independent transposed direct-form-II delay lines per channel. */
    private class EqBand(frequency: Double, rate: Int, channels: Int) {
        private val valid = frequency < rate * .45
        private val cosine = cos(2 * PI * frequency / rate)
        private val alpha = sin(2 * PI * frequency / rate) / (2 * 1.1)
        private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
        private var a1 = 0.0; private var a2 = 0.0
        private var lastGain = Double.NaN
        private val z1 = DoubleArray(channels)
        private val z2 = DoubleArray(channels)
        fun process(input: Double, channel: Int): Double {
            if (!valid) return input
            val output = b0 * input + z1[channel]
            val next1 = b1 * input - a1 * output + z2[channel]
            val next2 = b2 * input - a2 * output
            if (!output.isFinite() || !next1.isFinite() || !next2.isFinite()) {
                z1[channel] = 0.0; z2[channel] = 0.0
                return 0.0
            }
            z1[channel] = if (abs(next1) < 1e-30) 0.0 else next1
            z2[channel] = if (abs(next2) < 1e-30) 0.0 else next2
            return output
        }
        fun gain(db: Double) {
            if (!valid || db == lastGain) return
            lastGain = db
            val a = 10.0.pow(db / 40)
            val denominator = 1 + alpha / a
            b0 = (1 + alpha * a) / denominator
            b1 = -2 * cosine / denominator
            b2 = (1 - alpha * a) / denominator
            a1 = b1; a2 = (1 - alpha / a) / denominator
        }
    }

    companion object { private const val COEFFICIENT_FRAMES = 32 }
}
