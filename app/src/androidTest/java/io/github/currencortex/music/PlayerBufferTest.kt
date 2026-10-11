package io.github.currencortex.music

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerBufferTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun seekTrackDistinguishesPlayedBufferedAndUnloadedWithoutDraggingTheBuffer() {
        var position by mutableFloatStateOf(.2f)
        var buffered by mutableFloatStateOf(.65f)
        var seeks = 0
        compose.setContent {
            Box(Modifier.width(300.dp).background(Color.Black)) {
                PlayerSeekBar(position, true, { position = it }, { position = it; seeks++ }, {}, bufferedValue = buffered)
            }
        }
        val bar = compose.onNodeWithTag("player_seek")
        val pixels = bar.captureToImage().toPixelMap()
        fun brightness(fraction: Float) = pixels[(pixels.width * fraction).toInt(), pixels.height / 2].red
        assertTrue(brightness(.1f) > brightness(.4f) + .2f)
        assertTrue(brightness(.4f) > brightness(.85f) + .15f)
        compose.mainClock.autoAdvance = false
        bar.performTouchInput { down(percentOffset(.25f, .5f)); moveTo(percentOffset(.85f, .5f)) }
        compose.mainClock.advanceTimeBy(64)
        assertTrue(bar.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current > .8f)
        assertEquals(.65f, bar.fetchSemanticsNode().config[PlayerBufferedProgress], .001f)
        bar.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(1, seeks)
        compose.runOnIdle { buffered = .9f }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(.9f, bar.fetchSemanticsNode().config[PlayerBufferedProgress], .001f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun changingTrackClearsPreviousBufferImmediately() {
        var track by mutableIntStateOf(1)
        var buffered by mutableFloatStateOf(.8f)
        compose.setContent {
            Box(Modifier.width(300.dp).background(Color.Black)) {
                key(track) { PlayerSeekBar(0f, true, {}, {}, {}, bufferedValue = buffered) }
            }
        }
        compose.runOnIdle { track = 2; buffered = 0f }
        val bar = compose.onNodeWithTag("player_seek")
        assertEquals(0f, bar.fetchSemanticsNode().config[PlayerBufferedProgress], 0f)
        val pixels = bar.captureToImage().toPixelMap()
        assertTrue("New track must not flash the previous track's loaded segment", pixels[pixels.width / 2, pixels.height / 2].red < .25f)
    }

    @Test fun actualExoPlayerBufferIsPublishedAndStaleTrackOrResolutionClearsIt() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val queue = PlaybackQueue()
        val controller = PlayerController(compose.activity, queue, scope)
        lateinit var player: ExoPlayer
        var playerCreated = false
        val duration = 20_000L
        val bytes = ByteBuffer.allocate(44 + 8_000 * 20 * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(bytes.capacity() - 8).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(bytes.capacity() - 44)
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "audio/wav").setBody(Buffer().write(bytes.array()))
                .throttleBody(16_000, 100, TimeUnit.MILLISECONDS))
            server.start()
            val sourceUrl = server.url("/audio.wav").toString()
            try {
                compose.runOnUiThread {
                    queue.replace(listOf(Song(id = 1, name = "Buffer fixture", durationMs = duration)), 0)
                    player = ExoPlayer.Builder(compose.activity).build()
                    playerCreated = true
                    player.apply {
                        volume = 0f
                        addListener(object : Player.Listener {
                            override fun onEvents(player: Player, events: Player.Events) { controller.publish(player) }
                        })
                        setMediaItem(MediaItem.Builder().setMediaId("1").setUri(sourceUrl).build())
                        prepare()
                    }
                }
                compose.waitUntil(10_000) {
                    compose.runOnUiThread { controller.publish(player) }
                    controller.state.value.bufferedPositionMs > 0
                }
                compose.runOnUiThread {
                    assertEquals(player.bufferedPosition, controller.state.value.bufferedPositionMs)
                    assertTrue(controller.state.value.bufferedProgress() > 0f)
                    controller.state.value = controller.state.value.copy(resolving = true)
                    controller.publish(player)
                    assertEquals(0L, controller.state.value.bufferedPositionMs)
                    controller.state.value = controller.state.value.copy(resolving = false)
                    queue.replace(listOf(Song(id = 2, name = "Next track", durationMs = duration)), 0)
                    controller.publish(player)
                    assertEquals(2L, controller.state.value.song!!.id)
                    assertEquals(0L, controller.state.value.bufferedPositionMs)
                }
            } finally {
                compose.runOnUiThread { if (playerCreated) player.release() }
                scope.cancel()
            }
        }
    }
}
