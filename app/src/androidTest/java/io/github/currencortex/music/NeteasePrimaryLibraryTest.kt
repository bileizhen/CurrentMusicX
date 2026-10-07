package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Real native UI with isolated account/storage and a fake gateway; no production library writes. */
class NeteasePrimaryLibraryTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    @Volatile private var liked = true
    @Volatile private var bound = true
    private val nativeLikes = CopyOnWriteArrayList<Boolean>()
    private val nativeCollections = CopyOnWriteArrayList<Long>()
    private val currentLikeWrites = AtomicInteger()
    private val imports = AtomicInteger()
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    @Before fun setup(): Unit = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "native-main-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/cm/auth/me" -> json("""{"id":7,"username":"listener","nickname":"Listener"}""")
                "/cm/ncmbind" -> json("""{"bound":$bound,"profile":{"uid":700,"nickname":"Native listener"},"ncmLikedPlId":42}""")
                "/cm/ncmbind/phone/login" -> { bound = true; json("""{"code":200}""") }
                "/cm/ncmbind/likelist" -> json("""{"ids":${if (liked) "[11]" else "[]"}}""")
                "/cm/ncmbind/like/11" -> {
                    assertEquals("Bearer isolated-native", request.getHeader("Authorization"))
                    liked = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["like"]!!.jsonPrimitive.boolean
                    nativeLikes += liked; json("""{"code":200,"like":$liked}""")
                }
                "/cm/ncm/user/playlist" -> json("""{"code":200,"playlist":[
                    {"id":9918106457,"name":"Native likes","specialType":5,"creator":{"userId":700},"trackCount":1},
                    {"id":88,"name":"Own native playlist","creator":{"userId":700},"trackCount":1},
                    {"id":89,"name":"Foreign playlist","creator":{"userId":900}}]}""")
                "/cm/ncm/playlist/detail" -> json("""{"code":200,"playlist":{"id":${request.requestUrl!!.queryParameter("id")},"name":"Own native playlist","creator":{"userId":700},"trackIds":[{"id":11}],"trackCount":1}}""")
                "/cm/ncm/song/detail" -> json("""{"code":200,"songs":[{"id":11,"name":"Native favorite","ar":[{"id":2,"name":"Artist"}],"al":{"name":"Album"},"dt":180000}]}""")
                "/cm/ncm/playlist/tracks" -> {
                    assertEquals("1", request.requestUrl!!.queryParameter("confirm"))
                    assertEquals("11", request.requestUrl!!.queryParameter("tracks"))
                    nativeCollections += request.requestUrl!!.queryParameter("pid")!!.toLong()
                    json("""{"code":200}""")
                }
                "/cm/ncm/song/red/count" -> json("""{"code":200,"data":{"count":12001}}""")
                "/cm/ncm/comment/music" -> json("""{"code":200,"total":3,"comments":[]}""")
                "/cm/songs/status" -> json("""{"liked":[11]}""") // Different library stays liked when NetEase is unliked.
                "/cm/likes/11" -> { currentLikeWrites.incrementAndGet(); json("""{"on":false}""") }
                "/cm/likes/mine" -> json("""{"songs":[{"ncm_id":77,"name":"Legacy favorite"}]}""")
                "/cm/playlists" -> json("""{"playlists":[{"id":88,"name":"CurrentMusic playlist","user_id":7}]}""")
                "/cm/ncmbind/sync" -> { imports.incrementAndGet(); json("{}") }
                "/cm/daily" -> json("""{"daily":[],"forYou":[]}""")
                "/cm/plays/recent" -> json("""{"songs":[]}""")
                "/cm/room/active" -> json("""{"room":null}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val url = server.url("/cm/").toString()
        container.musicSettings.setServer(url); container.accountRepository.server = url
        container.accountRepository.save("isolated-native", UserDto(7, "listener", "Listener"))
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { AppearanceSettings(blur = false) }
        val song = Song(11, "Native favorite", "Artist", durationMs = 180000)
        container.playbackQueue.replace(listOf(song), 0)
        container.playerController.state.value = PlayerState(song = song)
    }
    @After fun cleanup() { container.close(); server.shutdown() }
    private fun open() {
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(15000) { container.primaryLibrary.usesNetease.value && compose.onAllNodesWithTag("mini_cover").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun player() {
        open()
        compose.onNodeWithTag("mini_cover").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("player_like").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun playerTapTogglesOnlyNeteaseAndDoesNotUnionTheCurrentMusicHeart() {
        player()
        compose.waitUntil(10000) { compose.onNodeWithTag("player_like").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Selected] }
        compose.onNodeWithTag("player_like").performClick()
        compose.waitUntil(10000) { nativeLikes.toList() == listOf(false) }
        compose.waitUntil(10000) { !compose.onNodeWithTag("player_like").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Selected] }
        compose.onNodeWithTag("song_like_sheet").assertDoesNotExist()
        assertEquals(0, currentLikeWrites.get())
        compose.onNodeWithTag("player_like").performClick()
        compose.waitUntil(10000) { nativeLikes.toList() == listOf(false, true) }
        assertEquals(0, currentLikeWrites.get())
    }
    @Test fun playerLongPressOffersOwnedNeteasePlaylistsAndUsesTheirRealIds() {
        player()
        compose.onNodeWithTag("player_like").performTouchInput { longClick() }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("collect_playlist_netease_88").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("collect_playlist_netease_9918106457").assertExists()
        compose.onNodeWithTag("collect_playlist_netease_89").assertDoesNotExist()
        compose.onNodeWithTag("collect_playlist__88").assertExists()
        assertTrue(nativeLikes.isEmpty())
        compose.onNodeWithTag("collect_playlist_netease_88").performClick()
        compose.waitUntil(10000) { nativeCollections.toList() == listOf(88L) }
        assertEquals(0, currentLikeWrites.get())
    }
    @Test fun myFavoritesOpenTheLiveNeteasePlaylistAndSettingCanRestoreTheCurrentLibrary() {
        open()
        compose.onNodeWithTag("tab_2").performClick()
        val myLikes = hasTestTag("open_likes") and hasAnyAncestor(hasTestTag("profile_screen"))
        compose.waitUntil(10000) { compose.onAllNodes(myLikes).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(myLikes).performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("playlist_song_11").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("playlist_song_77").assertDoesNotExist()
        compose.onNodeWithText("网易云音乐").assertIsDisplayed()
        runBlocking { container.musicSettings.setNeteaseMainLibrary(false) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("playlist_song_77").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("playlist_song_11").assertDoesNotExist()
        assertEquals(0, imports.get())
    }
    @Test fun bindingDisplaysTheEnabledPrimaryLibraryChoiceWithoutImportingSnapshots() {
        open()
        compose.onNodeWithTag("tab_2").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("open_binding").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open_binding").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("binding_account_card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("netease_main_library").performScrollTo().assertIsOn()
        compose.onNodeWithText("刷新网易云音乐库").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("音乐库已更新").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, imports.get())
        compose.onNodeWithTag("netease_main_library").performScrollTo().performClick()
        compose.waitUntil(5000) { !container.musicSettings.state.value.neteaseMainLibrary }
        assertFalse(runBlocking { container.musicSettings.snapshot().neteaseMainLibrary })
    }
    @Test fun completingBindingWithPrimaryModeSkipsImportAndConnectsTheLiveFavorites() {
        bound = false
        lateinit var bindingVm: io.github.currencortex.music.feature.binding.BindingViewModel
        compose.setContent {
            bindingVm = androidx.lifecycle.viewmodel.compose.viewModel(key = "primary-binding-test",
                factory = io.github.currencortex.music.ui.util.viewModelFactory { io.github.currencortex.music.feature.binding.BindingViewModel(container) })
            CurrentMusicApp(container)
        }
        compose.waitUntil(10000) { bindingVm.state.value.binding?.bound == false }
        compose.runOnUiThread { bindingVm.phone("123456789", "1234", "86") }
        compose.waitUntil(10000) { bindingVm.message.value == "绑定成功，已使用网易云音乐库" }
        assertEquals(0, imports.get())
        compose.waitUntil(10000) { container.primaryLibrary.usesNetease.value }
        compose.onNodeWithTag("open_likes").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("playlist_song_11").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("playlist_song_77").assertDoesNotExist()
    }
}
