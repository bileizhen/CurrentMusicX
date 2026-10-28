package io.github.currencortex.music

import androidx.media3.common.*
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.*
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.settings.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.ByteArrayOutputStream

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class BitPerfectAudioTest {
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private class Capture(private val format: Format) : BitPerfectStream {
        val bytes = ByteArrayOutputStream()
        var playing = false
        var closed = false
        var broken = false
        var writeFails = false
        var volumeCalls = 0
        val frameSize = Util.getPcmFrameSize(format.pcmEncoding, format.channelCount)
        override val output = java.lang.reflect.Proxy.newProxyInstance(AudioOutput::class.java.classLoader,
            arrayOf(AudioOutput::class.java)) { _, method, args -> when (method.name) {
                "write" -> {
                    if (writeFails) throw IllegalStateException("synthetic write failure")
                    val buffer = args!![0] as ByteBuffer
                    val data = ByteArray(minOf(buffer.remaining(), frameSize * 7))
                    buffer.get(data); bytes.write(data); !buffer.hasRemaining()
                }
                "getPositionUs" -> bytes.size() / frameSize * 1_000_000L / format.sampleRate
                "getSampleRate" -> format.sampleRate
                "getBufferSizeInFrames" -> 256L
                "getAudioSessionId" -> 0
                "play" -> { playing = true; null }
                "pause", "stop" -> { playing = false; null }
                "setVolume" -> { volumeCalls++; null }
                "isOffloadedPlayback", "isStalled", "canReuseAudioOutput" -> false
                "getPlaybackParameters" -> PlaybackParameters.DEFAULT
                else -> null
            } } as AudioOutput
        override fun verify() { check(!broken) { "USB 配置已撤销" } }
        override fun close() { closed = true }
    }
    private fun sink(capture: Capture, status: MutableList<String> = mutableListOf()) = BitPerfectAudioSink(
        DefaultAudioSink.Builder(context).build(), { AudioOutputSettings(mode = AudioOutputMode.BIT_PERFECT) },
        { status += it }, { format, _, _ ->
            assertEquals(capture.frameSize, Util.getPcmFrameSize(format.pcmEncoding, format.channelCount)); capture
        })
    @Test fun pcmConstantsAndAndroidFormatPropertyMasksAreNotInterchangeable() {
        assertEquals(android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED, C.ENCODING_PCM_24BIT)
        assertNotEquals(C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)
        val position = android.media.AudioFormat.Builder().setSampleRate(192000).setEncoding(21).setChannelMask(12).build()
        val indexed = android.media.AudioFormat.Builder(position).setChannelIndexMask(3).build()
        assertNotEquals(position, indexed)
        fun pcm(f: android.media.AudioFormat) = BitPerfectPcm(f.sampleRate, f.encoding, f.channelCount, f.channelMask, f.channelIndexMask)
        assertTrue(pcm(position).accepts(pcm(indexed)))
    }
    @Test fun failedWriteReleasesStreamAndReportsFailure() {
        val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setSampleRate(192000)
            .setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_24BIT).build()
        val capture = Capture(format).apply { writeFails = true }
        val status = mutableListOf<String>(); val output = sink(capture, status)
        try {
            output.configure(AudioSink.AudioSinkConfig.Builder(format).build()); output.play()
            assertThrows(AudioSink.InitializationException::class.java) { output.handleBuffer(ByteBuffer.allocateDirect(42), 0, 1) }
            assertTrue(capture.closed); assertTrue(status.last().contains("WRITE_FAILED"))
        } finally { output.release() }
    }
    @Test fun otherBackendsContinueDelegatingToTheirExistingSink() {
        val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setSampleRate(48000)
            .setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
        for (mode in AudioOutputMode.entries.filter { !it.rawPcm }) {
            val calls = mutableListOf<String>()
            val normal = java.lang.reflect.Proxy.newProxyInstance(AudioSink::class.java.classLoader,
                arrayOf(AudioSink::class.java)) { _, method, args ->
                calls += method.name
                when (method.name) {
                    "getFormatSupport" -> AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
                    "handleBuffer" -> { (args!![0] as ByteBuffer).apply { position(limit()) }; true }
                    else -> null
                }
            } as AudioSink
            val output = BitPerfectAudioSink(normal, { AudioOutputSettings(mode = mode) }, {},
                { _, _, _ -> error("Must not open Bit-perfect for $mode") })
            output.configure(AudioSink.AudioSinkConfig.Builder(format).build()); output.play()
            assertTrue(output.handleBuffer(ByteBuffer.allocateDirect(28), 0, 1))
            output.pause(); output.flush(); output.release()
            assertTrue("$mode: $calls", calls.containsAll(listOf("configure", "play", "handleBuffer", "pause", "flush", "release")))
        }
    }
    @Test fun songFormatChangeDrainsAndClosesBeforeOpeningNewFormat() {
        val first = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setSampleRate(48000)
            .setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
        val second = first.buildUpon().setSampleRate(192000).setPcmEncoding(C.ENCODING_PCM_24BIT).build()
        val captures = mutableListOf<Capture>()
        val output = BitPerfectAudioSink(DefaultAudioSink.Builder(context).build(),
            { AudioOutputSettings(mode = AudioOutputMode.BIT_PERFECT) }, {}, { f, _, _ ->
                if (captures.isNotEmpty()) assertTrue(captures.last().closed)
                Capture(f).also { captures += it }
            })
        try {
            output.configure(AudioSink.AudioSinkConfig.Builder(first).build()); output.play()
            assertTrue(output.handleBuffer(ByteBuffer.allocateDirect(28), 0, 1))
            output.configure(AudioSink.AudioSinkConfig.Builder(second).build())
            val data = ByteArray(42) { (it * 13).toByte() }
            assertTrue(output.handleBuffer(ByteBuffer.allocateDirect(42).put(data).apply { flip() }, 1_000_000, 1))
            assertEquals(2, captures.size); assertEquals(6, captures.last().frameSize)
            assertArrayEquals(data, captures.last().bytes.toByteArray())
        } finally { output.release() }
        assertTrue(captures.all { it.closed })
    }
    @Test fun pcmBytesAreIdenticalForEveryDepthWithPartialWritesAndCodecTrimming() {
        for (encoding in BitPerfectAudioSink.PCM_ENCODINGS) {
            val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setSampleRate(96000)
                .setChannelCount(2).setPcmEncoding(encoding).setEncoderDelay(3).setEncoderPadding(5).build()
            val capture = Capture(format)
            val output = sink(capture)
            try {
                output.configure(AudioSink.AudioSinkConfig.Builder(format).build())
                output.setPlaybackParameters(PlaybackParameters(1.5f)); output.setSkipSilenceEnabled(true)
                assertEquals(PlaybackParameters.DEFAULT, output.getPlaybackParameters())
                assertFalse(output.getSkipSilenceEnabled())
                output.play()
                val original = ByteArray(capture.frameSize * 127) { ((it * 73 + 129) % 256).toByte() }
                // Chunk boundaries deliberately cross the padding-retention window.
                for (offset in original.indices step capture.frameSize * 11) {
                    val end = minOf(offset + capture.frameSize * 11, original.size)
                    val buffer = ByteBuffer.allocateDirect(end - offset).order(ByteOrder.nativeOrder())
                        .put(original, offset, end - offset).apply { flip() }
                    repeat(100) { if (buffer.hasRemaining()) output.handleBuffer(buffer, 0, 1) }
                    assertFalse(buffer.hasRemaining())
                }
                repeat(100) { if (!output.isEnded()) output.playToEndOfStream() }
                assertTrue(output.isEnded())
                assertArrayEquals("Encoding $encoding must preserve every audio byte", original.copyOfRange(
                    capture.frameSize * 3, original.size - capture.frameSize * 5), capture.bytes.toByteArray())
                assertEquals(0, capture.volumeCalls)
            } finally { output.release() }
            assertTrue(capture.closed)
        }
    }
    @Test fun duckingPausesWithoutScalingAndRevokedRouteFailsClosed() {
        val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setSampleRate(48000)
            .setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_24BIT).build()
        val capture = Capture(format)
        val status = mutableListOf<String>()
        val output = sink(capture, status)
        try {
            output.configure(AudioSink.AudioSinkConfig.Builder(format).build()); output.play()
            output.handleBuffer(ByteBuffer.allocateDirect(42), 0, 1)
            assertTrue(capture.playing)
            output.setVolume(0.2f)
            assertFalse(capture.playing); assertEquals(0, capture.volumeCalls)
            output.setVolume(1f); assertTrue(capture.playing)
            capture.broken = true
            assertThrows(AudioSink.InitializationException::class.java) { output.handleBuffer(ByteBuffer.allocateDirect(42), 0, 1) }
            assertTrue(capture.closed)
            assertTrue(status.last().contains("USB 配置已撤销"))
        } finally { output.release() }
    }
    @Test fun channelRemappingIsRejectedRatherThanSilentlyProcessed() {
        val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setSampleRate(48000)
            .setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
        val output = sink(Capture(format))
        try {
            assertThrows(AudioSink.ConfigurationException::class.java) {
                output.configure(AudioSink.AudioSinkConfig.Builder(format)
                    .setOutputChannelMapping(com.google.common.primitives.ImmutableIntArray.of(1, 0)).build())
            }
        } finally { output.release() }
    }
    @Test fun seekFlushClosesOldOutputAndResetsPcmClock() {
        val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setSampleRate(48000)
            .setChannelCount(2).setPcmEncoding(C.ENCODING_PCM_32BIT).build()
        val captures = mutableListOf<Capture>()
        val output = BitPerfectAudioSink(DefaultAudioSink.Builder(context).build(),
            { AudioOutputSettings(mode = AudioOutputMode.BIT_PERFECT) }, {},
            { f, _, _ -> Capture(f).also { captures += it } })
        try {
            output.configure(AudioSink.AudioSinkConfig.Builder(format).build()); output.play()
            output.handleBuffer(ByteBuffer.allocateDirect(80), 0, 1)
            output.flush()
            assertTrue(captures.single().closed)
            assertEquals(AudioSink.CURRENT_POSITION_NOT_SET, output.getCurrentPositionUs(false))
            val next = ByteArray(56) { 0x35 }
            val buffer = ByteBuffer.allocateDirect(56).put(next).apply { flip() }
            assertTrue(output.handleBuffer(buffer, 10_000_000, 1))
            assertEquals(2, captures.size)
            assertArrayEquals(next, captures.last().bytes.toByteArray())
            assertTrue(output.getCurrentPositionUs(false) >= 10_000_000)
        } finally { output.release() }
        assertTrue(captures.all { it.closed })
    }
    @Test fun phoneWithoutUsbCannotPretendToEnableBitPerfect() {
        val manager = context.getSystemService(android.media.AudioManager::class.java)
        org.junit.Assume.assumeTrue(manager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).none {
            it.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE || it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
        })
        val instrument = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val status = java.util.concurrent.CopyOnWriteArrayList<String>()
        val failed = java.util.concurrent.CountDownLatch(1)
        var player: androidx.media3.exoplayer.ExoPlayer? = null
        val wav = java.io.File(context.cacheDir, "bit-perfect-probe.wav")
        val data = ByteBuffer.allocate(44 + 48000 * 4).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + 48000 * 4).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(2).putInt(48000).putInt(48000 * 4)
            .putShort(4).putShort(16).put("data".toByteArray()).putInt(48000 * 4)
        wav.writeBytes(data.array())
        try {
            instrument.runOnMainSync {
                player = androidx.media3.exoplayer.ExoPlayer.Builder(context, DockRenderersFactory(context, DockAudioLevel(),
                    outputSettings = { AudioOutputSettings(mode = AudioOutputMode.BIT_PERFECT) }, outputStatus = { status += it }))
                    .build().apply {
                        volume = 0f
                        addListener(object : Player.Listener { override fun onPlayerError(error: PlaybackException) { failed.countDown() } })
                        setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(wav))); prepare(); play()
                    }
            }
            assertTrue("Unsupported hardware must fail promptly", failed.await(8, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(status.toString(), status.any { it.contains("Bit-perfect 已停止") && it.contains("USB") })
            assertFalse(status.any { it.contains("已确认") || it.contains("已回退") })
        } finally { instrument.runOnMainSync { player?.release() }; wav.delete() }
    }
}
