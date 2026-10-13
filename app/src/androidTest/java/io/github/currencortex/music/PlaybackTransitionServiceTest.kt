package io.github.currencortex.music

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.settings.MusicTransitionMode
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList

/** Exercises the actual service, business queue, session replacement, lyrics and listening reports. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackTransitionServiceTest {
    @Test fun automaticOverlapKeepsSessionTransportLyricsAndSingleListeningReports(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName.endsWith(".verification"))
        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val container = app.container
        container.ready.await(); container.navidrome.ready.await()
        check(!container.navidrome.state.value.configured && !container.playerController.state.value.playing)
        val settings = container.musicSettings.snapshot()
        val reports = CopyOnWriteArrayList<String>()
        val bytes = 8000 * 30 * 2
        val wav = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + bytes); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(bytes)
        }.array()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath.substringAfterLast('/')
                    if (path == "stream.view") return MockResponse().setHeader("Content-Type", "audio/wav").setBody(Buffer().write(wav))
                    if (path == "scrobble.view" && request.requestUrl!!.queryParameter("submission") == "false")
                        reports += request.requestUrl!!.queryParameter("id")!!
                    val data = when (path) {
                        "ping.view" -> "\"serverVersion\":\"transition-test\""
                        "getUser.view" -> "\"user\":{\"downloadRole\":false}"
                        "getAlbum.view" -> """"album":{"song":[{"id":"fade-1","title":"过渡一","duration":30,"suffix":"wav"},{"id":"fade-2","title":"过渡二","duration":30,"suffix":"wav"}]}"""
                        "getLyricsBySongId.view" -> """"lyricsList":{"structuredLyrics":[{"synced":true,"line":[{"start":0,"value":"${request.requestUrl!!.queryParameter("id")}"}]}]}"""
                        else -> ""
                    }
                    return MockResponse().setBody("""{"subsonic-response":{"status":"ok"${if(data.isBlank()) "" else ",$data"}}}""")
                }
            }
            start()
        }
        suspend fun await(label: String, condition: () -> Boolean) {
            try { withTimeout(15000) { while (!condition()) delay(50) } }
            catch (e: TimeoutCancellationException) { throw AssertionError("$label: ${container.playerController.state.value}", e) }
        }
        try {
            container.musicSettings.setConcurrentPlayback(false)
            container.musicSettings.setTransitionMode(MusicTransitionMode.CROSSFADE)
            container.musicSettings.setCrossfadeSeconds(5)
            container.navidrome.connect(server.url("/").toString(), "fixture", "fixture-password")
            val songs = container.navidrome.album("album")
            withContext(Dispatchers.Main) { container.playerController.playList(songs, 0) }
            await("first playing") { container.playerController.state.value.playing && "fade-1" in reports }
            val controller = withContext(Dispatchers.Main) { container.playerController.connect() }
            val boundaries = container.playerController.completedTracks.value
            withContext(Dispatchers.Main) { controller.seekTo(23_000) }
            await("automatic handoff") { container.playbackQueue.state.value.current?.id == songs[1].id && container.playerController.state.value.playing }
            await("new lyrics") { container.playbackLyrics.state.value.document.lines.firstOrNull()?.text == "fade-2" }
            await("new listening report") { "fade-2" in reports }
            withContext(Dispatchers.Main) {
                assertEquals(songs[1].id.toString(), controller.currentMediaItem?.mediaId)
                assertTrue(controller.currentPosition in 1500..6500)
                assertEquals(boundaries + 1, container.playerController.completedTracks.value)
                controller.pause()
            }
            await("pause through existing controller") { !container.playerController.state.value.playing }
            // The service publishes its UI pause state before the asynchronous session
            // position acknowledgement arrives. Wait for the controller position to settle
            // before measuring drift, while still requiring the transport to be paused.
            val stopped = withTimeout(2000) {
                var previous = withContext(Dispatchers.Main) { controller.currentPosition }
                var stableSamples = 0
                while (stableSamples < 2) {
                    delay(100)
                    val sample = withContext(Dispatchers.Main) {
                        Triple(controller.currentPosition, controller.isPlaying, controller.playWhenReady)
                    }
                    stableSamples = if (!sample.second && !sample.third &&
                        kotlin.math.abs(sample.first - previous) < 20) stableSamples + 1 else 0
                    previous = sample.first
                }
                previous
            }
            delay(400)
            withContext(Dispatchers.Main) {
                assertFalse(controller.isPlaying)
                assertFalse(controller.playWhenReady)
                assertTrue("Paused position changed from $stopped to ${controller.currentPosition}",
                    kotlin.math.abs(controller.currentPosition - stopped) < 100)
                controller.play()
            }
            await("resume through existing controller") { container.playerController.state.value.playing }
            delay(3000)
            assertEquals(listOf("fade-1", "fade-2"), reports.toList())
            withContext(Dispatchers.Main) { controller.seekToPreviousMediaItem() }
            await("manual previous after handoff") { container.playbackQueue.state.value.current?.id == songs[0].id && container.playerController.state.value.playing }
        } finally {
            withContext(Dispatchers.Main) { container.playerController.clear(); container.playerController.disconnect() }
            app.stopService(Intent(app, MusicService::class.java))
            delay(300)
            container.musicSettings.setTransitionMode(settings.transitionMode)
            container.musicSettings.setCrossfadeSeconds(settings.crossfadeSeconds)
            container.musicSettings.setConcurrentPlayback(settings.allowConcurrentPlayback)
            container.navidrome.disconnect(); server.shutdown()
        }
    }
}
