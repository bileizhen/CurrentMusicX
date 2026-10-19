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
import io.github.currencortex.music.feature.roaming.PersonalRoamingScreen
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

class PersonalRoamingUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var c: AppContainer
    private lateinit var server: MockWebServer
    private var back = 0
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
                else MockResponse().setBody("""{"code":200}""")
            }
            start()
        }
        val endpoint = server.url("/").toString()
        c = AppContainer(context, "roaming-ui-${UUID.randomUUID()}", useNativeNetease = true,
            neteaseEndpoints = NeteaseEndpoints(endpoint, endpoint, endpoint))
        c.ready.await(); c.sessionRestored.await(); c.neteaseSessions.ready.await()
        c.neteaseSessions.login(mapOf("MUSIC_U" to "fixture"), BoundProfile(7, "听友"), c.neteaseSessions.snapshot())
        compose.waitUntil(5_000) { c.personalRoaming.state.value.ready }
    }
    @After fun cleanup() { if (::c.isInitialized) c.close(); if (::server.isInitialized) server.shutdown() }
    private fun show() {
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            Scaffold { PersonalRoamingScreen(c, { back++ }, {}, {}) }
        } }
        compose.waitUntil(8_000) { c.personalRoaming.state.value.catalog.scenes.size == 2 }
    }
    private fun click(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
    private fun save(name: String) {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun nativeModesScenesAndSettingsStayAlignedAndBackDoesNotStartPlayback() {
        show()
        listOf("DEFAULT", "FAMILIAR", "EXPLORE", "PUZZLE_MODE_RCMD").forEach {
            compose.onNodeWithTag("roaming_mode_$it").assertExists()
        }
        val default = compose.onNodeWithTag("roaming_mode_DEFAULT").getUnclippedBoundsInRoot()
        val familiar = compose.onNodeWithTag("roaming_mode_FAMILIAR").getUnclippedBoundsInRoot()
        assertEquals(default.left.value, familiar.left.value, .5f)
        assertEquals(default.right.value, familiar.right.value, .5f)
        save("roaming-modes.png")
        click("roaming_settings")
        compose.onNodeWithText("漫游设置").assertIsDisplayed()
        click("roaming_preload")
        assertFalse(c.personalRoaming.state.value.settings.preload)
        save("roaming-settings.png")
        click("roaming_settings_done")
        click("roaming_back"); assertEquals(1, back)
        assertFalse(c.personalRoaming.state.value.active)
        assertTrue(c.playbackQueue.state.value.songs.isEmpty())
    }
    @Test fun coverPuzzleSwapsTilesAndCompletesWithoutChangingPlayback() {
        val song = Song(11, "拼图中的歌曲", "示例听友", cover = server.url("/cover.png").toString())
        compose.runOnUiThread {
            c.playbackQueue.startRadio(listOf(song), "fixture")
            c.playerController.state.value = PlayerState(song = song)
            c.personalRoaming.state.value = c.personalRoaming.state.value.copy(active = true,
                choice = RoamingCatalog.fallback.modes.last())
        }
        show()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("roaming_puzzle_tile_0").fetchSemanticsNodes().isNotEmpty() }
        save("roaming-puzzle.png")
        for ((a, b) in listOf(0 to 5, 2 to 7, 4 to 5, 5 to 8, 6 to 7)) {
            click("roaming_puzzle_tile_$a"); click("roaming_puzzle_tile_$b")
        }
        compose.onNodeWithText("拼好了，继续聆听这首歌").assertExists()
        assertEquals(song, c.playbackQueue.state.value.current)
        assertFalse(c.playerController.state.value.showPause)
    }
    @Test fun homeShortcutUsesTheExistingNavigationHost() {
        var route = ""
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) { Scaffold {
            HomeLibraryShortcuts({ route = it }, false)
        } } }
        click("open_personal_roaming")
        assertEquals("lib/roaming", route)
        compose.onNodeWithTag("open_likes").assertIsDisplayed()
        compose.onNodeWithTag("open_rooms").assertIsDisplayed()
    }
}
