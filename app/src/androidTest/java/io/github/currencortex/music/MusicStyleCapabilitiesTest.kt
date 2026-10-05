package io.github.currencortex.music

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Only new style discovery: isolated files, local fixtures and a paused queue. */
class MusicStyleCapabilitiesTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    @Volatile private var catalogFail = true
    @Volatile private var nextPageFail = true
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun song(id: Long) = """{"id":$id,"name":"曲风歌曲 $id","ar":[{"id":7,"name":"测试歌手"}],"al":{"name":"测试专辑"},"dt":10000}"""
    @Before fun setup() = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "styles-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val url = request.requestUrl!!
                    return when (url.encodedPath) {
                        "/cm/ncm/style/list" -> if (catalogFail) MockResponse().setResponseCode(503) else json("""{"data":[{"tagId":1000,"tagName":"流行","enName":"Pop","childrenTags":[{"tagId":1020,"tagName":"华语流行"}]},{"tagId":1008,"tagName":"摇滚","childrenTags":null}]}""")
                        "/cm/ncm/style/detail" -> {
                            val child = url.queryParameter("tagId") == "1020"
                            json("""{"data":{"tagId":${if (child) 1020 else 1000},"name":"${if (child) "华语流行" else "流行"}","enName":"Pop","desc":"曲风简介","cover":[]}}""")
                        }
                        "/cm/ncm/style/song" -> when {
                            url.queryParameter("tagId") == "1020" -> json("""{"data":{"songs":[${song(61)}],"page":{"cursor":0,"size":1,"total":1,"more":false}}}""")
                            url.queryParameter("sort") == "1" -> json("""{"data":{"songs":[${song(51)}],"page":{"cursor":0,"size":1,"total":1,"more":false}}}""")
                            url.queryParameter("cursor") == "2" && nextPageFail -> MockResponse().setResponseCode(503)
                            url.queryParameter("cursor") == "2" -> json("""{"data":{"songs":[${song(42)},${song(43)}],"page":{"cursor":2,"size":2,"total":3,"more":false}}}""")
                            else -> json("""{"data":{"songs":[${song(41)},${song(42)}],"page":{"cursor":0,"size":2,"total":3,"more":true}}}""")
                        }
                        "/cm/users/square" -> json("""{"total":0,"users":[],"stats":{"users":10,"listening":2}}""")
                        "/cm/daily" -> json("""{"daily":[],"forYou":[],"artists":[]}""")
                        "/cm/playlists" -> json("""{"playlists":[]}""")
                        "/cm/plays/recent", "/cm/likes/mine" -> json("""{"songs":[]}""")
                        "/cm/songs/status" -> json("""{"liked":[],"faved":[]}""")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
        val url = server.url("/cm/").toString()
        container.musicSettings.setServer(url); container.accountRepository.server = url
        container.accountRepository.save("isolated-styles-token", UserDto(7, nickname = "Style fixture"))
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { it.copy(themeMode = ThemeMode.LIGHT, blur = false) }
        val paused = Song(900, "保留当前歌曲")
        container.playerController.queue.replace(listOf(paused), 0)
        container.playerController.state.value = PlayerState(song = paused)
    }
    @After fun cleanup() { container.close(); server.shutdown() }
    private fun waitFor(tag: String) {
        compose.waitUntil(15000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun save(name: String) {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        compose.onNodeWithTag("music_window").captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(app.externalCacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun stylesNavigateSortPageRetryAndAddToQueueWithoutTouchingCurrentPlayback() {
        compose.setContent { CurrentMusicApp(container) }
        waitFor("mini_cover")
        compose.onNodeWithText("发现").performClick()
        waitFor("style_catalog_retry")
        catalogFail = false
        compose.onNodeWithTag("style_catalog_retry").performScrollTo().performClick()
        waitFor("style_category_1000")
        compose.onNodeWithText("音乐社区").assertExists()
        save("style-catalog-test.png")
        compose.onNodeWithTag("style_category_1000").performClick()
        waitFor("style_song_41")
        compose.onNodeWithText("曲风简介").assertExists()
        save("style-detail-test.png")
        compose.onNodeWithTag("style_load_more").performScrollTo().performClick()
        waitFor("style_songs_retry")
        compose.onNodeWithTag("style_song_41").assertExists()
        nextPageFail = false
        compose.onNodeWithTag("style_songs_retry").performScrollTo().performClick()
        waitFor("style_song_43")
        assertEquals(1, compose.onAllNodesWithTag("style_song_42").fetchSemanticsNodes().size)
        compose.onNodeWithTag("style_sort_new").performScrollTo().performClick()
        waitFor("style_song_51")
        compose.onNodeWithTag("style_song_41").assertDoesNotExist()
        compose.onNodeWithTag("style_category_1020").performScrollTo().performClick()
        waitFor("style_song_61")
        compose.onNodeWithTag("song_menu_61").performScrollTo().performClick()
        compose.onNodeWithText("加入队列").performClick()
        compose.waitForIdle()
        assertEquals(listOf(900L, 61L), container.playerController.queue.state.value.songs.map { it.id })
        assertEquals(900L, container.playerController.queue.state.value.current?.id)
        assertFalse(container.playerController.state.value.playing)
        compose.onNodeWithText("返回").performScrollTo().performClick()
        waitFor("style_song_51")
        assertTrue(requests.any { it.requestUrl!!.queryParameter("cursor") == "2" })
        assertTrue(requests.filter { it.requestUrl!!.encodedPath.contains("/style/") }.all { it.getHeader("Authorization") == null })
    }
}
