package io.github.currencortex.music

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlaybackEqualizer
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.settings.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Silent isolated sessions; never modifies the user's queue, credentials, or output volume. */
class EqualizerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()

    private fun track() = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2)
        .setTransferMode(AudioTrack.MODE_STREAM).build()

    @Test fun nativeBandsEnableDisableAndSessionReplacementMatchSavedCurveWithoutPlayingSound() {
        val engine = PlaybackEqualizer()
        val first = track()
        val second = track()
        try {
            assertTrue("Connected phone must expose its actual equalizer engine", engine.state.value.supported)
            assertTrue(first.audioSessionId > 0)
            engine.attach(first.audioSessionId)
            assertFalse(engine.state.value.active)
            assertTrue(engine.actualLevels().isEmpty())
            val bass = EqualizerSettings(true, EqualizerPreset.BASS, EqualizerPreset.BASS.gains)
            engine.configure(bass)
            assertNull(engine.state.value.error)
            assertTrue(engine.state.value.active)
            assertFalse(engine.actualLevels().isEmpty())
            engine.actualLevels().forEach { (frequency, gain) -> assertEquals((bass.gainAt(frequency) / 100.0).roundToInt() * 100, gain) }
            engine.attach(second.audioSessionId)
            assertEquals(second.audioSessionId, engine.state.value.sessionId)
            assertTrue(engine.state.value.active)
            engine.actualLevels().forEach { (frequency, gain) -> assertEquals((bass.gainAt(frequency) / 100.0).roundToInt() * 100, gain) }
            engine.configure(bass.copy(enabled = false))
            assertFalse(engine.state.value.active)
            assertTrue(engine.actualLevels().isEmpty())
            assertEquals(AudioTrack.PLAYSTATE_STOPPED, first.playState)
            assertEquals(AudioTrack.PLAYSTATE_STOPPED, second.playState)
            engine.attach(0)
            assertEquals(0, engine.state.value.sessionId)
        } finally { engine.close(); first.release(); second.release() }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun actualPlaybackSinkSessionReceivesLiveEqualizerUpdatesSilently() = runBlocking {
        val engine = PlaybackEqualizer()
        val bass = EqualizerSettings(true, EqualizerPreset.BASS, EqualizerPreset.BASS.gains)
        val bytes = ByteBuffer.allocate(44 + 48000 * 10 * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(capacity() - 8); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(48000); putInt(96000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(capacity() - 44)
        }.array()
        val file = File(context.cacheDir, "equalizer-silent-${UUID.randomUUID()}.wav").also { it.writeBytes(bytes) }
        var player: ExoPlayer? = null
        try {
            withContext(Dispatchers.Main) {
                engine.configure(bass)
                player = ExoPlayer.Builder(context, io.github.currencortex.music.core.media.DockRenderersFactory(context,
                    io.github.currencortex.music.core.media.DockAudioLevel())).build().apply {
                    // Silent samples, muted player, and no audio focus request: user's playback stays untouched.
                    setAudioAttributes(androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA).build(), false)
                    volume = 0f
                    addListener(object : Player.Listener {
                        override fun onAudioSessionIdChanged(audioSessionId: Int) { engine.attach(audioSessionId) }
                    })
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file)))
                    prepare(); play()
                }
            }
            withTimeout(10000) { while (!engine.state.value.active) delay(25) }
            withContext(Dispatchers.Main) {
                assertEquals(player!!.audioSessionId, engine.state.value.sessionId)
                assertNull(player!!.playerError)
                val custom = bass.withBand(2, -300)
                engine.configure(custom)
                assertTrue(engine.state.value.active)
                engine.actualLevels().forEach { (frequency, gain) ->
                    assertEquals((custom.gainAt(frequency) / 100.0).roundToInt() * 100, gain)
                }
                engine.configure(custom.copy(enabled = false))
                assertFalse(engine.state.value.active)
                assertTrue(engine.actualLevels().isEmpty())
            }
        } finally {
            withContext(Dispatchers.Main) { player?.release(); engine.close() }
            check(file.parentFile == context.cacheDir && file.name.startsWith("equalizer-silent-"))
            file.delete()
        }
    }

    @Test fun presetsCustomSlidersResetAndRestorationPersistWithoutChangingAccountOrQueue() = runBlocking {
        val container = AppContainer(context, "equalizer-test-${UUID.randomUUID()}")
        try {
            container.ready.await(); container.sessionRestored.await()
            val originalQueue = container.playbackQueue.state.value
            val originalAccount = container.accountRepository.state.value
            val vm = EqualizerViewModel(container)
            val restoration = StateRestorationTester(compose)
            restoration.setContent { LeiTheme(AppearanceSettings()) { EqualizerScreen(vm) } }
            compose.onNodeWithTag("equalizer_enabled").performClick()
            compose.waitUntil(5000) { vm.settings.value.enabled }
            compose.onNodeWithTag("equalizer_preset_bass").performClick()
            compose.waitUntil(5000) { vm.settings.value.preset == EqualizerPreset.BASS }
            compose.onNodeWithTag("equalizer_screen").performScrollToNode(hasTestTag("equalizer_band_2"))
            compose.onNodeWithTag("equalizer_band_2").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(4.5f) }
            compose.waitUntil(5000) { vm.settings.value.gains[2] == 500 && vm.settings.value.preset == EqualizerPreset.CUSTOM }
            restoration.emulateSavedInstanceStateRestore()
            val saved = container.equalizerSettings.snapshot()
            assertTrue(saved.enabled)
            assertEquals(500, saved.gains[2])
            assertEquals(EqualizerPreset.CUSTOM, saved.preset)
            assertEquals(originalQueue, container.playbackQueue.state.value)
            assertEquals(originalAccount, container.accountRepository.state.value)
            compose.onNodeWithTag("equalizer_screen").performScrollToNode(hasTestTag("equalizer_reset"))
            compose.onNodeWithTag("equalizer_reset").performClick()
            compose.waitUntil(5000) { vm.settings.value.gains.all { it == 0 } && vm.settings.value.preset == EqualizerPreset.FLAT }
            assertTrue(vm.settings.value.enabled)
            compose.onNodeWithTag("equalizer_enabled").performScrollTo().performClick()
            compose.waitUntil(5000) { !vm.settings.value.enabled }
        } finally { container.close() }
    }
}
