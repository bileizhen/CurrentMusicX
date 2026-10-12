package io.github.currencortex.music

import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.PlaybackParameters
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.MusicService
import io.github.currencortex.music.feature.lyrics.timeline.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real MusicService/Media3 playback; only isolated credentials and a local server are used. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DesktopLyricsPlaybackTest {
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private fun shell(command: String) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
        java.io.FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
    }
    private suspend fun await(condition: () -> Boolean) = withTimeout(15000) { while (!condition()) delay(50) }
    @Test fun lateLyricsLoadInBackgroundAndPauseSeekSpeedAndTrackUseTheServiceClock(): Unit = runBlocking {
        check(context.packageName.endsWith(".verification"))
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val container = context.container
        val release = CountDownLatch(1)
        val requested = CountDownLatch(1)
        // Silence decodes through the real audio pipeline without disturbing the user's audio.
        val count = 44100 * 30 * 2
        val wav = ByteBuffer.allocate(44 + count).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + count); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(44100); putInt(88200); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(count)
        }.array()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath.substringAfterLast('/')
                    if (path == "stream.view") return MockResponse().setHeader("Content-Type", "audio/wav").setBody(Buffer().write(wav))
                    val data = when (path) {
                        "ping.view" -> "\"serverVersion\":\"desktop-test\""
                        "getUser.view" -> "\"user\":{\"downloadRole\":false}"
                        "getAlbum.view" -> """"album":{"song":[{"id":"desktop-1","title":"桌面后台验证","duration":30,"suffix":"wav"},{"id":"desktop-2","title":"下一首验证","duration":30,"suffix":"wav"}]}"""
                        "getLyricsBySongId.view" -> {
                            if (request.requestUrl!!.queryParameter("id") == "desktop-1") { requested.countDown(); release.await(12, TimeUnit.SECONDS) }
                            """"lyricsList":{"structuredLyrics":[{"synced":true,"line":[{"start":0,"value":"${request.requestUrl!!.queryParameter("id")}"},{"start":5000,"value":"后台共享歌词"},{"start":10000,"value":"播放器时间同步"}]}]}"""
                        }
                        else -> ""
                    }
                    return MockResponse().setBody("""{"subsonic-response":{"status":"ok"${if(data.isBlank()) "" else ",$data"}}}""")
                }
            }
            start()
        }
        try {
            container.ready.await(); container.navidrome.ready.await(); container.desktopLyrics.ready.await()
            container.desktopLyrics.edit { it.copy(enabled = android.provider.Settings.canDrawOverlays(context), locked = true) }
            container.navidrome.connect(server.url("/").toString(), "fixture", "fixture-password")
            val tracks = container.navidrome.album("album")
            withContext(Dispatchers.Main) { container.playerController.playList(tracks, 0) }
            assertTrue(requested.await(10, TimeUnit.SECONDS))
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            shell("input keyevent 3")
            release.countDown()
            await { container.playbackLyrics.state.value.document.lines.isNotEmpty() }
            await { container.desktopLyricsStatus.value.visible }
            assertEquals("desktop-1", container.playbackLyrics.state.value.document.lines.first().text)
            await { container.playerController.state.value.playing }
            val controller = withContext(Dispatchers.Main) { container.playerController.connect() }
            withContext(Dispatchers.Main) { container.playerController.pause() }
            delay(400)
            val stopped = container.playerController.state.value
            val clock = LyricsPlaybackClock()
            clock.update(stopped.song?.id, PlaybackAnchor(stopped.positionMs, stopped.anchorRealtimeMs, stopped.playing && !stopped.loading,
                stopped.durationMs, stopped.playbackSpeed, stopped.timelineRevision))
            assertEquals(stopped.positionMs, clock.positionAt(SystemClock.elapsedRealtime() + 1000))
            val revision = stopped.timelineRevision
            withContext(Dispatchers.Main) { container.playerController.seek(7000) }
            await { container.playerController.state.value.positionMs in 6950..7050 }
            assertTrue(container.playerController.state.value.timelineRevision > revision)
            withContext(Dispatchers.Main) { controller.playbackParameters = PlaybackParameters(2f); container.playerController.toggle() }
            await { container.playerController.state.value.playing && container.playerController.state.value.playbackSpeed == 2f }
            val initial = withContext(Dispatchers.Main) { controller.currentPosition }
            delay(500)
            val advanced = withContext(Dispatchers.Main) { controller.currentPosition }
            assertTrue("Real 2x decoder must advance roughly one second", advanced - initial in 700..1400)
            withContext(Dispatchers.Main) { container.playerController.next() }
            await { container.playbackLyrics.state.value.songId == tracks[1].id && container.playbackLyrics.state.value.document.lines.isNotEmpty() }
            assertEquals("desktop-2", container.playbackLyrics.state.value.document.lines.first().text)
        } finally {
            release.countDown()
            withContext(Dispatchers.Main) { container.playerController.clear(); container.playerController.disconnect() }
            context.stopService(Intent(context, MusicService::class.java))
            await { !container.desktopLyricsStatus.value.attached }
            container.desktopLyrics.edit { it.copy(enabled = false) }
            container.navidrome.disconnect(); server.shutdown()
        }
    }
}
