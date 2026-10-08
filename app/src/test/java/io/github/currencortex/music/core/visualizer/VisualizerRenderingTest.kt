package io.github.currencortex.music.core.visualizer

import io.github.currencortex.music.data.visualizer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisualizerRenderingTest {
    private val start = 1_000_000_000L
    private fun frame(time: Long = start, energy: Float = .8f, kick: Boolean = false, generation: Long = 1) =
        AudioAnalysisFrame(time, 48000, 1024, rms = energy, bass = energy, mid = energy, treble = energy,
            spectrum = List(48) { energy }, kick = kick, generation = generation,
            kickSequence = if (kick) 1 else 0, kickTimestampNanos = if (kick) time else 0)

    @Test fun pacingAtAllRatesUsesVsyncDivisors() {
        for (fps in listOf(30, 60, 90, 120)) {
            val clockHz = if (fps == 90) 90 else 120
            val pacer = VisualizerFramePacer()
            var frames = 0
            for (i in 0 until clockHz * 2) if (pacer.select(start + i * 1_000_000_000L / clockHz, fps)) frames++
            assertEquals("fps=$fps", fps * 2, frames)
        }
    }
    @Test fun firstDuplicateBackwardsAndInvalidFrameTimesAreSafe() {
        val pacer = VisualizerFramePacer()
        assertFalse(pacer.select(0, 120)); assertFalse(pacer.select(-1, 120))
        assertTrue(pacer.select(start, 120)); assertEquals(0f, pacer.deltaSeconds, 0f)
        assertFalse(pacer.select(start, 120)); assertFalse(pacer.select(start - 1, 120))
        assertTrue(pacer.select(start + 8_333_333, 120)); assertEquals(1f / 120, pacer.deltaSeconds, .00001f)
    }
    @Test fun resumeAndLongFramesNeverIntegrateSeconds() {
        val pacer = VisualizerFramePacer()
        pacer.select(start, 60); pacer.select(start + 2_000_000_000, 60)
        assertEquals(0f, pacer.deltaSeconds, 0f)
        pacer.reset(); assertTrue(pacer.select(start + 3_000_000_000, 60)); assertEquals(0f, pacer.deltaSeconds, 0f)
    }
    @Test fun sameElapsedTimeHasSameEnvelopeAcrossAllRates() {
        val values = listOf(30, 60, 90, 120).map { fps ->
            val interpolator = VisualizerFrameInterpolator(); interpolator.accept(frame())
            repeat(fps / 10) { i -> interpolator.step(start + (i + 1) * 100_000_000L / (fps / 10), 1f / fps) }
            interpolator.output.bass
        }
        values.forEach { assertEquals(values.first(), it, .001f) }
    }
    @Test fun lowRateSamplesMoveSmoothlyAt120AndUseReusedBuffers() {
        val interpolator = VisualizerFrameInterpolator(); interpolator.accept(frame(energy = 0f))
        interpolator.step(start, 0f)
        val buffer = interpolator.output.spectrum
        interpolator.accept(frame(start + 52_000_000))
        var previous = 0f
        repeat(6) { i ->
            val output = interpolator.step(start + 52_000_000 + i * 8_333_333L, 1f / 120)
            assertSame(buffer, output.spectrum); assertTrue(output.bass > previous); assertTrue(output.bass < .8f)
            previous = output.bass
        }
    }
    @Test fun missingAudioDecaysWithoutInventingKicks() {
        val interpolator = VisualizerFrameInterpolator(); interpolator.accept(frame())
        repeat(240) { i -> interpolator.step(start + i * 8_333_333L, 1f / 120) }
        assertTrue(interpolator.output.bass < .001f)
        assertTrue(interpolator.output.spectrum.all { it < .001f }); assertEquals(0L, interpolator.output.kickCount)
    }
    @Test fun rawSpectrumBypassesEnvelopesWhileWaveformInterpolates() {
        val interpolator = VisualizerFrameInterpolator()
        interpolator.accept(frame(energy = .2f).copy(rawSpectrum = List(48) { .9f }, waveform = List(128) { .8f }))
        val output = interpolator.step(start, 1f / 120)
        assertEquals(.9f, output.rawSpectrum[0], 0f)
        assertTrue(output.spectrum[0] in 0f.. .2f)
        assertTrue(output.waveform[0] > 0f && output.waveform[0] < .8f)
    }
    @Test fun kickIsImmediateAndNeverRepeatedByDrawing() {
        val interpolator = VisualizerFrameInterpolator(); interpolator.accept(frame(kick = true))
        val first = interpolator.step(start, 0f)
        assertEquals(1f, first.pulse, 0f); assertEquals(1L, first.kickCount)
        repeat(5) { i -> interpolator.step(start + (i + 1) * 8_333_333, 1f / 120) }
        assertEquals(1L, first.kickCount); assertTrue(first.pulse in .1f.. .99f)
    }
    @Test fun eventSequenceSurvivesConflatedAudioFrames() {
        val interpolator = VisualizerFrameInterpolator()
        interpolator.accept(frame(kick = true).copy(kick = false, timestampNanos = start + 52_000_000))
        interpolator.step(start + 52_000_000, 0f)
        assertEquals(1L, interpolator.output.kickCount); assertTrue(interpolator.output.pulse > .7f)
        interpolator.accept(frame(start + 100_000_000, kick = true).copy(kickSequence = 2))
        interpolator.step(start + 100_000_000, 1f / 120)
        assertEquals(2L, interpolator.output.kickCount); assertEquals(1f, interpolator.output.pulse, .001f)
    }
    @Test fun staleEventDoesNotTriggerAfterConsumerRestart() {
        val interpolator = VisualizerFrameInterpolator()
        interpolator.accept(frame(start + 2_000_000_000, kick = true).copy(kickTimestampNanos = start))
        interpolator.step(start + 2_000_000_000, 0f)
        assertEquals(0L, interpolator.output.kickCount); assertEquals(0f, interpolator.output.pulse, 0f)
    }
    @Test fun generationAndPauseClearOldTargets() {
        val interpolator = VisualizerFrameInterpolator(); interpolator.accept(frame(kick = true)); interpolator.step(start, .02f)
        interpolator.accept(frame(start + 52_000_000, 0f, generation = 2)); interpolator.step(start + 52_000_000, 0f)
        assertEquals(0f, interpolator.output.pulse, 0f); assertEquals(0f, interpolator.output.bass, 0f)
        interpolator.accept(frame(start + 100_000_000, generation = 2)); interpolator.step(start + 100_000_000, .02f)
        interpolator.pause()
        repeat(100) { i -> interpolator.step(start + 120_000_000 + i * 16_666_666L, 1f / 60) }
        assertTrue(interpolator.output.bass < .001f)
    }
    @Test fun invalidSignalsCannotPoisonRenderState() {
        val interpolator = VisualizerFrameInterpolator()
        interpolator.accept(frame(energy = Float.NaN).copy(mid = Float.POSITIVE_INFINITY, treble = -100f,
            spectrum = listOf(Float.NaN, Float.POSITIVE_INFINITY, -2f, 20f)))
        interpolator.step(start, .01f)
        assertEquals(0f, interpolator.output.bass, 0f); assertEquals(0f, interpolator.output.mid, 0f)
        assertTrue(interpolator.output.spectrum.all { it.isFinite() && it in 0f..1f })
    }
    @Test fun interpolatorIgnoresDuplicateFramesAndTimes() {
        val interpolator = VisualizerFrameInterpolator(); interpolator.accept(frame()); interpolator.step(start, .01f)
        val first = interpolator.output.bass
        interpolator.accept(frame(energy = 0f)); interpolator.step(start, .05f)
        assertEquals(first, interpolator.output.bass, 0f)
    }
    @Test fun transientIsSingleAndFasterThanKickDecay() {
        val interpolator = VisualizerFrameInterpolator()
        interpolator.accept(frame(kick = true).copy(transient = true, transientSequence = 1, transientTimestampNanos = start))
        interpolator.step(start, 0f); interpolator.step(start + 33_333_333, 1f / 30)
        assertEquals(1L, interpolator.output.transientCount)
        assertTrue(interpolator.output.transientPulse < interpolator.output.pulse)
    }
    @Test fun renderLoopStopsWithoutFurtherUpdates() = runTest {
        val events = Channel<Long>(Channel.UNLIMITED)
        val clock = VisualizerFrameClock { events.receive() }
        var draws = 0
        val job = launch { VisualizerRenderLoop(clock).run({ 60 }) { _, _ -> draws++; true } }
        events.send(start); runCurrent(); assertEquals(1, draws)
        job.cancelAndJoin(); events.send(start + 16_666_666); runCurrent(); assertEquals(1, draws)
    }
    @Test fun renderLoopCanFinishFadeAndReturn() = runTest {
        var time = start
        val clock = VisualizerFrameClock { time.also { time += 16_666_666 } }
        var frames = 0
        VisualizerRenderLoop(clock).run({ 60 }) { _, _ -> ++frames < 4 }
        assertEquals(4, frames)
    }
}
