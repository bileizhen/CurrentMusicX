package io.github.currencortex.music.core.media

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow

/** Band replacement with weighted Chebyshev harmonics, in normalized PCM units.
 * The caller owns final headroom/limiting; intermediate output may exceed full scale.
 */
internal class AdaptiveVirtualBass(sampleRate: Int, channels: Int) {
    private val states = Array(channels) { Channel() }
    private val excitationHighPass = exp(-2 * PI * 30 / sampleRate)
    private val excitationLowPass = 1 - exp(-2 * PI * 120 / sampleRate)
    private val splitLowPass = 1 - exp(-2 * PI * 30 / sampleRate)
    private val replacementHighPass = exp(-2 * PI / sampleRate)
    private val switchSmoothing = 1 - exp(-1.0 / (.024 * sampleRate))
    private val parameterSmoothing = 1 - exp(-1.0 / (.055 * sampleRate))
    // Keep the reference 44.1 kHz DC rejection consistent at other sample rates.
    private val dcPole = .995.pow(44100.0 / sampleRate)
    private var enabledMix = 0.0
    private var amount = 0.0
    private var cleared = true

    /** Advance shared envelopes once per frame, keeping each channel's filter history separate. */
    fun process(frame: DoubleArray, enabled: Boolean, strength: Int) {
        val targetAmount = strength.coerceIn(0, 100) / 100.0
        enabledMix += switchSmoothing * ((if (enabled) 1.0 else 0.0) - enabledMix)
        amount += parameterSmoothing * (targetAmount - amount)
        if (abs(amount - targetAmount) < 1e-6) amount = targetAmount
        if (!enabled && enabledMix < 1e-5) enabledMix = 0.0
        val mix = enabledMix * amount
        if (mix == 0.0) {
            if (!cleared) { states.forEach(Channel::reset); cleared = true }
            for (channel in states.indices) {
                if (!frame[channel].isFinite() || abs(frame[channel]) > MAX_INPUT) frame[channel] = 0.0
            }
            return
        }
        cleared = false
        for (channel in states.indices) {
            val state = states[channel]
            val input = frame[channel]
            // Leave ample internal headroom, but never retain a corrupt finite value
            // in the slow low-frequency filters for seconds after that sample.
            if (!input.isFinite() || abs(input) > MAX_INPUT) {
                state.reset()
                frame[channel] = 0.0
                continue
            }
            val excited = state.highPass(input, excitationHighPass, 0)
            val band = state.lowPass(excited, excitationLowPass, 0)
            val sub = state.lowPass(excited, splitLowPass, 2)
            val source = (band - sub).coerceIn(-1.0, 1.0)
            // Default speaker voicing: fractional orders interpolate adjacent polynomials.
            val harmonics = (harmonic(source, 1.7) + harmonic(source, 2.4) + harmonic(source, 3.1)) / 3
            val removed = state.lowPass(state.highPass(input, replacementHighPass, 2), excitationLowPass, 4)
            // Align the generated branch with the dry blend at ordinary bass frequencies.
            // The reference polarity creates a deep cancellation at low strength. Use a
            // smaller drive with this polarity; the parent provides final peak protection.
            val generated = harmonics * -4.0
            val dcFree = generated - state.previousHarmonics + dcPole * state.previousDcFree
            state.previousHarmonics = generated
            state.previousDcFree = dcFree
            // Strength zero is dry; intermediate strengths replace only that fraction of the band.
            val output = input + mix * (dcFree - removed)
            if (output.isFinite()) frame[channel] = output
            else { state.reset(); frame[channel] = 0.0 }
        }
    }

    private fun harmonic(input: Double, order: Double): Double {
        val lower = floor(order).toInt()
        val fraction = order - lower
        val low = polynomial(input, lower)
        return low + fraction * (polynomial(input, lower + 1) - low)
    }

    /** T_n(x) - T_n(0): silence never creates the even-order constant term. */
    private fun polynomial(input: Double, order: Int): Double {
        var previous = 1.0
        var current = input
        for (n in 2..order) {
            val next = 2 * input * current - previous
            previous = current
            current = next
        }
        val atZero = when (order and 3) { 0 -> 1.0; 2 -> -1.0; else -> 0.0 }
        return current - atZero
    }

    private class Channel {
        private val previousInputs = DoubleArray(4)
        private val highPassOutputs = DoubleArray(4)
        private val lowPassOutputs = DoubleArray(6)
        var previousHarmonics = 0.0
        var previousDcFree = 0.0

        fun highPass(input: Double, coefficient: Double, offset: Int): Double {
            var value = input
            for (stage in offset until offset + 2) {
                val output = coefficient * (highPassOutputs[stage] + value - previousInputs[stage])
                previousInputs[stage] = value
                highPassOutputs[stage] = output
                value = output
            }
            return value
        }

        fun lowPass(input: Double, coefficient: Double, offset: Int): Double {
            var value = input
            for (stage in offset until offset + 2) {
                lowPassOutputs[stage] += coefficient * (value - lowPassOutputs[stage])
                value = lowPassOutputs[stage]
            }
            return value
        }

        fun reset() {
            previousInputs.fill(0.0); highPassOutputs.fill(0.0); lowPassOutputs.fill(0.0)
            previousHarmonics = 0.0; previousDcFree = 0.0
        }
    }

    companion object { private const val MAX_INPUT = 64.0 }
}
