package io.github.currencortex.music

import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.DockAudioLevel
import io.github.currencortex.music.core.media.DockRenderersFactory
import io.github.currencortex.music.core.media.engine.AudioDiagnostics
import io.github.currencortex.music.core.media.engine.AudioBufferController
import io.github.currencortex.music.data.settings.AudioOutputMode
import io.github.currencortex.music.data.settings.AudioOutputSettings
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/** Local synthetic media only. Muted AudioTrack progress is not an acoustic or DAC test. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioOptimizationRegressionTest {
    @get:Rule val foreground = createComposeRule()
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()

    @Before fun requireIsolatedPackage() {
        assumeTrue("Run these fixtures in an isolated verification package", context.packageName != "com.bileizhen.currentmusic")
        // Some OEMs freeze instrumentation in the cached state. Keep only the empty test activity visible.
        foreground.setContent { }
    }

    @Test fun generatedPcmRatesDecodeAdvancePauseSeekAndFinishOnAudioTrack() {
        for (rate in listOf(44100, 48000, 96000, 192000)) {
            val file = File.createTempFile("pcm-$rate-", ".wav", context.cacheDir)
            try {
                writeSilentWav(file, rate)
                verifyLocalPlayback(file, rate, 2, expectedDurationMs = 2000)
            } finally { file.delete() }
        }
    }

    @Test fun existingMp3FlacAndAacFixturesReachDecodedPcmAndSeekWithoutNetwork() {
        for (extension in listOf("mp3", "flac", "aac")) {
            val file = File.createTempFile("codec-fixture-", ".$extension", context.cacheDir)
            try {
                instrument.context.assets.open("download/fixture.$extension").use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                verifyLocalPlayback(file, 44100, 1)
            } finally { file.delete() }
        }
    }

    @Test fun rawProcessingPolicySkipsOriginalEffectSelectionAndForcedResampling() {
        // Keep the transport AudioTrack to test processor selection without claiming USB availability.
        // The separate BitPerfectAudioTest verifies the raw sink's byte identity and partial writes.
        for (mode in AudioOutputMode.entries.filter { it.bypassProcessing }) {
            val presetReads = AtomicInteger()
            val file = File.createTempFile("bypass-${mode.name}-", ".wav", context.cacheDir)
            try {
                writeSilentWav(file, 44100)
                verifyLocalPlayback(file, 44100, 2, expectedDurationMs = 2000) { diagnostics ->
                    DockRenderersFactory(context, DockAudioLevel(), diagnostics = diagnostics,
                        outputSettings = { AudioOutputSettings(mode = AudioOutputMode.AUDIO_TRACK) },
                        processingSettings = { AudioOutputSettings(mode = mode, sampleRate = 192000) },
                        outputSampleRate = { 192000 },
                        originalPreset = { presetReads.incrementAndGet(); error("Raw policy must not select a DSP preset") })
                }
                assertEquals("$mode must bypass original EQ/MNN preset selection", 0, presetReads.get())
            } finally { file.delete() }
        }
    }

    private fun verifyLocalPlayback(file: File, rate: Int, channels: Int, expectedDurationMs: Long? = null,
        factory: (AudioDiagnostics) -> DockRenderersFactory = { diagnostics ->
            DockRenderersFactory(context, DockAudioLevel(), diagnostics = diagnostics)
        }) {
        val diagnostics = AudioDiagnostics()
        var player: ExoPlayer? = null
        fun onPlayer(action: (ExoPlayer) -> Unit) = instrument.runOnMainSync { action(checkNotNull(player)) }
        fun await(label: String, condition: (ExoPlayer) -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (true) {
                var done = false
                onPlayer { current -> assertNull("${file.name}: $label", current.playerError); done = condition(current) }
                if (done) return
                assertTrue("${file.name}: timed out waiting for $label\n${diagnostics.text()}", SystemClock.elapsedRealtime() < deadline)
                Thread.sleep(10)
            }
        }
        try {
            instrument.runOnMainSync {
                player = ExoPlayer.Builder(context, factory(diagnostics))
                    .setLoadControl(AudioBufferController().createLoadControl()).build().apply {
                    volume = 0f
                    setAudioAttributes(AudioAttributes.DEFAULT, false)
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    prepare()
                }
            }
            await("prepared decoded PCM") { it.playbackState == Player.STATE_READY && diagnostics.state.value.decoded.sampleRate != null }
            val decoded = diagnostics.state.value.decoded
            assertEquals("Sink input rate, not container metadata", rate, decoded.sampleRate)
            assertEquals(channels, decoded.channels)
            assertEquals(C.ENCODING_PCM_16BIT, decoded.encoding)
            expectedDurationMs?.let { expected -> onPlayer { assertEquals(expected, it.duration) } }
            onPlayer { it.play() }
            await("muted clock advance") { it.currentPosition >= 60 }
            val pauseAcknowledged = java.util.concurrent.CountDownLatch(1)
            onPlayer {
                it.pause()
                // FIFO playback-thread message acknowledges the preceding asynchronous pause.
                it.createMessage { _, _ -> pauseAcknowledged.countDown() }.send()
            }
            assertTrue("Playback thread must acknowledge pause", pauseAcknowledged.await(2, java.util.concurrent.TimeUnit.SECONDS))
            await("paused ready state") { !it.isPlaying && !it.playWhenReady && it.playbackState == Player.STATE_READY }
            var pausedPosition = 0L
            onPlayer { pausedPosition = it.currentPosition; assertFalse(it.isPlaying) }
            // Account for a small final AudioTrack timestamp correction, not ongoing playback.
            val firstPausedPosition = pausedPosition
            repeat(3) { Thread.sleep(20); onPlayer { pausedPosition = it.currentPosition; assertFalse(it.isPlaying) } }
            assertTrue("Pause completion correction must stay within 20 ms", kotlin.math.abs(pausedPosition - firstPausedPosition) <= 20)
            Thread.sleep(100)
            onPlayer {
                assertFalse(it.isPlaying)
                assertTrue("Acknowledged paused clock must remain stable within 2 ms", kotlin.math.abs(it.currentPosition - pausedPosition) <= 2)
            }
            onPlayer { it.seekTo(0) }
            await("paused seek to beginning") { it.playbackState == Player.STATE_READY && it.currentPosition == 0L }
            onPlayer { assertFalse(it.isPlaying); it.play() }
            await("end of local media after seek") { it.playbackState == Player.STATE_ENDED }
            val snapshot = diagnostics.state.value
            assertEquals("AudioTrack", snapshot.backend)
            assertEquals("Opened AudioTrack object rate", rate, snapshot.output.sampleRate)
            assertEquals(channels, snapshot.output.channels)
            assertEquals(C.ENCODING_PCM_16BIT, snapshot.output.encoding)
            val route = context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.id == snapshot.deviceId }
            instrument.sendStatus(0, Bundle().apply { putString("stream",
                "\nLOCAL_MUTED_PCM sdk=${Build.VERSION.SDK_INT} file=${file.name} rate=$rate channels=$channels " +
                    "routeType=${route?.type ?: "system-unavailable"}; no acoustic/Bluetooth/DAC verification\n") })
        } finally { instrument.runOnMainSync { player?.release() } }
    }

    private fun writeSilentWav(file: File, rate: Int) {
        val pcmBytes = rate * 2 * 2 * 2 // two seconds, stereo, signed 16-bit PCM
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + pcmBytes)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1); putShort(2); putInt(rate); putInt(rate * 4)
            putShort(4); putShort(16); put("data".toByteArray(Charsets.US_ASCII)); putInt(pcmBytes)
        }.array()
        file.outputStream().use { it.write(header); it.write(ByteArray(pcmBytes)) }
    }
}
