package io.github.currencortex.music

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.currencortex.music.core.download.*
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.song.AudioSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class SongDownloadUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun songMenuStartsRealForegroundWorkRetriesAndRemembersCompletedDownload(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val server = MockWebServer().apply { start() }
        val attempts = AtomicInteger()
        val selected = java.util.concurrent.atomic.AtomicReference<AudioQuality>()
        val audio = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("download/fixture.mp3").use { it.readBytes() }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/audio" -> if (attempts.incrementAndGet() == 1) MockResponse().setResponseCode(502)
                    else MockResponse().setBody(Buffer().write(audio))
                "/cm/ncm/lyric" -> MockResponse().setBody("""{"lines":[{"t":0,"txt":"内嵌歌词验证"}]}""")
                "/cm/daily" -> MockResponse().setBody("{}")
                "/cm/room/active" -> MockResponse().setBody("""{"room":null}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val song = Song(990901, "界面下载验证-${UUID.randomUUID()}", "测试歌手", "测试专辑")
        val temp = File(context.cacheDir, "download-ui-${UUID.randomUUID()}")
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? {
                if (workerClassName != SongDownloadWorker::class.java.name) return null
                return object : SongDownloadWorker(appContext, workerParameters) {
                    override suspend fun createEngine() = SongDownloadEngine(temp,
                        { _, quality -> selected.set(quality); AudioSource(server.url("/audio").toString(), quality.value, 44100, 1) },
                        { song }, { LyricsDocument(listOf(LyricLine(0, 600, "内嵌歌词验证"))) }, SongDownloadStore(appContext))
                }
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build())
        val work = WorkManager.getInstance(context)
        val container = AppContainer(context, "download-ui-${UUID.randomUUID()}")
        val ids = mutableListOf<UUID>()
        try {
            container.ready.await(); container.sessionRestored.await()
            container.musicSettings.setServer(server.url("/cm/").toString())
            container.accountRepository.server = server.url("/cm/").toString()
            container.updateSettings.setAutoCheck(false)
            container.playbackQueue.replace(listOf(song), 0)
            container.playerController.state.value = PlayerState(song = song, durationMs = 600)
            compose.setContent { CurrentMusicApp(container) }
            compose.onNodeWithTag("mini_cover").performClick()
            fun openDownload() {
                compose.onNodeWithTag("lyrics_options").performClick()
                compose.onNodeWithText("歌曲操作").performClick()
                compose.onNodeWithTag("song_download").performClick()
                compose.onNodeWithTag("song_download_dialog").assertIsDisplayed()
            }
            fun constraints() {
                val job = work.getWorkInfosByTag("song-download").get().first { !it.state.isFinished }
                ids += job.id
                WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(job.id)
            }
            openDownload()
            compose.onNodeWithTag("song_download_quality").performClick()
            // The download picker is the same sheet as the playback quality picker.
            compose.onNodeWithTag("quality_choices").assertIsDisplayed()
            compose.onNodeWithTag("quality_LOSSLESS").performClick()
            compose.onNodeWithTag("song_download_start").performClick(); constraints()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("song_download_error").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("song_download_error").assertTextContains("502", substring = true)
            compose.onNodeWithTag("song_download_start").assertTextContains("重试下载").performClick(); constraints()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("song_download_complete").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(AudioQuality.LOSSLESS, selected.get()); assertEquals(2, attempts.get())
            compose.onNodeWithText("关闭").performClick()
            compose.onNodeWithTag("song_download_dialog").assertDoesNotExist()
            openDownload()
            compose.onNodeWithTag("song_download_complete").assertIsDisplayed()
            assertEquals(song.id, container.playbackQueue.state.value.current!!.id)
            assertFalse(container.playerController.state.value.playing)
            // Deleting one song must not prune another completed task's record.
            val keep = OneTimeWorkRequestBuilder<KeepDownloadRecordWorker>().build()
            work.enqueue(keep).result.get(); ids += keep.id
            compose.waitUntil(5000) { work.getWorkInfoById(keep.id).get()?.state == WorkInfo.State.SUCCEEDED }
            // Deleting removes the published files and returns the dialog to its initial state.
            val published = work.getWorkInfosByTag("song-download").get()
                .flatMap { it.outputData.getStringArray("files").orEmpty().toList() }
            assertTrue("Expected published files before deleting", published.isNotEmpty())
            compose.onNodeWithTag("song_download_delete").assertIsDisplayed().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("song_download_complete").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("song_download_dialog").assertIsDisplayed()
            compose.onNodeWithTag("song_download_start").assertTextContains("开始下载")
            assertNotNull("Other completed tasks must remain queryable", work.getWorkInfoById(keep.id).get())
            published.forEach { uri ->
                val remaining = runCatching {
                    context.contentResolver.query(Uri.parse(uri), null, null, null, null)?.use { it.count } ?: 0
                }.getOrDefault(0)
                assertEquals("Deleted file must be gone: $uri", 0, remaining)
            }
        } finally {
            for (id in ids) {
                val info = work.getWorkInfoById(id).get()
                info?.outputData?.getStringArray("files")?.forEach { context.contentResolver.delete(Uri.parse(it), null, null) }
                work.cancelWorkById(id).result.get()
            }
            container.close(); server.shutdown(); temp.deleteRecursively()
        }
    }
}

class KeepDownloadRecordWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = Result.success()
}
