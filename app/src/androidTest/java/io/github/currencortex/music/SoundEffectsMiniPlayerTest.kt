package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** A paused synthetic queue: navigation, list clearance and queue control without audio or accounts. */
class SoundEffectsMiniPlayerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun effectsRetainsThePlayerAndLastEffectRemainsReachable() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(app, "effects-mini-${UUID.randomUUID()}", useNativeNetease = false)
        val server = MockWebServer()
        try {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when (request.requestUrl!!.encodedPath) {
                        "/cm/daily" -> """{"daily":[],"forYou":[],"artists":[]}"""
                        "/cm/plays/recent", "/cm/likes/mine" -> """{"songs":[]}"""
                        "/cm/songs/status" -> """{"liked":[]}"""
                        "/cm/playlists" -> """{"playlists":[]}"""
                        "/cm/ncmbind" -> """{"bound":false}"""
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            server.start()
            container.ready.await(); container.sessionRestored.await()
            val url = server.url("/cm/").toString()
            container.musicSettings.setServer(url); container.accountRepository.server = url
            container.musicSettings.setNeteaseMainLibrary(false)
            container.accountRepository.save("effects-mini-fixture", UserDto(7, nickname = "Mini fixture"))
            container.updateSettings.setAutoCheck(false)
            container.desktopLyrics.markRecommendationSeen()
            container.playerController.queue.replace(listOf(Song(55, "浮光", artists = "悬浮播放器预览")), 0)
            compose.setContent { CurrentMusicApp(container) }
            compose.waitUntil(15000) { compose.onAllNodesWithTag("open_likes").fetchSemanticsNodes().isNotEmpty() }
            compose.mainClock.advanceTimeBy(700)
            compose.onNodeWithTag("tab_3").performClick()
            compose.onNodeWithTag("open_network").performScrollTo().performClick()
            compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("open_equalizer"))
            compose.onNodeWithTag("open_equalizer").performClick()
            compose.onNodeWithTag("native_sound_effects_page").assertIsDisplayed()
            compose.onNodeWithTag("mini_player").assertIsDisplayed()
            compose.onNodeWithTag("mini_title", useUnmergedTree = true).assertTextEquals("浮光")
            compose.onNodeWithTag("sound_effects_screen").performScrollToNode(hasTestTag("sound_local_jazz"))
            compose.onNodeWithTag("sound_effects_screen").performTouchInput { swipeUp() }
            val last = compose.onNodeWithTag("sound_local_jazz").fetchSemanticsNode().boundsInRoot
            val mini = compose.onNodeWithTag("mini_glass_surface").fetchSemanticsNode().boundsInRoot
            assertTrue("The last effect must scroll above the player: $last / $mini", last.bottom < mini.top)
            compose.onNodeWithTag("mini_queue").performClick()
            compose.onNodeWithTag("mini_queue_sheet").assertIsDisplayed()
            assertEquals(55L, container.playerController.queue.state.value.current?.id)
            assertFalse(container.playerController.state.value.playing)
        } finally { container.close(); server.shutdown() }
    }
}
