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
import io.github.currencortex.music.feature.player.PlayerButtonVisuals
import io.github.currencortex.music.feature.player.PlayerPagePosition
import io.github.currencortex.music.feature.player.PlayerSheetGeometry
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
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("tab_3").assertIsSelected()
        compose.onNodeWithTag("mini_cover").performClick()
        compose.onNodeWithTag("player_screen").assertIsDisplayed()
        compose.onNodeWithTag("wide_navigation").assertDoesNotExist()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("tab_3").assertIsSelected()
        assertEquals(rail, compose.onNodeWithTag("wide_navigation").fetchSemanticsNode().boundsInRoot)
        assertEquals(55L, container.playerController.queue.state.value.current?.id)
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun shortLandscapeShowsFullCoverMetadataAndControlsAndClosesToMini() {
        compose.setContent { Box(Modifier.requiredSize(780.dp, 350.dp)) { CurrentMusicApp(container) } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("mini_cover").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mini_cover").performClick()
        compose.waitForIdle()
        val pane = compose.onNodeWithTag("player_wide_cover_content").fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("player_cover").fetchSemanticsNode().boundsInRoot
        assertTrue("A full circular cover must fit the pane", cover.width > 200 && cover.height > 200)
        assertEquals("Artwork must remain square instead of becoming a clipped semicircle", cover.width, cover.height, 1f)
        assertTrue(pane.contains(cover.topLeft)); assertTrue(pane.contains(cover.bottomRight))
        compose.onNodeWithTag("player_song_title").assertIsDisplayed().assertTextContains("Landscape title")
        compose.onNodeWithTag("player_song_artist").assertIsDisplayed().assertTextContains("Landscape artist")
        compose.onNodeWithTag("player_cover_lyrics").assertDoesNotExist()
        listOf("player_seek", "player_toggle", "player_like", "player_comments", "player_cycle_mode", "open_player_queue").forEach {
            compose.onNodeWithTag(it).assertIsDisplayed()
        }
        compose.onNodeWithTag("player_seek").assertIsEnabled()
        compose.runOnIdle { container.playerController.state.value = container.playerController.state.value.copy(canControlPlayback = false) }
        compose.onNodeWithTag("player_toggle").assertIsNotEnabled()
        compose.onNodeWithTag("player_seek").assertIsNotEnabled()
        compose.runOnIdle { container.playerController.state.value = container.playerController.state.value.copy(canControlPlayback = true, loading = true, playRequested = true) }
        compose.onNodeWithTag("player_toggle").assertContentDescriptionEquals("暂停")
        compose.waitForIdle()
        assertEquals(1f, compose.onNodeWithTag("player_toggle").fetchSemanticsNode().config[PlayerButtonVisuals].glyphProgress, .01f)
        compose.runOnIdle { container.playerController.state.value = container.playerController.state.value.copy(loading = false, playRequested = false) }
        compose.mainClock.autoAdvance = false
        val panePager = compose.onNodeWithTag("player_wide_pager")
        panePager.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .8f, height * .12f))
            advanceEventTime(200)
            moveBy(androidx.compose.ui.geometry.Offset(-width * .68f, 0f))
        }
        compose.mainClock.advanceTimeBy(32)
        val dragged = panePager.fetchSemanticsNode().config[PlayerPagePosition]
        assertTrue("Lyrics follow the finger before release", dragged > .05f && dragged < .95f)
        assertEquals("The large cover stays fixed while the right pane moves", cover,
            compose.onNodeWithTag("player_cover").fetchSemanticsNode().boundsInRoot)
        panePager.performTouchInput { advanceEventTime(400); up() }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(1f, panePager.fetchSemanticsNode().config[PlayerPagePosition], .01f)
        compose.onNodeWithTag("player_toggle").assertDoesNotExist()
        compose.onNodeWithTag("lyrics_panel").assertIsDisplayed()
        // Use the lyric viewport's upper blank area so this remains a paging gesture,
        // rather than a new touch on a lyric row while its vertical fling is stopping.
        panePager.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .12f, height * .12f))
            advanceEventTime(200)
            moveBy(androidx.compose.ui.geometry.Offset(width * .68f, 0f))
        }
        compose.mainClock.advanceTimeBy(32)
        val returning = panePager.fetchSemanticsNode().config[PlayerPagePosition]
        assertTrue("Controls also follow the finger before release: $returning", returning > .05f && returning < .95f)
        panePager.performTouchInput { advanceEventTime(400); up() }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(0f, panePager.fetchSemanticsNode().config[PlayerPagePosition], .01f)
        compose.onNodeWithTag("player_toggle").assertIsDisplayed()
        assertEquals(cover, compose.onNodeWithTag("player_cover").fetchSemanticsNode().boundsInRoot)
        panePager.performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .85f, height * .12f),
                androidx.compose.ui.geometry.Offset(width * .15f, height * .12f), 300)
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("lyrics_panel").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, height * .35f), 200)
        }
        compose.mainClock.advanceTimeBy(1500)
        assertEquals("Vertical lyric browsing must not close the player", 1f,
            compose.onNodeWithTag("player_sheet").fetchSemanticsNode().config[PlayerSheetGeometry].progress, .01f)
        panePager.performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .15f, height * .12f),
                androidx.compose.ui.geometry.Offset(width * .85f, height * .12f), 300)
        }
        compose.mainClock.advanceTimeBy(800)
        assertEquals("Horizontal paging remains usable after vertical lyric browsing", 0f,
            panePager.fetchSemanticsNode().config[PlayerPagePosition], .01f)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("open_player_queue").performClick()
        compose.onNodeWithTag("player_queue_sheet").assertExists()
        compose.onNodeWithText("向下轻扫返回播放界面").performClick()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("mini_cover").assertIsDisplayed()
        assertEquals(55L, container.playerController.queue.state.value.current?.id)
        assertFalse(container.playerController.state.value.playing)
    }
}
