package io.github.currencortex.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.collect.ImmutableList
import io.github.currencortex.music.core.media.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Exercises Media3's repeated downstream EOS delivery, using the bundled native DSP. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OriginalAudioDrainTest {
    @Test fun realAudioTrackFinishesWithNativeEffectAndCanReplayAfterSeek() {
        playAndReplay(48000, NeteaseOriginalPreset(0, "Playback fixture", 1,
            """{"eq":{"on":false}}""".toByteArray()))
    }

    @Test fun realAudioTrackFinishesHighRateMoodEffectAndCanReplayAfterSeek() {
        playAndReplay(192000, moodSpatialPreset())
    }

    private fun playAndReplay(sampleRate: Int, preset: NeteaseOriginalPreset) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".verification"))
        NeteaseNativeEngine.configure(context)
        assertTrue(NeteaseNativeEngine.available())
        val processed = java.util.concurrent.atomic.AtomicInteger()
        val failure = java.util.concurrent.atomic.AtomicReference<String?>()
        val diagnostics = io.github.currencortex.music.core.media.engine.AudioDiagnostics()
        val file = File.createTempFile("native-drain-", ".wav", context.cacheDir)
        val pcmBytes = sampleRate * 4
        val data = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()).putInt(36 + pcmBytes).put("WAVEfmt ".toByteArray())
            putInt(16).putShort(1).putShort(2).putInt(sampleRate).putInt(sampleRate * 4)
            putShort(4).putShort(16).put("data".toByteArray()).putInt(pcmBytes)
        }
        file.writeBytes(data.array())
        var player: ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(context, DockRenderersFactory(context, DockAudioLevel(),
                    originalPreset = { preset }, originalProcessing = { processed.incrementAndGet() },
                    originalFailure = { _, message -> failure.set(message) },
                    diagnostics = diagnostics, outputSampleRate = { 192000 })).build().apply {
                    volume = 0f
                    setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                    prepare(); play()
                }
            }
            repeat(2) { cycle ->
                if (cycle > 0) instrumentation.runOnMainSync { player!!.seekTo(0); player!!.play() }
                val deadline = SystemClock.elapsedRealtime() + 10000
                var ended = false
                while (!ended && SystemClock.elapsedRealtime() < deadline) {
                    Thread.sleep(20)
                    instrumentation.runOnMainSync {
                        assertNull(player!!.playerError)
                        ended = player!!.playbackState == Player.STATE_ENDED
                    }
                }
                assertTrue("cycle=$cycle: native DSP and 192 kHz AudioTrack must reach EOS", ended)
                assertNull(failure.get())
                assertTrue("Native DSP must process each playback cycle", processed.get() >= cycle + 1)
                assertEquals(192000, diagnostics.state.value.output.sampleRate)
            }
        } finally {
            instrumentation.runOnMainSync { player?.release() }
            file.delete()
        }
    }

    @Test fun nativeEffectDrainsInPipelineAndAcceptsAudioAfterFlush() {
        NeteaseNativeEngine.configure(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(NeteaseNativeEngine.available())
        val preset = NeteaseOriginalPreset(0, "Drain fixture", 1,
            """{"eq":{"on":false}}""".toByteArray())
        val errors = mutableListOf<String>()
        val processor = NeteaseOriginalAudioProcessor({ preset }, { _, message -> errors += message })
        val pipeline = AudioProcessingPipeline(ImmutableList.of(
            DockAudioProcessor(DockAudioLevel()), processor, OutputSampleRateProcessor({ 192000 })))
        try {
            pipeline.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
            repeat(2) { cycle ->
                pipeline.flush(AudioProcessor.StreamMetadata(C.TIME_UNSET))
                var outputBytes = 0
                fun consume() {
                    val output = pipeline.output
                    outputBytes += output.remaining()
                    output.position(output.limit())
                }
                repeat(16) { block ->
                    val input = ByteBuffer.allocateDirect(512 * 4).order(ByteOrder.nativeOrder()).apply {
                        repeat(1024) { putShort(((it + block) % 2000).toShort()) }; flip()
                    }
                    repeat(100) { if (input.hasRemaining()) { pipeline.queueInput(input); consume() } }
                    assertFalse("cycle=$cycle: input must progress", input.hasRemaining())
                }
                pipeline.queueEndOfStream()
                repeat(100) { if (!pipeline.isEnded) consume() }
                assertTrue("cycle=$cycle: repeated upstream EOS must finish native draining", pipeline.isEnded)
                assertTrue("cycle=$cycle: processed PCM must reach resampled output", outputBytes > 0)
                assertTrue(errors.toString(), errors.isEmpty())
            }
        } finally { pipeline.reset() }
    }
}
