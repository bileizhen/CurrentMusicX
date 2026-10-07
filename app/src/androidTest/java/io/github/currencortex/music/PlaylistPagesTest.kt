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
    @Volatile private var holdRefresh = false
    @Volatile private var longPlaylist = false
    @Volatile private var refreshedTracks = false
    @Volatile private var refreshFailure = false
    private val refreshEntered = CountDownLatch(1)
    private val releaseRefresh = CountDownLatch(1)
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private fun json(value: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(value)

    @Before fun setup(): Unit = runBlocking {
        container = AppContainer(context, "playlist-ui-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        val image = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("release-preview.png").use { it.readBytes() }
        val artwork = File(context.cacheDir, "playlist-preview.png").apply { writeBytes(image) }.toURI().toString()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
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
    @After fun teardown() { compose.mainClock.autoAdvance = true; releaseRefresh.countDown(); container.close(); server.shutdown() }

    private fun open() {
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("open_playlists").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open_playlists").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("playlist_card_3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("playlist_card_3").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("playlist_song_2").fetchSemanticsNodes().isNotEmpty() }
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
        container.playerController.pause()
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
