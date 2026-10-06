package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Isolated storage and local HTTP fixtures; the phone's account and playback are untouched. */
class SearchArtistCapabilitiesTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    @Volatile private var songPageFail = true
    @Volatile private var biographyFail = true
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun song(id: Int) = """{"ncm_id":$id,"name":"Fixture song $id","artists":"Fixture Artist","artist_ids":[31]}"""
    private fun album(id: Int) = """{"id":$id,"name":"Fixture Album $id","artist":"Fixture Artist","size":2,"publishTime":1790870400000}"""

    @Before fun prepare() = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "search-artist-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val url = request.requestUrl!!
                return when (url.encodedPath) {
                    "/cm/ncm/search" -> when (url.queryParameter("type")) {
                        "artist" -> json("""{"artists":[{"id":${if (url.queryParameter("keywords") == "draft") 32 else 31},"name":"Fixture Artist"}],"totals":{"artist":1},"hasMore":{"artist":false}}""")
                        "album" -> json("""{"albums":[${album(41)}],"totals":{"album":1},"hasMore":{"album":false}}""")
                        else -> when {
                            url.queryParameter("keywords") == "draft" -> json("""{"songs":[${song(99)}],"totals":{"song":1},"hasMore":{"song":false}}""")
                            url.queryParameter("offset") == "2" && songPageFail -> MockResponse().setResponseCode(503)
                            url.queryParameter("offset") == "2" -> json("""{"songs":[${song(12)},${song(13)}],"totals":{"song":3},"hasMore":{"song":false}}""")
                            else -> json("""{"songs":[${song(11)},${song(12)}],"totals":{"song":3},"hasMore":{"song":true}}""")
                        }
                    }
                    "/cm/ncm/artist" -> if (url.queryParameter("offset") == "2") json("""{"id":31,"name":"Fixture Artist","total":3,"more":false,"songs":[${song(12)},${song(13)}],"albumTotal":2}""")
                        else json("""{"id":31,"name":"Fixture Artist","total":3,"more":true,"songs":[${song(11)},${song(12)}],"albumTotal":2,"albums":[${album(41)}]}""")
                    "/cm/ncm/artist/desc" -> if (biographyFail) MockResponse().setResponseCode(503)
                        else json("""{"code":200,"briefDesc":"Fixture biography","introduction":[{"ti":"Career","txt":"Fixture career details"}]}""")
                    "/cm/ncm/artist/albums" -> json("""{"albums":[${album(42)}],"more":false}""")
                    "/cm/ncm/album" -> json("""{"id":41,"name":"Fixture Album 41","songs":[${song(11)}]}""")
                    "/cm/daily" -> json("""{"daily":[],"forYou":[],"artists":[]}""")
                    "/cm/playlists" -> json("""{"playlists":[]}""")
                    "/cm/plays/recent", "/cm/likes/mine" -> json("""{"songs":[]}""")
                    "/cm/songs/status" -> json("""{"liked":[]}""")
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
        val paused = Song(900, "Paused fixture")
        container.playerController.queue.replace(listOf(paused), 0)
        container.playerController.state.value = PlayerState(song = paused)
    }
    @After fun finish() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        java.io.File(app.externalCacheDir, "search-artist-requests.txt").writeText(requests.joinToString("\n") { it.path.orEmpty() })
        runCatching { save("search-artist-last.png") }
        if (::container.isInitialized) { container.accountRepository.clear(); container.close() }
        if (::server.isInitialized) server.shutdown()
    }
    private fun awaitTag(tag: String) = compose.waitUntil(15000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun scroll(parent: String, tag: String) { compose.onNodeWithTag(parent).performScrollToNode(hasTestTag(tag)) }
    private fun save(name: String) {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        compose.onNodeWithTag("music_window").captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(app.externalCacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun clickFooter(parent: String, tag: String) {
        scroll(parent, tag)
        compose.onNodeWithTag(parent).performTouchInput { swipeUp(startY = height * .6f, endY = height * .25f, durationMillis = 400) }
        compose.onNodeWithTag(tag).assertIsEnabled()
        val action = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val mini = compose.onNodeWithTag("mini_player").fetchSemanticsNode().boundsInRoot
        assertTrue("Footer must scroll above the floating player: $action / $mini", action.bottom <= mini.top)
        compose.onNodeWithTag(tag).performClick()
    }
    private fun artistTab(tab: String) { compose.onNodeWithTag("artist_home").performScrollToIndex(0); compose.onNodeWithTag("artist_tab_$tab").performClick() }
    private fun searchCount() = requests.count { it.requestUrl!!.encodedPath == "/cm/ncm/search" }

    @Test fun categorizedSearchAndArtistHomepageKeepSubmittedQueryAndPausedQueue() {
        compose.setContent { CurrentMusicApp(container) }
        awaitTag("open_home_search"); compose.onNodeWithTag("open_home_search").performClick()
        awaitTag("search_input"); compose.onNodeWithTag("search_input").performTextInput("fixture")
        assertEquals(0, searchCount())
        compose.onNodeWithTag("submit_search").performClick(); awaitTag("search_song_11")
        compose.onNodeWithTag("search_input").performTextReplacement("draft")
        compose.onNodeWithTag("search_category_artist").performClick(); awaitTag("search_artist_31")
        compose.onNodeWithTag("search_category_song").performClick(); awaitTag("search_song_11")
        assertEquals(2, searchCount())
        scroll("search_screen", "search_load_more"); compose.onNodeWithTag("search_load_more").performClick()
        awaitTag("search_retry"); songPageFail = false
        scroll("search_screen", "search_retry"); compose.onNodeWithTag("search_retry").performClick(); awaitTag("search_song_13")
        compose.onAllNodesWithTag("search_song_12").assertCountEquals(1)
        val pages = requests.filter { it.requestUrl!!.encodedPath == "/cm/ncm/search" && it.requestUrl!!.queryParameter("offset") == "2" }
        assertEquals(2, pages.size); assertTrue(pages.all { it.requestUrl!!.queryParameter("keywords") == "fixture" })

        compose.onNodeWithTag("search_screen").performScrollToIndex(0)
        compose.onNodeWithTag("search_category_artist").performClick(); awaitTag("search_artist_31")
        compose.onNodeWithTag("search_artist_31").performClick(); awaitTag("artist_name")
        compose.onNodeWithText("3 首歌曲 · 2 张专辑").assertExists()
        artistTab("about"); awaitTag("artist_bio_retry")
        biographyFail = false; scroll("artist_home", "artist_bio_retry"); compose.onNodeWithTag("artist_bio_retry").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Fixture biography").fetchSemanticsNodes().isNotEmpty() }
        artistTab("songs"); scroll("artist_home", "artist_more_songs"); compose.onNodeWithTag("artist_more_songs").performClick()
        compose.waitUntil(10000) { requests.any { it.requestUrl!!.encodedPath == "/cm/ncm/artist" && it.requestUrl!!.queryParameter("offset") == "2" } }
        scroll("artist_home", "artist_song_13"); compose.onAllNodesWithTag("artist_song_12").assertCountEquals(1)
        artistTab("albums"); clickFooter("artist_home", "artist_more_albums")
        compose.waitUntil(10000) { requests.any { it.requestUrl!!.encodedPath == "/cm/ncm/artist/albums" } }
        scroll("artist_home", "catalog_entry_42")
        assertEquals("1", requests.first { it.requestUrl!!.encodedPath == "/cm/ncm/artist/albums" }.requestUrl!!.queryParameter("offset"))
        scroll("artist_home", "catalog_entry_41"); compose.onNodeWithTag("catalog_entry_41").performClick(); awaitTag("library_detail")
        compose.onNodeWithText("返回").performClick(); awaitTag("artist_home")
        scroll("artist_home", "catalog_entry_42")
        compose.onNodeWithTag("artist_home").performScrollToIndex(0); compose.onNodeWithTag("artist_back").performClick(); awaitTag("search_artist_31")
        compose.onNodeWithTag("search_category_album").performClick(); awaitTag("search_album_41")
        compose.onNodeWithTag("search_album_41").performClick(); awaitTag("library_detail")
        compose.onNodeWithText("返回").performClick(); awaitTag("search_album_41")
        assertEquals("fixture", requests.last { it.requestUrl!!.encodedPath == "/cm/ncm/search" }.requestUrl!!.queryParameter("keywords"))
        compose.onNodeWithTag("search_category_song").performClick(); awaitTag("search_song_13")
        compose.onNodeWithTag("search_input").assertTextContains("draft")
        compose.onNodeWithTag("submit_search").performClick(); awaitTag("search_song_99")
        compose.onNodeWithTag("search_song_11").assertDoesNotExist()
        compose.onNodeWithTag("search_category_artist").performClick(); awaitTag("search_artist_32")
        val publicPaths = setOf("/cm/ncm/search", "/cm/ncm/artist", "/cm/ncm/artist/desc", "/cm/ncm/artist/albums", "/cm/ncm/album")
        assertTrue(requests.filter { it.requestUrl!!.encodedPath in publicPaths }.all { it.getHeader("Authorization") == null })
        assertEquals(listOf(900L), container.playerController.queue.state.value.songs.map { it.id })
        assertFalse(container.playerController.state.value.playing)
    }
}
