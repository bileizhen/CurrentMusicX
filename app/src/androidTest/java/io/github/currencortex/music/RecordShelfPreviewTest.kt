package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.RecordShelfPreviewMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.PlaylistRecordShelf
import io.github.currencortex.music.feature.library.RecordShelfPreview
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RecordShelfPreviewTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var preview: RecordShelfPreview
    private lateinit var server: MockWebServer
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private val samples = mutableListOf<String>()

    @Before fun setup() = runBlocking {
        container = AppContainer(context, "shelf-audio-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        val audio = tone()
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/cm/ncm/song/chorus" -> MockResponse().setBody("""{"chorus":[{"id":${request.requestUrl!!.queryParameter("id")},"startTime":2000,"endTime":5500}]}""")
                    "/cm/ncm/song/url" -> MockResponse().setBody("""{"url":"${url("/tone.wav")}","type":"wav","sr":8000,"ch":1}""")
                    "/tone.wav" -> MockResponse().setHeader("Content-Type", "audio/wav").setBody(Buffer().write(audio))
                    else -> MockResponse().setResponseCode(404)
                }
            }
            start()
        }
        container.musicSettings.setServer(server.url("/cm/").toString())
        container.accountRepository.server = server.url("/cm/").toString()
        container.accountRepository.save("fixture", UserDto(7))
        container.playbackQueue.replace(listOf(Song(99, "Original")), 0)
        compose.runOnUiThread { preview = RecordShelfPreview(context, container) }
    }
    @After fun cleanup() {
        if (::preview.isInitialized) {
            compose.runOnUiThread { preview.close() }
            compose.waitUntil(3000) { preview.released }
        }
        java.io.File(context.getExternalFilesDir(null), "record-shelf-audio-gain.csv").writeText(samples.joinToString("\n"))
        container.close(); server.shutdown()
    }
    private fun sample(label: String) = compose.runOnUiThread {
        samples += "$label,${preview.auditionPlayer.currentPosition},${preview.auditionPlayer.volume},${preview.auditionPlayer.isPlaying}"
    }
    private fun waitPlaying() = compose.waitUntil(10000) {
        var playing = false
        compose.runOnUiThread { playing = preview.auditionPlayer.isPlaying && preview.auditionPlayer.volume > .95f }
        playing
    }
    private fun showSettingsShelf(songs: List<Song>) {
        compose.setContent {
            val settings by container.settings.state.collectAsState()
            val scope = rememberCoroutineScope()
            SideEffect {
                preview.setFullLength(settings.recordShelfFullPreview)
                preview.setMode(settings.recordShelfPreviewMode)
            }
            LeiTheme(settings, darkSystemBars = true) {
                top.yukonga.miuix.kmp.basic.Scaffold {
                    PlaylistRecordShelf(songs, true, {}, {}, onPreview = preview::select,
                        previewMode = settings.recordShelfPreviewMode,
                        onPreviewModeChange = { mode -> scope.launch { container.settings.edit { it.copy(recordShelfPreviewMode = mode) } } },
                        fullPreview = settings.recordShelfFullPreview,
                        onFullPreviewChange = { full -> scope.launch { container.settings.edit { it.copy(recordShelfFullPreview = full) } } },
                        nextPreviewRequests = preview.nextRequests)
                }
            }
        }
    }
    @Test fun extractionPlaysTheChorusAndRetractionDrainsAudioWithoutReplacingQueue() {
        val songs = listOf(Song(1, "First", durationMs = 10000), Song(2, "Second", durationMs = 10000))
        compose.setContent { LeiTheme(AppearanceSettings(), darkSystemBars = true) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                PlaylistRecordShelf(songs, true, {}, {}, onPreview = preview::select)
            }
        } }
        waitPlaying(); sample("first-full")
        assertEquals("高潮试听", preview.state.value.label)
        compose.runOnUiThread {
            assertEquals(2000L, preview.auditionPlayer.currentMediaItem!!.clippingConfiguration.startPositionMs)
            assertEquals(5500L, preview.auditionPlayer.currentMediaItem!!.clippingConfiguration.endPositionMs)
        }
        assertEquals(listOf(99L), container.playbackQueue.state.value.songs.map { it.id })
        compose.runOnUiThread { preview.select(null) }
        Thread.sleep(100); sample("retract-middle")
        compose.runOnUiThread { assertTrue(preview.auditionPlayer.volume in .01f.. .99f) }
        compose.waitUntil(3000) { var stopped = false; compose.runOnUiThread { stopped = !preview.auditionPlayer.isPlaying && preview.auditionPlayer.volume == 0f }; stopped }
        sample("retract-muted")
        compose.onNodeWithTag("record_shelf_canvas").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex) { it(1) }
        waitPlaying(); sample("second-full")
        assertEquals(2L, preview.state.value.song!!.id)
        compose.waitUntil(7000) { preview.state.value.label == "试听结束" }
        sample("clip-ended")
        compose.runOnUiThread { assertFalse(preview.auditionPlayer.isPlaying); assertEquals(0f, preview.auditionPlayer.volume) }
        compose.onNodeWithTag("record_shelf_search").performClick()
        compose.onNodeWithTag("record_shelf_query").performTextInput("First")
        compose.onNodeWithText("查看唱片").performClick()
        compose.waitUntil(7000) { preview.state.value.song?.id == 1L && preview.state.value.audible }
        waitPlaying()
        compose.onNodeWithTag("record_shelf_title").assertTextEquals("First")
        assertEquals(listOf(99L), container.playbackQueue.state.value.songs.map { it.id })
    }

    @Test fun optionsLoopTheClipThenAdvanceTheShelfWithoutReplacingTheMainQueue() {
        var preparations = 0
        compose.runOnUiThread {
            preview.auditionPlayer.addListener(object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                    if (item != null) preparations++
                }
            })
        }
        val songs = listOf(Song(1, "First", durationMs = 10000), Song(2, "Second", durationMs = 10000))
        showSettingsShelf(songs)
        waitPlaying()
        compose.onNodeWithTag("record_shelf_options").performClick()
        compose.onNodeWithTag("record_shelf_preview_loop").performClick()
        compose.onNodeWithTag("record_shelf_preview_loop").assertIsOn()
        compose.onNodeWithTag("record_shelf_preview_next").assertIsOff()
        compose.waitUntil(9000) { var count = 0; compose.runOnUiThread { count = preparations }; count >= 2 }
        assertEquals(1L, preview.state.value.song!!.id)
        waitPlaying()
        compose.onNodeWithTag("record_shelf_preview_next").performClick()
        compose.onNodeWithTag("record_shelf_preview_next").assertIsOn()
        compose.onNodeWithTag("record_shelf_preview_loop").assertIsOff()
        compose.waitUntil(10000) { preview.state.value.song?.id == 2L && preview.state.value.audible }
        compose.onNodeWithTag("record_shelf_title").assertTextEquals("Second")
        assertEquals(RecordShelfPreviewMode.NEXT, container.settings.state.value.recordShelfPreviewMode)
        // The end of the list wraps to the first record, through the same extraction animation.
        compose.waitUntil(10000) { preview.state.value.song?.id == 1L && preview.state.value.audible }
        compose.onNodeWithTag("record_shelf_preview_random").performClick()
        compose.onNodeWithTag("record_shelf_preview_random").assertIsOn()
        compose.onNodeWithTag("record_shelf_preview_next").assertIsOn()
        compose.onNodeWithTag("record_shelf_preview_loop").assertIsOff()
        assertEquals(RecordShelfPreviewMode.RANDOM, container.settings.state.value.recordShelfPreviewMode)
        compose.waitUntil(10000) { preview.state.value.song?.id == 2L && preview.state.value.audible }
        compose.onNodeWithTag("record_shelf_preview_random").performClick()
        compose.onNodeWithTag("record_shelf_preview_random").assertIsOff()
        compose.onNodeWithTag("record_shelf_preview_next").assertIsOn()
        assertEquals(RecordShelfPreviewMode.NEXT, container.settings.state.value.recordShelfPreviewMode)
        compose.onNodeWithTag("record_shelf_preview_next").performClick()
        compose.onNodeWithTag("record_shelf_preview_next").assertIsOff()
        compose.waitUntil(7000) { preview.state.value.label == "试听结束" }
        assertEquals(2L, preview.state.value.song!!.id)
        assertEquals(listOf(99L), container.playbackQueue.state.value.songs.map { it.id })
        assertEquals(RecordShelfPreviewMode.ONCE, container.settings.state.value.recordShelfPreviewMode)
    }

    @Test fun fullSongOptionRemovesClippingAndWaitsForTheWholeTrackBeforeRandomAdvance() {
        showSettingsShelf(listOf(Song(1, "First", durationMs = 10000), Song(2, "Second", durationMs = 10000)))
        waitPlaying()
        compose.onNodeWithTag("record_shelf_options").performClick()
        compose.onNodeWithTag("record_shelf_preview_next").performClick()
        compose.onNodeWithTag("record_shelf_preview_random").performClick()
        compose.onNodeWithTag("record_shelf_preview_full").performClick()
        compose.onNodeWithTag("record_shelf_preview_full").assertIsOn()
        compose.waitUntil(7000) { preview.state.value.label == "整首试听" && preview.state.value.song?.id == 1L }
        waitPlaying()
        compose.runOnUiThread {
            val clip = preview.auditionPlayer.currentMediaItem!!.clippingConfiguration
            assertEquals(0L, clip.startPositionMs)
            assertEquals(androidx.media3.common.C.TIME_END_OF_SOURCE, clip.endPositionMs)
            assertEquals(10000L, preview.auditionPlayer.duration)
            // Advance beyond the old 3.5 second clip; this must remain on the same record.
            preview.auditionPlayer.seekTo(6000)
        }
        compose.waitUntil(3000) {
            var progressed = false
            compose.runOnUiThread { progressed = preview.auditionPlayer.isPlaying && preview.auditionPlayer.currentPosition > 6500 }
            progressed
        }
        assertEquals(1L, preview.state.value.song!!.id)
        compose.runOnUiThread { preview.auditionPlayer.seekTo(9400) }
        compose.waitUntil(7000) { preview.state.value.label == "整首试听" && preview.state.value.song?.id == 2L }
        assertTrue(container.settings.state.value.recordShelfFullPreview)
        compose.onNodeWithTag("record_shelf_preview_full").performClick()
        compose.waitUntil(7000) { preview.state.value.label == "高潮试听" && preview.state.value.song?.id == 2L }
        compose.runOnUiThread {
            assertEquals(2000L, preview.auditionPlayer.currentMediaItem!!.clippingConfiguration.startPositionMs)
            assertEquals(5500L, preview.auditionPlayer.currentMediaItem!!.clippingConfiguration.endPositionMs)
        }
        assertFalse(container.settings.state.value.recordShelfFullPreview)
        assertEquals(listOf(99L), container.playbackQueue.state.value.songs.map { it.id })
    }
    private fun tone(): ByteArray {
        val count = 80000
        return ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + count * 2); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(count * 2)
            repeat(count) { putShort((kotlin.math.sin(it * 2 * Math.PI * 330 / 8000) * 1200).toInt().toShort()) }
        }.array()
    }
}
