package io.github.currencortex.music

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlaybackMode
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.feature.library.PlaylistPanelCornerRadius
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises real navigation and menus with isolated storage and synthetic server data. */
class PlaylistPagesTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private var animationScales: Map<String, String?>? = null
    @Volatile private var holdRefresh = false
    @Volatile private var longPlaylist = false
    @Volatile private var refreshedTracks = false
    @Volatile private var refreshFailure = false
    private val refreshEntered = CountDownLatch(1)
    private val releaseRefresh = CountDownLatch(1)
    private val selectedAudioRequested = CountDownLatch(1)
    private val selectedAudioBytesRequested = CountDownLatch(1)
    private val selectedAudioResolutions = java.util.concurrent.atomic.AtomicInteger()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private fun json(value: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(value)

    @Before fun setup(): Unit = runBlocking {
        container = AppContainer(context, "playlist-ui-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        val image = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("release-preview.png").use { it.readBytes() }
        val artwork = File(context.cacheDir, "playlist-preview.png").apply { writeBytes(image) }.toURI().toString()
        val audio = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("download/fixture.wav").use { it.readBytes() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath == "/cm/ncm/song/url" && request.requestUrl!!.queryParameter("id") == "60") {
                    selectedAudioResolutions.incrementAndGet(); selectedAudioRequested.countDown()
                    return json("""{"url":"${server.url("/selected.wav")}","level":"standard","sr":44100,"ch":2,"type":"wav"}""")
                }
                if (request.requestUrl!!.encodedPath == "/selected.wav") {
                    selectedAudioBytesRequested.countDown()
                    return MockResponse().setHeader("Content-Type", "audio/wav").setBody(Buffer().write(audio))
                }
                if (request.requestUrl!!.encodedPath == "/cm/playlists/3" && holdRefresh) {
                    refreshEntered.countDown()
                    releaseRefresh.await(15, TimeUnit.SECONDS)
                }
                if (request.requestUrl!!.encodedPath == "/cm/playlists/3" && refreshFailure)
                    return MockResponse().setResponseCode(502)
                val cover = artwork
                val tracks = """{"ncm_id":1,"name":"Library track","artists":"Artist","album":"Moonlight","pic":"$cover","duration":110000},
                    {"ncm_id":2,"name":"Alpha night","artists":"Second artist","album":"City lights","pic":"$cover","duration":190000},
                    {"ncm_id":3,"name":"Zulu sunset","artists":"Third artist","album":"Ocean","pic":"$cover","duration":240000}"""
                val allTracks = if (refreshedTracks) """{"ncm_id":77,"name":"New arrival","artists":"New artist","pic":"$cover","duration":180000},""" + tracks
                else if (longPlaylist) tracks + "," + (4..60).joinToString(",") {
                    """{"ncm_id":$it,"name":"Playlist track $it","artists":"Test artist","pic":"$cover","duration":180000}"""
                } else tracks
                return when (request.requestUrl!!.encodedPath) {
                    "/cover.png" -> MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(image))
                    "/cm/playlists" -> json("""{"playlists":[{"id":3,"name":"夜晚的音乐收藏","cover":"$cover","source":"ncm","user_id":7,"track_count":3}]}""")
                    "/cm/playlists/3" -> json("""{"id":3,"name":"夜晚的音乐收藏","cover":"$cover","description":"让旋律陪伴每一个安静的夜晚。","source":"ncm","user_id":7,"tracks":[$allTracks]}""")
                    "/cm/daily" -> json("""{"daily":[],"forYou":[]}""")
                    "/cm/ncmbind" -> json("""{"bound":false}""")
                    "/cm/plays/recent", "/cm/likes/mine" -> json("""{"songs":[]}""")
                    "/cm/songs/status" -> json("""{"liked":[2],"faved":[]}""")
                    "/cm/room/active" -> json("""{"room":null}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        container.musicSettings.setServer(server.url("/cm/").toString())
        container.accountRepository.server = server.url("/cm/").toString()
        container.accountRepository.save("isolated-playlist-token", UserDto(7, nickname = "Test listener"))
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { AppearanceSettings(themeMode = ThemeMode.LIGHT, blur = true) }
        val paused = Song(99, "Paused test track", "Artist", cover = artwork)
        container.playbackQueue.replace(listOf(paused), 0)
        container.playerController.state.value = PlayerState(song = paused)
    }
    @After fun teardown() {
        compose.mainClock.autoAdvance = true; releaseRefresh.countDown(); container.close(); server.shutdown()
        animationScales?.forEach { (key, value) ->
            val command = if (value == null) "settings delete global $key" else "settings put global $key ${value.toFloat()}"
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            ).use { it.readBytes() }
        }
    }

    private fun open() {
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("open_playlists").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open_playlists").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("playlist_card_3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("playlist_card_3").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("playlist_song_2").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun depthRibbonFocusDoesNotPlayUntilRequestedAndReturnsToTheList() {
        open()
        compose.onNodeWithTag("playlist_depth_ribbon_toggle").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("playlist_depth_ribbon").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { container.settings.snapshot().playlistDepthRibbon })
        compose.onNodeWithTag("playlist_top_bar").assertDoesNotExist()
        compose.onNodeWithText("返回列表").assertDoesNotExist()
        compose.onNodeWithText("暂停漫游").assertDoesNotExist()
        compose.onNodeWithText("查看当前歌曲").assertDoesNotExist()
        compose.onNodeWithText("左右滑动穿行 · 点击封面 · 长按看全景").assertDoesNotExist()
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        compose.onNodeWithTag("depth_ribbon_canvas").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex) { it(1) }
        compose.onNodeWithText("Alpha night").assertIsDisplayed()
        assertEquals(listOf(99L), container.playbackQueue.state.value.songs.map { it.id })
        compose.onNodeWithTag("depth_ribbon_play").performClick()
        if (compose.onAllNodesWithText("继续播放").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("继续播放").performClick()
        compose.waitUntil(5000) { container.playbackQueue.state.value.current?.id == 2L }
        assertEquals(listOf(1L, 2L, 3L), container.playbackQueue.state.value.songs.map { it.id })
        compose.runOnUiThread { container.playerController.pause() }
        compose.onNodeWithTag("depth_ribbon_release").performClick()
        compose.onNodeWithTag("depth_ribbon_play").assertDoesNotExist()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("playlist_song_2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("playlist_song_2").assertExists()
        assertFalse(runBlocking { container.settings.snapshot().playlistDepthRibbon })
    }

    @Test fun firstPlaybackAndPlayerReturnRetainTheSameRibbonAndTravelPosition() {
        longPlaylist = true
        container.playbackQueue.clear()
        container.playerController.state.value = PlayerState()
        open()
        compose.onNodeWithTag("playlist_depth_ribbon_toggle").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("depth_ribbon_canvas").fetchSemanticsNodes().isNotEmpty() }
        fun renderer(): io.github.currencortex.music.feature.library.DepthRibbonView {
            lateinit var result: io.github.currencortex.music.feature.library.DepthRibbonView
            compose.runOnUiThread {
                fun find(view: android.view.View): io.github.currencortex.music.feature.library.DepthRibbonView? {
                    if (view is io.github.currencortex.music.feature.library.DepthRibbonView) return view
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    return null
                }
                val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
                result = requireNotNull(find(activity.window.decorView))
            }
            return result
        }
        val original = renderer()
        // Return to the first song after a full loop, so resetting to zero is observable.
        compose.onNodeWithTag("depth_ribbon_canvas").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(-8850f, 0f) }
        compose.onNodeWithTag("depth_ribbon_canvas").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex) { it(0) }
        compose.runOnUiThread { assertEquals(60f, original.travelPosition, .001f) }
        compose.onNodeWithTag("depth_ribbon_play").performClick()
        if (compose.onAllNodesWithText("继续播放").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("继续播放").performClick()
        compose.waitUntil(5000) { container.playbackQueue.state.value.current?.id == 1L }
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        compose.waitForIdle()
        assertSame("The first mini player must not recreate the gallery", original, renderer())
        compose.onNodeWithTag("depth_ribbon_play").assertIsDisplayed()
        repeat(2) {
            compose.onNodeWithTag("mini_song_gesture").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("player_screen").fetchSemanticsNodes().isNotEmpty() }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5000) { compose.onAllNodesWithTag("player_screen").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("mini_player").assertIsDisplayed()
            assertSame("Closing the player must keep the retained renderer", original, renderer())
            compose.runOnUiThread { assertEquals(60f, original.travelPosition, .001f) }
            compose.onNodeWithTag("depth_ribbon_play").assertIsDisplayed()
        }
    }

    @Test fun depthRibbonDragHoldAndFocusKeepTheOriginalQueue() {
        // Compose's rule disables platform animators. Enable them only for this real-frame probe.
        animationScales = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .associateWith { android.provider.Settings.Global.getString(context.contentResolver, it) }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.setAnimationScale(1f)
        compose.waitUntil(5000) { android.animation.ValueAnimator.areAnimatorsEnabled() }
        longPlaylist = true
        open()
        compose.onNodeWithTag("playlist_depth_ribbon_toggle").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("depth_ribbon_canvas").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        lateinit var renderer: io.github.currencortex.music.feature.library.DepthRibbonView
        lateinit var window: android.view.Window
        fun findRenderer(view: android.view.View): io.github.currencortex.music.feature.library.DepthRibbonView? {
            if (view is io.github.currencortex.music.feature.library.DepthRibbonView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findRenderer(view.getChildAt(i))?.let { return it }
            return null
        }
        compose.runOnIdle {
            window = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first().window
            renderer = requireNotNull(findRenderer(window.decorView))
        }
        Thread.sleep(700)
        val drawStart = renderer.renderedFrames
        val frames = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            frames.add(metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION))
        }
        compose.runOnUiThread { window.addOnFrameMetricsAvailableListener(listener, android.os.Handler(android.os.Looper.getMainLooper())) }
        Thread.sleep(2000)
        compose.runOnUiThread { window.removeOnFrameMetricsAvailableListener(listener) }
        val sorted = frames.toList().sorted()
        val detail = listOf("running", "paused", "touching", "focused").associateWith { field ->
            renderer.javaClass.getDeclaredField(field).apply { isAccessible = true }.get(renderer)
        }
        val diagnostic = "nativeDraws=${renderer.renderedFrames - drawStart}, metrics=${sorted.size}, animators=${android.animation.ValueAnimator.areAnimatorsEnabled()}, $detail"
        File(context.externalCacheDir, "depth-ribbon-diagnostic.txt").writeText(diagnostic)
        assertTrue("Native ride must continue without Compose recomposition: $diagnostic", sorted.size > 20)
        File(context.externalCacheDir, "depth-ribbon-frames.txt").writeText(
            "frames=${sorted.size}, medianMs=${sorted[sorted.size / 2] / 1e6}, p95Ms=${sorted[(sorted.size * .95).toInt()] / 1e6}")
        val pauseRide = compose.onNodeWithTag("depth_ribbon_canvas").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].first { it.label == "暂停漫游" }
        compose.runOnUiThread { assertTrue(pauseRide.action()) }
        compose.waitForIdle()
        // The native renderer uses Android's frame clock, independent of Compose test time.
        Thread.sleep(900)
        fun screenshot(name: String) {
            compose.onNodeWithTag("music_window").captureToImage().asAndroidBitmap().let { bitmap ->
                File(context.externalCacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        screenshot("depth-ribbon-ride.png")
        var before = 0L
        compose.runOnUiThread { renderer.setRunning(false); before = renderer.renderedFrames }
        Thread.sleep(350)
        compose.runOnUiThread { assertTrue("Background ribbon must stop scheduling frames", renderer.renderedFrames - before <= 1); renderer.setRunning(true) }
        repeat(5) { compose.onNodeWithTag("depth_ribbon_canvas").performTouchInput { swipe(center, center.copy(x = width * .15f), 400) } }
        compose.onNodeWithTag("depth_ribbon_canvas").performTouchInput { down(center) }
        Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 180)
        compose.runOnUiThread { assertTrue("Long hold must ease through an intermediate view", renderer.revealFraction in .02f.. .65f) }
        screenshot("depth-ribbon-overview-transition.png")
        Thread.sleep(1000)
        compose.runOnUiThread { assertTrue("Holding completes the pull-back", renderer.revealFraction > .95f) }
        screenshot("depth-ribbon-overview.png")
        compose.onNodeWithTag("depth_ribbon_canvas").performTouchInput { up() }
        Thread.sleep(180)
        compose.runOnUiThread { assertTrue("Release must ease back instead of snapping", renderer.revealFraction in .4f.. .99f) }
        Thread.sleep(1500)
        compose.onNodeWithTag("depth_ribbon_canvas").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex) { it(59) }
        compose.onNodeWithText("Playlist track 60").assertIsDisplayed()
        Thread.sleep(900)
        screenshot("depth-ribbon-focus.png")
        assertEquals(listOf(99L), container.playbackQueue.state.value.songs.map { it.id })
        compose.onNodeWithTag("depth_ribbon_play").performClick()
        Thread.sleep(200)
        compose.runOnUiThread { assertTrue("Play opens the sleeve before changing the queue", (renderer.recordFlightProgress ?: 1f) in .01f.. .5f) }
        assertEquals("Keep the original song during extraction", 99L, container.playbackQueue.state.value.current?.id)
        compose.onNodeWithTag("mini_title", useUnmergedTree = true).assertTextEquals("Paused test track")
        assertTrue("Resolve the selected song while the old song remains active", selectedAudioRequested.await(500, TimeUnit.MILLISECONDS))
        assertTrue("Warm audio bytes before landing", selectedAudioBytesRequested.await(500, TimeUnit.MILLISECONDS))
        screenshot("depth-ribbon-record-eject.png")
        compose.runOnUiThread {
            val beforeRepeat = renderer.recordFlightProgress
            renderer.ejectRecord(60)
            assertEquals("Repeated play must not restart or stack records", beforeRepeat, renderer.recordFlightProgress)
        }
        Thread.sleep(420)
        screenshot("depth-ribbon-record-flight.png")
        Thread.sleep(1200)
        compose.runOnUiThread { assertNull("The record must finish without locking navigation", renderer.recordFlightProgress) }
        if (compose.onAllNodesWithText("继续播放").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("继续播放").performClick()
        compose.waitUntil(5000) { container.playbackQueue.state.value.current?.id == 60L }
        compose.onNodeWithTag("mini_title", useUnmergedTree = true).assertTextEquals("Playlist track 60")
        val request = container.audioRequest(container.playbackQueue.state.value.current!!, container.musicSettings.state.value.quality)
        runBlocking { container.audioSources.resolve(request) }
        assertEquals("Playback reuses the prefetched source", 1, selectedAudioResolutions.get())
        assertTrue(runBlocking(kotlinx.coroutines.Dispatchers.IO) { container.audioCache.cachedBytes(request.key, 1024) > 0 })
        compose.onNodeWithTag("depth_ribbon_release").performClick()
        compose.onNodeWithTag("depth_ribbon_play").assertDoesNotExist()
    }

    @Test fun searchSortAndSongActionsUseTheVisibleSongsAndPreserveReadonlyOwnership() {
        open()
        compose.onNodeWithTag("playlist_hero").assertIsDisplayed()
        compose.onNodeWithText("3 首 · 9分钟").assertIsDisplayed()
        compose.onNodeWithTag("playlist_more").performClick()
        compose.onNodeWithTag("rename_playlist").assertDoesNotExist()
        compose.onNodeWithTag("delete_playlist").assertDoesNotExist()
        compose.onNodeWithTag("playlist_menu_info").performClick()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("playlist_search_toggle").performClick()
        compose.onNodeWithTag("playlist_query").performTextInput("ALPHA")
        compose.onNodeWithText("找到 1 首 / 共 3 首").assertIsDisplayed()
        compose.onNodeWithTag("playlist_song_1").assertDoesNotExist()
        compose.onNodeWithTag("song_menu_2").performClick()
        compose.onNodeWithTag("song_download").assertExists()
        compose.onNodeWithText("加入队列").performClick()
        assertEquals(listOf(99L, 2L), container.playbackQueue.state.value.songs.map { it.id })
        assertFalse(container.playerController.state.value.playing)
        compose.onNodeWithContentDescription("关闭歌单搜索").performClick()
        compose.onNodeWithTag("playlist_sort").performClick()
        compose.onNodeWithTag("playlist_order_1").performClick()
        compose.onNodeWithTag("play_library_all").performClick()
        if (compose.onAllNodesWithText("继续播放").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("继续播放").performClick()
        compose.waitUntil(5000) { container.playbackQueue.state.value.songs.map { it.id } == listOf(2L, 1L, 3L) }
        assertEquals(PlaybackMode.LIST, container.playbackQueue.state.value.mode)
        assertEquals(2L, container.playbackQueue.state.value.current!!.id)
        compose.runOnUiThread { container.playerController.pause() }
    }

    @Test fun coverHeroAndStickyPlaybackRemainVisibleInLightAndDarkThemes() {
        open()
        compose.waitForIdle()
        fun screenshot(name: String) {
            compose.onNodeWithTag("music_window").captureToImage().asAndroidBitmap().let { bitmap ->
                File(context.externalCacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        screenshot("playlist-redesign-light.png")
        runBlocking { container.settings.edit { it.copy(themeMode = ThemeMode.DARK) } }
        compose.waitForIdle()
        screenshot("playlist-redesign-dark.png")
        compose.onNodeWithTag("playlist_search_toggle").performClick()
        compose.onNodeWithTag("play_library_all").assertIsDisplayed()
        compose.onNodeWithTag("playlist_query").performTextInput("not found")
        compose.onNodeWithText("没有找到匹配的歌曲").assertIsDisplayed()
        compose.onNodeWithTag("play_library_all").assertIsNotEnabled()
        compose.onNodeWithContentDescription("关闭歌单搜索").performClick()
        assertEquals(listOf(99L), container.playbackQueue.state.value.songs.map { it.id })
    }

    @Test fun pullMovesOnlyTheSongSurfaceAndReboundsWithoutAWhiteHeader() {
        open()
        compose.waitForIdle()
        val initialTop = compose.onNodeWithTag("playlist_hero").fetchSemanticsNode().boundsInRoot.top
        val panelTop = compose.onNodeWithTag("play_library_all").fetchSemanticsNode().boundsInRoot.top
        val miniBounds = compose.onNodeWithTag("mini_player").fetchSemanticsNode().boundsInRoot
        holdRefresh = true
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("playlist_tracks").performTouchInput {
            down(center.copy(y = height * .55f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, 350f), delayMillis = 240)
        }
        compose.mainClock.advanceTimeBy(32)
        val draggedTop = compose.onNodeWithTag("play_library_all").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The song surface must follow the downward drag", draggedTop > panelTop + 1)
        assertTrue("Pull travel is damped and bounded", draggedTop - panelTop <= 36 * context.resources.displayMetrics.density + 1)
        assertEquals(initialTop, compose.onNodeWithTag("playlist_hero").fetchSemanticsNode().boundsInRoot.top, 2f)
        assertEquals(miniBounds, compose.onNodeWithTag("mini_player").fetchSemanticsNode().boundsInRoot)
        val window = compose.onNodeWithTag("music_window")
        val bitmap = window.captureToImage().asAndroidBitmap()
        val topBarBottom = compose.onNodeWithTag("playlist_top_bar").fetchSemanticsNode().boundsInRoot.bottom
        val pixel = bitmap.getPixel((12 * context.resources.displayMetrics.density).toInt(), (topBarBottom + 8).toInt())
        assertTrue("Pulling must not reveal a white area below the fixed toolbar",
            android.graphics.Color.red(pixel) < 128 && android.graphics.Color.green(pixel) < 128 && android.graphics.Color.blue(pixel) < 128)
        File(context.externalCacheDir, "playlist-panel-drag.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithTag("playlist_tracks").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(32)
        val settlingTop = compose.onNodeWithTag("play_library_all").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Release should animate instead of snapping", settlingTop > panelTop + 1 && settlingTop < draggedTop)
        compose.mainClock.advanceTimeBy(1000)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10000) { refreshEntered.count == 0L }
        compose.onNodeWithText("正在刷新").assertDoesNotExist()
        compose.onNodeWithText("松开刷新").assertDoesNotExist()
        compose.onNodeWithText("Refreshing...").assertDoesNotExist()
        compose.onNodeWithTag("playlist_share").assertIsEnabled()
        compose.onNodeWithTag("playlist_song_2").assertExists()
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        assertEquals(initialTop, compose.onNodeWithTag("playlist_hero").fetchSemanticsNode().boundsInRoot.top, 2f)
        assertEquals(panelTop, compose.onNodeWithTag("play_library_all").fetchSemanticsNode().boundsInRoot.top, 2f)
        releaseRefresh.countDown()
        compose.waitForIdle()
        compose.onNodeWithTag("playlist_share").assertIsEnabled()
        assertEquals(initialTop, compose.onNodeWithTag("playlist_hero").fetchSemanticsNode().boundsInRoot.top, 2f)
        assertEquals(listOf(99L), container.playbackQueue.state.value.songs.map { it.id })
    }

    @Test fun playlistMiniPlayerOpensQueueAndPlayerAndReturnsWithoutStartingPlayback() {
        open()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread { container.playerController.state.value = container.playerController.state.value.copy(playing = true) }
            compose.mainClock.advanceTimeBy(240)
            compose.onNodeWithTag("mini_player").assertIsDisplayed()
            compose.onNodeWithTag("mini_toggle").assertContentDescriptionEquals("暂停")
        } finally {
            compose.runOnUiThread { container.playerController.state.value = container.playerController.state.value.copy(playing = false) }
            compose.mainClock.advanceTimeBy(64)
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        compose.onNodeWithTag("mini_toggle").assertContentDescriptionEquals("播放")
        compose.onNodeWithTag("mini_queue").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("mini_queue_sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("queue_song_0").assertIsDisplayed()
        compose.onNodeWithText("向下轻扫关闭播放队列").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("mini_queue_sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        compose.onNodeWithTag("mini_cover").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("player_screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("收起播放器").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("player_screen").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        compose.onNodeWithTag("playlist_song_2").assertIsDisplayed()
        assertEquals(listOf(99L), container.playbackQueue.state.value.songs.map { it.id })
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun playlistDoesNotShowAnEmptyMiniPlayerWithoutACurrentSong() {
        container.playbackQueue.clear()
        container.playerController.state.value = PlayerState()
        open()
        compose.onNodeWithTag("mini_player").assertDoesNotExist()
        compose.onNodeWithTag("playlist_song_2").assertIsDisplayed()
    }

    private fun requestRefresh() {
        compose.onNodeWithTag("playlist_more").performClick()
        compose.onNodeWithText("刷新歌单").performClick()
    }

    @Test fun backgroundRefreshKeepsOldRowsAndAnimatesOnlyTheNewArrival() {
        open()
        compose.waitForIdle()
        val before = compose.onNodeWithTag("playlist_song_1").fetchSemanticsNode().boundsInRoot
        holdRefresh = true
        requestRefresh()
        compose.waitUntil(10000) { refreshEntered.count == 0L }
        compose.onNodeWithTag("playlist_song_1").assertIsDisplayed()
        compose.onNodeWithTag("playlist_song_2").assertIsDisplayed()
        compose.onNodeWithTag("playlist_share").assertIsEnabled()
        assertEquals(before, compose.onNodeWithTag("playlist_song_1").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        refreshedTracks = true
        compose.mainClock.autoAdvance = false
        releaseRefresh.countDown()
        compose.waitUntil(10000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("playlist_song_77").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(32)
        val window = compose.onNodeWithTag("music_window")
        fun arrivalImage(): Bitmap {
            val root = window.fetchSemanticsNode().boundsInRoot
            val row = compose.onNodeWithTag("playlist_song_77").fetchSemanticsNode().boundsInRoot
            val bitmap = window.captureToImage().asAndroidBitmap()
            return Bitmap.createBitmap(bitmap, (row.left - root.left).toInt(), (row.top - root.top).toInt(), row.width.toInt(), row.height.toInt())
        }
        val early = arrivalImage()
        val movingTop = compose.onNodeWithTag("playlist_song_1").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(400)
        val final = arrivalImage()
        val settledTop = compose.onNodeWithTag("playlist_song_1").fetchSemanticsNode().boundsInRoot.top
        File(context.externalCacheDir, "playlist-arrival-early.png").outputStream().use { early.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(context.externalCacheDir, "playlist-arrival-final.png").outputStream().use { final.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The old first row is still moving out of this area during fade-in, so
        // the darkest pixel alone cannot measure the new row's opacity.
        assertTrue("Existing row must be partway through making room for the arrival",
            movingTop >= before.top && movingTop < settledTop - 1)
        val changedPixels = (0 until minOf(early.height, final.height) step 3).sumOf { y ->
            (0 until minOf(early.width, final.width) step 3).count { x -> early.getPixel(x, y) != final.getPixel(x, y) }
        }
        assertTrue("Arrival must have distinct transitional and completed frames", changedPixels > 100)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("4 首 · 12分钟").assertIsDisplayed()
        compose.onNodeWithTag("playlist_song_1").assertIsDisplayed()
        compose.onNodeWithTag("playlist_song_2").assertIsDisplayed()
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        assertEquals(listOf(99L), container.playbackQueue.state.value.songs.map { it.id })
    }

    @Test fun revisionRefreshDoesNotClearThePinnedListAndUnchangedRowsStayInPlace() {
        longPlaylist = true
        open()
        compose.onNodeWithTag("playlist_tracks").performScrollToIndex(10)
        compose.waitForIdle()
        val before = compose.onNodeWithTag("playlist_song_9").fetchSemanticsNode().boundsInRoot
        holdRefresh = true
        container.libraryRepository.invalidate()
        compose.waitUntil(10000) { refreshEntered.count == 0L }
        assertEquals(before, compose.onNodeWithTag("playlist_song_9").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        releaseRefresh.countDown()
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithTag("playlist_song_9").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun failedBackgroundRefreshPreservesSongsAndUsableControls() {
        open()
        refreshFailure = true
        requestRefresh()
        compose.waitUntil(10000) { compose.onAllNodesWithText("刷新失败，已保留原列表：", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("playlist_song_1").assertIsDisplayed()
        compose.onNodeWithTag("playlist_song_2").assertIsDisplayed()
        compose.onNodeWithTag("playlist_share").assertIsEnabled()
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        compose.onNodeWithText("3 首 · 9分钟").assertIsDisplayed()
    }

    @Test fun scrollingMorphsThePanelToAnOpaqueRectangleAndRestoresItsCorners() {
        longPlaylist = true
        open()
        val density = context.resources.displayMetrics.density
        fun radius() = compose.onNodeWithTag("playlist_playback_panel").fetchSemanticsNode().config[PlaylistPanelCornerRadius]
        assertEquals(26 * density, radius(), 1f)
        val list = compose.onNodeWithTag("playlist_tracks")
        val travel = compose.onNodeWithTag("playlist_playback_panel").fetchSemanticsNode().boundsInRoot.top - list.fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        list.performTouchInput {
            down(center.copy(y = height * .65f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, -(travel - 24 * density)), delayMillis = 300)
        }
        compose.mainClock.advanceTimeBy(32)
        assertTrue("Corners must change continuously before reaching the toolbar", radius() > 0 && radius() < 26 * density)
        list.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f, -100 * density), delayMillis = 300) }
        compose.mainClock.advanceTimeBy(32)
        assertEquals("Pinned playback panel must be exactly rectangular", 0f, radius(), .01f)
        val panel = compose.onNodeWithTag("playlist_playback_panel").fetchSemanticsNode().boundsInRoot
        val viewport = list.fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.top, panel.top, 1f)
        val window = compose.onNodeWithTag("music_window")
        val root = window.fetchSemanticsNode().boundsInRoot
        val bitmap = window.captureToImage().asAndroidBitmap()
        for (x in listOf(panel.left + 18 * density, panel.right - 18 * density)) {
            val pixel = bitmap.getPixel((x - root.left).toInt(), (panel.top - root.top + 2).toInt())
            assertTrue("Pinned corners must cover the album art underneath", android.graphics.Color.red(pixel) > 210 &&
                android.graphics.Color.green(pixel) > 210 && android.graphics.Color.blue(pixel) > 210)
        }
        File(context.externalCacheDir, "playlist-panel-pinned.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        list.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        list.performScrollToIndex(0)
        compose.waitForIdle()
        assertEquals(26 * density, radius(), 1f)
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
    }
}
