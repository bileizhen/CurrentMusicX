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
    private var coverFixture: java.io.File? = null

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

    @After fun cleanup() { compose.mainClock.autoAdvance = true; container.close(); server.shutdown(); coverFixture?.delete() }

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
        compose.onNodeWithTag("music_window").captureToImage().asAndroidBitmap().let { bitmap ->
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
                compose.onNodeWithContentDescription("收起播放器").performClick()
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
        val sceneIdentity = compose.onNodeWithTag("music_scene_21").fetchSemanticsNode().id
        val mini = compose.onNodeWithTag("mini_glass_surface").fetchSemanticsNode().boundsInRoot
        val miniIdentity = compose.onNodeWithTag("mini_cover").fetchSemanticsNode().id
        val underlay = compose.onNodeWithTag("music_window").captureToImage().toPixelMap()
        fun sampleSurface(frame: PlayerSheetFrame): androidx.compose.ui.graphics.Color {
            val pixels = compose.onNodeWithTag("music_window").captureToImage().toPixelMap()
            return pixels[(frame.bounds.left + 80).toInt(), (frame.bounds.bottom - 80).toInt()]
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
        val pixels = compose.onNodeWithTag("music_window").captureToImage().toPixelMap()
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
        assertEquals("The source page stays composed even while the player is fully open", sceneIdentity,
            compose.onNodeWithTag("music_scene_21").fetchSemanticsNode().id)
        val fullPixels = compose.onNodeWithTag("music_window").captureToImage().toPixelMap()
        fun assertSurfaceFade(frame: PlayerSheetFrame, color: androidx.compose.ui.graphics.Color) {
            val x = (frame.bounds.left + 80).toInt()
            val y = (frame.bounds.bottom - 80).toInt()
            val behind = underlay[x, y]
            val front = fullPixels[(x - frame.bounds.left).toInt().coerceIn(0, fullPixels.width - 1),
                (y - frame.bounds.top).toInt().coerceIn(0, fullPixels.height - 1)]
            val alpha = io.github.currencortex.music.feature.player.playerSheetSurfaceAlpha(frame.progress)
            val expected = behind.red * (1 - alpha) + front.red * alpha
            assertEquals("The card background must fade too, not just its text", expected, color.red, .04f)
        }
        assertSurfaceFade(first, firstSurface)
        assertSurfaceFade(middle, middleSurface)
        compose.onNodeWithContentDescription("收起播放器").performClick()
        compose.mainClock.advanceTimeBy(96)
        val closing = frame()
        assertSurfaceFade(closing, sampleSurface(closing))
        assertTrue("Closing reverses the same upward expansion", closing.progress < .99f && closing.bounds.top > 0)
        compose.onNodeWithTag("mini_cover").assertDoesNotExist()
        save("player-sheet-close-096.png")
        compose.runOnUiThread { dispatcher.onBackPressed() }
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
        assertEquals("Collapse must not rebuild the source page", sceneIdentity,
            compose.onNodeWithTag("music_scene_21").fetchSemanticsNode().id)
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

    @Test fun pagerSharesOneCircularArtworkAlongReversibleArc() {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val file = java.io.File(context.cacheDir, "pager-artwork-${UUID.randomUUID()}.png")
        coverFixture = file
        android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.MAGENTA)
            file.outputStream().use { compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val song = container.playerController.queue.state.value.current!!.copy(cover = file.toURI().toString())
        container.playerController.queue.replace(listOf(song), 0)
        container.playerController.state.value = container.playerController.state.value.copy(song = song)
        start()
        compose.onNodeWithTag("mini_cover").performClick()
        compose.waitForIdle()
        val pager = compose.onNodeWithTag("player_content_pager")
        fun artwork() = compose.onNodeWithTag("player_pager_artwork").fetchSemanticsNode()
            .config[io.github.currencortex.music.feature.player.PlayerPagerArtworkGeometry]
        val start = artwork()
        compose.mainClock.autoAdvance = false
        pager.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .9f, height * .4f))
            moveBy(androidx.compose.ui.geometry.Offset(-width * .42f, 0f))
        }
        compose.mainClock.advanceTimeBy(64)
        val held = artwork()
        assertTrue(held.progress > .3f && held.progress < .5f)
        assertTrue("The single artwork shrinks toward the lyric header", held.bounds.width < start.bounds.width)
        compose.onAllNodesWithTag("player_pager_artwork").assertCountEquals(1)
        for (tag in listOf("player_cover", "player_lyrics_cover")) {
            assertFalse(compose.onNodeWithTag(tag).fetchSemanticsNode()
                .config[io.github.currencortex.music.feature.player.PlayerArtworkVisible])
        }
        // The pure magenta fixture separates the actual image from the tinted backdrop.
        // Both dimensions must match one circle; a second preloaded image expands this box.
        val pixels = pager.captureToImage().toPixelMap()
        var left = pixels.width; var top = pixels.height; var right = -1; var bottom = -1
        for (y in 0 until pixels.height step 3) for (x in 0 until pixels.width step 3) {
            val color = pixels[x, y]
            if (color.red > .98f && color.blue > .98f && color.green < .02f) {
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        assertTrue("Shared image pixels must be visible", right >= left)
        assertEquals("Only one circular artwork is drawn horizontally", held.bounds.width, (right - left).toFloat(), 8f)
        assertEquals("Only one circular artwork is drawn vertically", held.bounds.height, (bottom - top).toFloat(), 8f)
        save("player-pager-single-artwork-held.png")
        compose.mainClock.advanceTimeBy(176)
        assertEquals("Artwork follows a stationary finger without auto animation", held.bounds, artwork().bounds)
        pager.performTouchInput { advanceEventTime(400); up() }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("A cancelled page drag restores the large cover exactly", start.bounds, artwork().bounds)
        pager.performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .85f, height * .4f),
                androidx.compose.ui.geometry.Offset(width * .15f, height * .4f), 180)
        }
        compose.mainClock.advanceTimeBy(1000)
        val end = artwork()
        assertEquals(1f, end.progress, .001f)
        assertTrue("At the lyric page the shared artwork becomes the small cover", end.bounds.width < start.bounds.width / 2)
        val straight = start.bounds.center * (1 - held.progress) + end.bounds.center * held.progress
        assertTrue("The middle of the path bends gently above the straight line", held.bounds.center.y < straight.y)
        pager.performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .15f, height * .4f),
                androidx.compose.ui.geometry.Offset(width * .85f, height * .4f), 180)
        }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("Reverse page switching returns the same image to its original bounds", start.bounds, artwork().bounds)
        assertEquals(55L, container.playerController.queue.state.value.current?.id)
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun coverLyricsAndPagerFollowDragWithoutChangingPlayback() {
        start()
        compose.onNodeWithTag("mini_cover").performClick()
        compose.waitUntil(12000) {
            compose.onAllNodes(hasTestTag("player_cover_lyric_current") and hasText("星光陪着你"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val title = compose.onNodeWithTag("player_song_title").fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithTag("player_cover_lyrics").fetchSemanticsNode().boundsInRoot
        assertTrue("The lyric preview sits below the song title", preview.top > title.bottom)
        compose.runOnIdle { container.playerController.state.value = container.playerController.state.value.copy(positionMs = 6500) }
        compose.waitUntil(5000) {
            compose.onAllNodes(hasTestTag("player_cover_lyric_current") and hasText("一起听音乐"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        save("player-cover-lyric-preview.png")
        compose.mainClock.autoAdvance = false
        val pager = compose.onNodeWithTag("player_content_pager")
        fun page() = pager.fetchSemanticsNode().config[io.github.currencortex.music.feature.player.PlayerPagePosition]
        pager.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .85f, height * .4f))
            moveBy(androidx.compose.ui.geometry.Offset(-width * .22f, 0f))
        }
        compose.mainClock.advanceTimeBy(64)
        val held = page()
        assertTrue("Horizontal page position follows the held drag", held > 0f && held < .5f)
        compose.mainClock.advanceTimeBy(176)
        assertEquals("A held pager drag must remain under finger control", held, page(), .001f)
        save("player-pager-held.png")
        pager.performTouchInput { advanceEventTime(400); up() }
        compose.mainClock.advanceTimeBy(64)
        val settling = page()
        assertTrue("Release eases toward the cover instead of jumping to its endpoint", settling > 0f && settling < held)
        compose.mainClock.advanceTimeBy(64)
        assertTrue("The buffer moves toward the cover without reversing", page() >= 0f && page() < settling)
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("A short swipe returns to the cover", 0f, page(), .001f)
        pager.performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .85f, height * .4f),
                androidx.compose.ui.geometry.Offset(width * .15f, height * .4f), 180)
        }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("Left swipe opens lyrics", 1f, page(), .001f)
        compose.onNodeWithTag("lyrics_panel").assertIsDisplayed()
        compose.onNodeWithTag("lyrics_panel").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .15f, height * .5f),
                androidx.compose.ui.geometry.Offset(width * .85f, height * .5f), 180)
        }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("Right swipe from lyrics returns to the cover", 0f, page(), .001f)
        val seek = compose.onNodeWithTag("player_seek")
        seek.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .1f, centerY))
            moveTo(androidx.compose.ui.geometry.Offset(width * .75f, centerY))
        }
        compose.mainClock.advanceTimeBy(64)
        assertTrue("Horizontal seeking retains its own preview gesture",
            seek.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current > .6f)
        assertEquals("Seeking must not drag the player sheet", 1f, frame().progress, .001f)
        assertEquals("Seeking must not change the pager", 0f, page(), .001f)
        seek.performTouchInput { up() }
        assertEquals(55L, container.playerController.queue.state.value.current?.id)
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun upwardPlayerDragOpensQueueFollowsFingerAndKeepsLyricsScroll() {
        start()
        compose.onNodeWithTag("mini_cover").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val root = compose.onNodeWithTag("music_window")
        fun coverPoint() = compose.onNodeWithTag("player_cover").fetchSemanticsNode().boundsInRoot.center -
            root.fetchSemanticsNode().boundsInRoot.topLeft
        fun queueProgress() = compose.onNodeWithTag("player_queue_sheet").fetchSemanticsNode()
            .config[io.github.currencortex.music.feature.player.QueuePageProgress]
        root.performTouchInput { down(coverPoint()); moveBy(androidx.compose.ui.geometry.Offset(0f, -180f)) }
        compose.mainClock.advanceTimeBy(64)
        val held = queueProgress()
        assertTrue("Queue follows upward drag before release", held > 0f && held < .14f)
        assertEquals("Upward drag does not collapse the player", 1f, frame().progress, .001f)
        compose.mainClock.advanceTimeBy(240)
        assertEquals("Held queue drag must not run its entry animation", held, queueProgress(), .001f)
        save("player-queue-upward-held.png")
        root.performTouchInput { advanceEventTime(400); up() }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("player_queue_sheet").assertDoesNotExist()
        root.performTouchInput { down(coverPoint()); moveBy(androidx.compose.ui.geometry.Offset(0f, -650f)) }
        compose.mainClock.advanceTimeBy(64)
        assertTrue(queueProgress() > .14f)
        root.performTouchInput { advanceEventTime(400); up() }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(1f, queueProgress(), .001f)
        compose.onNodeWithTag("queue_current_song").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("player_queue_sheet").assertDoesNotExist()
        root.performTouchInput { swipe(coverPoint(), coverPoint() - androidx.compose.ui.geometry.Offset(0f, 180f), 40) }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("A short fast upward swipe opens the queue", 1f, queueProgress(), .001f)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("player_queue_sheet").assertDoesNotExist()
        root.performTouchInput { down(coverPoint()); moveBy(androidx.compose.ui.geometry.Offset(0f, -650f)) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(64)
        root.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("player_queue_sheet").assertDoesNotExist()
        root.performTouchInput { swipe(coverPoint(), coverPoint() - androidx.compose.ui.geometry.Offset(0f, 180f), 40) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("player_queue_sheet").assertDoesNotExist()
        compose.onNodeWithTag("lyrics_options").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("open_lyrics").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("lyrics_panel").performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("player_queue_sheet").assertDoesNotExist()
        compose.onNodeWithTag("player_lyrics_header").performTouchInput {
            swipe(center, center - androidx.compose.ui.geometry.Offset(0f, 650f), 300)
        }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("Lyrics header can still open the queue", 1f, queueProgress(), .001f)
        assertEquals(55L, container.playerController.queue.state.value.current?.id)
        assertEquals(1500L, container.playerController.state.value.positionMs)
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun miniTitleAndPlayerArtworkOrLyricsHeaderCanBeDragged() {
        start()
        compose.mainClock.autoAdvance = false
        val title = compose.onNodeWithTag("mini_song_gesture")
        title.performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, -160f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, -40f))
        }
        compose.mainClock.advanceTimeBy(64)
        val pulled = frame()
        assertTrue("The mini title opens a sheet that follows a held drag", pulled.progress > 0f && pulled.progress < .14f)
        compose.mainClock.advanceTimeBy(176)
        assertEquals("A held gesture must not auto-complete the animation", pulled.progress, frame().progress, .001f)
        save("player-drag-mini-held.png")
        compose.onNodeWithTag("music_window").performTouchInput { advanceEventTime(400); up() }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("player_sheet").assertDoesNotExist()
        title.performTouchInput { swipe(center, center - androidx.compose.ui.geometry.Offset(0f, 900f), 160) }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(1f, frame().progress, .001f)
        val cover = compose.onNodeWithTag("player_cover")
        cover.performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 100f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, 40f))
        }
        compose.mainClock.advanceTimeBy(64)
        assertTrue("Dragging the cover down moves the sheet", frame().progress > .82f && frame().progress < 1f)
        assertTrue(frame().bounds.top > 0)
        cover.performTouchInput { advanceEventTime(400); up() }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("A short downward drag springs back to full player", 1f, frame().progress, .001f)
        compose.onNodeWithTag("player_screen").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(12f, height * .4f),
                androidx.compose.ui.geometry.Offset(12f, height * .4f + 900f), 160)
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("player_sheet").assertDoesNotExist()
        title.performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("player_transport").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 900f), 160)
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("player_sheet").assertDoesNotExist()
        assertFalse("A control-area drag must not toggle playback", container.playerController.state.value.playing)
        title.performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("lyrics_options").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("open_lyrics").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("lyrics_panel").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 600f), 160)
        }
        compose.mainClock.advanceTimeBy(500)
        assertEquals("The entire lyric viewport is excluded even at its scroll boundary", 1f, frame().progress, .001f)
        compose.onNodeWithTag("player_lyrics_header").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 900f), 160)
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("player_sheet").assertDoesNotExist()
        compose.onNodeWithTag("mini_cover").assertIsDisplayed()
        assertEquals(55L, container.playerController.queue.state.value.current?.id)
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun circularArtworkMovesWithTheSheetAndReturnsToMini() {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val file = java.io.File(context.cacheDir, "artwork-${UUID.randomUUID()}.png")
        coverFixture = file
        android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.MAGENTA)
            file.outputStream().use { compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val song = container.playerController.queue.state.value.current!!.copy(cover = file.toURI().toString())
        container.playerController.queue.replace(listOf(song), 0)
        container.playerController.state.value = container.playerController.state.value.copy(song = song)
        start()
        val mini = compose.onNodeWithTag("mini_cover").fetchSemanticsNode()
        val source = mini.boundsInRoot
        fun artwork() = compose.onNodeWithTag("player_artwork_flight").fetchSemanticsNode()
            .config[io.github.currencortex.music.feature.player.PlayerArtworkGeometry]
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("mini_cover").performClick()
        compose.mainClock.advanceTimeBy(64)
        val first = artwork()
        compose.mainClock.advanceTimeBy(112)
        val middle = artwork()
        assertTrue("Artwork grows as the player expands", middle.bounds.width > first.bounds.width)
        assertEquals("Keep the artwork circular throughout the flight", middle.bounds.width, middle.bounds.height, .01f)
        assertEquals("Artwork and sheet must use the same clock", frame().progress, middle.progress, .001f)
        save("player-artwork-open-176.png")
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("player_artwork_flight").assertDoesNotExist()
        val destination = compose.onNodeWithTag("player_cover").fetchSemanticsNode().boundsInRoot
        fun assertPath(f: io.github.currencortex.music.feature.player.PlayerArtworkFrame) {
            val vertical = f.progress * f.progress
            val horizontal = 1 - (1 - f.progress) * (1 - f.progress)
            val expectedY = source.center.y * (1 - vertical) + destination.center.y * vertical
            assertEquals("Vertical travel matches the recorded slower initial lift", expectedY, f.bounds.center.y, .5f)
            assertEquals("The horizontal flight lands on the measured artwork center",
                source.center.x * (1 - horizontal) + destination.center.x * horizontal, f.bounds.center.x, .5f)
            assertEquals("The scaled circle connects the actual two cover sizes",
                source.width * (1 - vertical) + destination.width * vertical, f.bounds.width, .5f)
        }
        assertPath(first); assertPath(middle)
        val pixels = compose.onNodeWithTag("player_pager_artwork").captureToImage().toPixelMap()
        assertTrue("The cover image has loaded", pixels[pixels.width / 2, pixels.height / 2].red > .9f)
        for ((x, y) in listOf(4 to 4, pixels.width - 5 to 4, 4 to pixels.height - 5, pixels.width - 5 to pixels.height - 5)) {
            assertTrue("Square corners show the player backdrop outside the circular cover", pixels[x, y].red < .8f)
        }
        compose.onNodeWithContentDescription("收起播放器").performClick()
        compose.mainClock.advanceTimeBy(96)
        val closing = artwork()
        assertPath(closing)
        assertTrue(closing.bounds.width < destination.width)
        assertEquals(frame().progress, closing.progress, .001f)
        save("player-artwork-close-096.png")
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("player_artwork_flight").assertDoesNotExist()
        assertEquals("The source artwork node survives the round trip", mini.id,
            compose.onNodeWithTag("mini_cover").fetchSemanticsNode().id)
        assertEquals(source, compose.onNodeWithTag("mini_cover").fetchSemanticsNode().boundsInRoot)
        assertFalse(container.playerController.state.value.playing)

        // Drive an isolated playback state without starting audio or the production service.
        fun angle(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode()
            .config[io.github.currencortex.music.feature.player.CoverRotation]
        compose.runOnIdle { container.playerController.state.value = container.playerController.state.value.copy(playing = true) }
        compose.mainClock.advanceTimeBy(500)
        val miniAngle = angle("mini_cover")
        assertTrue("Playing mini artwork rotates", miniAngle > 0f)
        compose.onNodeWithTag("mini_cover").performClick()
        compose.mainClock.advanceTimeBy(176)
        val flightAngle = artwork().rotation
        assertTrue("The shared artwork keeps rotating during expansion", flightAngle > miniAngle)
        compose.mainClock.advanceTimeBy(1000)
        val fullAngle = angle("player_pager_artwork")
        assertTrue("Full player continues the same rotation", fullAngle > flightAngle)
        compose.onNodeWithContentDescription("收起播放器").performClick()
        compose.mainClock.advanceTimeBy(96)
        val closingAngle = artwork().rotation
        assertTrue("Rotation also continues during collapse", closingAngle > fullAngle)
        compose.mainClock.advanceTimeBy(1000)
        val returnedAngle = angle("mini_cover")
        assertTrue("Mini artwork resumes without resetting the angle", returnedAngle > closingAngle)
        compose.runOnIdle { container.playerController.state.value = container.playerController.state.value.copy(playing = false) }
        compose.mainClock.advanceTimeBy(100)
        val pausedAngle = angle("mini_cover")
        compose.mainClock.advanceTimeBy(500)
        assertEquals("Pausing preserves the shared angle", pausedAngle, angle("mini_cover"), .01f)
    }

    @Test fun playerCanVisitCastPageAndReturnWithSavedLyricsView() {
        runBlocking { container.settings.edit { it.copy(predictiveBack = false) } }
        start()
        compose.onNodeWithTag("mini_cover").performClick()
        compose.onNodeWithTag("lyrics_options").performClick()
        compose.onNodeWithTag("open_lyrics").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("lyrics_panel").assertIsDisplayed()
        compose.onNodeWithTag("lyrics_options").performClick()
        compose.onNodeWithTag("open_player_cast").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("cast_screen").assertIsDisplayed()
        compose.onNodeWithTag("player_sheet").assertDoesNotExist()
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("player_sheet").assertIsDisplayed()
        compose.onNodeWithTag("lyrics_panel").assertIsDisplayed()
        assertEquals(1f, frame().progress, .001f)
        compose.onNodeWithContentDescription("收起播放器").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("mini_cover").assertIsDisplayed()
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun predictiveCollapseReversesCancelsAndCommitsWhileDialogsTakePriority() {
        start()
        val miniIdentity = compose.onNodeWithTag("mini_cover").fetchSemanticsNode().id
        val barIdentity = compose.onNodeWithTag("glass_floating_bar").fetchSemanticsNode().id
        val dock = compose.onNodeWithTag("music_dock").fetchSemanticsNode().boundsInRoot
        val sourceIdentity = compose.onNodeWithTag("music_scene_0").fetchSemanticsNode().id
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
        val p = middle.progress
        assertEquals("Collapse targets the whole merged glass panel", dock.height * (1 - p) + original.height * p,
            middle.bounds.height, .5f)
        assertEquals(dock.top * (1 - p), middle.bounds.top, .5f)
        assertEquals("Use the dock's 32dp corner instead of the inner mini row's capsule radius",
            32f * activity.resources.displayMetrics.density * io.github.currencortex.music.feature.player.playerSheetCornerScale(p), middle.radius, .5f)
        assertEquals("The retained home page keeps its node", sourceIdentity,
            compose.onNodeWithTag("music_scene_0").fetchSemanticsNode().id)
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
