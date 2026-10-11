package io.github.currencortex.music

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.PlayerSheetGeometry
import io.github.currencortex.music.feature.player.PlayerPagePosition
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.ui.component.SideWaterDropVisual
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Only the short landscape player, with isolated storage and a silent paused queue. */
class PlayerLandscapeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer

    @Before fun prepare() = runBlocking {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "landscape-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when (request.requestUrl!!.encodedPath) {
                        "/cm/daily" -> """{"daily":[],"forYou":[]}"""
                        "/cm/playlists" -> """{"playlists":[]}"""
                        "/cm/plays/recent" -> """{"songs":[]}"""
                        "/cm/ncm/lyric" -> """{"lines":[{"t":0,"txt":"Landscape lyric fixture"}]}"""
                        else -> "{}"
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            start()
        }
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.accountRepository.save("isolated-landscape-token", UserDto(7, nickname = "Landscape fixture"))
        container.updateSettings.setAutoCheck(false)
        val song = Song(55, "Landscape title", artists = "Landscape artist", durationMs = 180000)
        container.playerController.queue.replace(listOf(song), 0)
        container.playerController.state.value = PlayerState(song = song, durationMs = 180000)
    }
    @After fun finish() {
        if (::container.isInitialized) container.close()
        if (::server.isInitialized) server.shutdown()
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    @Test fun floatingSideTabsKeepContentClearAndRecoverAfterSecondaryAndPlayer() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Box(Modifier.requiredSize(780.dp, 350.dp)) { CurrentMusicApp(container) } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("wide_navigation").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val rail = compose.onNodeWithTag("wide_navigation").fetchSemanticsNode().boundsInRoot
        val page = compose.onNodeWithTag("root_tab_transition").fetchSemanticsNode().boundsInRoot
        val mini = compose.onNodeWithTag("mini_player").fetchSemanticsNode().boundsInRoot
        assertTrue("Navigation belongs to the right edge", rail.left > page.right)
        assertTrue("The mini player must not run underneath the side tabs", mini.right < rail.left)
        assertTrue("The page must draw behind the floating mini player, not stop above it", page.bottom >= mini.bottom)
        (0..3).forEach { index ->
            val tab = compose.onNodeWithTag("tab_$index")
            tab.assertIsDisplayed()
            val bounds = tab.fetchSemanticsNode().boundsInRoot
            assertTrue(rail.contains(bounds.topLeft) && rail.contains(bounds.bottomRight))
        }
        compose.onNodeWithTag("tab_0").assertIsSelected()
        compose.mainClock.autoAdvance = false
        val beforePress = compose.onRoot().captureToImage().asAndroidBitmap()
        val firstTab = compose.onNodeWithTag("tab_0").fetchSemanticsNode().boundsInRoot
        val drop = compose.onNodeWithTag("side_water_drop")
        drop.performTouchInput { down(center); advanceEventTime(600) }
        compose.mainClock.advanceTimeBy(600)
        val held = drop.fetchSemanticsNode().config[SideWaterDropVisual]
        assertTrue("Holding the tab must inflate its glass lens", held.press > .9f && held.scaleX > 1.2f && held.scaleY > 1.2f)
        val pressed = compose.onRoot().captureToImage().asAndroidBitmap()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        var outsideChanges = 0
        val left = (rail.left - root.left).toInt()
        val centerY = (firstTab.center.y - root.top).toInt()
        for (x in (left - 10)..(left - 3)) for (y in (centerY - 45)..(centerY + 45)) {
            if (x in 0 until pressed.width && y in 0 until pressed.height && beforePress.getPixel(x, y) != pressed.getPixel(x, y)) outsideChanges++
        }
        assertTrue("The enlarged water drop must render beyond the base panel's clip", outsideChanges > 20)
        drop.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f, firstTab.height + 14)); advanceEventTime(200) }
        compose.mainClock.advanceTimeBy(200)
        drop.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1200)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("tab_1").assertIsSelected()
        val released = drop.fetchSemanticsNode().config[SideWaterDropVisual]
        assertEquals(0f, released.press, .02f)
        assertEquals(1f, released.scaleX, .02f)
        (1..3).forEach { index ->
            compose.onNodeWithTag("tab_$index").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("tab_$index").assertIsSelected()
            assertEquals("The capsule stays still while the content pages slide", rail,
                compose.onNodeWithTag("wide_navigation").fetchSemanticsNode().boundsInRoot)
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("tab_3").assertIsSelected()
        compose.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("open_network"))
        compose.onNodeWithTag("open_network").performClick()
        compose.onNodeWithTag("network_settings").assertIsDisplayed()
        compose.onNodeWithTag("wide_navigation").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab_3").assertIsSelected()
        compose.onNodeWithTag("mini_cover").performClick()
        compose.onNodeWithTag("player_screen").assertIsDisplayed()
        compose.onNodeWithTag("wide_navigation").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab_3").assertIsSelected()
        assertEquals(rail, compose.onNodeWithTag("wide_navigation").fetchSemanticsNode().boundsInRoot)
        assertEquals(55L, container.playerController.queue.state.value.current?.id)
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun landscapeCentersBothPanesAndSwipesBetweenLyricsAndControls() {
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("mini_cover").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mini_cover").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("lyrics_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("player_cover").assertIsDisplayed()
        compose.onNodeWithTag("lyrics_panel").assertIsDisplayed()
        compose.onNodeWithTag("lyrics_options").assertIsDisplayed()
        compose.onNodeWithTag("player_wide_pager").assertIsDisplayed()
        listOf("navigate_back", "open_quality_sheet",
            "player_transport", "player_cover_lyrics", "player_lyrics_header", "player_song_title").forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
        val stage = compose.onNodeWithTag("player_wide_stage").fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("player_cover").fetchSemanticsNode().boundsInRoot
        val coverPane = compose.onNodeWithTag("player_wide_cover_content").fetchSemanticsNode().boundsInRoot
        val lyrics = compose.onNodeWithTag("lyrics_panel").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("player_screen").fetchSemanticsNode().boundsInRoot
        val options = compose.onNodeWithTag("lyrics_options").fetchSemanticsNode().boundsInRoot
        assertEquals("Cover stays square", cover.width, cover.height, 1f)
        assertEquals("Both panes center vertically", cover.center.y, lyrics.center.y, 2f)
        assertEquals("Artwork centers in the left pane", coverPane.center.x, cover.center.x, 2f)
        assertEquals("Bottom system insets must not shift the stage upwards", viewport.center.y, stage.center.y, 2f)
        assertEquals("The panes have equal width", coverPane.width, lyrics.width, 2f)
        assertTrue(stage.contains(cover.topLeft) && stage.contains(cover.bottomRight))
        assertTrue("Lyrics follow the cover", cover.right < lyrics.left)
        compose.waitUntil(5000) { !statusBarVisible() }
        compose.onNodeWithTag("player_screen").captureToImage().asAndroidBitmap().let { bitmap ->
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            java.io.File(context.externalCacheDir, "player-landscape-immersive.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("lyrics_panel").performTouchInput { swipeDown() }
        assertEquals("Browsing lyrics leaves the player open", 1f,
            compose.onNodeWithTag("player_sheet").fetchSemanticsNode().config[PlayerSheetGeometry].progress, .01f)
        val pager = compose.onNodeWithTag("player_wide_pager")
        pager.performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(0f, pager.fetchSemanticsNode().config[PlayerPagePosition], .01f)
        compose.onNodeWithTag("player_transport").assertIsDisplayed()
        compose.onNodeWithTag("player_song_title").assertIsDisplayed()
        compose.onNodeWithTag("lyrics_panel").assertDoesNotExist()
        assertEquals("The cover stays still during the page change", cover,
            compose.onNodeWithTag("player_cover").fetchSemanticsNode().boundsInRoot)
        assertEquals("Only the right pane moves; settings stay in the corner", options,
            compose.onNodeWithTag("lyrics_options").fetchSemanticsNode().boundsInRoot)
        pager.performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width * .9f, height * .15f),
            androidx.compose.ui.geometry.Offset(width * .1f, height * .15f), 300) }
        compose.waitForIdle()
        assertEquals(1f, pager.fetchSemanticsNode().config[PlayerPagePosition], .01f)
        compose.onNodeWithTag("lyrics_panel").assertIsDisplayed()
        compose.onNodeWithTag("player_transport").assertDoesNotExist()
        compose.onNodeWithTag("lyrics_options").performClick()
        compose.onNodeWithTag("open_lyrics_display").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("open_lyrics").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("player_transport").assertIsDisplayed()
        compose.waitUntil(5000) { !statusBarVisible() }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("mini_cover").assertIsDisplayed()
        compose.waitUntil(5000) { statusBarVisible() }
        assertEquals(55L, container.playerController.queue.state.value.current?.id)
        assertFalse(container.playerController.state.value.playing)
    }

    private fun statusBarVisible() = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
        ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars()) == true
}
