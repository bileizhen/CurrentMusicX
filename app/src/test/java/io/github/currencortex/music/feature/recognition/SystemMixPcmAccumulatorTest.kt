package io.github.currencortex.music.feature.recognition

import org.junit.Assert.*
import org.junit.Test

class SystemMixPcmAccumulatorTest {
    private fun frame(vararg unsigned: Int) = unsigned.map(Int::toByte).toByteArray()
    private fun pcm(vararg unsigned: Int) = unsigned.map { ((it - 128) * 256).toShort() }.toShortArray()

    @Test fun overlappingSnapshotsAppendOnlyTheirNewTail() {
        val capture = SystemMixPcmAccumulator(8000)
        capture.appendSnapshot(frame(128, 129, 130, 131, 132, 133))
        capture.appendSnapshot(frame(131, 132, 133, 134, 135, 136))
        assertEquals(9L, capture.inputSamples)
        assertArrayEquals(pcm(128, 129, 130, 131, 132, 133, 134, 135, 136), capture.copyPcm())
    }

    @Test fun identicalAndSilentSnapshotsCannotArtificiallyAdvanceTime() {
        val capture = SystemMixPcmAccumulator(48000)
        val silence = ByteArray(1024) { 128.toByte() }
        capture.appendSnapshot(silence)
        val initial = capture.size
        repeat(1000) { assertEquals(0, capture.appendSnapshot(silence)) }
        assertEquals(initial, capture.size)
        assertFalse(capture.hasSignal)
        assertTrue(capture.copyPcm().all { it == 0.toShort() })
    }

    @Test fun repeatedPrefixStillChoosesTheLargestExactOverlap() {
        val capture = SystemMixPcmAccumulator(8000)
        capture.appendSnapshot(frame(128, 129, 128, 129))
        capture.appendSnapshot(frame(128, 129, 130, 131))
        assertArrayEquals(pcm(128, 129, 128, 129, 130, 131), capture.copyPcm())
    }

    @Test fun unsignedSilenceAndExtremaHaveTheCorrectSigned16BitValues() {
        val resampler = Unsigned8PcmResampler(8000)
        resampler.append(frame(0, 1, 127, 128, 129, 254, 255))
        assertArrayEquals(shortArrayOf(-32768, -32512, -256, 0, 256, 32256, 32512), resampler.copyPcm())
    }

    @Test fun bothNativeRatesProduceExactlyEightThousandSamplesPerSecond() {
        for (rate in listOf(44100, 48000)) {
            val source = ByteArray(rate) { 160.toByte() }
            val whole = Unsigned8PcmResampler(rate)
            whole.append(source)
            val partitioned = Unsigned8PcmResampler(rate)
            var offset = 0
            while (offset < source.size) {
                val length = minOf(137, source.size - offset)
                partitioned.append(source, offset, length)
                offset += length
            }
            assertEquals("rate=$rate", 8000, whole.size)
            assertArrayEquals("Fractional phase must survive frame boundaries at $rate Hz", whole.copyPcm(), partitioned.copyPcm())
            assertTrue(whole.copyPcm().all { it == 8192.toShort() })
        }
    }

    @Test fun fractionalWeightedBinsDoNotDropOrRepeatTheBoundarySample() {
        val resampler = Unsigned8PcmResampler(44100)
        resampler.append(frame(128, 128, 128, 128, 128, 255))
        assertEquals(1, resampler.size)
        assertEquals(((127L * 256 * 4100) / 44100).toShort(), resampler.copyPcm().single())
        resampler.append(ByteArray(5) { 128.toByte() })
        assertEquals(1, resampler.size) // 11 input samples cover less than two 44.1 kHz bins.
        resampler.append(frame(128))
        assertEquals(2, resampler.size)
        assertEquals(((127L * 256 * 3900) / 44100).toShort(), resampler.copyPcm()[1])
    }

    @Test fun boundedOutputStopsAtCapacityAndClearRemovesPreviousAudioState() {
        val capture = SystemMixPcmAccumulator(8000, maximumSamples = 5)
        capture.appendSnapshot(frame(129, 130, 131, 132))
        capture.appendSnapshot(frame(140, 141, 142, 143))
        assertTrue(capture.full)
        val result = capture.copyPcm()
        assertArrayEquals(pcm(129, 130, 131, 132, 140), result)
        capture.clear()
        assertEquals(0, capture.size)
        assertEquals(0L, capture.inputSamples)
        assertFalse(capture.hasSignal)
        assertArrayEquals(pcm(129, 130, 131, 132, 140), result)
    }
}
