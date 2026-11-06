package io.github.currencortex.music

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.currencortex.music.core.media.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import java.util.concurrent.atomic.AtomicReference

@androidx.annotation.OptIn(UnstableApi::class)
class DockAudioTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun rhythmTracksChangingPcmTempoAcrossBufferSizesWithoutChangingAudio() {
        for ((encoding, channels) in listOf(C.ENCODING_PCM_16BIT to 2, C.ENCODING_PCM_FLOAT to 6)) {
            val meter = DockAudioLevel().apply { enabled = true }
            val processor = DockAudioProcessor(meter)
            processor.configure(AudioFormat(48000, channels, encoding)); processor.flush()
            val wave = FloatArray(480) { (sin(2 * PI * it / 480) * .5).toFloat() }
            fun segment(period: Int) {
                var frame = 0
                val total = 48000 * 8
                while (frame < total) {
                    val frames = minOf(if (frame % 2 == 0) 137 else 2048, total - frame)
                    val bytes = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
                    val input = ByteBuffer.allocateDirect(frames * channels * bytes).order(ByteOrder.LITTLE_ENDIAN)
                    repeat(frames) {
                        val value = if (frame % period < 2400) wave[frame % wave.size] else 0f
                        repeat(channels) {
                            if (bytes == 4) input.putFloat(value) else input.putShort((value * 32767).toInt().toShort())
                        }
                        frame++
                    }
                    input.flip()
                    val original = ByteArray(input.remaining()).also { input.duplicate().get(it) }
                    processor.queueInput(input)
                    val output = processor.output
                    assertArrayEquals(original, ByteArray(output.remaining()).also { output.get(it) })
                }
            }
            try {
                segment(38400)
                assertEquals("75 BPM must come from PCM sample time, not buffer arrival time", 75f, meter.sample.bpm!!, 3f)
                segment(19200)
                assertEquals("Tempo changes within one stream must be detected", 150f, meter.sample.bpm!!, 5f)
                processor.flush()
                assertNull(meter.sample.bpm)
            } finally { processor.reset() }
        }
    }
    @Test fun floatPcmAnalysisPreservesEveryByteAndWorksWithoutVisualMeter() {
        val probe = TrackEnergyProbe().apply { enabled = true }
        val processor = DockAudioProcessor(DockAudioLevel(), probe)
        processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT)); processor.flush()
        val input = ByteBuffer.allocateDirect(48000 * 2 * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(48000 * 2) { input.putFloat(if (it == 0) Float.NaN else .25f) }; input.flip()
        val original = ByteArray(input.remaining()).also { input.duplicate().get(it) }
        processor.queueInput(input)
        val output = processor.output
        val actual = ByteArray(output.remaining()).also { output.get(it) }
        assertArrayEquals(original, actual)
        assertEquals(.25f, probe.profile()!!.rms, .001f)
        processor.reset()
    }
    @Test fun originalPcmSurvivesEnabledDisabledAndSilenceWithNoGainChange() {
        val meter = DockAudioLevel().apply { enabled = true }
        val processor = DockAudioProcessor(meter)
        processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); processor.flush()
        processor.queueInput(androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER)
        assertFalse(processor.output.hasRemaining())
        fun pass(value: Short): ByteArray {
            val input = ByteBuffer.allocateDirect(19200).order(ByteOrder.LITTLE_ENDIAN)
            repeat(9600) { input.putShort(value) }; input.flip()
            val original = ByteArray(input.remaining()); input.duplicate().get(original)
            processor.queueInput(input)
            val output = processor.output
            val actual = ByteArray(output.remaining()); output.get(actual)
            assertArrayEquals(original, actual); assertFalse(input.hasRemaining())
            return actual
        }
        pass(8192); assertTrue(meter.sample.energy > .4f)
        processor.queueInput(androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER)
        pass(0); pass(0); assertEquals(0f, meter.sample.energy, .001f)
        meter.enabled = false; meter.reset(); pass(16384)
        assertEquals(0L, meter.sample.timestampNanos)
        processor.reset()
    }

    @Test fun realExoPlayerSinkPublishesChangingRhythmAndThenSilence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val meter = DockAudioLevel().apply { enabled = true }
        val frames = 48000 * 15
        val data = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + frames * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(48000).putInt(96000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(frames * 2)
        repeat(frames) { frame ->
            val period = if (frame < 48000 * 6) 38400 else 19200
            val attack = frame < 48000 * 12 && frame % period < 2400
            data.putShort(if (attack) (sin(2 * PI * 100 * frame / 48000) * 8000).toInt().toShort() else 0)
        }
        val file = File.createTempFile("dock-pcm-", ".wav", context.cacheDir).apply { writeBytes(data.array()) }
        var player: ExoPlayer? = null
        val details = AtomicReference("not created")
        try {
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(context, DockRenderersFactory(context, meter)).build().apply {
                    volume = 0f
                    addListener(object : androidx.media3.common.Player.Listener {
                        override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                            details.set("state=${player.playbackState}, playing=${player.isPlaying}, error=${player.playerError?.stackTraceToString()}")
                        }
                    })
                    setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file))); prepare(); play()
                }
            }
            fun await(test: () -> Boolean) {
                val end = System.nanoTime() + 8_000_000_000L
                while (!test() && System.nanoTime() < end) Thread.sleep(20)
                assertTrue("enabled=${meter.enabled}, sample=${meter.sample}, ${details.get()}", test())
            }
            await { meter.sample.energy > .25f && meter.sample.bass > .20f }
            await { meter.sample.bpm?.let { kotlin.math.abs(it - 75f) < 4f } == true }
            await { meter.sample.bpm?.let { kotlin.math.abs(it - 150f) < 5f } == true }
            await { meter.sample.timestampNanos != 0L && meter.sample.energy < .001f && meter.sample.bpm == null }
        } finally { instrumentation.runOnMainSync { player?.release() }; file.delete() }
    }
}
