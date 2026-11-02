package io.github.currencortex.music.core.media

import org.junit.Assert.*
import org.junit.Test

class NeteaseUsbDescriptorsTest {
    private fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()
    private fun join(vararg arrays: ByteArray) = arrays.fold(byteArrayOf()) { a, b -> a + b }
    private fun config(vararg descriptors: ByteArray): ByteArray {
        val payload = join(*descriptors); val length = payload.size + 9
        return bytes(9, 2, length and 255, length shr 8, 1, 1, 0, 0x80, 50) + payload
    }
    private fun face(protocol: Int = 0, alternate: Int = 1) = bytes(9, 4, 1, alternate, 1, 1, 2, protocol, 0)
    private fun ep(attributes: Int = 1, address: Int = 1) = bytes(7, 5, address, attributes, 0x80, 1, 1)
    private val general1 = bytes(7, 0x24, 1, 1, 0, 1, 0)
    private fun discrete(vararg rates: Int): ByteArray = bytes(8 + rates.size * 3, 0x24, 2, 1, 2, 3, 24, rates.size) +
        rates.flatMap { listOf((it and 255).toByte(), ((it shr 8) and 255).toByte(), ((it shr 16) and 255).toByte()) }.toByteArray()

    @Test fun uac1ReturnsDeclaredPcmAndDiscreteRatesWithoutClaimingActualPlayback() {
        val report = UsbAudioDescriptorParser.parse(config(face(), general1, discrete(44100, 48000, 192000), ep()))
        assertTrue(report.valid)
        val format = report.formats.single()
        assertEquals(1, format.uacVersion); assertEquals(24, format.validBits); assertEquals(3, format.subslotBytes)
        assertEquals(listOf(44100, 48000, 192000), format.discreteRates)
        assertEquals(1, format.outputEndpoint); assertEquals(384, format.maxPacketBytes); assertEquals(2, format.channels)
    }
    @Test fun uac2DoesNotInventRatesFromItsPcmContainer() {
        val general = bytes(16, 0x24, 1, 1, 0, 1, 1, 0, 0, 0, 2, 3, 0, 0, 0, 0)
        val report = UsbAudioDescriptorParser.parse(config(face(0x20), general, bytes(6, 0x24, 2, 1, 4, 24), ep()))
        val format = report.formats.single()
        assertEquals(2, format.uacVersion); assertEquals(24, format.validBits); assertEquals(4, format.subslotBytes)
        assertTrue(format.discreteRates.isEmpty()); assertNull(format.continuousRateRange)
    }
    @Test fun feedbackInputAndBulkEndpointsNeverBecomeAudioOutputCandidates() {
        for (endpoint in listOf(ep(attributes = 0x11), ep(address = 0x81), ep(attributes = 2))) {
            assertTrue(UsbAudioDescriptorParser.parse(config(face(), general1, discrete(48000), endpoint)).formats.isEmpty())
        }
        assertFalse(usableUsbIsoOutput(0, 1, 0, 1))
        assertTrue(usableUsbIsoOutput(0, 1, 192, 0x21)) // implicit-feedback data endpoint
    }
    @Test fun truncatedConfigurationsAndMalformedLengthsNeverExposePartialCapabilities() {
        val valid = config(face(), general1, discrete(48000), ep())
        for (malformed in listOf(valid.copyOf(valid.size - 1), bytes(0, 4), bytes(1, 4), bytes(8, 4, 1),
            config(face(), general1, bytes(11, 0x24, 2, 1, 2, 3, 24, 2, 0x80, 0xbb, 0), ep()))) {
            val report = UsbAudioDescriptorParser.parse(malformed)
            assertFalse(report.valid); assertTrue(report.formats.isEmpty())
        }
    }
    @Test fun continuousRangeAndAlternateBoundariesRemainDistinct() {
        val range = bytes(14, 0x24, 2, 1, 2, 2, 16, 0, 0x44, 0xac, 0, 0, 0xee, 2)
        val report = UsbAudioDescriptorParser.parse(config(face(alternate = 0), face(), general1, range, ep()))
        assertEquals(44100..192000, report.formats.single().continuousRateRange)
        assertEquals(1, report.formats.single().alternateSetting)
        assertTrue(report.formats.single().discreteRates.isEmpty())
    }
    @Test fun floatFormatsImpossiblePrecisionAndUnsupportedProtocolsAreNotIntegerPcm() {
        val nonPcm = general1.copyOf().apply { this[5] = 3 }
        val invalidPrecision = discrete(48000).apply { this[6] = 32 }
        for (value in listOf(config(face(), nonPcm, discrete(48000), ep()), config(face(), general1, invalidPrecision, ep()),
            config(face(0x30), general1, discrete(48000), ep()))) {
            assertTrue(UsbAudioDescriptorParser.parse(value).formats.isEmpty())
        }
    }
}
