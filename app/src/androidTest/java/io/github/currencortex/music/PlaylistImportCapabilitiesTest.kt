package io.github.currencortex.music

import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.netease.NeteaseEndpoints
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.playlistimport.PlaylistImportScreen
import io.github.currencortex.music.feature.playlistimport.PlaylistImportViewModel
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import top.yukonga.miuix.kmp.basic.Scaffold
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Local endpoint fixtures only: no online task, imported playlist, or real account mutation. */
class PlaylistImportCapabilitiesTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private lateinit var vm: PlaylistImportViewModel
    private val paths = CopyOnWriteArrayList<String>()
    private val imported = AtomicInteger()
    private val loginRequests = AtomicInteger()
    private val openedPlaylist = AtomicLong()
    @Volatile private var imeBottom = 0
    @Volatile private var statusBody = """{"code":200,"data":{"taskProcess":"导入完成","tasks":[{"id":99,"playlistId":123,"status":"COMPLETE","msg":"导入完成"}]}}"""

    @Before fun prepare() = runBlocking<Unit> {
        check(context.packageName.endsWith(".verification"))
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    paths += path
                    val body = when (path) {
                        "/eapi/user/playlist" -> """{"code":200,"playlist":[{"id":123,"name":"目标歌单","creator":{"userId":42}}]}"""
                        "/eapi/nos/token/whalealloc" -> """{"code":200,"data":{"objectKey":"fixture.png","token":"upload-fixture"}}"""
                        "/yyimgs/fixture.png" -> """{"offset":${request.body.size},"context":"finished"}"""
                        "/eapi/playlist/import/create" -> """{"code":200,"data":{"taskId":99}}"""
                        "/eapi/playlist/import/url/check" -> """{"code":200,"data":{"link":"https://y.qq.com/n/ryqq/playlist/7"}}"""
                        "/eapi/playlist/import/name/task/create" -> """{"code":200,"data":{"taskId":99}}"""
                        "/eapi/playlist/import/task/status/v2" -> statusBody
                        else -> """{"code":200,"items":[],"data":[],"songs":[]}"""
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            start()
        }
        val endpoint = server.url("/").toString()
        container = AppContainer(context, "playlist-import-${UUID.randomUUID()}", useNativeNetease = true,
            neteaseEndpoints = NeteaseEndpoints(endpoint, endpoint, endpoint))
        container.ready.await(); container.sessionRestored.await()
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base)
        container.accountRepository.server = base
        container.updateSettings.setAutoCheck(false)
        container.neteaseSessions.ready.await()
        container.neteaseSessions.login(mapOf("MUSIC_U" to "local-fixture"), BoundProfile(42, "Import fixture"),
            container.neteaseSessions.snapshot())
    }

    @After fun finish() {
        if (::vm.isInitialized) compose.runOnIdle { vm.viewModelScope.cancel() }
        if (::container.isInitialized) container.close()
        if (::server.isInitialized) server.shutdown()
    }

    private fun show() {
        compose.setContent {
            val keyboardBottom = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect { imeBottom = keyboardBottom }
            val model = remember {
                val base = server.url("/").toString()
                val repository = io.github.currencortex.music.data.netease.playlistimport.NeteasePlaylistImportRepository(
                    io.github.currencortex.music.core.netease.NeteaseTransport(container.neteaseSessions, NeteaseEndpoints(base, base, base)), base)
                PlaylistImportViewModel(repository) { imported.incrementAndGet() }.also { vm = it }
            }
            LeiTheme(AppearanceSettings(blur = false)) { Scaffold {
                CompositionLocalProvider(LocalMusicBottomInset provides 88.dp) {
                PlaylistImportScreen(model, {}, { loginRequests.incrementAndGet() }, { openedPlaylist.set(it) })
                }
            } }
        }
        compose.waitForIdle()
    }

    private fun scrollTo(tag: String) {
        if (tag.startsWith("playlist_import_tab_") || tag in setOf("playlist_import_submit", "playlist_import_login")) return
        compose.onNodeWithTag("playlist_import_list").performScrollToNode(hasTestTag(tag))
    }

    private fun savePage(name: String) {
        compose.onNodeWithTag("playlist_import").captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private fun submitLink() {
        scrollTo("playlist_import_link_0")
        compose.onNodeWithTag("playlist_import_link_0").performTextInput("https://y.qq.com/n/ryqq/playlist/7")
        scrollTo("playlist_import_submit")
        compose.onNodeWithTag("playlist_import_submit").performClick()
    }

    @Test fun missingLoginShowsLoginActionAndDoesNotSubmitAnyImportRequest() = runBlocking<Unit> {
        container.neteaseSessions.clear()
        show()
        scrollTo("playlist_import_login")
        compose.onNodeWithTag("playlist_import_login").assertIsDisplayed().performClick()
        assertEquals(1, loginRequests.get())
        compose.onNodeWithTag("playlist_import_submit").assertDoesNotExist()
        compose.runOnIdle { vm.link(0, "https://y.qq.com/n/ryqq/playlist/7"); vm.submit() }
        compose.waitForIdle()
        assertFalse(paths.any { it.contains("/playlist/import/") })
    }

    @Test fun linkFormCanAddAndRemoveRowsButNeverExceedsTen() {
        show()
        val actions = compose.onNodeWithTag("playlist_import_actions").getUnclippedBoundsInRoot()
        val page = compose.onNodeWithTag("playlist_import").getUnclippedBoundsInRoot()
        val tab = compose.onNodeWithTag("playlist_import_tab_LINKS").getUnclippedBoundsInRoot()
        val list = compose.onNodeWithTag("playlist_import_list").getUnclippedBoundsInRoot()
        assertTrue("Controls must stay compact", actions.bottom - actions.top <= 80.dp)
        assertTrue("Controls belong above the import tabs", actions.bottom <= tab.top + 1.dp)
        assertTrue("Content must extend behind the glass mini player", list.bottom >= page.bottom - 1.dp)
        savePage("playlist-import-links-redesign.png")
        compose.onNodeWithTag("playlist_import_tab_TEXT").performClick()
        scrollTo("playlist_import_text")
        compose.onNodeWithTag("playlist_import_text").performTextInput("歌曲 - 歌手")
        compose.runOnIdle { assertEquals("歌曲 - 歌手", vm.state.value.text) }
        savePage("playlist-import-text-redesign.png")
        compose.onNodeWithTag("playlist_import_tab_LOCAL").performClick()
        compose.waitUntil(5000) { imeBottom == 0 }
        savePage("playlist-import-local-redesign.png")
        compose.onNodeWithTag("playlist_import_tab_LINKS").performClick()
        repeat(9) {
            scrollTo("playlist_import_add_link")
            compose.onNodeWithTag("playlist_import_add_link").performClick()
        }
        compose.runOnIdle { assertEquals(10, vm.state.value.links.size); vm.addLink() }
        scrollTo("playlist_import_link_9")
        compose.onNodeWithTag("playlist_import_link_9").assertIsDisplayed()
        // The list deliberately draws behind the dock; scroll to the trailing clearance as a user would.
        compose.onNodeWithTag("playlist_import_list").performTouchInput { swipeUp() }
        val lastInput = compose.onNodeWithTag("playlist_import_link_9").getUnclippedBoundsInRoot()
        assertTrue("Last input can scroll clear of the mini player", lastInput.bottom <= page.bottom - 88.dp + 1.dp)
        compose.onNodeWithTag("playlist_import_add_link").assertDoesNotExist()
        scrollTo("playlist_import_link_9_remove")
        compose.onNodeWithTag("playlist_import_link_9_remove").performClick()
        compose.runOnIdle { assertEquals(9, vm.state.value.links.size) }
        scrollTo("playlist_import_add_link")
        compose.onNodeWithTag("playlist_import_add_link").assertIsDisplayed()
        assertFalse(paths.any { it.contains("/playlist/import/") })
    }

    @Test fun verifiedLinkCreatesTaskAndCompleteResultOpensItsPlaylist() {
        show(); submitLink()
        compose.waitUntil(10_000) { vm.state.value.result?.finished == true && !vm.state.value.busy }
        scrollTo("playlist_import_result_123")
        compose.onNodeWithTag("playlist_import_result_123").assertIsDisplayed().performClick()
        assertEquals(123L, openedPlaylist.get())
        assertEquals(1, imported.get())
        val routes = paths.filter { it.contains("/playlist/import/") }
        assertEquals(listOf("/eapi/playlist/import/url/check", "/eapi/playlist/import/name/task/create",
            "/eapi/playlist/import/task/status/v2"), routes)
        assertEquals(99L, vm.state.value.task?.id)
    }

    @Test fun emptyStatusKeepsPendingAndSwitchingAccountClearsTheOldTask() = runBlocking<Unit> {
        statusBody = """{"code":200,"data":{"taskProcess":"正在处理","tasks":[]}}"""
        show(); submitLink()
        compose.waitUntil(10_000) { vm.state.value.result != null }
        compose.runOnIdle {
            assertEquals(false, vm.state.value.result?.finished)
            assertEquals(false, vm.state.value.result?.successful)
            assertEquals(99L, vm.state.value.task?.id)
        }
        assertEquals(0, imported.get())
        compose.onNodeWithTag("playlist_import_result_123").assertDoesNotExist()
        container.neteaseSessions.login(mapOf("MUSIC_U" to "second-local-fixture"), BoundProfile(43, "Second fixture"),
            container.neteaseSessions.snapshot())
        compose.waitUntil(5_000) { vm.state.value.task == null && !vm.state.value.busy }
        compose.runOnIdle { assertNull(vm.state.value.result); assertNull(vm.state.value.message) }
        compose.onNodeWithTag("playlist_import_status").assertDoesNotExist()
    }

    @Test fun failedTaskPreservesTheServersExplanationWithoutShowingSuccess() {
        statusBody = """{"code":200,"data":{"tasks":[{"id":99,"playlistId":0,"status":"FAILED","msg":"该分享歌单未公开，请修改后重试"}]}}"""
        show(); submitLink()
        compose.waitUntil(10_000) { vm.state.value.result?.finished == true && !vm.state.value.busy }
        scrollTo("playlist_import_status")
        compose.onNodeWithTag("playlist_import_status").assertTextEquals("该分享歌单未公开，请修改后重试")
        assertEquals(false, vm.state.value.result?.successful)
        assertEquals(0, imported.get())
        compose.onNodeWithTag("playlist_import_result_123").assertDoesNotExist()
    }

    @Test fun selectedImageIsReadLocallyAndUploadsOnlyAfterExplicitSubmit() {
        val file = java.io.File(context.cacheDir, "diagnostics/import-fixture.png")
        file.parentFile!!.mkdirs()
        android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888).let { bitmap ->
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        try {
            show()
            scrollTo("playlist_import_tab_IMAGES")
            compose.onNodeWithTag("playlist_import_tab_IMAGES").performClick()
            compose.waitUntil(5000) { vm.state.value.targetsLoaded }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            compose.runOnIdle { vm.readImages(context, listOf(uri)) }
            compose.waitUntil(5000) { vm.state.value.images.size == 1 && !vm.state.value.imagesLoading }
            savePage("playlist-import-images-redesign.png")
            assertFalse(paths.any { it.contains("nos/token") || it.startsWith("/yyimgs/") || it.contains("/playlist/import/create") })
            scrollTo("playlist_import_target_123")
            compose.onNodeWithTag("playlist_import_target_123").performClick()
            scrollTo("playlist_import_submit")
            compose.onNodeWithTag("playlist_import_submit").performClick()
            compose.waitUntil(10_000) { vm.state.value.result?.finished == true && !vm.state.value.busy }
            assertEquals(true, vm.state.value.result?.successful)
            assertTrue(paths.contains("/eapi/nos/token/whalealloc"))
            assertTrue(paths.contains("/yyimgs/fixture.png"))
            assertTrue(paths.contains("/eapi/playlist/import/create"))
            assertTrue(vm.state.value.images.isEmpty())
        } finally { file.delete() }
    }
}
