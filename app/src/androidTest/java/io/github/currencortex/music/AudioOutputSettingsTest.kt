package io.github.currencortex.music

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.OutputSampleRateProcessor
import io.github.currencortex.music.feature.settings.AudioOutputScreen
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.data.settings.AppearanceSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import top.yukonga.miuix.kmp.basic.Scaffold

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioOutputSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeBackendsAdvancePauseFlushAndReportDirectFallback() {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val reports = mutableListOf<String>()
        val config = androidx.media3.exoplayer.audio.AudioOutputProvider.OutputConfig.Builder()
            .setEncoding(C.ENCODING_PCM_16BIT).setSampleRate(48000)
            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO).setBufferSize(96000).build()
        for (mode in listOf(io.github.currencortex.music.data.settings.AudioOutputMode.OPENSL_ES,
            io.github.currencortex.music.data.settings.AudioOutputMode.AAUDIO,
            io.github.currencortex.music.data.settings.AudioOutputMode.OBOE,
            io.github.currencortex.music.data.settings.AudioOutputMode.DIRECT)) {
            val provider = io.github.currencortex.music.core.media.MusicAudioOutputProvider(context,
                { io.github.currencortex.music.data.settings.AudioOutputSettings(mode = mode) }, { reports += it })
            lateinit var created: androidx.media3.exoplayer.audio.AudioOutput
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                created = provider.getAudioOutput(config)
            }
            val output: androidx.media3.exoplayer.audio.AudioOutput = created
            val nativeOutput = output is io.github.currencortex.music.core.media.NativeAudioOutput
            try {
                if (!nativeOutput) {
                    assertEquals(io.github.currencortex.music.data.settings.AudioOutputMode.DIRECT, mode)
                    assertTrue("A denied exclusive request must be reported", reports.last().contains("已回退 AudioTrack"))
                    // AudioTrack buffering/EOS is covered by the Media3 integration test;
                    // the fixed short buffer below exercises our callback-based engine.
                    continue
                }
                if (mode != io.github.currencortex.music.data.settings.AudioOutputMode.DIRECT)
                    assertTrue("$mode must actually use the native engine on this device: ${reports.last()}",
                        nativeOutput)
                output.setVolume(0f)
                val bytes = ByteBuffer.allocateDirect(48000 * 4 / 3).order(ByteOrder.nativeOrder())
                assertTrue(output.write(bytes, 1, 0))
                output.play()
                val deadline = android.os.SystemClock.elapsedRealtime() + 3000
                while (output.getPositionUs() < 100000 && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(10)
                assertTrue("$mode clock must advance", output.getPositionUs() >= 100000)
                output.pause()
                val paused = output.getPositionUs()
                Thread.sleep(70)
                if (nativeOutput)
                    assertEquals("$mode pause freezes the clock", paused, output.getPositionUs())
                output.flush()
                if (nativeOutput) assertEquals(0L, output.getPositionUs())
                bytes.clear(); assertTrue(output.write(bytes, 1, 0))
                output.play(); output.stop()
                val end = android.os.SystemClock.elapsedRealtime() + 3000
                while (output.getPositionUs() < 333000 && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(10)
                assertTrue("$mode drains the last buffer", output.getPositionUs() >= 333000)
            } finally {
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    output.release(); provider.release()
                }
            }
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0,
            android.os.Bundle().apply { putString("stream", reports.joinToString("\n", "\n", "\n")) })
    }

    @Test fun everyBackendPlaysSeeksAndEndsThroughMedia3() {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val instrument = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val wav = java.io.File(context.cacheDir, "output-eos-fixture.wav")
        val frames = 48000 * 2
        val data = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + frames * 4).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(2).putInt(48000).putInt(48000 * 4)
            .putShort(4).putShort(16).put("data".toByteArray()).putInt(frames * 4)
        wav.writeBytes(data.array())
        try {
            for (mode in io.github.currencortex.music.data.settings.AudioOutputMode.entries.filter { it != io.github.currencortex.music.data.settings.AudioOutputMode.BIT_PERFECT }) {
                val ended = java.util.concurrent.CountDownLatch(1)
                val failure = java.util.concurrent.atomic.AtomicReference<androidx.media3.common.PlaybackException?>()
                val position = java.util.concurrent.atomic.AtomicLong()
                var player: androidx.media3.exoplayer.ExoPlayer? = null
                try {
                    instrument.runOnMainSync {
                        player = androidx.media3.exoplayer.ExoPlayer.Builder(context,
                            io.github.currencortex.music.core.media.DockRenderersFactory(context,
                                io.github.currencortex.music.core.media.DockAudioLevel(),
                                outputSettings = { io.github.currencortex.music.data.settings.AudioOutputSettings(mode = mode) }))
                            .build().apply {
                                volume = 0f
                                addListener(object : androidx.media3.common.Player.Listener {
                                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failure.set(error); ended.countDown() }
                                    override fun onPlaybackStateChanged(state: Int) {
                                        if (state == androidx.media3.common.Player.STATE_ENDED) ended.countDown()
                                    }
                                })
                                setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(wav)))
                                prepare(); play()
                            }
                    }
                    val deadline = android.os.SystemClock.elapsedRealtime() + 5000
                    while (position.get() < 200 && failure.get() == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                        Thread.sleep(25)
                        instrument.runOnMainSync { position.set(player!!.currentPosition) }
                    }
                    assertNull("$mode playback error", failure.get())
                    assertTrue("$mode playback clock", position.get() >= 200)
                    instrument.runOnMainSync { player!!.seekTo(1500) }
                    assertTrue("$mode reaches EOS after seeking", ended.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    assertNull("$mode error after seeking", failure.get())
                } finally { instrument.runOnMainSync { player?.release() } }
            }
        } finally { wav.delete() }
    }

    @Test fun realAudioTrackUsesRequestedRateAfterReprepare() {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val instrument = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val wav = java.io.File(context.cacheDir, "output-fixture.wav")
        val frames = 44100 * 3
        val data = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + frames * 4).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(2).putInt(44100).putInt(44100 * 4)
            .putShort(4).putShort(16).put("data".toByteArray()).putInt(frames * 4)
        wav.writeBytes(data.array())
        val rate = java.util.concurrent.atomic.AtomicInteger(48000)
        val tracks = java.util.concurrent.LinkedBlockingQueue<Int>()
        var player: androidx.media3.exoplayer.ExoPlayer? = null
        try {
            instrument.runOnMainSync {
                player = androidx.media3.exoplayer.ExoPlayer.Builder(context,
                    io.github.currencortex.music.core.media.DockRenderersFactory(context,
                        io.github.currencortex.music.core.media.DockAudioLevel(), outputSampleRate = rate::get)).build().apply {
                    volume = 0f
                    addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                        override fun onAudioTrackInitialized(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                            audioTrackConfig: androidx.media3.exoplayer.audio.AudioSink.AudioTrackConfig) {
                            tracks.offer(audioTrackConfig.sampleRate)
                        }
                    })
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(wav)))
                    prepare(); play()
                }
            }
            assertEquals(48000, tracks.poll(10, java.util.concurrent.TimeUnit.SECONDS))
            instrument.runOnMainSync {
                rate.set(96000)
                player!!.stop(); player!!.seekTo(0); player!!.prepare(); player!!.play()
            }
            assertEquals(96000, tracks.poll(10, java.util.concurrent.TimeUnit.SECONDS))
        } finally { instrument.runOnMainSync { player?.release() }; wav.delete() }
    }

    @Test fun resamplingPreservesDurationAndCanReturnToSourceRate() {
        var rate = 48000
        val processor = OutputSampleRateProcessor({ rate })
        val input = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        assertEquals(48000, processor.configure(input).sampleRate)
        processor.flush(AudioProcessor.StreamMetadata(C.TIME_UNSET))
        val pcm = ByteBuffer.allocateDirect(44100 * 4).order(ByteOrder.nativeOrder())
        repeat(44100) { i ->
            val sample = (kotlin.math.sin(i * 2.0 * Math.PI * 440 / 44100) * 12000).toInt().toShort()
            pcm.putShort(sample); pcm.putShort(sample)
        }
        pcm.flip()
        processor.queueInput(pcm)
        var bytes = processor.output.remaining()
        processor.queueEndOfStream()
        while (!processor.isEnded) bytes += processor.output.remaining()
        assertEquals("One second of stereo PCM remains one second after conversion", 48000.0, bytes / 4.0, 2.0)
        processor.reset()
        rate = 0
        assertEquals(44100, processor.configure(input).sampleRate)
        assertFalse(processor.isActive)
    }

    @Test fun pickerPersistsRateAndResetWithoutStartingPlayback(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "output-${UUID.randomUUID()}")
        try {
            container.ready.await()
            compose.setContent { LeiTheme(AppearanceSettings()) { Scaffold { AudioOutputScreen(container) } } }
            compose.onNodeWithTag("audio_output_settings").performScrollToNode(hasTestTag("output_sample_rate"))
            compose.onNodeWithTag("output_sample_rate").performClick()
            compose.onNodeWithTag("output_rate_48000").performClick()
            compose.waitUntil(5000) { container.audioOutput.state.value.sampleRate == 48000 }
            compose.onNodeWithTag("output_sample_rate").performClick()
            compose.onNodeWithTag("output_rate_48000").assertIsSelected()
            compose.onNodeWithTag("output_rate_0").performClick()
            compose.waitUntil(5000) { container.audioOutput.state.value.sampleRate == 0 }
            assertNull(container.playbackQueue.state.value.current)
        } finally { container.close() }
    }
}
