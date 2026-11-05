package io.github.currencortex.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.util.UnstableApi
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.settings.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

@androidx.annotation.OptIn(UnstableApi::class)
class AdaptiveAudioTest {
    private fun pcm(frames: Int, encoding: Int = C.ENCODING_PCM_16BIT, channels: Int = 2) =
        ByteBuffer.allocateDirect(frames * channels * if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2)
            .order(ByteOrder.LITTLE_ENDIAN).apply {
                repeat(frames) { i -> repeat(channels) {
                    val value = sin(2 * PI * 60 * i / 48000) * .35
                    if (encoding == C.ENCODING_PCM_FLOAT) putFloat(value.toFloat()) else putShort((value * 32767).toInt().toShort())
                } }
                flip()
            }
    private fun bytes(buffer: ByteBuffer) = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }

    @Test fun disabledAndUnsupportedFormatsPreserveEveryByte() {
        for ((encoding, channels) in listOf(C.ENCODING_PCM_16BIT to 2, C.ENCODING_PCM_FLOAT to 2, C.ENCODING_PCM_16BIT to 6)) {
            val settings = AdaptiveAudioSettings(enabled = false)
            val processor = AdaptiveAudioProcessor({ settings })
            processor.configure(AudioFormat(48000, channels, encoding)); processor.flush()
            val input = pcm(2048, encoding, channels); val expected = bytes(input)
            processor.queueInput(input)
            assertArrayEquals(expected, bytes(processor.output))
            processor.queueInput(AudioProcessor.EMPTY_BUFFER)
            assertFalse(processor.output.hasRemaining())
            processor.queueEndOfStream(); assertTrue(processor.isEnded)
            processor.reset()
        }
        var runtime = AdaptiveAudioRuntime()
        val unsupported = AdaptiveAudioProcessor({ AdaptiveAudioSettings(enabled = true) }, report = { runtime = it })
        unsupported.configure(AudioFormat(48000, 2, C.ENCODING_PCM_24BIT)); unsupported.flush()
        val input = ByteBuffer.allocateDirect(60).apply { repeat(60) { put(it.toByte()) }; flip() }
        val expected = bytes(input)
        unsupported.queueInput(input)
        assertArrayEquals(expected, bytes(unsupported.output))
        assertFalse(runtime.processing)
        assertTrue(runtime.message.contains("编码不支持"))
        unsupported.reset()
    }
    @Test fun processingKeepsFrameCountAndSanitizesFloatingPointAudio() {
        var runtime = AdaptiveAudioRuntime()
        val processor = AdaptiveAudioProcessor({ AdaptiveAudioSettings(enabled = true, virtualBass = true) }, report = { runtime = it })
        processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT)); processor.flush()
        val input = pcm(48000, C.ENCODING_PCM_FLOAT).apply { putFloat(0, Float.NaN); putFloat(4, Float.POSITIVE_INFINITY) }
        val expected = input.remaining()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(expected, output.remaining())
        while (output.hasRemaining()) assertTrue(output.float.let { it.isFinite() && it in -1f..1f })
        assertTrue(runtime.processing)
        processor.queueEndOfStream(); assertTrue(processor.isEnded)
        processor.reset()
    }
    @Test fun hapticWindowsFollowMediaTimeAndFlushDropsOldTrack() {
        val timeline = MusicHapticTimeline()
        var settings = AdaptiveAudioSettings(enabled = true, haptics = true)
        val processor = AdaptiveAudioProcessor({ settings }, timeline)
        processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        processor.flush(StreamMetadata.Builder().setPositionOffsetUs(5_000_000).build())
        processor.queueInput(pcm(4800)); processor.output
        assertNull(timeline.poll(0, null))
        val first = timeline.poll(5_000_000, null)
        assertNotNull(first); assertTrue(first!!.bass > 0); assertTrue(first.full > 0)
        processor.flush(StreamMetadata.Builder().setPositionOffsetUs(30_000_000).build())
        assertNull(timeline.poll(5_040_000, null))
        processor.queueInput(pcm(4800)); processor.output
        assertNotNull(timeline.poll(30_000_000, null))
        settings = settings.copy(haptics = false)
        processor.queueInput(pcm(4800)); processor.output
        assertNull(timeline.poll(30_040_000, null))
        processor.reset()
    }
    @Test fun disabledMasterSettlesToExactBytesAndHandlesRepeatedEos() {
        var settings = AdaptiveAudioSettings(enabled = true, virtualBass = true)
        val processor = AdaptiveAudioProcessor({ settings })
        processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); processor.flush()
        processor.queueInput(pcm(4800)); processor.output
        settings = settings.copy(enabled = false)
        processor.queueInput(pcm(9600)); processor.output
        val input = pcm(4800); val expected = bytes(input)
        processor.queueInput(input)
        assertArrayEquals(expected, bytes(processor.output))
        repeat(3) { processor.queueEndOfStream(); assertFalse(processor.output.hasRemaining()); assertTrue(processor.isEnded) }
        processor.reset()
    }

    @Test fun realDecoderFeedsAdaptiveStageAndTimestampedHaptics() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = java.io.File(context.cacheDir, "adaptive-live-${java.util.UUID.randomUUID()}.wav")
        val audio = bytes(pcm(48000 * 5))
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(audio.size + 36); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(2); putInt(48000); putInt(192000); putShort(4); putShort(16)
            put("data".toByteArray()); putInt(audio.size)
        }.array()
        file.outputStream().use { it.write(header); it.write(audio) }
        val settings = java.util.concurrent.atomic.AtomicReference(AdaptiveAudioSettings(enabled = true, haptics = true, virtualBass = true))
        val reported = java.util.concurrent.atomic.AtomicReference(AdaptiveAudioRuntime())
        val timeline = MusicHapticTimeline()
        var player: androidx.media3.exoplayer.ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                player = androidx.media3.exoplayer.ExoPlayer.Builder(context, DockRenderersFactory(context, DockAudioLevel(),
                    adaptiveSettings = settings::get, hapticTimeline = timeline, adaptiveReport = reported::set)).build().apply {
                    volume = 0f // Exercise decoding and the real sink without audible test tones.
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file)))
                    prepare(); play()
                }
            }
            var found: MusicHapticTimeline.Frame? = null
            val deadline = System.nanoTime() + 8_000_000_000L
            while (found == null && System.nanoTime() < deadline) {
                instrumentation.runOnMainSync {
                    val p = player!!
                    if (p.isPlaying && !p.currentTimeline.isEmpty)
                        found = timeline.poll(p.currentPosition * 1000, p.currentTimeline.getUidOfPeriod(p.currentPeriodIndex))
                    p.playerError?.let { throw it }
                }
                Thread.sleep(20)
            }
            assertNotNull("Decoded windows must match the actual playback timeline", found)
            assertTrue(reported.get().processing)
            assertTrue(found!!.full > 0f)
            settings.set(settings.get().copy(enabled = false))
            val offDeadline = System.nanoTime() + 2_000_000_000L
            while (reported.get().processing && System.nanoTime() < offDeadline) Thread.sleep(20)
            assertFalse(reported.get().processing)
        } finally { instrumentation.runOnMainSync { player?.release() }; file.delete() }
    }

    @Test fun supportedMotorAcceptsAMediaPulseAndStops() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val driver = MusicHapticDriver(instrumentation.targetContext)
        val scenario = androidx.test.core.app.ActivityScenario.launch(androidx.activity.ComponentActivity::class.java)
        try {
            org.junit.Assume.assumeTrue(driver.supported)
            assertTrue(driver.play(MusicHapticTimeline.Frame(0, null, .2f, .3f),
                AdaptiveAudioSettings(enabled = true, haptics = true, hapticStrength = 25), 1f))
            if (!driver.amplitudeControl) assertFalse("Fallback must not turn weak settings into continuous vibration",
                driver.play(MusicHapticTimeline.Frame(40000, null, .2f, .3f),
                    AdaptiveAudioSettings(enabled = true, haptics = true, hapticStrength = 25), 1f))
            Thread.sleep(60)
            driver.stop()
            assertFalse(driver.play(MusicHapticTimeline.Frame(80000, null, .2f, .3f), AdaptiveAudioSettings(), 1f))
            // The device history can verify delivery; this does not assert subjective tactile quality.
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "\nADAPTIVE_MOTOR amplitude_control=${driver.amplitudeControl} media_pulse_requested=true\n") })
        } finally { driver.close(); scenario.close() }
    }
}
