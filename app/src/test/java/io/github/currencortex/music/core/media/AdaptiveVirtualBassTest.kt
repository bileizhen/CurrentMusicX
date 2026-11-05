package io.github.currencortex.music.core.media

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AdaptiveVirtualBassTest {
    private val rate = 44100
    private fun tone(amplitude: Double, frequency: Double = 60.0, strength: Int = 100): DoubleArray {
        val processor = AdaptiveVirtualBass(rate, 1)
        val frame = DoubleArray(1)
        return DoubleArray(rate * 2) { i ->
            frame[0] = amplitude * sin(2 * PI * frequency * i / rate)
            processor.process(frame, enabled = true, strength = strength)
            assertTrue(frame[0].isFinite())
            frame[0]
        }
    }
    private fun amplitude(samples: DoubleArray, frequency: Double): Double {
        var real = 0.0
        var imaginary = 0.0
        for (i in rate until samples.size) {
            real += samples[i] * cos(2 * PI * frequency * i / rate)
            imaginary += samples[i] * sin(2 * PI * frequency * i / rate)
        }
        return 2 * hypot(real, imaginary) / (samples.size - rate)
    }

    @Test fun silenceStaysSilentAndZeroStrengthIsDry() {
        val processor = AdaptiveVirtualBass(rate, 8)
        val frame = DoubleArray(8)
        repeat(rate) {
            processor.process(frame, enabled = true, strength = 100)
            assertArrayEquals(DoubleArray(8), frame, 0.0)
        }
        val dryProcessor = AdaptiveVirtualBass(rate, 8)
        repeat(1000) { i ->
            val dry = DoubleArray(8) { sin((i + it) * .05) * .3 }
            dry.copyInto(frame)
            dryProcessor.process(frame, enabled = true, strength = 0)
            assertArrayEquals(dry, frame, 0.0)
        }
    }

    @Test fun bassProducesSecondAndThirdHarmonicsAcrossInputLevels() {
        for (input in listOf(.03, .1, .3)) {
            val output = tone(input)
            val fundamental = amplitude(output, 60.0)
            val second = amplitude(output, 120.0)
            val third = amplitude(output, 180.0)
            val peak = output.drop(rate).maxOf(::abs)
            println("VIRTUAL_BASS input=$input fundamental=$fundamental second=$second third=$third peak=$peak")
            assertTrue("Even harmonics must be generated", second > input * .005)
            assertTrue("Odd harmonics must also be generated", third > input * .00005)
            assertTrue("Typical bass should need only brief final peak protection", peak <= 1.1)
            assertTrue("DC must be rejected", abs(output.drop(rate).average()) < 1e-5)
        }
    }

    @Test fun middleAndHighFrequenciesArePreserved() {
        for (frequency in listOf(2000.0, 8000.0)) {
            val output = tone(.2, frequency)
            assertEquals("The bass effect must preserve the upper spectrum", .2, amplitude(output, frequency), .01)
        }
    }

    @Test fun lowAndDefaultStrengthAvoidDeepBassCancellation() {
        for (input in listOf(.1, .3)) for (frequency in listOf(40.0, 60.0, 100.0)) {
            for (strength in listOf(10, 20, 30, 50)) {
                val output = tone(input, frequency, strength)
                assertTrue("$frequency Hz at $strength% must not lose more than 1.5 dB",
                    amplitude(output, frequency) >= input * 10.0.pow(-1.5 / 20))
            }
            val full = tone(input, frequency, strength = 100)
            assertTrue("Full strength must not depend on continuous heavy limiting", full.drop(rate).maxOf(::abs) <= 1.1)
        }
    }

    @Test fun channelHistoryDoesNotLeakToOtherSurroundChannels() {
        for (activeChannel in 0 until 6) {
            val processor = AdaptiveVirtualBass(rate, 6)
            val frame = DoubleArray(6)
            var power = 0.0
            repeat(rate / 2) { i ->
                frame.fill(0.0)
                frame[activeChannel] = .1 * sin(2 * PI * 60 * i / rate)
                processor.process(frame, enabled = true, strength = 100)
                for (channel in frame.indices) if (channel != activeChannel) assertEquals(0.0, frame[channel], 0.0)
                power += frame[activeChannel] * frame[activeChannel]
            }
            assertTrue(power > 1.0)
        }
    }

    @Test fun switchesAndStrengthChangesAreSmoothAndSettleToDry() {
        val processor = AdaptiveVirtualBass(rate, 1)
        val frame = doubleArrayOf(.1)
        var previous = .1
        var worstStep = 0.0
        repeat(rate * 3) { i ->
            frame[0] = .1
            processor.process(frame, enabled = i < rate * 2, strength = if (i < rate) 100 else 0)
            worstStep = max(worstStep, abs(frame[0] - previous))
            previous = frame[0]
        }
        assertTrue("Switching must not create a step", worstStep < .02)
        assertEquals(.1, frame[0], 0.0)
    }

    @Test fun invalidSamplesDoNotPoisonFilterHistory() {
        for (sampleRate in listOf(4000, 44100, 96000, 384000)) {
            val processor = AdaptiveVirtualBass(sampleRate, 2)
            val frame = DoubleArray(2)
            for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.MAX_VALUE, -Double.MAX_VALUE, 65.0, -65.0)) {
                // Establish actual history, then verify a bad sample clears it rather than
                // merely returning a finite value while leaving an enormous decaying tail.
                repeat(sampleRate / 20) { i ->
                    frame[0] = .1 * sin(2 * PI * 60 * i / sampleRate); frame[1] = 0.0
                    processor.process(frame, enabled = true, strength = 100)
                }
                frame[0] = bad; frame[1] = 0.0
                processor.process(frame, enabled = true, strength = 100)
                assertArrayEquals(doubleArrayOf(0.0, 0.0), frame, 0.0)
                repeat(sampleRate / 20) {
                    frame.fill(0.0)
                    processor.process(frame, enabled = true, strength = 100)
                    assertArrayEquals("Corrupt history must not reappear", doubleArrayOf(0.0, 0.0), frame, 0.0)
                }
            }
            var recoveredPower = 0.0
            repeat(sampleRate / 10) { i ->
                frame[0] = sin(i * .1) * .1; frame[1] = 0.0
                processor.process(frame, enabled = true, strength = 100)
                assertTrue(frame.all { it.isFinite() && abs(it) < 1.1 })
                recoveredPower += frame[0] * frame[0]
            }
            assertTrue("Normal audio must resume after rejecting bad data", recoveredPower > .01)
        }
    }
}
