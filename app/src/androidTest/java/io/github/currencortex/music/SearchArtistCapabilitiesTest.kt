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
    @Volatile private var playlistPageFail = true
    @Volatile private var playlistFixture = false
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
                        "playlist" -> when {
                            url.queryParameter("offset") == "2" && playlistPageFail -> MockResponse().setResponseCode(503)
                            url.queryParameter("offset") == "2" -> json("""{"playlists":[{"id":72,"name":"Fixture playlist 72"},{"id":73,"name":"Fixture playlist 73"}],"totals":{"playlist":3},"hasMore":{"playlist":false}}""")
                            else -> json("""{"playlists":[{"id":71,"name":"Fixture playlist 71","creator":"Fixture creator","trackCount":1},{"id":72,"name":"Fixture playlist 72"}],"totals":{"playlist":3},"hasMore":{"playlist":true}}""")
                        }
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
                    "/cm/ncmbind" -> if (playlistFixture) json("""{"bound":true,"profile":{"uid":8,"nickname":"Fixture creator"}}""") else MockResponse().setResponseCode(404)
                    "/cm/ncm/playlist/detail" -> json("""{"code":200,"playlist":{"id":71,"name":"Fixture playlist 71","creator":{"userId":8,"nickname":"Fixture creator"},"trackCount":1,"tracks":[{"id":11,"name":"Playlist fixture song","ar":[{"name":"Fixture artist"}]}]}}""")
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
        compose.onNodeWithTag(parent).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        compose.onNodeWithTag(tag).assertIsEnabled()
        val action = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val mini = compose.onNodeWithTag("mini_player", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Footer must scroll above the floating player: $action / $mini", action.bottom <= mini.top)
        compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
    }
    private fun artistTab(tab: String) { compose.onNodeWithTag("artist_home").performScrollToIndex(0); compose.onNodeWithTag("artist_tab_$tab").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() } }
    private fun searchCount() = requests.count { it.requestUrl!!.encodedPath == "/cm/ncm/search" }

    @Test fun playlistSearchRetriesDeduplicatesAndOpensTheExistingNativeDetail() {
        playlistFixture = true
        compose.setContent { CurrentMusicApp(container) }
        awaitTag("open_home_search")
        compose.onNodeWithTag("open_home_search").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        awaitTag("search_input"); compose.onNodeWithTag("search_input").performTextInput("fixture")
        compose.onNodeWithTag("submit_search").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        awaitTag("search_category_playlist")
        compose.onNodeWithTag("search_category_playlist").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        awaitTag("search_playlist_71")
        compose.onNodeWithText("找到 3 个歌单").assertExists()
        compose.onNodeWithTag("search_input").performTextReplacement("draft")
        clickFooter("search_screen", "search_load_more")
        awaitTag("search_retry"); playlistPageFail = false
        compose.onNodeWithTag("search_retry").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        awaitTag("search_playlist_73")
        compose.onAllNodesWithTag("search_playlist_72").assertCountEquals(1)
        compose.onNodeWithTag("search_playlist_71").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        awaitTag("library_detail"); compose.onNodeWithText("Playlist fixture song").assertExists()
        compose.onNodeWithTag("navigate_back").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        awaitTag("search_playlist_73")
        compose.onNodeWithTag("search_input").assertTextContains("draft")
        val pages = requests.filter { it.requestUrl!!.encodedPath == "/cm/ncm/search" && it.requestUrl!!.queryParameter("type") == "playlist" }
        assertEquals(listOf("0", "2", "2"), pages.map { it.requestUrl!!.queryParameter("offset") })
        assertTrue(pages.all { it.requestUrl!!.queryParameter("keywords") == "fixture" && it.getHeader("Authorization") == null })
        assertTrue(requests.any { it.requestUrl!!.encodedPath == "/cm/ncm/playlist/detail" && it.requestUrl!!.queryParameter("id") == "71" })
        assertEquals(listOf(900L), container.playerController.queue.state.value.songs.map { it.id })
        assertFalse(container.playerController.state.value.playing)
    }

    @Test fun tappingSearchResultsKeepsExistingQueueAndOnlyAddsSelectedSong() {
        check(ApplicationProvider.getApplicationContext<CurrentMusicApplication>().packageName.endsWith(".verification"))
        val original = listOf(Song(900, "Paused fixture"), Song(11, "Already queued"), Song(902, "Queue tail"))
        container.playbackQueue.replace(original, 0)
        compose.setContent { CurrentMusicApp(container) }
        awaitTag("open_home_search")
        compose.onNodeWithTag("open_home_search").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        awaitTag("search_input"); compose.onNodeWithTag("search_input").performTextInput("fixture")
        compose.onNodeWithTag("submit_search").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("search_song_11")
        compose.onNodeWithText("Fixture song 11").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        if (compose.onAllNodesWithText("继续播放").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("继续播放").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { container.playbackQueue.state.value.current?.id == 11L }
        assertEquals(original, container.playbackQueue.state.value.songs)
        compose.onNodeWithText("Fixture song 12").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { container.playbackQueue.state.value.current?.id == 12L }
        assertEquals(listOf(900L, 11L, 12L, 902L), container.playbackQueue.state.value.songs.map { it.id })
        assertEquals(0L, container.playbackQueue.state.value.positionMs)
    }

    @Test fun categorizedSearchAndArtistHomepageKeepSubmittedQueryAndPausedQueue() {
        compose.setContent { CurrentMusicApp(container) }
        awaitTag("open_home_search")
        compose.onNodeWithTag("open_home_search").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        awaitTag("search_input"); compose.onNodeWithTag("search_input").performTextInput("fixture")
        assertEquals(0, searchCount())
        compose.onNodeWithTag("submit_search").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("search_song_11")
        compose.onNodeWithTag("search_input").performTextReplacement("draft")
        compose.onNodeWithTag("search_category_artist").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("search_artist_31")
        compose.onNodeWithTag("search_category_song").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("search_song_11")
        assertEquals(2, searchCount())
        scroll("search_screen", "search_load_more"); compose.onNodeWithTag("search_load_more").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        awaitTag("search_retry"); songPageFail = false
        scroll("search_screen", "search_retry"); compose.onNodeWithTag("search_retry").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("search_song_13")
        compose.onAllNodesWithTag("search_song_12").assertCountEquals(1)
        val pages = requests.filter { it.requestUrl!!.encodedPath == "/cm/ncm/search" && it.requestUrl!!.queryParameter("offset") == "2" }
        assertEquals(2, pages.size); assertTrue(pages.all { it.requestUrl!!.queryParameter("keywords") == "fixture" })

        compose.onNodeWithTag("search_screen").performScrollToIndex(0)
        compose.onNodeWithTag("search_category_artist").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("search_artist_31")
        compose.onNodeWithTag("search_artist_31").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("artist_name")
        compose.onNodeWithText("3 首歌曲 · 2 张专辑").assertExists()
        artistTab("about"); awaitTag("artist_bio_retry")
        biographyFail = false; scroll("artist_home", "artist_bio_retry"); compose.onNodeWithTag("artist_bio_retry").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Fixture biography").fetchSemanticsNodes().isNotEmpty() }
        artistTab("songs"); scroll("artist_home", "artist_more_songs"); compose.onNodeWithTag("artist_more_songs").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(10000) { requests.any { it.requestUrl!!.encodedPath == "/cm/ncm/artist" && it.requestUrl!!.queryParameter("offset") == "2" } }
        scroll("artist_home", "artist_song_13"); compose.onAllNodesWithTag("artist_song_12").assertCountEquals(1)
        artistTab("albums"); clickFooter("artist_home", "artist_more_albums")
        compose.waitUntil(10000) { requests.any { it.requestUrl!!.encodedPath == "/cm/ncm/artist/albums" } }
        scroll("artist_home", "catalog_entry_42")
        assertEquals("1", requests.first { it.requestUrl!!.encodedPath == "/cm/ncm/artist/albums" }.requestUrl!!.queryParameter("offset"))
        scroll("artist_home", "catalog_entry_41"); compose.onNodeWithTag("catalog_entry_41").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("library_detail")
        compose.onNodeWithText("返回").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("artist_home")
        scroll("artist_home", "catalog_entry_42")
        compose.onNodeWithTag("artist_home").performScrollToIndex(0); compose.onNodeWithTag("artist_back").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("search_artist_31")
        compose.onNodeWithTag("search_category_album").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("search_album_41")
        compose.onNodeWithTag("search_album_41").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("library_detail")
        compose.onNodeWithText("返回").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("search_album_41")
        assertEquals("fixture", requests.last { it.requestUrl!!.encodedPath == "/cm/ncm/search" }.requestUrl!!.queryParameter("keywords"))
        compose.onNodeWithTag("search_category_song").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("search_song_13")
        compose.onNodeWithTag("search_input").assertTextContains("draft")
        compose.onNodeWithTag("submit_search").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("search_song_99")
        compose.onNodeWithTag("search_song_11").assertDoesNotExist()
        compose.onNodeWithTag("search_category_artist").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }; awaitTag("search_artist_32")
        val publicPaths = setOf("/cm/ncm/search", "/cm/ncm/artist", "/cm/ncm/artist/desc", "/cm/ncm/artist/albums", "/cm/ncm/album")
        assertTrue(requests.filter { it.requestUrl!!.encodedPath in publicPaths }.all { it.getHeader("Authorization") == null })
        assertEquals(listOf(900L), container.playerController.queue.state.value.songs.map { it.id })
        assertFalse(container.playerController.state.value.playing)
    }
}
