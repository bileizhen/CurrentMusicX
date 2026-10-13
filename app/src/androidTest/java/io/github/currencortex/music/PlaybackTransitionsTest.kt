package io.github.currencortex.music

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.data.settings.MusicTransitionMode
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackTransitionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private lateinit var engine: PlaybackTransitions
    private val live = mutableListOf<ExoPlayer>()
    private val first = Song(1, "first", durationMs = 24000)
    private val second = Song(2, "second", durationMs = 24000)
    private var current = first
    private var promotions = 0
    private var mode = MusicTransitionMode.CROSSFADE
    private var unavailable = false
    private var slow = false
    private var offerEnabled = true
    private var resolved = 0
    private var probe = TrackEnergyProbe()
    private fun item(song: Song) = MediaItem.Builder().setMediaId(song.id.toString()).setUri(Uri.fromFile(file)).build()
    @Before fun setup() {
        val frames = 8000 * 24
        val wave = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        wave.put("RIFF".toByteArray()).putInt(36 + frames * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(frames * 2)
        // Silence lets actual native audio tracks run without changing the user's system volume.
        file = File.createTempFile("transition-", ".wav", context.cacheDir).apply { writeBytes(wave.array()) }
        main {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            fun create(features: TrackEnergyProbe): ExoPlayer = ExoPlayer.Builder(context, DockRenderersFactory(context, DockAudioLevel(), features))
                .build().also { source ->
                    live += source
                    source.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (::engine.isInitialized && source === engine.player && !isPlaying) engine.pauseCompanion()
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            if (::engine.isInitialized && state == Player.STATE_ENDED) engine.ended(source)
                        }
                    })
                }
            val initial = create(probe)
            engine = PlaybackTransitions(initial, probe, scope, ::create, offer = {
                if (offerEnabled && current == first) TransitionOffer(first, second, AudioRequest(2, AudioQuality.AUTO, 0, RequestSession("", null))) else null
            }, resolve = {
                resolved++
                if (slow) delay(30_000)
                if (unavailable) null else item(second)
            }, promote = { song, _, _ -> current = song; promotions++ }, discard = { live.remove(it) }, currentSong = { current })
            engine.tick(mode, 5)
            initial.volume = .8f
            initial.setMediaItem(item(first), 18_000); initial.prepare(); initial.play()
        }
    }
    @After fun cleanup() {
        main { scope.cancel(); engine.close(); live.toList().forEach { it.release() }; live.clear() }
        file.delete()
    }
    private fun await(label: String, timeoutMs: Long = 10_000, predicate: () -> Boolean) {
        val until = android.os.SystemClock.elapsedRealtime() + timeoutMs
        var passed = false
        var details = ""
        while (!passed && android.os.SystemClock.elapsedRealtime() < until) {
            main {
                engine.tick(mode, 5)
                passed = predicate()
                details = live.joinToString { "id=${it.currentMediaItem?.mediaId} state=${it.playbackState} play=${it.isPlaying} pos=${it.currentPosition} gain=${it.volume} error=${it.playerError}" }
            }
            if (!passed) Thread.sleep(30)
        }
        assertTrue("$label: $details", passed)
    }
    @Test fun nativeOverlapPromotesOnceAndReleasesOutgoingWithContinuousIncomingPosition() {
        await("both native players overlap") { engine.active && live.size == 2 && live.all { it.isPlaying && it.volume in .1f.. .79f } }
        main {
            assertEquals(first, current)
            val power = live.sumOf { (it.volume * it.volume).toDouble() }
            assertEquals(.64, power, .015)
        }
        var positionAtHandoff = 0L
        await("queue handoff") { (promotions == 1).also { if (it) positionAtHandoff = engine.player.currentPosition } }
        assertTrue(positionAtHandoff in 1500..4000)
        await("outgoing released") { !engine.active && live.size == 1 }
        main {
            assertEquals(second, current); assertEquals(1, promotions)
            assertTrue(engine.player.currentPosition > positionAtHandoff)
            assertTrue(engine.player.isPlaying); assertEquals(.8f, engine.player.volume, .0001f)
        }
    }
    @Test fun pauseFreezesBothStreamsAndResumeThenSeekCancellationLeavesOnlyPrimary() {
        await("overlap") { engine.active && live.size == 2 && live.all { it.isPlaying && it.volume > .1f } }
        var positions = emptyList<Long>()
        var volumes = emptyList<Float>()
        main { engine.player.pause(); engine.pauseCompanion() }
        Thread.sleep(120)
        main { engine.tick(mode, 5); positions = live.map { it.currentPosition }; volumes = live.map { it.volume } }
        Thread.sleep(350)
        main {
            engine.tick(mode, 5)
            assertTrue(live.none { it.isPlaying })
            live.forEachIndexed { i, p -> assertTrue(kotlin.math.abs(p.currentPosition - positions[i]) < 80); assertEquals(volumes[i], p.volume, .0001f) }
            engine.player.play()
        }
        await("resumed overlap") { live.all { it.isPlaying } }
        main {
            engine.cancel(); engine.player.seekTo(5000)
            assertEquals(1, live.size); assertFalse(engine.active)
            assertEquals(.8f, engine.player.volume, .0001f); assertEquals(0, promotions)
        }
    }
    @Test fun unavailableSourceAndCanceledResolutionDoNotAdvanceQueueOrLeakPlayers() {
        main { unavailable = true }
        await("failed resolution released") { resolved > 0 && live.size == 1 }
        main { assertEquals(0, promotions); assertEquals(first, current); assertTrue(engine.player.isPlaying) }
        main { mode = MusicTransitionMode.OFF; engine.tick(mode, 5); unavailable = false; slow = true; mode = MusicTransitionMode.CROSSFADE }
        await("resolution pending") { resolved == 2 && live.size == 2 }
        main { offerEnabled = false; engine.tick(mode, 5); assertEquals(1, live.size); assertEquals(0, promotions) }
    }
    @Test fun disablingAfterMidpointKeepsIncomingAndRestoresItsNormalGain() {
        await("handoff") { promotions == 1 && engine.active }
        main {
            mode = MusicTransitionMode.OFF; engine.tick(mode, 5)
            assertFalse(engine.active); assertEquals(1, live.size)
            assertEquals("2", engine.player.currentMediaItem?.mediaId)
            assertEquals(.8f, engine.player.volume, .0001f); assertTrue(engine.player.isPlaying)
        }
    }
    @Test fun failedIncomingDuringOverlapRestoresOutgoingAndDoesNotAdvanceQueue() {
        await("overlap") { engine.active && live.size == 2 && live.all { it.isPlaying && it.volume > .1f } }
        main {
            assertTrue(engine.failed(live[1]))
            assertFalse(engine.active); assertEquals(1, live.size)
            assertEquals(first, current); assertEquals(0, promotions)
            assertTrue(engine.player.isPlaying); assertEquals(.8f, engine.player.volume, .0001f)
            engine.tick(mode, 5); assertEquals(1, live.size)
        }
    }
    @Test fun smartModePreviewsMutedAudioAndHandsOffWithoutCountingPreview() {
        main { mode = MusicTransitionMode.SMART; engine.player.seekTo(14_000); engine.tick(mode, 5) }
        await("muted preview decoding") { live.size == 2 && live[1].isPlaying && live[1].volume == 0f }
        main { assertEquals(0, promotions); assertEquals(first, current) }
        await("smart overlap", 15000) { engine.active && live.size == 2 && live.all { it.isPlaying && it.volume > .1f } }
        main { assertNotNull(engine.probe.profile()) }
        await("smart handoff and cleanup") { promotions == 1 && live.size == 1 }
    }
    @Test fun concurrentPolicyChangesNativeAudioFocusWithoutRecreatingPlayer() {
        lateinit var policy: PlaybackAudioFocus
        val allowed = MutableStateFlow(false)
        val manager = context.getSystemService(android.media.AudioManager::class.java)
        val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { }.build()
        try {
            main { mode = MusicTransitionMode.OFF; engine.tick(mode, 5); policy = PlaybackAudioFocus(engine.player, allowed, scope) }
            await("focus initialized") { policy.ready.isCompleted && engine.player.isPlaying }
            main { assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(request)) }
            await("exclusive policy pauses on competing focus") { !engine.player.playWhenReady }
            main { manager.abandonAudioFocusRequest(request); allowed.value = true }
            Thread.sleep(100)
            main { engine.player.play() }
            await("concurrent resumed") { engine.player.isPlaying }
            main { assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(request)) }
            Thread.sleep(350)
            main { assertTrue(engine.player.isPlaying); assertEquals(1, live.size) }
        } finally { main { manager.abandonAudioFocusRequest(request) } }
    }
}
