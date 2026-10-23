package io.github.currencortex.music

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.core.netease.NeteaseEndpoints
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.roaming.RoamingCatalog
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.home.HomeLibraryShortcuts
import io.github.currencortex.music.feature.roaming.PersonalRoamingDialog
import io.github.currencortex.music.feature.player.PlayerScreen
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.ui.util.viewModelFactory
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import top.yukonga.miuix.kmp.basic.Scaffold
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class PersonalRoamingUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var c: AppContainer
    private lateinit var server: MockWebServer
    private var back = 0
    private val recommendationRequests = AtomicInteger()
    private val catalog = """{"code":200,"data":{"crossPlatformResource":{"dslData":{
        "recommendModeList":[{"code":"DEFAULT","title":"默认模式","subTitle":"沿着目前喜好继续聆听"},
        {"code":"FAMILIAR","title":"熟悉模式","subTitle":"喜欢过的歌曲与相似推荐"},
        {"code":"EXPLORE","title":"探索模式","subTitle":"偏好曲风与潜力好歌"},
        {"code":"PUZZLE_MODE_RCMD","title":"拼图模式","subTitle":"边听歌边拼歌曲封面"}],
        "currentSceneList":[{"code":"RAINY","title":"下雨天"},{"code":"FOCUS","title":"专注"}]}}}}"""
    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(context.packageName.endsWith(".verification"))
        val png = ByteArrayOutputStream().apply {
            Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
                for (y in 0..95) for (x in 0..95) setPixel(x, y, android.graphics.Color.rgb(x * 2, y * 2, 140))
            }.compress(Bitmap.CompressFormat.PNG, 100, this)
        }.toByteArray()
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = if (request.path == "/cover.png")
                    MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png))
                else if (request.path == "/eapi/link/position/show/resource") MockResponse().setBody(catalog)
                else if (request.path == "/eapi/v1/radio/get") {
                    recommendationRequests.incrementAndGet()
                    MockResponse().setBody("""{"code":503,"message":"推荐暂不可用"}""")
                }
                else MockResponse().setBody("""{"code":200}""")
            }
            start()
        }
        val endpoint = server.url("/").toString()
        c = AppContainer(context, "roaming-ui-${UUID.randomUUID()}", useNativeNetease = true,
            neteaseEndpoints = NeteaseEndpoints(endpoint, endpoint, endpoint))
        c.ready.await(); c.sessionRestored.await(); c.neteaseSessions.ready.await()
        compose.waitUntil(5_000) { c.personalRoaming.state.value.ready }
        compose.runOnUiThread { c.personalRoaming.state.value = c.personalRoaming.state.value.copy(ready = false) }
        c.neteaseSessions.login(mapOf("MUSIC_U" to "fixture"), BoundProfile(7, "听友"), c.neteaseSessions.snapshot())
        compose.waitUntil(5_000) { c.personalRoaming.state.value.ready }
    }
    @After fun cleanup() { if (::c.isInitialized) c.close(); if (::server.isInitialized) server.shutdown() }
    private val visible = androidx.compose.runtime.mutableStateOf(true)
    private fun showDialog() {
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) { Scaffold {
            if (visible.value) PersonalRoamingDialog(c, { back++; visible.value = false }, {})
        } } }
        compose.waitUntil(8_000) { c.personalRoaming.state.value.catalog.scenes.size == 2 }
    }
    private fun showPlayer(roamingRequest: Int = 0) {
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) { Scaffold {
            PlayerScreen(androidx.lifecycle.viewmodel.compose.viewModel(factory = viewModelFactory { PlayerViewModel(c) }),
                {}, {}, roamingRequest = roamingRequest)
        } } }
    }
    private fun seed(puzzle: Boolean = false): Song {
        val song = Song(11, "正在聆听的歌曲", "示例听友", cover = server.url("/cover.png").toString())
        compose.runOnUiThread {
            c.playbackQueue.startRadio(listOf(song), "fixture")
            c.playerController.state.value = PlayerState(song = song, durationMs = 180_000)
            c.personalRoaming.state.value = c.personalRoaming.state.value.copy(active = true,
                choice = if (puzzle) RoamingCatalog.fallback.modes.last() else RoamingCatalog.fallback.modes.first())
        }
        return song
    }
    private fun click(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
    private fun save(name: String) {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun modeRowsAndSettingsStayAlignedWithoutStartingPlayback() {
        showDialog()
        val default = compose.onNodeWithTag("roaming_mode_DEFAULT").getUnclippedBoundsInRoot()
        val familiar = compose.onNodeWithTag("roaming_mode_FAMILIAR").getUnclippedBoundsInRoot()
        assertEquals(default.left.value, familiar.left.value, .5f)
        assertEquals(default.right.value, familiar.right.value, .5f)
        save("roaming-dialog.png")
        click("roaming_settings")
        compose.onNodeWithText("漫游设置").assertIsDisplayed()
        click("roaming_preload")
        assertFalse(c.personalRoaming.state.value.settings.preload)
        save("roaming-settings.png")
        click("roaming_settings_done")
        compose.onNodeWithText("私人漫游").assertIsDisplayed()
        assertFalse(c.personalRoaming.state.value.active)
        assertTrue(c.playbackQueue.state.value.songs.isEmpty())
        assertEquals(0, recommendationRequests.get())
    }
    @Test fun selectingModesAndScenesRequestsNothingUntilExplicitStart() {
        showDialog()
        click("roaming_mode_FAMILIAR")
        compose.onNodeWithTag("roaming_mode_FAMILIAR").assertIsSelected()
        assertEquals(0, recommendationRequests.get())
        compose.onNodeWithTag("roaming_scene_RAINY").performScrollTo()
        click("roaming_scene_RAINY")
        compose.onNodeWithTag("roaming_scene_RAINY").assertIsSelected()
        assertEquals(0, recommendationRequests.get())
        assertFalse(c.personalRoaming.state.value.active)
        click("roaming_start")
        compose.waitUntil(5_000) { c.personalRoaming.state.value.error != null }
        assertEquals(1, recommendationRequests.get())
        assertTrue(c.playbackQueue.state.value.songs.isEmpty())
        compose.waitUntil(3_000) { c.errorNotices.state.value?.message == "推荐暂不可用" }
        compose.onNodeWithText("推荐暂不可用").assertDoesNotExist()
    }
    @Test fun activePlayerHeaderOpensModeDialogAndQualityRemainsInOptions() {
        val song = seed()
        showPlayer()
        compose.onNodeWithText("私人漫游 · 默认模式").assertIsDisplayed()
        compose.onNodeWithTag("open_quality_sheet").assertDoesNotExist()
        click("open_roaming_modes")
        compose.waitUntil(8_000) { c.personalRoaming.state.value.catalog.scenes.size == 2 }
        val end = compose.onNodeWithTag("roaming_stop").getUnclippedBoundsInRoot()
        val confirm = compose.onNodeWithTag("roaming_start").getUnclippedBoundsInRoot()
        assertEquals(end.top.value, confirm.top.value, .5f)
        assertEquals(end.bottom.value, confirm.bottom.value, .5f)
        assertEquals((end.right - end.left).value, (confirm.right - confirm.left).value, .5f)
        assertTrue(end.right < confirm.left)
        save("roaming-actions.png")
        click("roaming_mode_EXPLORE")
        assertEquals("DEFAULT", c.personalRoaming.state.value.choice.code)
        assertEquals(song, c.playbackQueue.state.value.current)
        assertEquals(0, recommendationRequests.get())
        click("roaming_mode_DEFAULT"); click("roaming_start")
        compose.onNodeWithTag("roaming_start").assertDoesNotExist()
        click("lyrics_options")
        compose.onNodeWithText("播放音质").assertExists()
        save("roaming-player-options.png")
        compose.onNodeWithTag("open_player_roaming").performScrollTo()
        click("open_player_roaming"); click("roaming_stop")
        compose.onNodeWithTag("open_quality_sheet").assertIsDisplayed()
        compose.onNodeWithTag("open_roaming_modes").assertDoesNotExist()
        val quality = compose.onNodeWithTag("open_quality_sheet").fetchSemanticsNode().boundsInRoot
        val seek = compose.onNodeWithTag("player_seek").fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("player_function_bar").fetchSemanticsNode().boundsInRoot
        assertTrue("Song actions sit above the progress bar", actions.bottom <= seek.top)
        val toggle = compose.onNodeWithTag("player_toggle").fetchSemanticsNode().boundsInRoot
        assertEquals("Quality stays centered beneath the seek bar", seek.center.x, quality.center.x, 1f)
        assertTrue(quality.top >= seek.bottom && quality.bottom <= toggle.top)
        save("player-quality-position.png")
        compose.onNodeWithTag("open_quality_sheet").performClick()
        compose.onNodeWithText("当前歌曲音质").assertIsDisplayed()
    }
    @Test fun longSceneCatalogScrollsAndKeepsConfirmationVisible() {
        showDialog()
        compose.runOnUiThread {
            val current = c.personalRoaming.state.value
            c.personalRoaming.state.value = current.copy(catalog = current.catalog.copy(scenes =
                List(41) { current.catalog.scenes.first().copy(code = "SCENE_$it", title = "场景 $it") }))
        }
        val before = compose.onNodeWithTag("roaming_start").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("roaming_scene_SCENE_40").performScrollTo().assertIsDisplayed()
        val after = compose.onNodeWithTag("roaming_start").getUnclippedBoundsInRoot()
        assertEquals(before.top.value, after.top.value, .5f)
        save("roaming-scenes-long.png")
        click("roaming_scene_SCENE_40")
        compose.onNodeWithTag("roaming_scene_SCENE_40").assertIsSelected()
        assertEquals(0, recommendationRequests.get())
    }
    @Test fun puzzleLivesInsideExistingPlayerAndDoesNotChangePlayback() {
        val song = seed(puzzle = true)
        showPlayer()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("roaming_puzzle_tile_0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("player_screen").assertIsDisplayed()
        compose.onNodeWithTag("player_toggle").assertIsDisplayed()
        compose.onNodeWithTag("player_pager_artwork").assertDoesNotExist()
        save("roaming-puzzle-player.png")
        for ((a, b) in listOf(0 to 5, 2 to 7, 4 to 5, 5 to 8, 6 to 7)) {
            compose.onNodeWithTag("roaming_puzzle_tile_$a").performClick()
            compose.onNodeWithTag("roaming_puzzle_tile_$b").performClick()
        }
        compose.onNodeWithTag("roaming_puzzle_tile_0").assertIsNotEnabled()
        assertEquals(song, c.playbackQueue.state.value.current)
        assertFalse(c.playerController.state.value.showPause)
    }
    @Test fun homeEntryUsesExistingPlayerOverlayInsteadOfANewPage() = runBlocking {
        c.updateSettings.setAutoCheck(false)
        c.accountRepository.save("roaming-ui-fixture", io.github.currencortex.music.data.auth.UserDto(7, nickname = "听友"))
        val base = server.url("/cm/").toString()
        c.musicSettings.setServer(base); c.accountRepository.server = base
        compose.setContent { io.github.currencortex.music.ui.CurrentMusicApp(c) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("open_personal_roaming").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open_personal_roaming").performScrollTo()
        click("open_personal_roaming")
        compose.onNodeWithTag("player_screen").assertExists()
        compose.onNodeWithTag("roaming_mode_DEFAULT").assertIsDisplayed()
        compose.onNodeWithTag("music_scene_lib/roaming").assertDoesNotExist()
        assertEquals(0, recommendationRequests.get())
    }
}
