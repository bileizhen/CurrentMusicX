package io.github.currencortex.music

import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.ui.component.PageContainerGeometry
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Actual app navigation and native rendering; isolated storage, no account or update download. */
class SettingsContainerNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var container:AppContainer
    private lateinit var server:MockWebServer

    @Before fun prepare()=runBlocking {
        server=MockWebServer().apply {
            dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest)=MockResponse().setResponseCode(404)
            }
            start()
        }
        container=AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(),"settings-container-${UUID.randomUUID()}")
        container.ready.await();container.sessionRestored.await()
        container.musicSettings.setServer(server.url("/cm/").toString())
        container.updateSettings.setAutoCheck(false)
        container.desktopLyrics.markRecommendationSeen()
    }
    @After fun close() {
        if(::container.isInitialized)container.close()
        if(::server.isInitialized)server.shutdown()
    }
    private fun advance(ms:Long=750) {
        compose.waitForIdle();compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(ms);compose.waitForIdle()
    }
    private fun start() {
        compose.setContent {CurrentMusicApp(container)}
        compose.waitUntil(15000){compose.onAllNodesWithTag("tab_3").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("tab_3").performClick()
        compose.mainClock.autoAdvance=false;advance()
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        assertEquals("Settings owns the system-status region instead of inheriting a second inset",
            compose.onNodeWithTag("music_window").fetchSemanticsNode().boundsInRoot.top,
            compose.onNodeWithTag("settings_root_toolbar").fetchSemanticsNode().boundsInRoot.top,1f)
    }
    private fun back()=compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
    private fun scroll(list:String,entry:String) {
        // Let LazyColumn apply each scroll request before the scroll-to-node search continues.
        compose.mainClock.autoAdvance=true
        compose.onNodeWithTag(list).performScrollToNode(hasTestTag(entry))
        compose.onNodeWithTag(entry).performScrollTo()
        // A floating dock deliberately overlays the viewport; reveal the touch target above it.
        val viewport=compose.onNodeWithTag(list).fetchSemanticsNode().boundsInRoot
        val target=compose.onNodeWithTag(entry).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag(list).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {
            it(0f,target.center.y-(viewport.top+viewport.height*.45f))
        }
        compose.waitForIdle();compose.mainClock.autoAdvance=false
    }
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.externalCacheDir,name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }

    @Test fun everySettingsPageExpandsFromItsRowAndReturnsToTheSameScrolledList() {
        start()
        capture("settings-root-overview.png")
        compose.onNodeWithTag("open_local_music_settings").assertDoesNotExist()
        for((entry,route) in listOf("open_appearance" to "1","open_desktop_lyrics" to "desktop-lyrics",
            "open_network" to "21","open_playback" to "playback-settings","open_equalizer" to "25","open_audio_output" to "audio-output","open_storage" to "24","open_about" to "2")) {
            scroll("settings_screen",entry)
            val row=compose.onNodeWithTag(entry).fetchSemanticsNode().boundsInRoot
            if(route=="24")capture("settings-storage-source.png")
            compose.onNodeWithTag(entry).performClick()
            advance(160)
            val pane=compose.onNodeWithTag("page_container_$route")
            if(compose.onAllNodesWithTag("page_container_$route").fetchSemanticsNodes().isEmpty())capture("settings-container-missing-$route.png")
            val opening=pane.fetchSemanticsNode().config[PageContainerGeometry]
            assertTrue("$entry needs an intermediate container frame: $opening",opening.progress in .01f.. .99f)
            assertTrue("$entry expands toward the top",opening.bounds.top<row.top&&opening.bounds.height>row.height)
            if(route=="1")capture("settings-container-opening.png")
            advance()
            assertEquals(1f,pane.fetchSemanticsNode().config[PageContainerGeometry].progress,.001f)
            capture("settings-page-$route.png")
            back();advance(160)
            assertTrue("$entry must visibly contract",pane.fetchSemanticsNode().config[PageContainerGeometry].progress in .01f.. .99f)
            back() // Repeated back cannot leave the settings tab during the return.
            advance()
            pane.assertDoesNotExist()
            compose.onNodeWithTag("settings_screen").assertIsDisplayed()
            val restored=compose.onNodeWithTag(entry).fetchSemanticsNode().boundsInRoot
            assertEquals("$entry keeps list position",row.top,restored.top,1f)
        }
    }

    @Test fun appearanceFontPageUsesNestedContainerAndReturnsToItsSource() {
        start()
        compose.runOnIdle {
            val song = io.github.currencortex.music.data.song.Song(902, "Retained mini for font test", durationMs = 10000)
            container.playbackQueue.restore(io.github.currencortex.music.core.media.QueueSnapshot(listOf(song), 0))
            container.playerController.state.value = io.github.currencortex.music.core.media.PlayerState(song = song, durationMs = 10000)
        }
        advance()
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        scroll("settings_screen", "open_appearance")
        compose.onNodeWithTag("open_appearance").performClick(); advance()
        scroll("appearance_screen", "setting_custom_font")
        val origin = compose.onNodeWithTag("setting_custom_font").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("setting_custom_font").performClick(); advance(160)
        val pane = compose.onNodeWithTag("page_container_appearance-font")
        assertTrue(pane.fetchSemanticsNode().config[PageContainerGeometry].progress in .01f.. .99f)
        advance()
        compose.onNodeWithTag("font_settings_page").assertIsDisplayed()
        compose.onNodeWithTag("font_import").assertIsDisplayed()
        compose.onNodeWithTag("font_search").assertIsDisplayed()
        compose.onNodeWithTag("mini_player").assertDoesNotExist()
        compose.onNodeWithTag("font_search_dock").assertHeightIsEqualTo(48.dp)
        capture("font-settings-container.png")
        back(); advance(160)
        assertTrue(pane.fetchSemanticsNode().config[PageContainerGeometry].progress in .01f.. .99f)
        advance()
        pane.assertDoesNotExist()
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        assertEquals(origin.top, compose.onNodeWithTag("setting_custom_font").fetchSemanticsNode().boundsInRoot.top, 1f)
        back(); advance()
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test fun nestedSettingsKeepTheirOwnOriginsAndReturnOneLevelAtATime() {
        start()
        scroll("settings_screen","open_network")
        compose.onNodeWithTag("open_network").performClick();advance()
        compose.onNodeWithTag("open_storage").assertDoesNotExist()
        compose.onNodeWithTag("open_desktop_lyrics").assertDoesNotExist()
        compose.onNodeWithTag("open_equalizer").assertDoesNotExist()
        scroll("network_settings","open_navidrome")
        val source=compose.onNodeWithTag("open_navidrome").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("open_navidrome").performClick();advance(160)
        val nested=compose.onNodeWithTag("page_container_navidrome")
        assertTrue(nested.fetchSemanticsNode().config[PageContainerGeometry].progress in .01f.. .99f)
        capture("settings-container-nested.png")
        advance();back();advance()
        nested.assertDoesNotExist()
        compose.onNodeWithTag("network_settings").assertIsDisplayed()
        assertEquals(source.top,compose.onNodeWithTag("open_navidrome").fetchSemanticsNode().boundsInRoot.top,1f)
        back();advance()
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        scroll("settings_screen","open_about")
        compose.onNodeWithTag("open_about").performClick();advance()
        val about = compose.onNodeWithTag("about_screen")
        val beforeSwipe = about.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        about.performTouchInput { swipeUp(durationMillis = 600) }; advance()
        assertTrue("About must scroll by touch after the container transition", about.fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value() > beforeSwipe + .02f)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithText("开源许可").performScrollTo()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithText("开源许可").performClick();advance(160)
        compose.onNodeWithTag("page_container_3").assertExists()
        advance();compose.onNodeWithTag("navigate_back").performClick();advance()
        compose.onNodeWithTag("page_container_3").assertDoesNotExist()
        compose.onNodeWithTag("page_container_2").assertExists()
        back();advance()
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
    }
}
