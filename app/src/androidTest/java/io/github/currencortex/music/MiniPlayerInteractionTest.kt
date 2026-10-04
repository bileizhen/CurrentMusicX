package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.*
import io.github.currencortex.music.feature.settings.MusicSettingsViewModel
import io.github.currencortex.music.feature.settings.MusicSettingsScreen
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
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
    @Test fun coverRotatesOnlyWhilePlayingResumesItsAngleAndResetsOnTrackChange() {
        compose.mainClock.autoAdvance = false
        component()
        fun angle() = compose.onNodeWithTag("mini_cover").fetchSemanticsNode().config[CoverRotation]
        fun playing(value: Boolean) {
            compose.runOnIdle { container.playerController.state.value = container.playerController.state.value.copy(playing = value) }
            compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
        }
        assertEquals(0f, angle(), .01f)
        playing(true); compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        val rotating = angle(); assertTrue("Playing cover should rotate", rotating > 25f)
        playing(false)
        val stopped = angle(); compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        assertEquals(stopped, angle(), .01f)
        playing(true); compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        assertTrue(angle() > stopped + 12f)
        compose.runOnIdle { container.playbackQueue.next() }
        compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
        assertTrue("New cover resets", angle() < 3f)
        playing(false)
        compose.onNodeWithTag("mini_cover").performClick(); assertEquals(1, opens)
    }
    @Test fun progressRingFollowsPositionAndResetsWhenTrackChanges() {
        component()
        compose.runOnIdle {
            container.playerController.state.value = PlayerState(song = container.playbackQueue.state.value.current,
                positionMs = 2500, durationMs = 10000)
        }
        compose.onNodeWithTag("mini_toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(.25f, 0f..1f)))
        compose.runOnIdle { container.playerController.state.value = container.playerController.state.value.copy(positionMs = 7500, playing = true) }
        compose.onNodeWithTag("mini_toggle").assertContentDescriptionEquals("暂停")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(.75f, 0f..1f)))
        compose.runOnIdle { container.playbackQueue.next() }
        compose.onNodeWithTag("mini_toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0f, 0f..1f)))
        compose.runOnIdle {
            container.playbackQueue.restore(QueueSnapshot(listOf(Song(5, "Restored song", durationMs = 10000)), 0, positionMs = 5000))
            container.playerController.state.value = PlayerState()
        }
        compose.onNodeWithTag("mini_toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(.5f, 0f..1f)))
    }
    @Test fun playIntentIsImmediateBeforeConnectionAndLoadingRingReturnsToProgress() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val isolated = PlayerController(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), container.playbackQueue, scope) {
            awaitCancellation() // Delayed connection without ever launching the production service.
        }
        try {
            withContext(Dispatchers.Main.immediate) { scope.launch { isolated.state.collect { container.playerController.state.value = it } } }
            val vm = PlayerViewModel(container)
            compose.setContent { LeiTheme(AppearanceSettings(blur = false)) { MiniPlayer(vm, {}, { isolated.toggle() }) } }
            compose.onNodeWithTag("mini_toggle").assertContentDescriptionEquals("播放").performClick()
            compose.onNodeWithTag("mini_toggle").assertContentDescriptionEquals("暂停")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
            assertTrue(isolated.state.value.playRequested); assertFalse(isolated.state.value.playing)
            compose.onNodeWithTag("mini_toggle").performClick()
            compose.onNodeWithTag("mini_toggle").assertContentDescriptionEquals("播放")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0f, 0f..1f)))
            compose.runOnIdle { isolated.state.value = PlayerState(song = container.playbackQueue.state.value.current,
                playRequested = true, loading = true, positionMs = 2500, durationMs = 10000) }
            compose.onNodeWithTag("mini_toggle").assertContentDescriptionEquals("暂停")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
            compose.runOnIdle { isolated.state.value = isolated.state.value.copy(playing = true, loading = false) }
            compose.onNodeWithTag("mini_toggle").assertContentDescriptionEquals("暂停")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(.25f, 0f..1f)))
            compose.runOnIdle { isolated.state.value = PlayerState(error = "fixture failed") }
            compose.onNodeWithTag("mini_toggle").assertContentDescriptionEquals("播放")
        } finally { scope.cancel() }
    }
    @Test fun audioCacheSettingsPersistPolicyAndClearOnlyFixtureCache() = runBlocking<Unit> {
        val source = io.github.currencortex.music.data.song.AudioSource(server.url("/fixture-bytes").toString(), "standard", 44100, 2, "mp3")
        val key = AudioCache.key(server.url("/cm/").toString(), 7, 1, AudioQuality.STANDARD)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            container.audioCache.rememberSource(key, source); container.audioCache.preload(key, source, 2)
        }
        assertTrue(container.audioCache.bytes.value > 0)
        val vm = MusicSettingsViewModel(container)
        compose.setContent { LeiTheme(AppearanceSettings()) { MusicSettingsScreen(vm) {} } }
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("preload_audio"))
        compose.onNodeWithTag("preload_audio").performClick()
        compose.waitUntil(3000) { !vm.settings.value.preloadAudio }
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("preload_metered"))
        compose.onNodeWithTag("preload_metered").performClick()
        compose.waitUntil(3000) { vm.settings.value.preloadMetered }
        assertFalse(container.musicSettings.snapshot().preloadAudio)
        assertTrue(container.musicSettings.snapshot().preloadMetered)
        compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("clear_audio_cache"))
        compose.onNodeWithTag("clear_audio_cache").performClick()
        compose.waitUntil(3000) { vm.cacheState.value.message == "歌曲缓存已清理" }
        assertEquals(0L, container.audioCache.bytes.value)
        assertEquals(3, container.playbackQueue.state.value.songs.size)
        assertEquals(1, container.playbackQueue.state.value.index)
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
