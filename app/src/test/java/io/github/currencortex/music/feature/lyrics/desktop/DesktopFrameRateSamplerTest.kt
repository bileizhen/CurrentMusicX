package io.github.currencortex.music.feature.lyrics.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopFrameRateSamplerTest {
    @Test fun calculatesActualTimestampCadenceRatherThanAnAdvertisedRate() {
        val sampler = DesktopFrameRateSampler()
        repeat(150) { sampler.record(100_000_000L + it * 8_333_333L) }
        assertEquals(120f, sampler.rate!!, .1f)
        repeat(150) { sampler.record(1_400_000_000L + it * 16_666_667L) }
        assertEquals(60f, sampler.rate!!, .1f)
    }
    @Test fun resetAndInterruptedSamplingDoNotRetainAStaleRate() {
        val sampler = DesktopFrameRateSampler()
        repeat(70) { sampler.record(100_000_000L + it * 16_666_667L) }
        assertNotNull(sampler.rate)
        sampler.record(0)
        assertNull(sampler.rate)
        repeat(70) { sampler.record(100_000_000L + it * 16_666_667L) }
        assertTrue("Interrupted sampling must clear the published label", sampler.record(5_000_000_000L))
        assertNull(sampler.rate)
        assertTrue(sampler.record(4_000_000_000L))
        assertNull(sampler.rate)
    }
}
