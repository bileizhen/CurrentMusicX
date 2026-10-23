package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Isolated account and delayed real home endpoints; never touches the installed user's data. */
class LaunchHomeReadinessTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private val paths = CopyOnWriteArrayList<String>()
    private val daily = CountDownLatch(1)
    private val recent = CountDownLatch(1)
    private val playlists = CountDownLatch(1)
    @Volatile private var failPlaylist = true
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Before fun prepare() = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "launch-home-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                paths += path
                return when (path) {
                    "/cm/ncmbind" -> json("""{"bound":false}""")
                    "/cm/daily" -> {
                        daily.await(20, TimeUnit.SECONDS)
                        json("""{"daily":[{"ncm_id":11,"name":"Ready fixture song","artists":"Fixture Artist"}],"forYou":[],"artists":[]}""")
                    }
                    "/cm/plays/recent" -> { recent.await(20, TimeUnit.SECONDS); json("""{"songs":[]}""") }
                    "/cm/playlists" -> {
                        playlists.await(20, TimeUnit.SECONDS)
                        if (failPlaylist) MockResponse().setResponseCode(503) else json("""{"playlists":[]}""")
                    }
                    "/cm/likes/mine" -> json("""{"songs":[]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.accountRepository.save("isolated-test-token", UserDto(7, nickname = "Fixture user"))
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { AppearanceSettings(blur = false) }
    }
    @After fun finish() = runBlocking {
        daily.countDown(); recent.countDown(); playlists.countDown()
        if (::container.isInitialized) { container.accountRepository.clear(); container.close() }
        if (::server.isInitialized) server.shutdown()
    }
    private fun pumpUntil(condition: () -> Boolean) = compose.waitUntil(15000) {
        compose.mainClock.advanceTimeBy(32)
        condition()
    }

    @Test fun storedHaloStartsDirectlyWithRestoredAccountAndDoesNotReplayOnRecomposition() {
        runBlocking { container.settings.edit { it.copy(launchStyle = io.github.currencortex.music.data.settings.LaunchStyle.HALO) } }
        daily.countDown(); recent.countDown(); playlists.countDown()
        compose.mainClock.autoAdvance = false
        compose.setContent { CurrentMusicApp(container, animateLaunch = true) }
        pumpUntil { compose.onAllNodesWithTag("halo_launch_frame").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("launch_wordmark", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("halo_launch_frame").assertContentDescriptionEquals("CurrentMusic · Fixture user")
        pumpUntil { paths.containsAll(listOf("/cm/daily", "/cm/plays/recent", "/cm/playlists")) }
        compose.mainClock.advanceTimeBy(9500)
        pumpUntil { compose.onAllNodesWithTag("launch_animation").fetchSemanticsNodes().isEmpty() }
        runBlocking { container.settings.edit { it.copy(launchStyle = io.github.currencortex.music.data.settings.LaunchStyle.CLASSIC) } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("launch_animation").assertDoesNotExist()
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun disabledLaunchDoesNotWaitForHomeRequestsOrBlockNavigation() {
        runBlocking {
            container.desktopLyrics.markRecommendationSeen()
            container.settings.edit { it.copy(launchStyle = io.github.currencortex.music.data.settings.LaunchStyle.NONE) }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent { CurrentMusicApp(container, animateLaunch = true, launchWindowReady = false) }
        pumpUntil { paths.containsAll(listOf("/cm/daily", "/cm/plays/recent", "/cm/playlists")) }
        compose.onNodeWithTag("launch_preparing").assertDoesNotExist()
        compose.onNodeWithTag("launch_animation").assertDoesNotExist()
        compose.onNodeWithTag("launch_wordmark", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("home_brand_title").assertIsDisplayed()
        // All three home responses are still held back; users can already leave the page.
        assertEquals(1L, daily.count); assertEquals(1L, recent.count); assertEquals(1L, playlists.count)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("tab_3").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("settings_screen").assertIsDisplayed()
        compose.onNodeWithText("外观").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("setting_launch_preview").assertDoesNotExist()
        compose.onNodeWithTag("setting_launch_style").performScrollTo().performClick()
        compose.onNodeWithTag("launch_style_none").assertIsSelected()
        compose.onNodeWithTag("launch_style_classic").performClick()
        compose.waitUntil(5000) { container.settings.state.value.launchStyle == io.github.currencortex.music.data.settings.LaunchStyle.CLASSIC }
        compose.onNodeWithTag("setting_launch_preview").assertExists()
        compose.onNodeWithTag("launch_animation").assertDoesNotExist()
        daily.countDown(); recent.countDown(); playlists.countDown()
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithTag("launch_animation").assertDoesNotExist()
    }

    @Test fun launchWaitsForEveryHomeSectionAndFailureOffersRetryWithoutReplaying() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CurrentMusicApp(container, animateLaunch = true) }
        pumpUntil { paths.containsAll(listOf("/cm/daily", "/cm/plays/recent", "/cm/playlists")) }
        compose.mainClock.advanceTimeBy(4000)
        compose.onNodeWithTag("launch_animation").assertExists()
        daily.countDown(); recent.countDown()
        pumpUntil { compose.onAllNodesWithText("Ready fixture song").fetchSemanticsNodes().isNotEmpty() }
        // Recommendations already exist, but the last home section is still pending.
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("launch_animation").assertExists()
        playlists.countDown()
        pumpUntil { compose.onAllNodesWithTag("launch_animation").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Ready fixture song").assertIsDisplayed()
        compose.onNodeWithTag("home_retry").assertIsDisplayed()
        val retryBounds = compose.onNodeWithTag("home_retry").fetchSemanticsNode().boundsInRoot
        val tabBounds = compose.onNodeWithTag("tab_1").fetchSemanticsNode().boundsInRoot
        assertTrue("Retry must stay above the floating tabs", retryBounds.bottom < tabBounds.top)
        failPlaylist = false
        compose.onNodeWithTag("home_retry").performClick()
        pumpUntil { paths.count { it == "/cm/playlists" } >= 2 && compose.onAllNodesWithTag("home_retry").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("launch_animation").assertDoesNotExist()
        assertFalse(container.playerController.state.value.playing)
    }
}
