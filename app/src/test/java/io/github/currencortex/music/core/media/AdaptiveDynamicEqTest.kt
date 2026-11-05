package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.settings.AdaptiveAudioSettings
import io.github.currencortex.music.data.settings.DynamicEqCurve
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AdaptiveDynamicEqTest {
    private val enabled = AdaptiveAudioSettings(enabled = true, dynamicEq = true)
    private fun tone(engine: AdaptiveDynamicEq, settings: AdaptiveAudioSettings, rate: Int = 48000,
        seconds: Double = 6.0, frequency: Double = 125.0, amplitude: Double = .3, channels: Int = 1) {
        val frame = DoubleArray(channels)
        repeat((seconds * rate).roundToInt()) { index ->
            frame.fill(amplitude * sin(2 * PI * frequency * index / rate))
            engine.process(frame, settings)
        }
    }

    @Test fun silenceAndInvalidSamplesCannotAcquireMakeupGain() {
        val eq = AdaptiveDynamicEq(8000, 6)
        val frame = doubleArrayOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, 0.0, 0.0)
        repeat(8000) { eq.process(frame, enabled.copy(strength = 4)) }
        assertArrayEquals(DoubleArray(10), eq.gains, 0.0)
        assertTrue(frame[0].isNaN()) // Analyzer must not write into the caller's PCM frame.
        assertEquals(Double.POSITIVE_INFINITY, frame[1], 0.0)
    }

    @Test fun energyAggregationDoesNotCancelOppositePhaseStereo() {
        val mono = AdaptiveDynamicEq(16000, 1)
        val stereo = AdaptiveDynamicEq(16000, 2)
        val surround = AdaptiveDynamicEq(16000, 6)
        val monoFrame = DoubleArray(1)
        val stereoFrame = DoubleArray(2)
        val surroundFrame = DoubleArray(6)
        repeat(16000 * 6) { index ->
            val sample = .3 * sin(2 * PI * 125 * index / 16000)
            monoFrame[0] = sample
            stereoFrame[0] = sample; stereoFrame[1] = -sample
            for (channel in surroundFrame.indices) surroundFrame[channel] = if (channel % 2 == 0) sample else -sample
            mono.process(monoFrame, enabled)
            stereo.process(stereoFrame, enabled)
            surround.process(surroundFrame, enabled)
        }
        assertTrue(mono.gains.any { abs(it) > .1 })
        assertArrayEquals(mono.gains, stereo.gains, 1e-9)
        assertArrayEquals(mono.gains, surround.gains, 1e-9)
    }

    @Test fun spectrumAndCurveBothShapeTheResultWithoutChangingManualSettings() {
        val low = AdaptiveDynamicEq(48000, 1)
        val high = AdaptiveDynamicEq(48000, 1)
        val vocal = AdaptiveDynamicEq(48000, 1)
        tone(low, enabled, frequency = 125.0)
        tone(high, enabled, frequency = 4000.0)
        tone(vocal, enabled.copy(curve = DynamicEqCurve.VOCAL), frequency = 125.0)
        assertTrue("Bass and presence content need different corrections", low.gains.indices.any { abs(low.gains[it] - high.gains[it]) > .1 })
        assertTrue("The selected profile must affect supported bands", low.gains.indices.any { abs(low.gains[it] - vocal.gains[it]) > .05 })
        val manual = List(10) { if (it % 2 == 0) 6f else -6f }
        val withManual = AdaptiveDynamicEq(48000, 1)
        val settings = enabled.copy(gains = manual)
        tone(withManual, settings, frequency = 125.0)
        assertArrayEquals("Analysis returns a correction independent of the manual baseline", low.gains, withManual.gains, 0.0)
        assertEquals(manual, settings.gains)
    }

    @Test fun absentBassIsNotBoostedAndNyquistBandsStayInactive() {
        val high = AdaptiveDynamicEq(48000, 1)
        tone(high, enabled.copy(curve = DynamicEqCurve.WARM, strength = 4), frequency = 6000.0)
        assertTrue("A high-only tone must not trigger missing-bass makeup", (0..2).all { high.gains[it] < .15 })
        val lowRate = AdaptiveDynamicEq(4000, 1)
        tone(lowRate, enabled.copy(strength = 4), rate = 4000, frequency = 125.0)
        for (band in 0..9) if (AdaptiveAudioSettings.FREQUENCIES[band] >= 4000 * .45) {
            assertEquals(0.0, lowRate.gains[band], 0.0)
        }
    }

    @Test fun disablingDynamicsDecaysItsCorrectionAndCanEnableAgain() {
        val eq = AdaptiveDynamicEq(8000, 1)
        tone(eq, enabled, rate = 8000)
        val before = eq.gains.sumOf { abs(it) }
        assertTrue(before > .1)
        tone(eq, enabled.copy(dynamicEq = false), rate = 8000, seconds = 5.0)
        assertTrue(eq.gains.sumOf { abs(it) } < before * .02)
        tone(eq, enabled, rate = 8000, seconds = 3.0)
        assertTrue(eq.gains.sumOf { abs(it) } > before * .1)
    }

    @Test fun fastResponseAndStrengthControlsAreEffective() {
        val slow = AdaptiveDynamicEq(8000, 1)
        val fast = AdaptiveDynamicEq(8000, 1)
        tone(slow, enabled.copy(speed = 0), rate = 8000, seconds = .2)
        tone(fast, enabled.copy(speed = 4), rate = 8000, seconds = .2)
        assertTrue(fast.gains.sumOf { abs(it) } > slow.gains.sumOf { abs(it) })
        val weak = AdaptiveDynamicEq(8000, 1)
        val strong = AdaptiveDynamicEq(8000, 1)
        tone(weak, enabled.copy(strength = 0), rate = 8000)
        tone(strong, enabled.copy(strength = 4), rate = 8000)
        assertTrue((0..2).sumOf { strong.gains[it] } > (0..2).sumOf { weak.gains[it] })
    }

    @Test fun transientHeadroomAndLiveControlChangesRemainFiniteAndBounded() {
        val eq = AdaptiveDynamicEq(8000, 8)
        val frame = DoubleArray(8)
        var previous = DoubleArray(10)
        val maximumStep = DoubleArray(10)
        repeat(8000 * 8) { index ->
            for (channel in frame.indices) frame[channel] = if (index % 1600 < 60) 12.0 else
                sin(2 * PI * (60 + channel * 100) * index / 8000) * .4
            val settings = enabled.copy(strength = if (index < 32000) 4 else 1,
                speed = if (index < 40000) 4 else 0, curve = DynamicEqCurve.BRIGHT)
            eq.process(frame, settings)
            if ((index + 1) % 1600 == 0) {
                for (band in 0..9) {
                    assertTrue(eq.gains[band].isFinite() && eq.gains[band] in -8.0..8.0)
                    maximumStep[band] = max(maximumStep[band], abs(eq.gains[band] - previous[band]))
                }
                previous = eq.gains.copyOf()
            }
        }
        assertTrue("Transient restraint prevents a single analysis block from jumping several dB", maximumStep.all { it < 1.5 })
    }
}
