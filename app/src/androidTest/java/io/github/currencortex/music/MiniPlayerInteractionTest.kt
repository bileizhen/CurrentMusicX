package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.*
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Isolated fixture: gesture callbacks move its queue without connecting an audio service. */
class MiniPlayerInteractionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private var next = 0; private var previous = 0; private var opens = 0; private var toggles = 0; private var queues = 0
    @Before fun prepare() = runBlocking {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setHeader("Content-Type", "application/json").setBody(when (request.requestUrl!!.encodedPath) {
                "/cm/daily" -> """{"daily":[],"forYou":[]}"""
                "/cm/playlists" -> """{"playlists":[]}"""
                "/cm/plays/recent", "/cm/likes/mine" -> """{"songs":[]}"""
                else -> "{}"
            })
        }
        server.start()
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "mini-test-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        val url = server.url("/cm/").toString()
        container.musicSettings.setServer(url); container.accountRepository.server = url
        container.accountRepository.save("isolated-mini-token", UserDto(7, nickname = "Mini fixture"))
        container.updateSettings.setAutoCheck(false)
        container.playerController.queue.replace(listOf(Song(1, "First song", "Artist"), Song(2, "Second song", "Artist"), Song(3, "Third song", "Artist")), 1)
    }
    @After fun finish() = runBlocking { container.close(); server.shutdown() }
    private fun component(member: Boolean = false) {
        container.playerController.state.value = PlayerState(mode = if (member) PlayerMode.ROOM else PlayerMode.LOCAL, canControlPlayback = !member)
        val vm = PlayerViewModel(container)
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) { MiniPlayer(vm, { opens++ }, { toggles++ },
            onNext = { next++; container.playerController.queue.next() },
            onPrevious = { previous++; container.playerController.queue.previous() }, onQueue = { queues++ }) } }
    }
    @Test fun titleSwipesDispatchOnceAndShortOrCancelledDragsDoNotClickOrSkip() {
        component()
        compose.onNodeWithTag("mini_song_gesture").performTouchInput { swipeLeft(durationMillis = 300) }
        assertEquals(1, next); assertEquals(2, container.playbackQueue.state.value.index); assertEquals(0, opens)
        compose.onNodeWithTag("mini_song_gesture").performTouchInput { swipeRight(durationMillis = 300) }
        assertEquals(1, previous); assertEquals(1, container.playbackQueue.state.value.index)
        compose.onNodeWithTag("mini_song_gesture").performTouchInput {
            down(center); moveTo(center.copy(x = center.x - width * .15f)); up()
        }
        compose.onNodeWithTag("mini_song_gesture").performTouchInput {
            down(center); moveTo(center.copy(x = 1f)); cancel()
        }
        assertEquals(1, next); assertEquals(1, previous); assertEquals(0, opens)
        compose.onNodeWithTag("mini_song_gesture").performClick()
        compose.onNodeWithTag("mini_toggle").performClick()
        compose.onNodeWithTag("mini_queue").performClick()
        assertEquals(1, opens); assertEquals(1, toggles); assertEquals(1, queues)
        assertFalse(container.playerController.state.value.playing)
    }
    @Test fun memberSwipesAreIgnoredAndPromotionRestoresGestureControls() {
        component(member = true)
        compose.onNodeWithTag("mini_toggle").assertIsNotEnabled()
        compose.onNodeWithTag("mini_song_gesture").performTouchInput { swipeLeft(durationMillis = 300) }
        assertEquals(0, next); assertEquals(0, opens)
        compose.onNodeWithTag("mini_queue").assertIsEnabled()
        compose.runOnIdle { container.playerController.state.value = container.playerController.state.value.copy(canControlPlayback = true) }
        compose.onNodeWithTag("mini_toggle").assertIsEnabled()
        compose.onNodeWithTag("mini_song_gesture").performTouchInput { swipeLeft(durationMillis = 300) }
        assertEquals(1, next)
    }
    @Test fun rootSharesGlassMaterialAndQueueDismissKeepsCurrentPageAndPausedQueue() = runBlocking {
        container.settings.edit { AppearanceSettings(blur = true, liquidGlass = true) }
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("mini_glass_surface").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("glass_floating_bar").assertExists()
        compose.onNodeWithTag("mini_queue").performClick()
        compose.onNodeWithTag("mini_queue_sheet").assertIsDisplayed()
        compose.onNodeWithText("▶ Second song").assertExists()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("mini_queue_sheet").assertDoesNotExist()
        compose.onNodeWithTag("music_home").assertExists()
        assertEquals(1, container.playbackQueue.state.value.index)
        assertFalse(container.playerController.state.value.playing)
    }
    @Test fun fixedNavigationRemainsVisibleWithGlassMiniPlayer() = runBlocking<Unit> {
        container.settings.edit { AppearanceSettings(blur = true, liquidGlass = true, floatingBar = false) }
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("mini_glass_surface").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("standard_navigation_bar").assertExists()
        compose.onNodeWithTag("mini_queue").assertExists()
    }
}
