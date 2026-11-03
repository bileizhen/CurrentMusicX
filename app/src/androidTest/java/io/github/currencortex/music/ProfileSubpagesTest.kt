package io.github.currencortex.music

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.ui.component.PageContainerGeometry
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Exercise the actual My -> child -> My flow without touching the user's music or accounts. */
class ProfileSubpagesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Before fun setup() = runBlocking {
        check(context.packageName.endsWith(".verification"))
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/rest/ping.view", "/rest/getUser.view" -> json("""{"subsonic-response":{"status":"ok","version":"1.16.1","user":{"username":"fixture"}}}""")
                    "/rest/getAlbumList2.view" -> json("""{"subsonic-response":{"status":"ok","albumList2":{"album":[]}}}""")
                    "/emby/Users/AuthenticateByName" -> json("""{"User":{"Id":"fixture-user"},"AccessToken":"fixture-token"}""")
                    "/emby/Users/fixture-user/Items" -> json("""{"Items":[],"TotalRecordCount":0}""")
                    "/cm/auth/me" -> json("""{"id":7,"nickname":"页面测试","stat":{"likes":1,"playlists":1}}""")
                    "/cm/playlists" -> json("""{"playlists":[{"id":71,"name":"测试歌单","count":1}]}""")
                    "/cm/plays/recent", "/cm/likes/mine" -> json("""{"songs":[{"ncm_id":101,"name":"星夜","artists":"测试歌手","duration":180000}]}""")
                    "/cm/ncmbind" -> json("""{"bound":true,"profile":{"uid":42,"nickname":"网易云测试账号"}}""")
                    "/cm/decorations" -> json("""{"decorations":[{"id":"key-star","name":"星光"}],"current":"","unlocked":true}""")
                    else -> json("""{"code":200,"data":[],"items":[],"songs":[],"scales":{},"playlists":[]}""")
                }
            }
            start()
        }
        container = AppContainer(context, "profile-subpages-${UUID.randomUUID()}", useNativeNetease = false)
        container.ready.await(); container.sessionRestored.await()
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.musicSettings.setNeteaseMainLibrary(false)
        container.accountRepository.save("isolated-profile-subpages", UserDto(7, nickname = "页面测试"))
        container.navidrome.connect(server.url("/").toString(), "fixture", "fixture")
        container.emby.connect(server.url("/").toString(), "fixture", "fixture")
        container.updateSettings.setAutoCheck(false)
        container.desktopLyrics.markRecommendationSeen()
        container.neteaseTaskPreferences.enable(true)
        val paused = Song(902, "保持暂停的歌曲")
        container.playbackQueue.replace(listOf(paused), 0)
        container.playerController.state.value = PlayerState(song = paused)
        container.settings.edit { AppearanceSettings(themeMode = ThemeMode.LIGHT) }
    }
    @After fun close() {
        if (::container.isInitialized) container.close()
        if (::server.isInitialized) server.shutdown()
    }
    private fun await(tag: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun click(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
    private fun advance(ms: Long = 850) {
        compose.waitForIdle(); compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(ms); compose.waitForIdle()
    }
    private fun start() {
        compose.setContent { CurrentMusicApp(container) }
        await("tab_2"); click("tab_2"); await("profile_screen")
        compose.mainClock.autoAdvance = false; advance()
    }
    private fun scroll(tag: String) {
        compose.mainClock.autoAdvance = true
        val list = compose.onNodeWithTag("profile_screen")
        list.performScrollToNode(hasTestTag(tag))
        val viewport = list.fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, row.center.y - viewport.height * .48f) }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
    }
    private fun capture(name: String) {
        val bitmap = compose.onNodeWithTag("music_window").captureToImage().asAndroidBitmap()
        File(context.externalCacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun visit(entry: String, route: String, header: String, dark: Boolean = false) {
        scroll(entry)
        val row = compose.onNodeWithTag(entry).fetchSemanticsNode().boundsInRoot
        click(entry); advance(160)
        val pane = compose.onNodeWithTag("page_container_$route")
        assertTrue(pane.fetchSemanticsNode().config[PageContainerGeometry].progress in .01f.. .99f)
        advance(); await(header)
        if (route == "lib/likes" || route == "lib/recent") await("playlist_song_101")
        compose.onNodeWithTag(header).assertIsDisplayed()
        // The status inset belongs to the floating toolbar, never to a second route wrapper.
        val toolbar = compose.onNodeWithTag(header).fetchSemanticsNode().boundsInRoot
        assertTrue(toolbar.top < context.resources.displayMetrics.density * 60)
        capture("my-child-${route.replace('/', '-')}-${if (dark) "dark-large" else "light"}.png")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        advance(160)
        assertTrue(pane.fetchSemanticsNode().config[PageContainerGeometry].progress in .01f.. .99f)
        advance()
        pane.assertDoesNotExist()
        assertEquals(row.top, compose.onNodeWithTag(entry).fetchSemanticsNode().boundsInRoot.top, 1f)
        assertEquals(listOf(902L), container.playbackQueue.state.value.songs.map { it.id })
        assertFalse(container.playerController.state.value.playing)
    }
    @Test fun everyMyDestinationUsesTheSharedChromeAndReturnsToItsSourceWithoutChangingPlayback() {
        start()
        listOf(
            Triple("open_likes", "lib/likes", "playlist_top_bar"),
            Triple("open_playlists", "lib/playlists", "playlist_index_page_toolbar"),
            Triple("open_recent", "lib/recent", "playlist_top_bar"),
            Triple("open_local_music", "lib/local", "playlist_top_bar"),
            Triple("open_binding", "user/binding", "binding_page_toolbar"),
            Triple("open_netease_tasks", "user/netease-tasks", "netease_tasks_toolbar"),
            Triple("profile_playlist_import", "user/playlist-import", "playlist_import_toolbar"),
            Triple("profile_navidrome", "navidrome", "navidrome_screen_toolbar"),
            Triple("profile_emby", "emby", "emby_screen_toolbar"),
            Triple("open_decorations", "user/decorations", "decoration_screen_toolbar"),
            Triple("open_security", "user/security", "security_page_toolbar")
        ).forEach { (entry, route, header) -> visit(entry, route, header) }
    }
    @Test fun embyUsesTheHomeDockAndGroupedToolbarSearch() {
        start()
        scroll("profile_emby"); click("profile_emby"); advance()
        await("emby_screen_toolbar"); await("music_dock")
        val statusInset = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
        assertTrue(compose.onNodeWithTag("emby_screen_toolbar").fetchSemanticsNode().boundsInRoot.top >= statusInset)
        compose.onNodeWithTag("emby_query").assertDoesNotExist()
        for (label in listOf("歌曲", "艺术家", "歌单", "我喜欢"))
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("music_dock"))).assertIsDisplayed()
        compose.onNode(hasText("专辑") and hasAnyAncestor(hasTestTag("music_dock"))).assertDoesNotExist()
        compose.onNodeWithTag("tab_0").assertIsSelected()
        val mini = compose.onNodeWithTag("mini_glass_surface").fetchSemanticsNode().boundsInRoot
        val tabs = compose.onNodeWithTag("tab_0").fetchSemanticsNode().boundsInRoot
        assertTrue("Mini player must remain above the four library tabs", mini.center.y < tabs.center.y)
        click("tab_1"); advance()
        compose.onNodeWithTag("tab_1").assertIsSelected()
        capture("emby-home-dock.png")
        click("emby_search"); advance()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("emby_query").performClick()
        compose.onNodeWithTag("emby_query").performTextInput("fixture")
        compose.onNodeWithTag("emby_query").assertTextEquals("fixture")
        compose.onNodeWithTag("emby_query").performImeAction(); advance()
        compose.onNodeWithTag("emby_query").assertTextEquals("fixture")
        click("emby_query_cancel"); advance()
        compose.onNodeWithTag("emby_query").assertDoesNotExist()
        compose.onNodeWithTag("tab_1").assertIsSelected()
        click("emby_account"); advance()
        compose.onNodeWithTag("tab_1").assertDoesNotExist()
        click("emby_account"); advance()
        compose.onNodeWithTag("tab_1").assertIsSelected()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; advance()
        await("profile_screen")
        compose.onNodeWithTag("tab_2").assertIsSelected()
        assertEquals(listOf(902L), container.playbackQueue.state.value.songs.map { it.id })
        assertFalse(container.playerController.state.value.playing)
    }
    @Test fun accountPagesStayUsableInDarkModeWithLargeTextAndNestedAddAccountReturns() {
        runBlocking { container.settings.edit { it.copy(themeMode = ThemeMode.DARK, customTextScale = true, textScale = 1.35f) } }
        start()
        listOf(
            Triple("open_binding", "user/binding", "binding_page_toolbar"),
            Triple("profile_playlist_import", "user/playlist-import", "playlist_import_toolbar"),
            Triple("open_decorations", "user/decorations", "decoration_screen_toolbar"),
            Triple("open_security", "user/security", "security_page_toolbar")
        ).forEach { (entry, route, header) -> visit(entry, route, header, dark = true) }
        scroll("open_security"); click("open_security"); advance()
        click("add_account"); advance()
        compose.onNodeWithTag("login_page_toolbar").assertIsDisplayed()
        compose.onNodeWithTag("login_username").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; advance()
        compose.onNodeWithTag("security_page_toolbar").assertIsDisplayed()
    }
}
