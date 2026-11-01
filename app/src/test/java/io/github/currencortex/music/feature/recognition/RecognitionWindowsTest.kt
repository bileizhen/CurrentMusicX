package io.github.currencortex.music.feature.recognition

import org.junit.Assert.*
import org.junit.Test

class RecognitionWindowsTest {
    @Test fun crossedMilestonesUseExactCumulativePrefixesWithoutDuplicates() {
        val pcm = ShortArray(120_000) { (it % 20_000).toShort() }
        val emitted = mutableListOf<ShortArray>()
        val windows = RecognitionWindows()
        for (available in listOf(0, 15_999, 16_113, 16_113, 24_128, 74_000, 120_000, 120_000)) {
            windows.emitAvailable(available, { pcm.copyOf(it) }, emitted::add)
        }
        assertEquals(listOf(16_000, 24_000, 48_000, 72_000, 80_000, 96_000, 120_000), emitted.map { it.size })
        emitted.forEach { assertArrayEquals(pcm.copyOf(it.size), it) }
        pcm.fill(0)
        assertEquals(15_999.toShort(), emitted.first().last())
        emitted.first().fill(0)
        assertEquals(15_999.toShort(), emitted[1][15_999])
    }

    @Test fun cancellingBeforeTwoSecondsDoesNotEmitAnInvalidQuery() {
        val windows = RecognitionWindows()
        windows.emitAvailable(15_999, { error("A short clip must not be copied") }, { error("A short clip must not be emitted") })
    }

    @Test fun callbackFailureClearsTheUnclaimedCopy() {
        val pcm = ShortArray(16_000) { 12 }
        val failure = runCatching {
            RecognitionWindows().emitAvailable(pcm.size, { pcm }, { throw IllegalStateException("consumer stopped") })
        }.exceptionOrNull()
        assertEquals("consumer stopped", failure?.message)
        assertTrue(pcm.all { it == 0.toShort() })
    }

    @Test fun mixResamplerSupportsFifteenSecondsAndBoundedIndependentPrefixes() {
        for (rate in listOf(44_100, 48_000)) {
            val resampler = Unsigned8PcmResampler(rate)
            resampler.append(ByteArray(rate * 16) { 160.toByte() })
            assertTrue(resampler.full)
            assertEquals(120_000, resampler.size)
            val firstQuery = resampler.copyPcm(16_000)
            assertEquals(16_000, firstQuery.size)
            assertTrue(firstQuery.all { it == 8192.toShort() })
            assertTrue(runCatching { resampler.copyPcm(120_001) }.isFailure)
            resampler.clear()
            assertTrue(firstQuery.all { it == 8192.toShort() })
        }
    }
}
