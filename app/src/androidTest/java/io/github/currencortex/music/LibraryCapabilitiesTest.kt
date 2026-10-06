package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Uses distinct encrypted token, DataStore and database files; never logs out the phone's account. */
class LibraryCapabilitiesTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    @Volatile private var playlistName = ""
    @Volatile private var likeFail = true
    @Volatile private var liked = false
    @Volatile private var catalogRequests = 0
    @Volatile private var dailyDelay = 0L
    private fun json(body: String) = MockResponse().setHeader("Content-Type","application/json").setBody(body)
    private val track = """{"ncm_id":1,"name":"Library track","artists":"Artist","artist_ids":[12],"album":"Album","mv":8}"""
    @Before fun prepare() = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "library-test-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path == "/cm/daily" -> json("""{"daily":[$track],"forYou":[$track],"artists":["Artist"]}""").setBodyDelay(dailyDelay, java.util.concurrent.TimeUnit.MILLISECONDS)
                    path == "/cm/plays/recent" -> json("""{"songs":[$track]}""")
                    path == "/cm/songs/status" -> json("""{"liked":${if (liked) "[1]" else "[]"}}""")
                    path == "/cm/likes/1" -> if (likeFail) MockResponse().setResponseCode(500) else { liked=!liked; json("{}") }
                    path == "/cm/likes/mine" -> json("""{"songs":[$track]}""")
                    path == "/cm/playlists" && request.method == "POST" -> { playlistName = io.github.currencortex.music.core.network.ApiJson.parseToJsonElement(request.body.readUtf8()).let { (it as kotlinx.serialization.json.JsonObject)["name"].toString().trim('"') };json("{}") }
                    path == "/cm/playlists" -> json("""{"playlists":[{"id":3,"name":"Cloud read-only","source":"ncm","user_id":7,"track_count":1}${if(playlistName.isNotEmpty()) ",{\"id\":4,\"name\":\"$playlistName\",\"user_id\":7}" else ""}]}""")
                    path == "/cm/playlists/3" -> json("""{"id":3,"name":"Cloud read-only","source":"ncm","user_id":7,"tracks":[$track]}""")
                    path == "/cm/playlists/4" && request.method == "PUT" -> { playlistName = "Renamed";json("{}") }
                    path == "/cm/playlists/4" -> json("""{"id":4,"name":"$playlistName","user_id":7,"tracks":[]}""")
                    path == "/cm/ncm/search" -> { catalogRequests++;json("""{"artists":[{"id":12,"name":"Artist"}],"hasMore":{"artist":false}}""") }
                    path == "/cm/ncm/artist" -> json("""{"id":12,"name":"Artist","songs":[$track],"albums":[{"id":5,"name":"Album"}]}""")
                    path == "/cm/ncm/album" -> json("""{"id":5,"name":"Album","songs":[$track]}""")
                    path == "/cm/ncm/mv/detail" -> json("""{"data":{"id":8,"name":"Native MV","artistName":"Artist"}}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val url = server.url("/cm/").toString()
        container.musicSettings.setServer(url);container.accountRepository.server=url
        container.accountRepository.save("isolated-test-token",UserDto(7,nickname="Library user"))
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { io.github.currencortex.music.data.settings.AppearanceSettings(blur=false) }
    }
    @After fun finish() = runBlocking {
        if (::container.isInitialized) { container.accountRepository.clear();container.close() }
        if (::server.isInitialized) server.shutdown()
    }
    private fun dismissMessage() { compose.onAllNodesWithText("关闭").fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { compose.onNodeWithText("关闭").performClick() } }
    @Test fun predictiveBackKeepsViewportAndCancelsWithoutPopping() = verifyPredictiveNavigation(floating = true)
    @Test fun predictiveBackWithFixedTabsKeepsViewportAndCompletes() = verifyPredictiveNavigation(floating = false)

    @Test fun enteringPagesCoverOutgoingScrimInLightTheme() = verifySceneSurface(ThemeMode.LIGHT, Color(0xFFF5F6F8))
    @Test fun enteringPagesCoverOutgoingScrimInDarkTheme() = verifySceneSurface(ThemeMode.DARK, Color(0xFF111214))

    private fun verifySceneSurface(theme: ThemeMode, expected: Color) {
        runBlocking { container.settings.edit { it.copy(themeMode = theme) } }
        container.playerController.queue.replace(listOf(io.github.currencortex.music.data.song.Song(55, "Paused surface fixture")), 0)
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("open_playlists").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        fun assertSurface(route: String, previous: String, frame: Int) {
            val viewport = compose.onNodeWithTag("music_navigation").fetchSemanticsNode().boundsInRoot
            val incoming = compose.onNodeWithTag("music_scene_$route").fetchSemanticsNode().boundsInRoot
            val outgoing = compose.onNodeWithTag("music_scene_$previous").fetchSemanticsNode().boundsInRoot
            assertTrue("Capture must be during entry, not after settling: $incoming", incoming.left > viewport.left)
            val overlapLeft = maxOf(incoming.left, outgoing.left)
            val overlapRight = minOf(incoming.right, outgoing.right)
            assertTrue("Both moving scenes must overlap", overlapRight - overlapLeft > 20f)
            val image = compose.onNodeWithTag("music_navigation").captureToImage()
            val pixels = image.toPixelMap()
            val x = ((overlapLeft + overlapRight) / 2 - viewport.left).toInt()
            // Blank status-bar inset and blank player inset must be painted by the scene too.
            val ys = listOf((viewport.height * .012f).toInt(), (viewport.height - 8).toInt())
            val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
            java.io.File(context.externalCacheDir, "scene-$theme-$route-$frame.png").outputStream().use {
                image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            ys.forEach { y ->
                for (dx in -2..2) for (dy in -2..2) {
                    val actual = pixels[x + dx, y + dy]
                    assertEquals("Scene $route frame $frame at ($x,$y) red; outgoing scrim must be covered", expected.red, actual.red, 2f / 255)
                    assertEquals("Scene $route frame $frame green", expected.green, actual.green, 2f / 255)
                    assertEquals("Scene $route frame $frame blue", expected.blue, actual.blue, 2f / 255)
                }
            }
        }

        compose.onNodeWithTag("tab_3").performClick()
        compose.mainClock.advanceTimeBy(700)
        assertSurface("0", "0", 700)
        compose.onNodeWithTag("open_network").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(160)
        assertSurface("21", "0", 160)
        compose.mainClock.advanceTimeBy(160)
        assertSurface("21", "0", 320)
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("network_settings").assertExists()
        compose.mainClock.autoAdvance = true
        assertEquals(listOf(55L), container.playerController.queue.state.value.songs.map { it.id })
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun predictiveBackCanInterruptForwardEntryAndCancelOrComplete() {
        lateinit var dispatcher: androidx.activity.OnBackPressedDispatcher
        compose.setContent {
            dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            CurrentMusicApp(container)
        }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("open_playlists").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.autoAdvance = false
        fun gesture(commit: Boolean) {
            compose.onNodeWithTag("open_playlists").performClick()
            compose.mainClock.advanceTimeBy(96)
            compose.runOnUiThread {
                dispatcher.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f, 500f, 0f, androidx.activity.BackEventCompat.EDGE_LEFT))
                dispatcher.dispatchOnBackProgressed(androidx.activity.BackEventCompat(250f, 500f, .4f, androidx.activity.BackEventCompat.EDGE_LEFT))
            }
            compose.mainClock.advanceTimeBy(32)
            compose.runOnUiThread { if (commit) dispatcher.onBackPressed() else dispatcher.dispatchOnBackCancelled() }
            compose.mainClock.advanceTimeBy(1500)
        }
        gesture(commit = false)
        compose.onNodeWithText("我的歌单").assertExists()
        compose.onNodeWithText("返回").performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithTag("open_playlists").assertExists()
        gesture(commit = true)
        compose.onNodeWithTag("open_playlists").assertExists()
        compose.onNodeWithText("我的歌单").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    private fun verifyPredictiveNavigation(floating: Boolean) {
        runBlocking { container.settings.edit { it.copy(floatingBar = floating, predictiveBack = true) } }
        container.playerController.queue.replace(listOf(io.github.currencortex.music.data.song.Song(55, "Paused navigation fixture")), 0)
        lateinit var dispatcher: androidx.activity.OnBackPressedDispatcher
        compose.setContent {
            dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            CurrentMusicApp(container)
        }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("open_playlists").fetchSemanticsNodes().isNotEmpty() }
        val viewport = compose.onNodeWithTag("music_navigation").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("open_playlists").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Cloud read-only").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("Pushing must not resize the animated viewport", viewport,
            compose.onNodeWithTag("music_navigation").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Cloud read-only").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("library_detail").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val original = compose.onNodeWithTag("library_detail").fetchSemanticsNode().boundsInRoot
        fun progress(value: Float, edge: Int = androidx.activity.BackEventCompat.EDGE_LEFT) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(androidx.activity.BackEventCompat(value * viewport.width, 500f, value, edge)) }
            compose.waitForIdle()
        }
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f, 500f, 0f, androidx.activity.BackEventCompat.EDGE_LEFT)) }
        progress(.2f)
        val first = compose.onNodeWithTag("library_detail").fetchSemanticsNode().boundsInRoot
        progress(.5f)
        val middle = compose.onNodeWithTag("library_detail").fetchSemanticsNode().boundsInRoot
        assertTrue("Page must follow predictive progress", middle.left > first.left)
        assertEquals(viewport, compose.onNodeWithTag("music_navigation").fetchSemanticsNode().boundsInRoot)
        progress(.1f)
        assertTrue("Reversing the gesture must reverse the page", compose.onNodeWithTag("library_detail").fetchSemanticsNode().boundsInRoot.left < middle.left)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        compose.onNodeWithText("网易云音乐 · 同步歌单只读").assertExists()
        assertEquals("Cancellation must restore the same page geometry", original,
            compose.onNodeWithTag("library_detail").fetchSemanticsNode().boundsInRoot)
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f, 500f, 0f, androidx.activity.BackEventCompat.EDGE_RIGHT)) }
        progress(.6f, androidx.activity.BackEventCompat.EDGE_RIGHT)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("library_detail").assertDoesNotExist()
        compose.onNodeWithText("我的歌单").assertExists()
        assertEquals(viewport, compose.onNodeWithTag("music_navigation").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("返回").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("open_playlists").assertExists()
        assertEquals("Returning to root must keep the animated viewport", viewport,
            compose.onNodeWithTag("music_navigation").fetchSemanticsNode().boundsInRoot)
        assertEquals(listOf(55L), container.playerController.queue.state.value.songs.map { it.id })
        assertFalse(container.playerController.state.value.playing)
    }
    @Test fun loadingPlaceholderGivesWayToRealContent() {
        dailyDelay = 1500
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("home_loading").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home_loading").assertExists()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Library track").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home_loading").assertDoesNotExist()
        compose.onNodeWithTag("open_playlists").assertExists()
    }
    @Test fun homeNavigationAndReadonlyPlaylistsSurviveStateRestoration() {
        val restoration=StateRestorationTester(compose);restoration.setContent { CurrentMusicApp(container) }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Library track").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open_playlists").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Cloud read-only").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cloud read-only").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("网易云音乐 · 同步歌单只读").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("rename_playlist").assertDoesNotExist();compose.onNodeWithTag("delete_playlist").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("网易云音乐 · 同步歌单只读").assertExists()
        assertEquals(7L,container.accountRepository.state.value.account!!.id)
    }
    @Test fun createRenameAndFailedLikeRollbackWorkThroughNativeUi() {
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Library track").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("song_menu_1")[0].performScrollTo().performClick()
        compose.onAllNodesWithTag("song_like_1")[0].performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("未收录，点击加入").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("like_destination_CURRENT_MUSIC").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("服务器异常，点击重试").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(container.libraryRepository.statuses.value[1]!!.liked)
        likeFail=false
        compose.onNodeWithTag("song_actions_sheet").assertDoesNotExist()
        compose.onNodeWithTag("like_destination_CURRENT_MUSIC").performClick()
        compose.waitUntil(10000) { container.libraryRepository.statuses.value[1]?.liked == true && container.libraryRepository.statuses.value[1]?.pending == false }
        dismissMessage()
        compose.onNodeWithTag("music_home").performTouchInput { swipeDown(startY=100f,endY=height-100f) }
        compose.onNodeWithTag("open_playlists").performScrollTo().performClick()
        compose.onNodeWithTag("create_playlist").performClick()
        compose.onNodeWithTag("playlist_name").performTextInput("New playlist")
        compose.onNodeWithTag("confirm_create_playlist").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("New playlist").fetchSemanticsNodes().isNotEmpty() }
        dismissMessage();compose.onNodeWithText("New playlist").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("rename_playlist").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("rename_playlist").performClick();compose.onNodeWithTag("rename_input").performTextReplacement("Renamed")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Renamed").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun artistAlbumAndMvDetailsUseExplicitSearchAndNativeNavigation() {
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(15000) { !container.accountRepository.state.value.loading }
        compose.onNodeWithTag("open_playlists").performClick();compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("歌手").performScrollTo().performClick()
        compose.onNodeWithTag("catalog_query").performTextInput("Artist");assertEquals(0,catalogRequests)
        compose.onNodeWithTag("catalog_submit").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Artist").fetchSemanticsNodes().size>=2 }
        compose.onNodeWithTag("catalog_entry_12").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("artist_tab_albums").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("artist_tab_albums").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Album").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Album").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Library track").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("song_menu_1").performScrollTo().performClick()
        compose.onNodeWithTag("song_mv_1").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Native MV").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("play_mv").assertExists()
    }
    @Test fun globalMiniPlayerSurvivesLibraryNavigationAndMenuDoesNotResizeList() {
        container.playerController.queue.replace(listOf(io.github.currencortex.music.data.song.Song(55, "Paused fixture queue")), 0)
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("open_playlists").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mini_player").assertExists()
        val content = compose.onNodeWithTag("music_home").fetchSemanticsNode().boundsInRoot
        val tab = compose.onNodeWithTag("tab_0").fetchSemanticsNode().boundsInRoot
        assertTrue("Scrolling content must extend behind the floating navigation", content.bottom > tab.top)
        compose.onNodeWithTag("music_home").performScrollToNode(hasTestTag("refresh_home"))
        compose.onNodeWithTag("music_home").performTouchInput { swipeUp(startY = height * .5f, endY = height * .1f, durationMillis = 600) }
        val lastAction = compose.onNodeWithTag("refresh_home").fetchSemanticsNode().boundsInRoot
        val mini = compose.onNodeWithTag("mini_player").fetchSemanticsNode().boundsInRoot
        assertTrue("The last action must scroll above the overlaid player: $lastAction / $mini", lastAction.bottom <= mini.top)
        compose.onNodeWithTag("music_home").performScrollToNode(hasTestTag("open_playlists"))
        compose.onNodeWithTag("open_playlists").performClick()
        compose.onNodeWithTag("mini_player").assertExists()
        compose.onNodeWithText("Cloud read-only").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("song_menu_1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mini_player").assertExists()
        val row = compose.onNodeWithText("Library track").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("song_menu_1").performClick()
        compose.onNodeWithTag("song_actions_sheet").assertExists()
        assertTrue("Opening the action sheet must not move the song row",
            compose.onAllNodesWithText("Library track").fetchSemanticsNodes().any { it.boundsInRoot == row })
        compose.onNodeWithText("加入队列").performClick()
        compose.onNodeWithTag("song_actions_sheet").assertDoesNotExist()
        assertEquals(listOf(55L, 1L), container.playerController.queue.state.value.songs.map { it.id })
        assertFalse(container.playerController.state.value.playing)
    }
}
