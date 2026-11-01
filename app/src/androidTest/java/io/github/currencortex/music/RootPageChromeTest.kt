package io.github.currencortex.music

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Full app roots, isolated data and no playback or community writes. */
class RootPageChromeTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    @Before fun setup() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName.endsWith(".verification"))
        server = MockWebServer().apply { dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when(request.requestUrl!!.encodedPath) {
                "/cm/auth/me" -> json("""{"id":7,"nickname":"听友","bio":"记录喜欢的音乐","stat":{"likes":127,"playlists":12,"playDays":236,"listenMs":91800000}}""")
                "/cm/daily" -> json("""{"daily":[],"forYou":[],"artists":[]}""")
                "/cm/playlists" -> json("""{"playlists":[]}""")
                "/cm/plays/recent" -> json("""{"songs":[${(1..10).joinToString(",") { """{"id":$it,"name":"喜欢的旋律 $it"}""" }}]}""")
                "/cm/decorations/scales" -> json("""{"scales":{}}""")
                "/cm/announcements" -> json("""{"items":[]}""")
                "/cm/users/square" -> json("""{"users":[],"stats":{"users":12,"listening":2}}""")
                "/cm/ncm/style/list" -> json("""{"data":[${(1..12).joinToString(",") { """{"tagId":$it,"tagName":"曲风 $it"}""" }}]}""")
                else -> MockResponse().setResponseCode(404)
            }
        }; start() }
        container = AppContainer(app, "root-chrome-${UUID.randomUUID()}", useNativeNetease = false)
        container.ready.await(); container.sessionRestored.await()
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.accountRepository.save("isolated-root-chrome", UserDto(7, nickname = "听友"))
        container.updateSettings.setAutoCheck(false)
        container.desktopLyrics.markRecommendationSeen()
    }
    @After fun close() { container.close(); server.shutdown() }
    private fun awaitTag(tag: String) = compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    private fun verify(dark: Boolean, scale: Float) {
        runBlocking { container.settings.edit { it.copy(themeMode = if(dark)ThemeMode.DARK else ThemeMode.LIGHT,
            customTextScale = true, textScale = scale) } }
        compose.setContent { CurrentMusicApp(container) }
        awaitTag("tab_0")
        val theme = if(dark)"dark-large"else "light"
        for((tab, bar, list) in listOf(Triple(0,"home_root_toolbar","music_home"),
            Triple(1,"discover_root_toolbar","discover_music"), Triple(2,"me_root_toolbar","profile_screen"))) {
            compose.onNodeWithTag("tab_$tab").performClick()
            awaitTag(bar); compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
            val header = compose.onNodeWithTag(bar).fetchSemanticsNode().boundsInRoot
            assertEquals("Every main header owns the status area",compose.onNodeWithTag("music_window")
                .fetchSemanticsNode().boundsInRoot.top,header.top,1f)
            if(tab==1) {
                awaitTag("style_catalog_expand")
                compose.onNodeWithTag("style_catalog_expand").performScrollTo().performClick()
                compose.onNodeWithTag("discover_tabs_0").assertIsSelected()
            }
            capture("root-$tab-$theme.png")
            val body = compose.onNodeWithTag(list)
            val before = body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
            if (tab == 1) body.performTouchInput { swipeUp() }
            else body.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f,260*density) }
            compose.waitForIdle()
            assertTrue("$list must scroll",body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()>before)
            assertEquals("$bar must not follow the list",header,compose.onNodeWithTag(bar).fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithTag(bar).assertIsDisplayed()
            if(tab==0)compose.onNodeWithTag("home_profile_avatar").assertIsDisplayed()
            if (tab == 1) {
                compose.onNodeWithTag("discover_tabs_0").assertIsNotDisplayed()
                capture("discover-tabs-scrolled-$theme.png")
                body.performScrollToIndex(0)
                body.performTouchInput {
                    swipe(androidx.compose.ui.geometry.Offset(centerX, height * .35f),
                        androidx.compose.ui.geometry.Offset(centerX, height * .8f), 400)
                }
                compose.onNodeWithTag("discover_tabs_0").assertIsDisplayed().assertIsSelected()
            }
            capture("root-$tab-$theme-scrolled.png")
        }
        assertFalse(container.playerController.state.value.playing)
    }
    @Test fun allMainPagesKeepTheHeaderFixedWhileTheirContentScrolls()=verify(false,1f)
    @Test fun darkLargeTextKeepsRootControlsAndSectionsReadable()=verify(true,1.4f)
}
