package io.github.currencortex.music

import android.content.ContentValues
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.MusicService
import io.github.currencortex.music.core.media.engine.BitPerfectEvidence
import io.github.currencortex.music.data.settings.AudioBufferPolicy
import io.github.currencortex.music.data.settings.AudioOutputMode
import io.github.currencortex.music.data.settings.MusicTransitionMode
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Intended real service/session regression without a DAC; explicit PASS/SKIP evidence is emitted.
 * LOCAL audio uses no HTTP. MainActivity's unrelated requests are outside this test's control. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioEngineFallbackServiceTest {
    private fun evidence(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "\n$message\n") })
    }

    @Test fun bitPerfectWithoutUsbFallsBackAndKeepsTheSameSessionTrack(): Unit = runBlocking {
        verifyFallback(AudioOutputMode.BIT_PERFECT)
    }

    @Test fun usbExclusiveWithoutUsbFallsBackAndKeepsTheSameSessionTrack(): Unit = runBlocking {
        verifyFallback(AudioOutputMode.NETEASE_USB)
    }

    private suspend fun verifyFallback(mode: AudioOutputMode) {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName.endsWith(".verification")) { "Only the isolated verification package is allowed" }
        if (Build.VERSION.SDK_INT < 29) evidence("NO_DAC_FALLBACK_SKIP mode=$mode mediaStoreApiSupported=false")
        assumeTrue("MediaStore-owned fixture requires API29+; older system coverage remains separate", Build.VERSION.SDK_INT >= 29)
        val usb = app.getSystemService(UsbManager::class.java)
        if (usb.deviceList.isNotEmpty()) evidence("NO_DAC_FALLBACK_SKIP mode=$mode usbCount=${usb.deviceList.size}")
        assumeTrue("This regression requires no attached USB device, not a real DAC test", usb.deviceList.isEmpty())
        val container = app.container
        container.ready.await(); container.sessionRestored.await(); container.navidrome.ready.await()
        // A prior process crash can leave the existing loopback service fixture logged in.
        // Never clear a real server/account, including in the isolated package.
        val priorFixture = container.navidrome.state.value
        if (priorFixture.configured && priorFixture.username == "fixture" &&
            android.net.Uri.parse(priorFixture.server).host in setOf("localhost", "127.0.0.1", "[::1]", "::1"))
            container.navidrome.disconnect()
        val account = container.accountRepository.token != null
        val native = container.neteaseSessions.state.value.loggedIn
        val nav = container.navidrome.state.value.configured
        val idle = !container.playerController.state.value.playing
        val premise = "mode=$mode idle=$idle account=$account native=$native nav=$nav usbCount=${usb.deviceList.size}"
        if (account || native || nav || !idle) evidence("NO_DAC_FALLBACK_SKIP $premise")
        assumeTrue("Use an idle isolated package without project/NetEase/Navidrome accounts", !account && !native && !nav && idle)
        evidence("NO_DAC_FALLBACK_PRECONDITIONS $premise")
        val oldMusic = container.musicSettings.snapshot()
        val oldOutput = container.audioOutput.state.value
        val oldStatus = container.audioOutput.status.value
        val oldQueue = container.playbackQueue.state.value
        val oldPlayerState = container.playerController.state.value
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "cm-output-fallback-${UUID.randomUUID()}.wav")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(app.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values))
        var activity: ActivityScenario<MainActivity>? = null
        var successEvidence: String? = null
        suspend fun await(label: String, condition: () -> Boolean) {
            try { withTimeout(20_000) { while (!condition()) delay(50) } }
            catch (error: TimeoutCancellationException) {
                throw AssertionError("$mode $label: player=${container.playerController.state.value}; diagnostics=${container.audioDiagnostics.state.value}", error)
            }
        }
        try {
            // A 30-second, mono 48kHz signed PCM16 WAV; every sample is zero.
            val pcmBytes = 48_000 * 30 * 2
            val wav = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + pcmBytes)
                put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
                putShort(1); putShort(1); putInt(48_000); putInt(96_000); putShort(2); putShort(16)
                put("data".toByteArray(Charsets.US_ASCII)); putInt(pcmBytes)
            }.array()
            requireNotNull(app.contentResolver.openOutputStream(uri)).use { it.write(wav) }
            app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            container.musicSettings.setConcurrentPlayback(false)
            container.musicSettings.setTransitionMode(MusicTransitionMode.OFF)
            container.musicSettings.setPreload(false)
            container.audioOutput.setDevice(""); container.audioOutput.setUsbDevice("")
            container.audioOutput.setSampleRate(0); container.audioOutput.setBufferPolicy(AudioBufferPolicy.BALANCED)
            container.audioOutput.setMode(mode)
            await("saved output mode") { container.audioOutput.state.value.mode == mode &&
                container.audioOutput.state.value.deviceKey.isEmpty() && container.audioOutput.state.value.usbDeviceKey.isEmpty() }
            activity = ActivityScenario.launch(Intent(app, MainActivity::class.java))
            await("foreground activity") { container.appForeground.value }
            val song = Song(-System.nanoTime().coerceAtLeast(1), "Local fallback fixture", artists = "Silent PCM",
                durationMs = 30_000, musicSource = MusicSource.LOCAL, localUri = uri.toString())
            withContext(Dispatchers.Main) { container.playerController.playList(listOf(song), 0) }
            val controller = withContext(Dispatchers.Main) { container.playerController.connect() }
            await("AudioTrack recovery playing") {
                val state = container.playerController.state.value
                val diagnostics = container.audioDiagnostics.state.value
                state.song?.id == song.id && state.playing && state.error == null &&
                    diagnostics.requestedMode == mode && diagnostics.backend == "AudioTrack" &&
                    diagnostics.fallbackReason?.isNotBlank() == true && diagnostics.bitPerfect == BitPerfectEvidence.FALLBACK
            }
            val start = withContext(Dispatchers.Main) {
                assertEquals(song.id.toString(), controller.currentMediaItem?.mediaId)
                assertTrue(controller.isPlaying)
                controller.currentPosition
            }
            await("position advances after fallback") {
                container.playerController.state.value.positionMs > start + 700 && container.playerController.state.value.playing
            }
            assertEquals(song.id, container.playbackQueue.state.value.current?.id)
            assertEquals(listOf(song), container.playbackQueue.state.value.songs)
            assertEquals(mode, container.audioOutput.state.value.mode)
            val diagnostics = container.audioDiagnostics.state.value
            assertEquals(mode, diagnostics.requestedMode)
            assertEquals("AudioTrack", diagnostics.backend)
            assertTrue(diagnostics.fallbackReason.orEmpty().isNotBlank())
            assertTrue(diagnostics.processingBypass.orEmpty().contains("绕过"))
            assertTrue(diagnostics.appliedEffects.isEmpty())
            withContext(Dispatchers.Main) {
                assertEquals(song.id.toString(), controller.currentMediaItem?.mediaId)
                assertTrue(controller.isPlaying)
            }
            val reason = diagnostics.fallbackReason.orEmpty().replace(Regex("[\\r\\n\\t]"), " ")
                .replace(Regex("(?:https?|content)://\\S+"), "[redacted]").take(300)
            successEvidence = "NO_DAC_FALLBACK_PASS mode=$mode backend=${diagnostics.backend} rate=${diagnostics.output.sampleRate ?: "unknown"} reason=$reason"
        } finally {
            withContext(NonCancellable) {
                withContext(Dispatchers.Main) { container.playerController.clear(); container.playerController.disconnect() }
                app.stopService(Intent(app, MusicService::class.java))
                delay(350)
                activity?.close()
                container.audioOutput.setMode(oldOutput.mode)
                container.audioOutput.setSampleRate(oldOutput.sampleRate)
                container.audioOutput.setDevice(oldOutput.deviceKey)
                container.audioOutput.setUsbDevice(oldOutput.usbDeviceKey)
                container.audioOutput.setBufferPolicy(oldOutput.bufferPolicy)
                container.audioOutput.status.value = oldStatus
                container.musicSettings.setConcurrentPlayback(oldMusic.allowConcurrentPlayback)
                container.musicSettings.setTransitionMode(oldMusic.transitionMode)
                container.musicSettings.setPreload(oldMusic.preloadAudio)
                withContext(Dispatchers.Main) {
                    container.playbackQueue.restore(oldQueue)
                    container.playerController.state.value = oldPlayerState
                }
                app.contentResolver.delete(uri, null, null)
            }
        }
        evidence(checkNotNull(successEvidence))
    }
}
