package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.settings.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AdaptiveAudioDspTest {
    private val rate = 48000
    private fun tone(settings: AdaptiveAudioSettings, frequency: Double, amplitude: Double = .3,
        seconds: Int = 2): Pair<DoubleArray, AdaptiveAudioDsp> {
        val dsp = AdaptiveAudioDsp(rate, 2)
        val output = DoubleArray(rate * seconds)
        val frame = DoubleArray(2)
        for (i in output.indices) {
            frame[0] = sin(2 * PI * frequency * i / rate) * amplitude
            frame[1] = -frame[0]
            dsp.process(frame, settings)
            assertTrue(frame.all { it.isFinite() && abs(it) <= 1.000001 })
            output[i] = frame[0]
        }
        return output to dsp
    }
    private fun magnitude(samples: DoubleArray, frequency: Double): Double {
        var re = 0.0; var im = 0.0
        for (i in rate until samples.size) {
            re += samples[i] * cos(2 * PI * frequency * i / rate)
            im += samples[i] * sin(2 * PI * frequency * i / rate)
        }
        return hypot(re, im) / (samples.size - rate)
    }
    @Test fun disabledStartsAsExactPassThrough() {
        val dsp = AdaptiveAudioDsp(rate, 2)
        val frame = doubleArrayOf(.125, -.75)
        dsp.process(frame, AdaptiveAudioSettings())
        assertArrayEquals(doubleArrayOf(.125, -.75), frame, 0.0)
    }
    @Test fun virtualBassAddsMultipleHarmonicsAndRejectsDc() {
        val base = AdaptiveAudioSettings(enabled = true, dynamicEq = false)
        val dry = tone(base, 60.0).first
        val wet = tone(base.copy(virtualBass = true, bassStrength = 100), 60.0).first
        assertTrue(magnitude(dry, 120.0) < 1e-5)
        assertTrue(magnitude(wet, 120.0) > .005)
        assertTrue(magnitude(wet, 180.0) > .001)
        assertTrue(abs(wet.takeLast(rate).average()) < 1e-4)
    }
    @Test fun manualBandRaisesItsOwnFrequencyAndKeepsStereoIndependent() {
        val base = AdaptiveAudioSettings(enabled = true, dynamicEq = false)
        val boosted = base.copy(gains = List(10) { if (it == 5) 6f else 0f })
        val dry = magnitude(tone(base, 1000.0).first, 1000.0)
        val wet = magnitude(tone(boosted, 1000.0).first, 1000.0)
        assertEquals(2.0, wet / dry, .06)
        val dsp = AdaptiveAudioDsp(rate, 2)
        val frame = DoubleArray(2)
        repeat(rate) { i ->
            frame[0] = sin(2 * PI * i * 1000 / rate) * .2; frame[1] = 0.0
            dsp.process(frame, boosted)
            assertEquals("Left channel must not leak into right", 0.0, frame[1], 0.0)
        }
    }
    @Test fun dynamicGainsRespondToAudioAndRemainBounded() {
        val settings = AdaptiveAudioSettings(enabled = true, strength = 4)
        val low = tone(settings, 125.0, seconds = 6).second.gains
        val high = tone(settings, 4000.0, seconds = 6).second.gains
        assertTrue(abs(low[2] - high[2]) > .5)
        assertTrue((low + high).all { it in -9f..9f })
    }
    @Test fun flatEnabledChainPreservesLevelWithoutSystemGainCompensation() {
        val settings = AdaptiveAudioSettings(enabled = true, dynamicEq = false)
        val samples = tone(settings, 1000.0).first
        assertEquals(.15, magnitude(samples, 1000.0), 1e-5)
    }
    @Test fun disablingDynamicEqPromptlyReturnsToTheDisplayedManualBaseline() {
        val dsp = AdaptiveAudioDsp(8000, 1)
        val manual = List(10) { if (it == 2) 2f else 0f }
        val settings = AdaptiveAudioSettings(enabled = true, gains = manual)
        val frame = DoubleArray(1)
        repeat(8000 * 6) { i ->
            frame[0] = sin(2 * PI * 125 * i / 8000) * .2
            dsp.process(frame, settings)
        }
        assertTrue(dsp.gains.indices.any { abs(dsp.gains[it] - manual[it]) > .1f })
        repeat(1600) { i ->
            frame[0] = sin(2 * PI * 125 * i / 8000) * .2
            dsp.process(frame, settings.copy(dynamicEq = false))
        }
        dsp.gains.forEachIndexed { band, gain -> assertEquals(manual[band], gain, .002f) }
    }
    @Test fun peakProtectionKeepsSurroundBalanceAndDoesNotClip() {
        val settings = AdaptiveAudioSettings(enabled = true, dynamicEq = false, gains = List(10) { 6f })
        val dsp = AdaptiveAudioDsp(rate, 6)
        val frame = DoubleArray(6)
        var peak = 0.0
        repeat(rate) { i ->
            val input = sin(2 * PI * 1000 * i / rate) * .95
            for (channel in frame.indices) frame[channel] = input / (channel + 1)
            dsp.process(frame, settings)
            peak = max(peak, abs(frame[0]))
            for (channel in frame.indices) {
                assertTrue(frame[channel].isFinite() && abs(frame[channel]) <= 1.0)
                assertEquals(frame[0] / (channel + 1), frame[channel], 1e-8)
            }
        }
        assertTrue(peak > .8)
    }
    @Test fun surroundChannelsKeepIndependentFilterAndBassHistory() {
        val settings = AdaptiveAudioSettings(enabled = true, dynamicEq = false,
            gains = List(10) { 6f }, virtualBass = true, bassStrength = 100)
        for (channels in listOf(6, 8)) for (activeChannel in 0 until channels) {
            val dsp = AdaptiveAudioDsp(44100, channels)
            val frame = DoubleArray(channels)
            var outputPower = 0.0
            repeat(4410) { i ->
                frame.fill(0.0)
                frame[activeChannel] = sin(2 * PI * 60 * i / 44100) * .25
                dsp.process(frame, settings)
                for (channel in 0 until channels) {
                    assertTrue(frame[channel].isFinite() && abs(frame[channel]) <= 1.0)
                    if (channel != activeChannel) assertEquals("Channel $activeChannel leaked to $channel", 0.0, frame[channel], 0.0)
                }
                outputPower += frame[activeChannel] * frame[activeChannel]
            }
            assertTrue("Every input channel must be processed", outputPower > 1.0)
        }
    }
    @Test fun silenceDoesNotCreateSoundOrMakeupGain() {
        val dsp = AdaptiveAudioDsp(rate, 2)
        val frame = DoubleArray(2)
        repeat(rate) {
            dsp.process(frame, AdaptiveAudioSettings(enabled = true, virtualBass = true, strength = 4))
            assertArrayEquals(doubleArrayOf(0.0, 0.0), frame, 0.0)
        }
        assertTrue(dsp.gains.all { it == 0f })
    }
    @Test fun maximumSettingsRemainFiniteAtSupportedRates() {
        for (sampleRate in listOf(8000, 44100, 48000, 96000, 192000)) {
            val dsp = AdaptiveAudioDsp(sampleRate, 2)
            val frame = DoubleArray(2)
            val settings = AdaptiveAudioSettings(enabled = true, gains = List(10) { 6f }, strength = 4,
                speed = 4, virtualBass = true, bassStrength = 100)
            repeat(sampleRate) { i ->
                frame[0] = if (i % 2 == 0) 1.0 else -1.0; frame[1] = sin(i * .1)
                dsp.process(frame, settings)
                assertTrue(frame.all { it.isFinite() && abs(it) <= 1.000001 })
            }
            AdaptiveAudioSettings.FREQUENCIES.forEachIndexed { i, f ->
                if (f >= sampleRate * .45) assertEquals(0f, dsp.gains[i], 0f)
            }
        }
    }
    @Test fun disableRampsBackToDryAndCanSettleToByteBypass() {
        val dsp = AdaptiveAudioDsp(rate, 1)
        val frame = DoubleArray(1)
        repeat(rate) { frame[0] = .25; dsp.process(frame, AdaptiveAudioSettings(enabled = true)) }
        var previous = frame[0]
        repeat(rate / 5) {
            frame[0] = .25; dsp.process(frame, AdaptiveAudioSettings())
            assertTrue(abs(frame[0] - previous) < .001); previous = frame[0]
        }
        assertTrue(dsp.settledBypass)
        assertEquals(.25, frame[0], 1e-8)
    }
    @Test fun malformedPreferencesAreNormalizedWithoutNan() {
        val s = AdaptiveAudioSettings(gains = listOf(Float.NaN, 90f), strength = 99, speed = -1,
            hapticStrength = -5, bassStrength = 200).normalized()
        assertEquals(10, s.gains.size); assertEquals(0f, s.gains[0]); assertEquals(6f, s.gains[1])
        assertEquals(4, s.strength); assertEquals(0, s.speed)
        assertEquals(0, s.hapticStrength); assertEquals(100, s.bassStrength)
    }
    @Test fun hapticsWaitForPlaybackClockAndNeverReplayExpiredOrOtherTracks() {
        val timeline = MusicHapticTimeline()
        timeline.offer(MusicHapticTimeline.Frame(500000, "A", .2f, .3f))
        assertNull(timeline.poll(100000, "A"))
        assertNull(timeline.poll(500000, "B"))
        assertNotNull(timeline.poll(500000, "A"))
        assertNull(timeline.poll(500000, "A"))
        timeline.offer(MusicHapticTimeline.Frame(500000, "A", .2f, .3f))
        assertNull(timeline.poll(900000, "A"))
        timeline.offer(MusicHapticTimeline.Frame(950000, "A", .2f, .3f))
        timeline.clear()
        assertNull(timeline.poll(950000, "A"))
    }
}
