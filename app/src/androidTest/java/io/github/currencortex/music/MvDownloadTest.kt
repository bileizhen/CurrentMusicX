package io.github.currencortex.music

import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.download.*
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class MvDownloadTest {
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private fun fixture() = InstrumentationRegistry.getInstrumentation().context.assets.open("download/fixture.mp4").use { it.readBytes() }
    private val song = Song(-999901, "MV 下载验证", "测试歌手", mv = 999901, video = true)

    @Test fun downloadsOriginalVideoWithAudioAndPublishesActualResolution(): Unit = runBlocking {
        val server = MockWebServer().apply { start(); enqueue(MockResponse().setBody(Buffer().write(fixture()))) }
        val temp = File(context.cacheDir, "mv-test-${UUID.randomUUID()}")
        try {
            val phases = mutableListOf<SongDownloadProgress>()
            val engine = MvDownloadEngine(temp, { id, quality ->
                assertEquals(song.mv, id); assertEquals(720, quality); server.url("/video").toString()
            }, SongDownloadStore(context))
            val result = engine.download(song, 720, null, UUID.randomUUID().toString()) { phases += it }
            try {
                assertEquals(360, result.height)
                assertTrue(result.saved.name.endsWith("MV 360P.mp4"))
                assertArrayEquals(fixture(), context.contentResolver.openInputStream(result.saved.audioUri)!!.use { it.readBytes() })
                MediaMetadataRetriever().use {
                    it.setDataSource(context, result.saved.audioUri)
                    assertEquals("yes", it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                    assertEquals("yes", it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                }
                assertTrue(phases.any { it.received > 0 && it.received == it.total })
                val request = server.takeRequest(3, TimeUnit.SECONDS)!!
                assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
            } finally { result.saved.files.forEach { context.contentResolver.delete(it, null, null) } }
            assertTrue(temp.listFiles().orEmpty().isEmpty())
        } finally { server.shutdown(); temp.deleteRecursively() }
    }

    @Test fun badResponsesCancellationAndAccountChangesLeaveNoCompletedFiles(): Unit = runBlocking {
        val server = MockWebServer().apply { start() }
        val temp = File(context.cacheDir, "mv-test-${UUID.randomUUID()}")
        var allowed = true
        val engine = MvDownloadEngine(temp, { _, _ -> server.url("/video").toString() }, SongDownloadStore(context), { check(allowed) })
        try {
            for (response in listOf(MockResponse().setResponseCode(403), MockResponse().setBody("<html>expired source</html>"),
                MockResponse().setBody(Buffer().write(InstrumentationRegistry.getInstrumentation().context.assets.open("download/fixture.m4a").use { it.readBytes() })))) {
                server.enqueue(response)
                try { engine.download(song, 1080, null, UUID.randomUUID().toString()) {}; fail("Invalid video must fail") }
                catch (_: IllegalStateException) { }
                assertTrue(temp.listFiles().orEmpty().isEmpty())
            }
            server.enqueue(MockResponse().setBody(Buffer().write(fixture())).throttleBody(256, 100, TimeUnit.MILLISECONDS))
            val started = CompletableDeferred<Unit>()
            val job = launch { engine.download(song, 1080, null, UUID.randomUUID().toString()) { if (it.received > 0) started.complete(Unit) } }
            withTimeout(5000) { started.await() }; withTimeout(3000) { job.cancelAndJoin() }
            assertTrue(temp.listFiles().orEmpty().isEmpty())
            server.enqueue(MockResponse().setBody(Buffer().write(fixture())))
            try {
                engine.download(song, 1080, null, UUID.randomUUID().toString()) { if (it.received > 0 && it.received == it.total) allowed = false }
                fail("Changed account must not publish")
            } catch (_: IllegalStateException) { }
            assertTrue(temp.listFiles().orEmpty().isEmpty())
        } finally { server.shutdown(); temp.deleteRecursively() }
    }

    @Test fun qualityKeepsVideoPositionAndReturningRestoresMusicPosition(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val queue = PlaybackQueue()
        val player = PlayerController(context, queue, scope, createController = { awaitCancellation() })
        try { withContext(Dispatchers.Main) {
            val music = Song(998801, "返回原歌曲")
            queue.replace(listOf(music), 0)
            player.state.value = PlayerState(song = music, playing = true, positionMs = 42000)
            player.playVideo(song)
            player.state.value = PlayerState(song = song, playing = false, positionMs = 17000)
            player.videoQuality(480)
            assertEquals(480, queue.state.value.current?.videoQuality)
            assertEquals(17000, player.state.value.positionMs)
            assertFalse(player.state.value.showPause)
            player.closeVideo()
            assertEquals(music, queue.state.value.current)
            assertEquals(42000, player.state.value.positionMs)
            assertFalse(player.state.value.showPause)
        } } finally { scope.cancel(); withContext(Dispatchers.Main) { player.disconnect() } }
    }
}
