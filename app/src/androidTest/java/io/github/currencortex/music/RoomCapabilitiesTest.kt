package io.github.currencortex.music

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.room.*
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.data.settings.AppearanceSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class RoomCapabilitiesTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private lateinit var player: SilentDevicePlayer
    @Volatile private var owner = true
    @Volatile private var approved = false
    private val approvals = AtomicInteger()
    private val playRequests = AtomicInteger()
    private val songRequests = AtomicInteger()
    @Volatile private var roomsDelay = 0L
    @Volatile private var createFails = false
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    @Before fun prepare() = runBlocking {
        player = SilentDevicePlayer().apply { queue.replace(listOf(Song(55, "Original local queue")), 0) }
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "room-test-${UUID.randomUUID()}", player)
        container.ready.await(); container.sessionRestored.await()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path == "/cm/rooms" && request.method == "POST" && createFails -> MockResponse().setResponseCode(500)
                    path == "/cm/rooms" -> json("""{"rooms":[{"id":1,"name":"Fixture room","code":"001234","online":2,"owner_name":"Owner"}]}""").setBodyDelay(roomsDelay, java.util.concurrent.TimeUnit.MILLISECONDS)
                    path == "/cm/rooms/search" -> json("""{"room":{"id":1,"code":"001234","name":"Fixture room"}}""")
                    path == "/cm/rooms/1" -> json("""{"room":{"id":1,"code":"001234","name":"Fixture room"},"members":[{"userId":7,"nickname":"Fixture user","role":"${if(owner) "owner" else "member"}"}],"queue":[{"id":12,"name":"Requested song","status":"${if(approved) "approved" else "pending"}","mine":false}],"latestSeq":0}""")
                    path == "/cm/rooms/1/queue/12/approve" -> { approvals.incrementAndGet(); approved = true; json("{}") }
                    path == "/cm/rooms/1/play" -> { playRequests.incrementAndGet(); json("{}") }
                    path == "/cm/rooms/1/queue" -> { songRequests.incrementAndGet(); json("{}").setBodyDelay(350, java.util.concurrent.TimeUnit.MILLISECONDS) }
                    path == "/cm/ncm/search" -> json("""{"songs":[{"ncm_id":95,"name":"Search request song","artists":"Fixture artist"}],"totals":{"song":1},"hasMore":{"song":false}}""")
                    path == "/cm/rooms/1/sync" -> json("""{"serverNow":${System.currentTimeMillis()}}""")
                    path == "/cm/live/1/events" -> MockResponse().setHeader("Content-Type", "text/event-stream").setBody(": ping\n\n")
                    path == "/cm/auth/me" -> json("""{"id":7,"username":"fixture","nickname":"Fixture user"}""")
                    path == "/cm/daily" -> json("""{"daily":[],"forYou":[]}""")
                    path == "/cm/playlists" -> json("""{"playlists":[]}""")
                    path == "/cm/plays/recent" -> json("""{"songs":[]}""")
                    path == "/cm/decorations/scales" -> json("""{"scales":{}}""")
                    else -> json("{}")
                }
            }
        }
        server.start()
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.accountRepository.save("isolated-room-device-token", UserDto(7, "fixture", "Fixture user"))
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { AppearanceSettings(blur = false) }
    }
    @After fun finish() = runBlocking {
        if (::container.isInitialized) { container.accountRepository.clear(); container.accountRepository.savedAccounts.value.forEach { container.accountRepository.removeSaved(it.key) }; container.close() }
        if (::server.isInitialized) server.shutdown()
    }
    private fun show(wide: Boolean = false): RoomViewModel {
        val vm = RoomViewModel(container)
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold(contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0,0,0,0)) {
                androidx.compose.foundation.layout.Box(if(wide) Modifier.requiredSize(900.dp, 650.dp) else Modifier) { RoomScreen(vm, {}, {}, {}) }
            }
        } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("加入").fetchSemanticsNodes().isNotEmpty() }
        if (wide) compose.runOnIdle { vm.find("001234") } else compose.onNodeWithText("加入").performClick()
        compose.waitUntil(10000) { vm.session.state.value.detail != null }
        return vm
    }
    @Test fun ownerApprovesRequestAndExitRestoresLocalQueuePaused() {
        val vm = show()
        compose.onNodeWithText("通过").performClick()
        compose.waitUntil(10000) { approvals.get() == 1 && vm.session.state.value.detail?.queue?.firstOrNull()?.status == "approved" }
        compose.onNodeWithText("已加入队列").assertExists()
        captureRoom("room-owner-preview.png")
        compose.onNodeWithText("退出房间").performClick()
        compose.onNodeWithTag("confirm_leave_room").performClick()
        compose.waitUntil(10000) { !vm.session.active }
        assertEquals(55L, player.queue.state.value.current?.id); assertEquals(PlayerMode.LOCAL, player.state.value.mode); assertFalse(player.state.value.playing)
    }
    @Test fun refreshingRoomBrowserKeepsExistingCardsVisible() {
        val vm = RoomViewModel(container)
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) { top.yukonga.miuix.kmp.basic.Scaffold { RoomScreen(vm, {}, {}, {}) } } }
        compose.waitUntil(10000) { vm.browser.value.rooms.isNotEmpty() && !vm.browser.value.loading }
        roomsDelay = 1000
        compose.onNodeWithText("刷新").performClick()
        compose.onNodeWithText("Fixture room").assertIsDisplayed()
        captureRoom("room-browser-preview.png")
        compose.onNodeWithText("加入").assertIsDisplayed()
        compose.onNodeWithText("刷新中").assertIsNotEnabled()
        compose.waitUntil(5000) { !vm.browser.value.loading }
        compose.onNodeWithText("Fixture room").assertIsDisplayed()
    }
    @Test fun failedRoomCreationKeepsDialogAndNameForRetry() {
        createFails = true
        val vm = RoomViewModel(container)
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) { top.yukonga.miuix.kmp.basic.Scaffold { RoomScreen(vm, {}, {}, {}) } } }
        compose.waitUntil(10000) { vm.browser.value.rooms.isNotEmpty() }
        compose.onNodeWithText("创建房间").performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("room_form")) and hasText("房间名称"))
            .performTextInput("Retry room")
        compose.onNodeWithText("保存").performScrollTo().performClick()
        compose.waitUntil(5000) { !vm.busy.value && vm.browser.value.error != null }
        compose.onNodeWithTag("room_form").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Retry room")).assertExists()
        compose.onNodeWithText("保存").performScrollTo().assertIsEnabled()
    }
    @Test fun memberCannotApproveOrControlPlayback() {
        owner = false
        val vm = show()
        compose.onNodeWithText("通过").assertDoesNotExist()
        compose.onNodeWithText("播放").assertDoesNotExist()
        compose.runOnIdle { vm.session.play(true); vm.session.queueAction(vm.session.state.value.detail!!.queue.first(), "approve") }
        compose.waitForIdle(); assertEquals(0, playRequests.get()); assertEquals(0, approvals.get())
        assertFalse(player.state.value.canControlPlayback)
    }
    @Test fun memberPlayerControlsAreDisabledAndRecoverAfterPromotion() {
        val controller = container.playerController
        controller.queue.replace(listOf(Song(55, "Fixture song", durationMs = 180000)), 0)
        controller.state.value = PlayerState(song = controller.queue.state.value.current, mode = PlayerMode.ROOM, canControlPlayback = false)
        val vm = io.github.currencortex.music.feature.player.PlayerViewModel(container)
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            androidx.compose.foundation.layout.Column {
                io.github.currencortex.music.feature.player.MiniPlayer(vm, {}, {})
                io.github.currencortex.music.feature.player.PlayerScreen(vm, {}, {})
            }
        } }
        compose.onNodeWithTag("mini_toggle").assertIsNotEnabled()
        compose.onNodeWithTag("player_toggle").assertIsNotEnabled()
        compose.onNodeWithTag("player_seek").assertIsNotEnabled()
        compose.onNodeWithTag("player_seek").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsActions.SetProgress))
        compose.onNodeWithText("播放由房主或管理员控制").assertExists()
        compose.runOnIdle { controller.state.value = controller.state.value.copy(canControlPlayback = true) }
        compose.onNodeWithTag("mini_toggle").assertIsEnabled()
        compose.onNodeWithTag("player_toggle").assertIsEnabled()
        compose.onNodeWithTag("player_seek").assertIsEnabled()
    }
    @Test fun createFormSaveRemainsReachableWithKeyboardOpen() {
        val vm = RoomViewModel(container)
        val keyboardHeight = AtomicInteger()
        compose.setContent {
            val imeHeight = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
            androidx.compose.runtime.SideEffect { keyboardHeight.set(imeHeight) }
            LeiTheme(AppearanceSettings(blur = false)) {
            top.yukonga.miuix.kmp.basic.Scaffold { RoomScreen(vm, {}, {}, {}) }
        } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("创建房间").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("创建房间").performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("room_form")) and hasText("房间名称"))
            .performClick().performTextInput("Fixture form")
        compose.waitUntil(5000) { keyboardHeight.get() > 0 }
        compose.onNodeWithText("保存").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }
    @Test fun wideRoomShowsQueueAndMembersTogether() {
        show(wide = true)
        compose.onNodeWithTag("room_two_panes").assertExists()
        compose.onNodeWithText("Requested song").assertExists()
        compose.onNodeWithText("Fixture user · 房主").assertExists()
    }
    private fun captureRoom(name: String) {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        java.io.File(context.filesDir, name).outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
    }
    @Test fun roomSongSearchSubmitsOnceAndReturnsToSameRoomWithoutLocalPlayback() {
        owner = false
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("open_rooms").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open_rooms").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("加入").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("加入").performClick()
        compose.waitUntil(10000) { container.roomSession.active }
        compose.onNodeWithText("点歌").performClick()
        compose.onNodeWithText("Fixture room · 点击歌曲提交点歌").assertExists()
        compose.onNodeWithTag("search_input").performTextInput("Fixture")
        compose.onNodeWithTag("submit_search").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Search request song").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Search request song").performClick()
        compose.waitUntil(10000) { compose.onAllNodes(hasText("已提交点歌", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, songRequests.get()); assertEquals(0, playRequests.get())
        assertFalse(player.state.value.playing)
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("room_search_back").performClick()
        compose.onNodeWithTag("room_screen").assertExists()
        assertEquals("1", container.roomSession.state.value.detail!!.room.id)
        assertTrue(container.roomSession.active)
    }
    @Test fun bottomTabsRecoverAfterRoomOpenedFromPlayer() {
        val song = Song(55, "Paused fixture song", durationMs = 180000)
        container.playerController.queue.replace(listOf(song), 0)
        container.playerController.state.value = PlayerState(song = song, durationMs = 180000)
        compose.setContent { CurrentMusicApp(container) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("mini_cover").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_0").assertIsDisplayed()
        repeat(2) {
            compose.onNodeWithTag("mini_cover").performClick()
            compose.onNodeWithTag("lyrics_options").performClick()
            compose.onNode(hasText("一起听") and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithTag("room_screen").assertIsDisplayed()
            compose.onNodeWithText("返回").performClick()
            compose.onNodeWithTag("navigate_back").performClick()
            compose.waitForIdle()
            (0..3).forEach { tab -> compose.onNodeWithTag("tab_$tab").assertIsDisplayed() }
            compose.onNodeWithTag("tab_2").performClick()
            compose.onNodeWithTag("edit_profile").assertExists()
            compose.onNodeWithTag("tab_0").performClick()
            compose.onNodeWithTag("open_rooms").assertExists()
        }
        assertEquals(55L, container.playerController.queue.state.value.current?.id)
        assertFalse(container.playerController.state.value.playing)
        assertFalse(container.roomSession.active)
    }
    @Test fun wideRootUsesPermanentNavigationAndRestoresSelectedTab() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { androidx.compose.foundation.layout.Box(Modifier.requiredSize(900.dp, 650.dp)) { CurrentMusicApp(container) } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("wide_navigation").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("wide_navigation").assertExists()
        compose.onNodeWithTag("tab_3").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("tab_3").assertIsSelected()
    }
    @Test fun cancelledWifiDiscoveryReleasesRealMulticastLock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        org.junit.Assume.assumeTrue(manager.allNetworks.any { manager.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true })
        val discovery = io.github.currencortex.music.core.dlna.DlnaDiscovery(context)
        val scan = launch(Dispatchers.IO) { discovery.scan() }
        withTimeout(3000) { while (!discovery.multicastHeld) delay(10) }
        scan.cancelAndJoin()
        assertFalse("Multicast lock must be released when scanning is cancelled", discovery.multicastHeld)
    }
    @Test fun nativeSoapAndXmlPipelineControlsSilentRendererEmulator() = runBlocking {
        okhttp3.mockwebserver.MockWebServer().use { renderer ->
            fun response(fields: String = "<ok/>") = MockResponse().setBody("""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>$fields</s:Body></s:Envelope>""")
            val service = io.github.currencortex.music.core.dlna.DlnaService("urn:schemas-upnp-org:service:AVTransport:1", renderer.url("/av").toString())
            val soap = io.github.currencortex.music.core.dlna.SoapClient()
            val av = io.github.currencortex.music.core.dlna.AvTransportClient(soap, service)
            renderer.enqueue(response())
            av.setUri("https://cdn.test/song.mp3?a=1&b=2", io.github.currencortex.music.core.dlna.DidlMetadataBuilder.build(Song(1, "A & B"), "https://cdn.test/song.mp3?a=1&b=2", "audio/mpeg"))
            val request = renderer.takeRequest(); assertNull(request.getHeader("Authorization"))
            val xml = io.github.currencortex.music.core.dlna.DlnaXml.parse(request.body.readUtf8())
            assertEquals("https://cdn.test/song.mp3?a=1&b=2", io.github.currencortex.music.core.dlna.DlnaXml.value(xml, "CurrentURI"))
            renderer.enqueue(response()); av.play()
            renderer.enqueue(response()); av.pause()
            renderer.enqueue(response()); av.seek(60000)
            renderer.enqueue(response("<RelTime>00:01:00</RelTime><TrackDuration>00:03:00</TrackDuration>"))
            assertEquals(60000L, av.position().positionMs)
            renderer.enqueue(response()); av.stop()
            val rendering = io.github.currencortex.music.core.dlna.RenderingControlClient(soap, service.copy(type = "urn:schemas-upnp-org:service:RenderingControl:1"))
            renderer.enqueue(response()); rendering.volume(65)
            renderer.enqueue(response("<CurrentVolume>65</CurrentVolume>")); assertEquals(65, rendering.volume())
        }
    }
    private class SilentDevicePlayer : ExternalPlayer {
        override val state = MutableStateFlow(PlayerState())
        override val queue = PlaybackQueue()
        override var external: ExternalPlayback? = null
        private var saved: QueueSnapshot? = null
        override suspend fun beginExternal(mode: PlayerMode, controls: ExternalPlayback): Boolean {
            if (state.value.mode != PlayerMode.LOCAL) return false
            saved = queue.state.value; external = controls; state.value = state.value.copy(mode = mode, playing = false); return true
        }
        override fun endExternal(controls: ExternalPlayback) { if (external === controls) { external = null; saved?.let(queue::restore); state.value = PlayerState(song = queue.state.value.current) } }
        override suspend fun roomTrack(song: Song?, url: String?, position: Long, playing: Boolean) { queue.replace(listOfNotNull(song), 0); state.value = PlayerState(song, playing, mode = PlayerMode.ROOM, positionMs = position) }
        override suspend fun roomCorrection(position: Long, seek: Boolean, speed: Float, playing: Boolean) { state.value = state.value.copy(positionMs = if(seek) position else state.value.positionMs, playing = playing) }
    }
}
