package io.github.currencortex.music

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.settings.AdaptiveAudioSettings
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin

@androidx.annotation.OptIn(UnstableApi::class)
class AdaptiveAudioFormatTest {
    private fun pcm(rate: Int, channels: Int, floating: Boolean = false): ByteBuffer =
        ByteBuffer.allocateDirect(rate / 5 * channels * if (floating) 4 else 2)
            .order(ByteOrder.LITTLE_ENDIAN).apply {
                repeat(rate / 5) { i -> repeat(channels) { channel ->
                    val sample = sin(2 * PI * (100 + channel * 100) * i / rate) * .25
                    if (floating) putFloat(sample.toFloat()) else putShort((sample * 32768).toInt().toShort())
                } }
                flip()
            }

    private fun bytes(buffer: ByteBuffer) = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }

    @Test fun multichannelAndHighRateFormatsProcessAndReconfigure() {
        var runtime = AdaptiveAudioRuntime()
        val processor = AdaptiveAudioProcessor({ AdaptiveAudioSettings(enabled = true, virtualBass = true) },
            report = { runtime = it })
        // Includes the reported FLAC's decoded format, 7.1, and rates outside the old gate.
        for ((rate, channels) in listOf(44100 to 6, 48000 to 8, 384000 to 2, 4000 to 1, 96000 to 6)) {
            for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
                processor.configure(AudioFormat(rate, channels, encoding))
                processor.flush()
                val input = pcm(rate, channels, encoding == C.ENCODING_PCM_FLOAT)
                val dry = bytes(input)
                processor.queueInput(input)
                val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
                assertEquals(dry.size, output.remaining())
                assertTrue("$rate Hz / $channels channels / $encoding: ${runtime.message}", runtime.processing)
                assertFalse("Effects must change PCM, not just report success", dry.contentEquals(bytes(output)))
                if (encoding == C.ENCODING_PCM_FLOAT) while (output.hasRemaining()) {
                    assertTrue(output.float.let { it.isFinite() && it in -1f..1f })
                }
                processor.queueEndOfStream()
                assertTrue(processor.isEnded)
            }
        }
        processor.reset()
    }

    @Test fun unconfiguredFlushWaitsForFormatInsteadOfReportingUnsupported() {
        var runtime = AdaptiveAudioRuntime()
        val processor = AdaptiveAudioProcessor({ AdaptiveAudioSettings(enabled = true) }, report = { runtime = it })
        processor.flush()
        assertFalse(runtime.processing)
        assertFalse(runtime.message.contains("不支持"))
        assertTrue(runtime.message.contains("等待"))
        processor.configure(AudioFormat(44100, 6, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.queueInput(pcm(44100, 6))
        assertTrue(runtime.processing)
        processor.reset()
    }

    @Test fun disabledMultichannelIsByteExactAndCanEnableWithoutReconfigure() {
        var settings = AdaptiveAudioSettings()
        var runtime = AdaptiveAudioRuntime()
        val processor = AdaptiveAudioProcessor({ settings }, report = { runtime = it })
        processor.configure(AudioFormat(44100, 6, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = pcm(44100, 6)
        val dry = bytes(input)
        processor.queueInput(input)
        assertArrayEquals(dry, bytes(processor.output))
        settings = settings.copy(enabled = true, virtualBass = true)
        processor.queueInput(pcm(44100, 6))
        assertTrue(runtime.processing)
        assertFalse(dry.contentEquals(bytes(processor.output)))
        settings = settings.copy(enabled = false)
        processor.queueInput(pcm(44100, 6)); processor.output
        processor.queueInput(pcm(44100, 6))
        assertArrayEquals(dry, bytes(processor.output))
        processor.reset()
    }

    @Test fun realMultichannelDecoderFeedsAdaptiveStage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Optional local reproduction file is never bundled with the application or test APK.
        val supplied = InstrumentationRegistry.getArguments().getString("adaptiveReproPath")
        val file = supplied?.let(::File) ?: File(context.cacheDir, "adaptive-six-channel-${UUID.randomUUID()}.wav")
        if (supplied == null) {
            val block = bytes(pcm(44100, 6))
            val size = block.size * 25
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(size + 36); put("WAVEfmt ".toByteArray())
                putInt(16); putShort(1); putShort(6); putInt(44100); putInt(44100 * 12)
                putShort(12); putShort(16); put("data".toByteArray()); putInt(size)
            }.array()
            file.outputStream().use { out -> out.write(header); repeat(25) { out.write(block) } }
        }
        val runtime = AtomicReference(AdaptiveAudioRuntime())
        val timeline = MusicHapticTimeline()
        var player: ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(context, DockRenderersFactory(context, DockAudioLevel(),
                    adaptiveSettings = { AdaptiveAudioSettings(enabled = true, virtualBass = true, haptics = true) },
                    hapticTimeline = timeline, adaptiveReport = runtime::set)).build().apply {
                    volume = 0f
                    setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                    prepare(); play()
                }
            }
            var window: MusicHapticTimeline.Frame? = null
            val deadline = System.nanoTime() + 10_000_000_000L
            while (window == null && System.nanoTime() < deadline) {
                instrumentation.runOnMainSync {
                    val p = player!!
                    p.playerError?.let { throw it }
                    if (p.isPlaying && !p.currentTimeline.isEmpty) {
                        window = timeline.poll(p.currentPosition * 1000, p.currentTimeline.getUidOfPeriod(p.currentPeriodIndex))
                    }
                }
                Thread.sleep(20)
            }
            assertTrue(runtime.get().message, runtime.get().processing)
            assertNotNull("Decoded multichannel PCM must reach processing and the playback timeline", window)
            assertTrue(window!!.full > 0f)
            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("stream", "\nADAPTIVE_MULTICHANNEL processing=true timeline_energy=${window!!.full} supplied_file=${supplied != null}\n")
            })
        } finally {
            instrumentation.runOnMainSync { player?.release() }
            if (supplied == null) file.delete()
        }
    }
}
