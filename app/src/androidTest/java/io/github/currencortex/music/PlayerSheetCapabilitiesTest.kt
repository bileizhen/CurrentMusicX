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
    private lateinit var activity: androidx.activity.ComponentActivity

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
            activity = androidx.activity.compose.LocalActivity.current as androidx.activity.ComponentActivity
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

    @Test fun profileCollapseIncludingMiniPlayerRestoration() {
        start()
        val handlerThread = android.os.HandlerThread("collapse-frame-metrics").apply { start() }
        val records = java.util.Collections.synchronizedList(mutableListOf<org.json.JSONObject>())
        val phase = java.util.concurrent.atomic.AtomicReference("idle")
        val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            if (phase.get() != "idle") records.add(org.json.JSONObject().apply {
                put("phase", phase.get())
                put("totalMs", metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION) / 1_000_000.0)
                put("layoutMs", metrics.getMetric(android.view.FrameMetrics.LAYOUT_MEASURE_DURATION) / 1_000_000.0)
                put("drawMs", metrics.getMetric(android.view.FrameMetrics.DRAW_DURATION) / 1_000_000.0)
                put("syncMs", metrics.getMetric(android.view.FrameMetrics.SYNC_DURATION) / 1_000_000.0)
                put("commandMs", metrics.getMetric(android.view.FrameMetrics.COMMAND_ISSUE_DURATION) / 1_000_000.0)
                put("droppedReports", dropped)
            })
        }
        compose.runOnUiThread { activity.window.addOnFrameMetricsAvailableListener(listener, android.os.Handler(handlerThread.looper)) }
        try {
            repeat(2) { index ->
                compose.onNodeWithTag("mini_cover").performClick()
                compose.waitForIdle()
                compose.mainClock.autoAdvance = false
                phase.set("collapse-${index + 1}")
                compose.onNodeWithTag("navigate_back").performClick()
                repeat(50) {
                    val begin = android.os.SystemClock.elapsedRealtime()
                    compose.mainClock.advanceTimeByFrame()
                    compose.waitForIdle()
                    val remaining = 16 - (android.os.SystemClock.elapsedRealtime() - begin)
                    if (remaining > 0) android.os.SystemClock.sleep(remaining)
                }
                phase.set("idle")
                compose.mainClock.autoAdvance = true
                compose.onNodeWithTag("mini_cover").assertIsDisplayed()
            }
            assertTrue("Real window frames must be captured", records.isNotEmpty())
            val label = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("profileLabel", "latest")
            val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
            val snapshot = synchronized(records) { records.toList() }
            java.io.File(context.externalCacheDir, "player-collapse-profile-$label.json").writeText(org.json.JSONArray(snapshot).toString(2))
        } finally {
            compose.runOnUiThread { activity.window.removeOnFrameMetricsAvailableListener(listener) }
            handlerThread.quitSafely()
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
        val miniIdentity = compose.onNodeWithTag("mini_cover").fetchSemanticsNode().id
        val underlay = compose.onNodeWithTag("music_navigation").captureToImage().toPixelMap()
        fun sampleSurface(frame: PlayerSheetFrame): androidx.compose.ui.graphics.Color {
            val pixels = compose.onNodeWithTag("music_navigation").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, (frame.bounds.top + 16).toInt()]
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("mini_cover").performClick()
        compose.mainClock.advanceTimeBy(64)
        val first = frame()
        val firstSurface = sampleSurface(first)
        assertTrue("The first captured frame must still be expanding", first.progress > 0 && first.progress < .95f)
        assertTrue("The player starts at the bottom mini player, then grows upward", first.bounds.top > original.height * .25f && first.bounds.top < mini.top)
        assertEquals("The underlying scene must not slide sideways", original,
            compose.onNodeWithTag("music_scene_21").fetchSemanticsNode().boundsInRoot)
        val layout = compose.onNodeWithTag("player_safe_content").fetchSemanticsNode().layoutInfo.let { it.width to it.height }
        save("player-sheet-open-064.png")
        compose.mainClock.advanceTimeBy(112)
        val middle = frame()
        val middleSurface = sampleSurface(middle)
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
        val fullSurface = sampleSurface(frame())
        fun assertSurfaceFade(frame: PlayerSheetFrame, color: androidx.compose.ui.graphics.Color) {
            val behind = underlay[underlay.width / 2, (frame.bounds.top + 16).toInt()]
            val expected = behind.red * (1 - frame.progress) + fullSurface.red * frame.progress
            assertEquals("The card background must fade too, not just its text", expected, color.red, .04f)
        }
        assertSurfaceFade(first, firstSurface)
        assertSurfaceFade(middle, middleSurface)
        compose.onNodeWithTag("navigate_back").performClick()
        compose.mainClock.advanceTimeBy(96)
        val closing = frame()
        assertSurfaceFade(closing, sampleSurface(closing))
        assertTrue("Closing reverses the same upward expansion", closing.progress < .99f && closing.bounds.top > 0)
        compose.onNodeWithTag("mini_cover").assertDoesNotExist()
        save("player-sheet-close-096.png")
        compose.mainClock.advanceTimeBy(96)
        val laterClosing = frame()
        assertTrue("Collapse must keep moving and fading rather than switch state early", laterClosing.progress < closing.progress)
        assertSurfaceFade(laterClosing, sampleSurface(laterClosing))
        save("player-sheet-close-192.png")
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithTag("player_sheet").assertDoesNotExist()
        compose.onNodeWithTag("network_settings").assertIsDisplayed()
        compose.onNodeWithTag("mini_cover").assertIsDisplayed()
        assertEquals("Docking must reuse the cover node", miniIdentity, compose.onNodeWithTag("mini_cover").fetchSemanticsNode().id)
        assertEquals("Docking must retain the original capsule position", mini, compose.onNodeWithTag("mini_glass_surface").fetchSemanticsNode().boundsInRoot)
        assertEquals(original, viewport())
        assertEquals(55L, container.playerController.queue.state.value.current?.id)
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun heldLiquidIndicatorCanDrawOutsideDock() {
        start()
        val root = compose.onNodeWithTag("music_navigation")
        val original = viewport()
        val dock = compose.onNodeWithTag("music_dock").fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag("glass_floating_bar")
        val barBounds = bar.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        val before = root.captureToImage().toPixelMap()
        bar.performTouchInput { down(androidx.compose.ui.geometry.Offset(center.x / 4, center.y)) }
        try {
            compose.mainClock.advanceTimeBy(600)
            val pressed = root.captureToImage().toPixelMap()
            save("dock-liquid-held.png")
            val left = (dock.left - original.left).toInt()
            val yStart = (barBounds.top - original.top - 40).toInt().coerceAtLeast(0)
            val yEnd = (barBounds.bottom - original.top + 40).toInt().coerceAtMost(pressed.height)
            var changed = 0
            for (x in (left - 40).coerceAtLeast(0) until left - 1) for (y in yStart until yEnd) {
                val a = before[x, y]; val b = pressed[x, y]
                val difference = kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)
                if (difference > .025f) changed++
            }
            assertTrue("The held indicator must paint outside the left glass boundary (pixels=$changed)", changed > 10)
            val right = (dock.right - original.left).toInt()
            val bottom = (dock.bottom - original.top).toInt()
            for (x in right - 10 until right - 2) for (y in bottom - 10 until bottom - 2) {
                val a = before[x, y]; val b = pressed[x, y]
                val difference = kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)
                assertTrue("Press background must leave the rounded bottom-right corner clear", difference < .01f)
            }
            assertEquals(original, viewport())
            assertEquals(dock, compose.onNodeWithTag("music_dock").fetchSemanticsNode().boundsInRoot)
        } finally {
            bar.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1000)
        }
        compose.onNodeWithTag("tab_0").assertIsSelected()
        compose.onNodeWithTag("mini_cover").assertIsDisplayed()
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun unifiedDockFadesNavigationAndSlidesMiniWithoutResizingThePage() {
        start()
        val original = viewport()
        val firstMini = compose.onNodeWithTag("mini_glass_surface").fetchSemanticsNode().boundsInRoot
        val miniIdentity = compose.onNodeWithTag("mini_cover").fetchSemanticsNode().id
        fun expansion() = compose.onNodeWithTag("music_dock").fetchSemanticsNode().config[io.github.currencortex.music.ui.component.DockExpansion]
        assertEquals(1f, expansion(), .001f)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("设置").performClick()
        compose.mainClock.advanceTimeBy(64)
        val early = expansion()
        assertTrue("Navigation must fade rather than disappear immediately", early > 0f && early < 1f)
        val earlyMini = compose.onNodeWithTag("mini_glass_surface").fetchSemanticsNode().boundsInRoot
        assertTrue("Mini must move down as navigation fades", earlyMini.top > firstMini.top)
        compose.onNodeWithTag("glass_floating_bar").assertDoesNotExist() // Exiting tabs are not interactive.
        compose.mainClock.advanceTimeBy(112)
        assertTrue(expansion() < early)
        val middleMini = compose.onNodeWithTag("mini_glass_surface").fetchSemanticsNode().boundsInRoot
        assertTrue(middleMini.top > earlyMini.top)
        save("unified-dock-secondary-176.png")
        assertEquals(original, viewport())
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(0f, expansion(), .001f)
        val secondaryMini = compose.onNodeWithTag("mini_glass_surface").fetchSemanticsNode().boundsInRoot
        assertTrue(secondaryMini.top > middleMini.top)
        assertEquals(miniIdentity, compose.onNodeWithTag("mini_cover").fetchSemanticsNode().id)
        save("unified-dock-secondary.png")
        compose.onNodeWithText("返回").performClick()
        compose.mainClock.advanceTimeBy(176)
        assertTrue("Returning must reveal navigation gradually", expansion() > 0f && expansion() < 1f)
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(1f, expansion(), .001f)
        assertEquals(firstMini, compose.onNodeWithTag("mini_glass_surface").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("glass_floating_bar").assertIsDisplayed()
        assertEquals(original, viewport())
        save("unified-dock-root.png")
        compose.mainClock.autoAdvance = true
        runBlocking { container.settings.edit { it.copy(blur = false) } }
        compose.waitForIdle()
        compose.onNodeWithTag("plain_floating_bar").assertIsDisplayed()
        compose.onNodeWithTag("mini_cover").assertIsDisplayed()
        assertEquals(1f, expansion(), .001f)
        compose.onNodeWithText("设置").performClick()
        compose.waitForIdle()
        assertEquals(0f, expansion(), .001f)
        compose.onNodeWithTag("plain_floating_bar").assertDoesNotExist()
        compose.onNodeWithTag("mini_cover").assertIsDisplayed()
        assertEquals(original, viewport())
    }

    @Test fun predictiveCollapseReversesCancelsAndCommitsWhileDialogsTakePriority() {
        start()
        val miniIdentity = compose.onNodeWithTag("mini_cover").fetchSemanticsNode().id
        val barIdentity = compose.onNodeWithTag("glass_floating_bar").fetchSemanticsNode().id
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
        assertEquals(miniIdentity, compose.onNodeWithTag("mini_cover").fetchSemanticsNode().id)
        assertEquals("Docking must reuse the glass bar", barIdentity, compose.onNodeWithTag("glass_floating_bar").fetchSemanticsNode().id)
        assertEquals(original, viewport())
        assertEquals(55L, container.playerController.queue.state.value.current?.id)
        assertFalse(container.playerController.state.value.playing)
    }
}
