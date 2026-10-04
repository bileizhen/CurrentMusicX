package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
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
        compose.waitUntil(10000) { compose.onAllNodesWithText("服务器异常").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(container.libraryRepository.statuses.value[1]!!.liked)
        dismissMessage();likeFail=false
        compose.onNodeWithTag("song_actions_sheet").assertDoesNotExist()
        compose.onAllNodesWithTag("song_menu_1")[0].performScrollTo().performClick()
        compose.onAllNodesWithTag("song_like_1")[0].performClick()
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
