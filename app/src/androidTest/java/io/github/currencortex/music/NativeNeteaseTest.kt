package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.netease.NeteaseEndpoints
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.binding.BindingViewModel
import io.github.currencortex.music.feature.library.LibraryViewModel
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Native UI and local encrypted credentials with an unavailable CurrentMusic backend. */
class NativeNeteaseTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var netease: MockWebServer
    private lateinit var current: MockWebServer
    private val paths = CopyOnWriteArrayList<String>()
    private val currentPaths = CopyOnWriteArrayList<String>()
    private val keys = AtomicInteger()
    @Volatile private var confirmed = false
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private val song = """{"id":11,"name":"Native favorite","ar":[{"id":22,"name":"Native artist"}],"al":{"name":"Native album"},"dt":180000}"""
    @Before fun setup() = runBlocking {
        current = MockWebServer().apply { dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                currentPaths += request.requestUrl!!.encodedPath
                return MockResponse().setResponseCode(503)
            }
        }; start() }
        netease = MockWebServer().apply { dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertNull(request.getHeader("Authorization")); paths += request.requestUrl!!.encodedPath
                return when (request.requestUrl!!.encodedPath) {
                    "/weapi/personalized/newsong" -> json("""{"code":200,"result":[{"song":$song}]}""")
                    "/weapi/v3/discovery/recommend/songs" -> json("""{"code":200,"data":{"dailySongs":[$song]}}""")
                    "/weapi/v1/radio/get" -> json("""{"code":200,"data":[$song]}""")
                    "/eapi/cloudsearch/pc" -> json("""{"code":200,"result":{"songCount":1,"songs":[$song]}}""")
                    "/weapi/hotsearchlist/get" -> json("""{"code":200,"data":[]}""")
                    "/eapi/login/qrcode/unikey" -> { keys.incrementAndGet(); json("""{"code":200,"unikey":"stable-key"}""") }
                    "/eapi/login/qrcode/client/login" -> if (confirmed) json("""{"code":803,"profile":{"userId":42,"nickname":"Native listener"}}""")
                        .addHeader("Set-Cookie", "MUSIC_U=isolated-native; Path=/; HttpOnly") else json("""{"code":801}""")
                    "/weapi/user/playlist" -> json("""{"code":200,"playlist":[{"id":88,"name":"Native likes","specialType":5,"creator":{"userId":42},"trackCount":1},{"id":89,"name":"Own playlist","creator":{"userId":42},"trackCount":1}]}""")
                    "/eapi/song/like/get" -> json("""{"code":200,"ids":[11]}""")
                    "/eapi/v6/playlist/detail" -> json("""{"code":200,"playlist":{"id":88,"name":"Native likes","specialType":5,"creator":{"userId":42},"trackCount":1,"trackIds":[{"id":11}],"tracks":[$song]}}""")
                    "/weapi/play-record/song/list" -> json("""{"code":200,"data":{"list":[{"data":$song}]}}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }; start() }
        val base = netease.url("/").toString()
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "direct-${UUID.randomUUID()}",
            useNativeNetease = true, neteaseEndpoints = NeteaseEndpoints(base, base, base))
        container.ready.await(); container.sessionRestored.await()
        container.musicSettings.setServer(current.url("/cm/").toString()); container.accountRepository.server = current.url("/cm/").toString()
        container.updateSettings.setAutoCheck(false); container.settings.edit { AppearanceSettings(blur = false) }
    }
    @After fun cleanup() { container.close(); netease.shutdown(); current.shutdown() }
    private fun waitFor(tag: String) = compose.waitUntil(15000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun assertMusicBypassesCurrentMusic() {
        // Community users, avatar decorations and public announcements still belong to CurrentMusic.
        assertTrue("Music unexpectedly reached CurrentMusic: $currentPaths", currentPaths.all {
            it == "/cm/decorations/scales" || it == "/cm/users/square" || it == "/cm/announcements"
        })
    }
    private fun open() {
        compose.setContent { CurrentMusicApp(container) }
        waitFor("open_home_search")
    }
    @Test fun homeAndSearchWorkWithoutCurrentMusicLoginOrServer() {
        open(); compose.waitUntil(15000) { compose.onAllNodesWithText("Native favorite").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home_connect_netease").assertExists()
        compose.onNodeWithTag("open_home_search").performClick(); waitFor("search_input")
        compose.onNodeWithTag("search_input").performTextInput("Die For You"); compose.onNodeWithTag("submit_search").performClick()
        waitFor("search_song_11"); assertTrue(paths.contains("/eapi/cloudsearch/pc")); assertMusicBypassesCurrentMusic()
    }
    @Test fun qrCanPauseAndResumeThenLoadsNativeLibraryWithoutImport() = runBlocking {
        val vm = BindingViewModel(container)
        val first = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val poll = first.launch { vm.qrSession() }
        withTimeout(10000) { while (vm.state.value.qrUrl == null) delay(25) }
        poll.cancelAndJoin(); val url = vm.state.value.qrUrl
        assertEquals(1, keys.get()); assertTrue(url!!.contains("stable-key"))
        confirmed = true
        withContext(Dispatchers.Main) { withTimeout(10000) { vm.qrSession() } }
        withTimeout(10000) { while (vm.state.value.binding?.bound != true) delay(25) }
        assertEquals(1, keys.get()); assertEquals(42L, container.neteaseSessions.state.value.profile?.uid)
        assertEquals(listOf(88L,89L), container.neteaseLibrary.playlists().map { it.id })
        assertEquals(listOf(11L), container.primaryLibrary.likedPlaylist()!!.songs.map { it.id })
        assertEquals(0, current.requestCount); first.cancel()
    }
    @Test fun myPageAndLikesOpenWithoutCurrentMusicAccount() = runBlocking {
        confirmed = true
        container.bindingRepository.qrStatus("stable-key", RequestSession(container.accountRepository.server, null))
        container.bindingRepository.status()
        open(); waitFor("tab_2"); compose.onNodeWithTag("tab_2").performClick()
        waitFor("profile_screen"); compose.onNodeWithText("Native listener").assertExists()
        compose.onNodeWithTag("open_likes").performClick(); waitFor("playlist_song_11")
        compose.onNodeWithText("我喜欢的音乐").assertExists(); assertMusicBypassesCurrentMusic()
    }
    @Test fun nativeAccountChangeClearsPriorHomeData() = runBlocking {
        val vm = LibraryViewModel(container)
        withTimeout(10000) { while (!vm.home.value.loaded) delay(25) }
        assertEquals(11L, vm.home.value.daily.single().id)
        // Confirming credentials invalidates the anonymous read cache and republishes
        // personal recommendations from the direct endpoint.
        confirmed = true; container.bindingRepository.qrStatus("stable-key", RequestSession(container.accountRepository.server, null))
        withTimeout(10000) { while (!paths.contains("/weapi/v3/discovery/recommend/songs") || !vm.home.value.loaded) delay(25) }
        assertTrue(container.neteaseSessions.state.value.loggedIn); assertEquals(0, current.requestCount)
    }
}
