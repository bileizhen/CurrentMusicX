package io.github.currencortex.music

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.netease.NeteaseEndpoints
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Isolated account fixtures; never signs out or changes the real phone's accounts. */
class NeteaseLoginPrerequisiteCapabilitiesTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private val paths = CopyOnWriteArrayList<String>()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    @Before fun prepare() = runBlocking<Unit> {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    paths += path
                    val body = when {
                        path.endsWith("/qrcode/unikey") -> """{"code":200,"unikey":"fixture-key"}"""
                        path.endsWith("/qrcode/client/login") -> """{"code":801}"""
                        path == "/cm/ncmbind" -> """{"bound":false}"""
                        path == "/cm/auth/me" -> """{"id":13,"username":"Fixture","nickname":"Fixture"}"""
                        else -> """{"code":200,"items":[],"data":[],"songs":[]}"""
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            start()
        }
        val endpoint = server.url("/").toString()
        container = AppContainer(context, "login-prerequisite-${UUID.randomUUID()}", useNativeNetease = true,
            neteaseEndpoints = NeteaseEndpoints(endpoint, endpoint, endpoint))
        container.ready.await(); container.sessionRestored.await()
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.updateSettings.setAutoCheck(false)
        container.desktopLyrics.markRecommendationSeen()
        container.settings.edit { AppearanceSettings(blur = false) }
    }
    @After fun finish() {
        if (::container.isInitialized) container.close()
        if (::server.isInitialized) server.shutdown()
    }
    private fun awaitTag(tag: String) = compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun click(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
    private fun openBinding() {
        compose.setContent { CurrentMusicApp(container) }
        awaitTag("tab_2"); click("tab_2"); awaitTag("profile_screen")
        compose.onNodeWithTag("profile_screen").performScrollToNode(hasTestTag("open_binding"))
        click("open_binding"); awaitTag("binding_screen")
    }
    private fun save(name: String) {
        compose.onNodeWithTag("music_window").captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.externalCacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun anonymousNeteaseEntryShowsPrerequisiteAndOpensExistingRegistration(): Unit = runBlocking {
        openBinding(); awaitTag("binding_currentmusic_required")
        compose.onNodeWithTag("binding_login_card").assertDoesNotExist()
        save("netease-login-prerequisite.png")
        click("binding_currentmusic_login"); awaitTag("login_card")
        click("login_switch_mode"); awaitTag("register_card")
        compose.onNodeWithTag("register_contact").assertExists()
        assertFalse(paths.any { it.contains("qrcode") || it.contains("captcha/sent") })
    }
    @Test fun signedInCurrentMusicCanGenerateQrAndLogoutClosesNeteaseLogin(): Unit = runBlocking {
        container.accountRepository.save("fixture-project-token", UserDto(13, "Fixture", "Fixture"))
        openBinding()
        compose.waitUntil(20_000) { paths.any { it.endsWith("/qrcode/unikey") } }
        compose.onNodeWithTag("binding_currentmusic_required").assertDoesNotExist()
        compose.onNodeWithTag("binding_login_card").assertExists()
        container.accountRepository.clear()
        awaitTag("binding_currentmusic_required")
        compose.onNodeWithTag("binding_login_card").assertDoesNotExist()
        val before = paths.count { it.contains("qrcode") }
        compose.mainClock.advanceTimeBy(3_000); compose.waitForIdle()
        assertEquals(before, paths.count { it.contains("qrcode") })
        assertFalse(container.neteaseSessions.state.value.loggedIn)
    }
}
