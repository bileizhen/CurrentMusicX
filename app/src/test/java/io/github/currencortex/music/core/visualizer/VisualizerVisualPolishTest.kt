package io.github.currencortex.music.core.visualizer

import io.github.currencortex.music.data.visualizer.VisualizerQuality
import org.junit.Assert.*
import org.junit.Test

class VisualizerVisualPolishTest {
    @Test fun loudBandsRetainContrastInsteadOfClippingToEqualLengths() {
        val bands = floatArrayOf(.04f, .1f, .2f, .4f, .7f)
        val mapped = bands.indices.map { VisualizerEffectState.spectrum(bands, it.toFloat() / bands.lastIndex, 1f) }
        for (i in 1 until mapped.size) assertTrue(mapped[i] > mapped[i - 1])
        assertTrue(mapped.last() < 1f)
        assertTrue(mapped[2] - mapped[1] > .1f)
        assertArrayEquals(floatArrayOf(.04f, .1f, .2f, .4f, .7f), bands, 0f)
    }
    @Test fun silenceAndZeroIntensityCannotCreateSpectrumEnergy() {
        assertEquals(0f, VisualizerEffectState.spectrum(FloatArray(48), .5f, 3f), 0f)
        assertEquals(0f, VisualizerEffectState.spectrum(floatArrayOf(1f), 0f, 0f), 0f)
        assertEquals(0f, VisualizerEffectState.spectrum(floatArrayOf(1f), 0f, Float.NaN), 0f)
    }
    private fun spring(hz: Int, reduced: Boolean = false): FloatArray {
        val s = VisualizerEffectState()
        val f = VisualizerInterpolatedFrame().apply { pulse = 1f; kickCount = 1 }
        val c = VisualizerEffectConfig(enabled = true, presetId = VisualizerPreset.BASS_IMPACT, motionIntensity = 1f, reduceMotion = reduced)
        val result = FloatArray(hz)
        repeat(hz) { s.step(f, c, VisualizerQuality.ULTRA, 1_000_000_000L + it * 1_000_000_000L / hz, 1f / hz); result[it] = s.coverScale }
        return result
    }
    @Test fun oneKickProducesBoundedRiseAndReturnsEvenIfPulseRemainsHigh() {
        val s = spring(120)
        assertTrue(s.maxOrNull()!! > 1.025f)
        assertTrue(s.all { it in 1f..1.12f })
        assertTrue(s.last() < 1.001f)
    }
    @Test fun springMatchesAcrossActualDeltaTimesAndReducedMotionAttenuates() {
        assertEquals(spring(30).maxOrNull()!!, spring(120).maxOrNull()!!, .005f)
        assertTrue(spring(120, true).maxOrNull()!! < spring(120).maxOrNull()!!)
    }
    @Test fun resetReleasesSpringMomentum() {
        val s = VisualizerEffectState()
        val f = VisualizerInterpolatedFrame().apply { kickCount = 1; pulse = 1f }
        val c = VisualizerEffectConfig(enabled = true)
        s.step(f, c, VisualizerQuality.ULTRA, 1, .02f); s.reset()
        s.step(VisualizerInterpolatedFrame(), c, VisualizerQuality.ULTRA, 2, .02f)
        assertEquals(1f, s.coverScale, 0f)
    }
}
