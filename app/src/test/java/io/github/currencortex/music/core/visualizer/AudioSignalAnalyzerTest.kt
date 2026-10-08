package io.github.currencortex.music.core.visualizer

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AudioSignalAnalyzerTest {
    private fun packet(time: Long, bin: Int = 0, value: Int = 0, rate: Int = 48000): AudioCapturePacket {
        val fft = ByteArray(1024)
        if (bin > 0) fft[if (bin == 512) 1 else bin * 2] = value.toByte()
        val wave = ByteArray(1024) { if (value == 0) 128.toByte() else (128 + if (it % 2 == 0) 64 else -64).toByte() }
        return AudioCapturePacket(time, rate, fft, wave)
    }
    @Test fun silenceHasNoEnergyOrBeats() {
        val analyzer = AudioSignalAnalyzer()
        repeat(60) {
            val f = analyzer.analyze(packet(1_000_000_000L + it * 33_333_333L))
            assertEquals(0f, f.rms, 0f); assertEquals(0f, f.bass, 0f)
            assertEquals(0f, f.beatPulse, 0f); assertFalse(f.kick); assertFalse(f.transient)
            assertTrue(f.spectrum.all { level -> level == 0f })
        }
    }
    @Test fun signedComplexFftMapsFrequency() {
        val p = packet(1_000_000_000L, 20, -64)
        p.fft[41] = -64
        val f = AudioSignalAnalyzer().analyze(p)
        assertEquals(937.5f, f.peakFrequencyHz, .01f)
        assertTrue(f.mid > .1f); assertEquals(0f, f.bass, 0f); assertEquals(0f, f.treble, 0f)
        assertTrue(f.spectrum.any { it > .1f })
    }
    @Test fun unsignedWaveformRmsAndBounds() {
        val f = AudioSignalAnalyzer().analyze(packet(1_000_000_000L, 2, 100))
        assertEquals(.5f * (1 - exp(-(1f / 30) / .025f)), f.rms, .0001f)
        assertEquals(128, f.waveform.size)
        assertTrue(f.waveform.all { abs(it) == .5f })
        assertTrue(f.spectrum.all { it in 0f..1f })
    }
    @Test fun bandsUseActualSamplingRate() {
        assertEquals(468.75f, AudioSignalAnalyzer().analyze(packet(1_000_000_000L, 20, 90, 24000)).peakFrequencyHz, .01f)
        val high = AudioSignalAnalyzer().analyze(packet(1_000_000_000L, 150, 90))
        assertTrue(high.treble > .1f); assertEquals(0f, high.mid, 0f)
    }
    @Test fun dcDoesNotBecomeBassAndNyquistIsDecoded() {
        val p = packet(1_000_000_000L)
        p.fft[0] = 127
        assertEquals(0f, AudioSignalAnalyzer().analyze(p).bass, 0f)
        p.fft[1] = -100
        assertEquals(24000f, AudioSignalAnalyzer().analyze(p).peakFrequencyHz, .01f)
    }
    @Test fun kickIsAdaptiveAndRefractoryAndPulseDecays() {
        val analyzer = AudioSignalAnalyzer()
        repeat(20) { analyzer.analyze(packet(1_000_000_000L + it * 33_333_333L, 2, 4)) }
        val hit = analyzer.analyze(packet(1_700_000_000L, 2, 100))
        assertTrue(hit.kick); assertTrue(hit.transient); assertEquals(1f, hit.beatPulse, 0f)
        val release = analyzer.analyze(packet(1_733_333_333L, 2, 4))
        assertTrue(release.beatPulse in 0f.. .99f)
        assertFalse(analyzer.analyze(packet(1_766_666_666L, 2, 110)).kick)
        repeat(15) { analyzer.analyze(packet(1_800_000_000L + it * 33_333_333L, 2, 4)) }
        assertTrue(analyzer.analyze(packet(2_400_000_000L, 2, 110)).kick)
    }
    @Test fun steadyBassDoesNotRepeatedlyTrigger() {
        val analyzer = AudioSignalAnalyzer()
        repeat(120) { assertFalse(analyzer.analyze(packet(1_000_000_000L + it * 33_333_333L, 2, 100)).kick) }
    }
    @Test fun smoothingUsesElapsedTime() {
        var many = 0f
        repeat(12) { many = AudioSignalAnalyzer.envelope(many, 1f, 1f / 120) }
        assertEquals(AudioSignalAnalyzer.envelope(0f, 1f, .1f), many, .00001f)
        assertTrue(AudioSignalAnalyzer.envelope(1f, 0f, .1f) > .5f)
    }
    @Test fun resetAndFormatChangeRemoveOldEnergy() {
        val analyzer = AudioSignalAnalyzer()
        analyzer.analyze(packet(1_000_000_000L, 2, 100))
        analyzer.reset()
        assertEquals(0f, analyzer.analyze(packet(1_000_000_000L)).bass, 0f)
        analyzer.analyze(packet(1_100_000_000L, 2, 100))
        assertEquals(0f, analyzer.analyze(packet(1_200_000_000L, rate = 44100)).bass, 0f)
    }
    @Test(expected = IllegalArgumentException::class) fun malformedPacketRejected() {
        AudioSignalAnalyzer().analyze(AudioCapturePacket(1, 48000, ByteArray(17), ByteArray(17)))
    }
    @Test(expected = IllegalArgumentException::class) fun nonIncreasingTimestampRejected() {
        val analyzer = AudioSignalAnalyzer()
        analyzer.analyze(packet(1_000_000_000L)); analyzer.analyze(packet(1_000_000_000L))
    }
}
