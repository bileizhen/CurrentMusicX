package io.github.currencortex.music

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Mock lists and a paused, isolated queue; never changes the user's account or playback. */
class MiniOverlayCapabilitiesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun secondaryListsExtendBehindGlassAndLastItemsRemainReachable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "mini-overlay-${UUID.randomUUID()}")
        val server = MockWebServer()
        try {
            val tracks = (1..40).joinToString(",") {
                """{"ncm_id":$it,"name":"漫游 · 第${it}首","artists":"玻璃下的音乐列表","album":"预览"}"""
            }
            fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/cm/daily" -> json("""{"daily":[] ,"forYou":[],"artists":[]}""")
                    "/cm/plays/recent" -> json("""{"songs":[]}""")
                    "/cm/likes/mine" -> json("""{"songs":[$tracks]}""")
                    "/cm/songs/status" -> json("""{"liked":[]}""")
                    "/cm/playlists" -> json("""{"playlists":[]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
            server.start()
            container.ready.await(); container.sessionRestored.await()
            val url = server.url("/cm/").toString()
            container.musicSettings.setServer(url); container.accountRepository.server = url
            container.accountRepository.save("isolated-mini-token", UserDto(7, nickname = "Mini fixture"))
            container.updateSettings.setAutoCheck(false)
            container.settings.edit { it.copy(themeMode = ThemeMode.LIGHT, blur = true, liquidGlass = true) }
            container.playerController.queue.replace(listOf(Song(55, "浮光", artists = "悬浮播放器预览")), 0)
            compose.setContent { CurrentMusicApp(container) }
            compose.waitUntil(15000) { compose.onAllNodesWithTag("open_likes").fetchSemanticsNodes().isNotEmpty() }
            val viewport = compose.onNodeWithTag("music_navigation").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("open_likes").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("song_menu_1").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()

            fun assertOverlay(listTag: String) {
                val list = compose.onNodeWithTag(listTag).fetchSemanticsNode().boundsInRoot
                val glass = compose.onNodeWithTag("mini_glass_surface").fetchSemanticsNode().boundsInRoot
                assertTrue("The list viewport must extend behind the whole glass capsule: $list / $glass", list.bottom >= glass.bottom)
                assertEquals("Entering a secondary page must not resize navigation", viewport,
                    compose.onNodeWithTag("music_navigation").fetchSemanticsNode().boundsInRoot)
            }
            fun savePreview(name: String) {
                compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                    java.io.File(context.externalCacheDir, name).outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
            assertOverlay("library_detail")
            compose.onNodeWithTag("library_detail").performTouchInput { swipeUp() }
            savePreview("mini-secondary-library.png")
            compose.onNodeWithTag("library_detail").performScrollToNode(hasTestTag("song_menu_40"))
            compose.onNodeWithTag("library_detail").performTouchInput { swipeUp() }
            val lastSong = compose.onNodeWithTag("song_menu_40").fetchSemanticsNode().boundsInRoot
            val mini = compose.onNodeWithTag("mini_glass_surface").fetchSemanticsNode().boundsInRoot
            assertTrue("End padding must allow the last song menu above the player", lastSong.bottom < mini.top)
            compose.onNodeWithTag("library_detail").performScrollToIndex(0)
            compose.onNodeWithText("返回").performClick()
            compose.onNodeWithTag("tab_3").performClick()
        compose.mainClock.advanceTimeBy(700)
            compose.onNodeWithTag("open_network").performScrollTo().performClick()
            compose.onNodeWithTag("network_settings").assertIsDisplayed()
            compose.waitForIdle()
            assertOverlay("network_settings")
            savePreview("mini-secondary-settings.png")
            compose.onNodeWithTag("network_settings").performScrollToNode(hasText("恢复播放队列"))
            compose.onNodeWithTag("network_settings").performTouchInput { swipeUp() }
            val lastSetting = compose.onNodeWithText("恢复播放队列").fetchSemanticsNode().boundsInRoot
            assertTrue("The final setting must remain above the glass player", lastSetting.bottom < mini.top)
            assertEquals(55L, container.playerController.queue.state.value.current?.id)
            assertFalse(container.playerController.state.value.playing)
        } finally { container.close(); server.shutdown() }
    }
}
