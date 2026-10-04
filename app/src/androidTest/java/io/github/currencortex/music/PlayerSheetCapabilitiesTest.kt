package io.github.currencortex.music

import androidx.activity.BackEventCompat
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.PlayerSheetGeometry
import io.github.currencortex.music.feature.player.PlayerSheetFrame
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Only this transition: silent queue, isolated account/preferences and local mock requests. */
class PlayerSheetCapabilitiesTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private lateinit var dispatcher: androidx.activity.OnBackPressedDispatcher

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        container = AppContainer(context, "player-sheet-${UUID.randomUUID()}")
        server = MockWebServer().apply {
            this.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when (request.requestUrl!!.encodedPath) {
                        "/cm/daily" -> """{"daily":[],"forYou":[],"artists":[]}"""
                        "/cm/plays/recent" -> """{"songs":[]}"""
                        "/cm/likes/mine" -> """{"songs":[]}"""
                        "/cm/playlists" -> """{"playlists":[]}"""
                        "/cm/ncm/lyric" -> """{"lines":[{"t":0,"txt":"星光陪着你"},{"t":5000,"txt":"一起听音乐"}]}"""
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            start()
        }
        container.ready.await(); container.sessionRestored.await()
        val url = server.url("/cm/").toString()
        container.musicSettings.setServer(url); container.accountRepository.server = url
        container.accountRepository.save("isolated-sheet-token", UserDto(7, nickname = "Sheet fixture"))
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { it.copy(themeMode = ThemeMode.LIGHT, blur = true, predictiveBack = true) }
        val song = Song(55, "星光", artists = "展开动画预览", durationMs = 9000)
        container.playerController.queue.replace(listOf(song), 0)
        container.playerController.state.value = PlayerState(song = song, positionMs = 1500, durationMs = 9000)
    }

    @After fun cleanup() { compose.mainClock.autoAdvance = true; container.close(); server.shutdown() }

    private fun start() {
        compose.setContent {
            dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            CurrentMusicApp(container)
        }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("mini_cover").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun frame(): PlayerSheetFrame = compose.onNodeWithTag("player_sheet").fetchSemanticsNode().config[PlayerSheetGeometry]
    private fun viewport(): Rect = compose.onNodeWithTag("music_navigation").fetchSemanticsNode().boundsInRoot
    private fun save(name: String) {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        compose.onNodeWithTag("music_navigation").captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(context.externalCacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun miniExpandsVerticallyAndCollapsesBackToTheSameSecondaryPage() {
        start()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithTag("open_network").performScrollTo().performClick()
        compose.onNodeWithTag("network_settings").assertIsDisplayed()
        compose.waitForIdle()
        val original = viewport()
        val mini = compose.onNodeWithTag("mini_glass_surface").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("mini_cover").performClick()
        compose.mainClock.advanceTimeBy(64)
        val first = frame()
        assertTrue("The first captured frame must still be expanding", first.progress > 0 && first.progress < .95f)
        assertTrue("The player starts at the bottom mini player, then grows upward", first.bounds.top > original.height * .25f && first.bounds.top < mini.top)
        assertEquals("The underlying scene must not slide sideways", original,
            compose.onNodeWithTag("music_scene_21").fetchSemanticsNode().boundsInRoot)
        val layout = compose.onNodeWithTag("player_safe_content").fetchSemanticsNode().layoutInfo.let { it.width to it.height }
        save("player-sheet-open-064.png")
        compose.mainClock.advanceTimeBy(112)
        val middle = frame()
        assertTrue(middle.bounds.top < first.bounds.top)
        assertTrue(middle.bounds.height > first.bounds.height)
        assertTrue(middle.radius > 0)
        assertEquals(original, viewport())
        assertEquals("Animation must not repeatedly resize the player layout", layout,
            compose.onNodeWithTag("player_safe_content").fetchSemanticsNode().layoutInfo.let { it.width to it.height })
        val pixels = compose.onNodeWithTag("music_navigation").captureToImage().toPixelMap()
        val background = pixels[pixels.width / 2, 16]
        assertTrue("No opaque player background or gray scrim outside the moving card", background.red > .9f && background.green > .9f)
        save("player-sheet-open-176.png")
        compose.mainClock.advanceTimeBy(128)
        save("player-sheet-open-304.png")
        compose.mainClock.advanceTimeBy(1200)
        assertEquals(1f, frame().progress, .001f)
        assertEquals(0f, frame().radius, .01f)
        assertEquals(0f, frame().bounds.top, .01f)
        compose.onNodeWithTag("player_transport").assertIsDisplayed()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.mainClock.advanceTimeBy(96)
        val closing = frame()
        assertTrue("Closing reverses the same upward expansion", closing.progress < .99f && closing.bounds.top > 0)
        save("player-sheet-close-096.png")
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithTag("player_sheet").assertDoesNotExist()
        compose.onNodeWithTag("network_settings").assertIsDisplayed()
        compose.onNodeWithTag("mini_cover").assertIsDisplayed()
        assertEquals(original, viewport())
        assertEquals(55L, container.playerController.queue.state.value.current?.id)
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun predictiveCollapseReversesCancelsAndCommitsWhileDialogsTakePriority() {
        start()
        compose.onNodeWithTag("mini_cover").performClick()
        compose.onNodeWithTag("player_transport").assertIsDisplayed()
        compose.waitForIdle()
        val original = viewport()
        fun progress(value: Float) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(value * original.width, 500f, value, BackEventCompat.EDGE_LEFT)) }
            compose.waitForIdle()
        }
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT)) }
        progress(.2f)
        val first = frame()
        progress(.6f)
        val middle = frame()
        assertTrue("The sheet follows the gesture downward", middle.bounds.top > first.bounds.top)
        assertTrue(middle.bounds.height < first.bounds.height)
        progress(.1f)
        assertTrue("Reversing the gesture expands the sheet again", frame().bounds.top < middle.bounds.top)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(1f, frame().progress, .001f)
        assertEquals(original, viewport())
        compose.onNodeWithTag("lyrics_options").performClick()
        compose.onNodeWithText("播放与歌词").assertExists()
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("播放与歌词").assertDoesNotExist()
        compose.onNodeWithTag("player_sheet").assertExists()
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT)) }
        progress(.6f)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("player_sheet").assertDoesNotExist()
        compose.onNodeWithTag("mini_cover").assertIsDisplayed()
        assertEquals(original, viewport())
        assertEquals(55L, container.playerController.queue.state.value.current?.id)
        assertFalse(container.playerController.state.value.playing)
    }
}
