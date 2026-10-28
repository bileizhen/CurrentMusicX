package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.settings.AudioOutputMode
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class NeteaseUsbSessionTest {
    private val pcm = NeteaseUsbPcm(48000, 2, 2)
    private class Connection : AutoCloseable {
        var closes = 0
        override fun close() { closes++ }
    }
    private class Driver : NeteaseUsbDriver {
        var opens = 0; var closes = 0; var bytesPerWrite = 8; var failed = false; var reject = false
        var playing = false
        val captured = mutableListOf<Byte>()
        val formats = mutableListOf<NeteaseUsbPcm>()
        override fun open(fd: Int, pcm: NeteaseUsbPcm): Long { opens++; formats += pcm; check(!reject) { "USB_NEGOTIATION" }; return 1 }
        override fun write(handle: Long, buffer: ByteBuffer): Int {
            val count = bytesPerWrite.coerceAtMost(buffer.remaining())
            repeat(count.coerceAtLeast(0)) { captured += buffer.get(buffer.position() + it) }
            return bytesPerWrite.takeIf { it < 0 } ?: count
        }
        override fun play(handle: Long, playing: Boolean) { this.playing = playing }
        override fun position(handle: Long) = captured.size / 4 * 1_000_000L / 48000
        override fun verify(handle: Long) { check(!failed) { "USB_TRANSFER" } }
        override fun close(handle: Long) { closes++ }
    }
    private fun open(driver: Driver = Driver(), connection: Connection = Connection(), permitted: Boolean = true,
        format: NeteaseUsbPcm = pcm, connected: () -> Boolean = { true }) =
        NeteaseUsbSession.open(format, permitted, 42, connection, connected, driver)
    @Test fun permissionDeniedNeverOpensDriverAndReleasesConnection() {
        val d = Driver(); val c = Connection()
        assertThrows(IllegalStateException::class.java) { open(d, c, permitted = false) }
        assertEquals(0, d.opens); assertEquals(1, c.closes)
    }
    @Test fun alreadyDisconnectedNeverOpensDriver() {
        val d = Driver(); val c = Connection()
        assertThrows(IllegalStateException::class.java) { open(d, c, connected = { false }) }
        assertEquals(0, d.opens); assertEquals(1, c.closes)
    }
    @Test fun pcmFormatsAreNotAliasedOrSilentlyConverted() {
        for (encoding in listOf(-1, 3, 4)) {
            val d = Driver(); val c = Connection()
            assertThrows(IllegalStateException::class.java) { open(d, c, format = pcm.copy(rate = 192000, encoding = encoding)) }
            assertEquals(0, d.opens); assertEquals(1, c.closes)
        }
    }
    @Test fun supportedExactPcmIsForwardedWithoutChangingRate() {
        for (format in listOf(pcm, pcm.copy(rate = 44100), pcm.copy(rate = 192000, encoding = 22))) {
            val d = Driver(); open(d, format = format).close()
            assertEquals(listOf(format), d.formats)
        }
    }
    @Test fun packed24ExpandsLosslesslyWithoutFloatOrRateConversion() {
        val d = Driver(); val session = open(d, format = pcm.copy(rate = 192000, encoding = 21))
        val samples = listOf(0, 1, -1, 8388607, -8388608, 0x123456)
        val input = ByteBuffer.allocateDirect(samples.size * 3).apply {
            samples.forEach { value -> repeat(3) { put((value shr (8 * it)).toByte()) } }; flip()
        }
        d.bytesPerWrite = 0; assertEquals(0, session.write(input)); assertEquals(0, input.position())
        d.bytesPerWrite = 8
        while (input.hasRemaining()) session.write(input)
        assertEquals(listOf(pcm.copy(rate = 192000, encoding = 22, precision = 24)), d.formats)
        val output = ByteBuffer.wrap(d.captured.toByteArray()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(samples.map { it shl 8 }, List(samples.size) { output.int })
        session.close()
    }
    @Test fun unsupportedDriverFormatCleansConnection() {
        val d = Driver().apply { reject = true }; val c = Connection()
        assertThrows(IllegalStateException::class.java) { open(d, c) }
        assertEquals(1, c.closes)
    }
    @Test fun disconnectReleasesDriverBeforeFurtherWrites() {
        val d = Driver(); val c = Connection(); var connected = true
        val session = open(d, c, connected = { connected }); connected = false
        assertThrows(IllegalStateException::class.java) { session.write(ByteBuffer.allocateDirect(8)) }
        session.close(); assertEquals(1, d.closes); assertEquals(1, c.closes); assertTrue(d.captured.isEmpty())
    }
    @Test fun nativeFailureStopsAndReleasesExactlyOnce() {
        val d = Driver(); val c = Connection(); val session = open(d, c); d.failed = true
        assertThrows(IllegalStateException::class.java) { session.verify() }
        session.close(); assertEquals(1, d.closes); assertEquals(1, c.closes)
    }
    @Test fun partialWritesPreserveEveryByteAndHonorBackpressure() {
        val d = Driver(); val session = open(d)
        val input = ByteBuffer.allocateDirect(40).apply { repeat(40) { put(it.toByte()) }; flip() }
        d.bytesPerWrite = 0; assertEquals(0, session.write(input)); assertEquals(0, input.position())
        d.bytesPerWrite = 8
        while (input.hasRemaining()) session.write(input)
        assertEquals((0..39).map(Int::toByte), d.captured); session.close()
    }
    @Test fun negativeWriteIsNotReportedAsSuccess() {
        val d = Driver().apply { bytesPerWrite = -1 }; val c = Connection(); val session = open(d, c)
        val input = ByteBuffer.allocateDirect(8)
        assertThrows(IllegalStateException::class.java) { session.write(input) }
        assertEquals(0, input.position()); assertEquals(1, d.closes); assertEquals(1, c.closes)
    }
    @Test fun unalignedOrHeapPcmIsRejectedBeforeNativeWrite() {
        for (input in listOf(ByteBuffer.allocate(8), ByteBuffer.allocateDirect(7))) {
            val d = Driver(); val session = open(d)
            assertThrows(IllegalStateException::class.java) { session.write(input) }
            assertTrue(d.captured.isEmpty()); assertEquals(1, d.closes)
        }
    }
    @Test fun pauseResumePreservesSubmittedSamples() {
        val d = Driver(); val session = open(d)
        session.write(ByteBuffer.allocateDirect(8)); session.play(true); session.play(false); session.play(true)
        assertEquals(8, d.captured.size); assertTrue(d.playing); session.close()
    }
    @Test fun changedSongGetsANewNativeFormatAndOldResourcesAreReleased() {
        val d = Driver(); val first = Connection(); val second = Connection()
        open(d, first).close(); open(d, second, format = pcm.copy(rate = 192000, encoding = 22)).close()
        assertEquals(listOf(pcm, pcm.copy(rate = 192000, encoding = 22)), d.formats)
        assertEquals(2, d.closes); assertEquals(1, first.closes); assertEquals(1, second.closes)
    }
    @Test fun usbExclusiveCannotFallThroughToNormalOutputOrEffects() {
        assertTrue(AudioOutputMode.NETEASE_USB.rawPcm); assertTrue(AudioOutputMode.NETEASE_USB.bypassProcessing)
        assertFalse(AudioOutputMode.AUDIO_TRACK.rawPcm); assertFalse(AudioOutputMode.AUDIO_TRACK.bypassProcessing)
        assertTrue(AudioOutputMode.BIT_PERFECT.rawPcm)
    }
}
