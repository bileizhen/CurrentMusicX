package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.core.netease.NeteaseEndpoints
import io.github.currencortex.music.feature.auth.AuthViewModel
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.profile.*
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.ui.util.viewModelFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class ProfilePageLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private val store = ViewModelStore()
    private val appearance = mutableStateOf(AppearanceSettings())
    private var destination = ""
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Before fun setup() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName.endsWith(".verification"))
        server = MockWebServer().apply { dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/cm/auth/me" -> json("""{"id":7,"nickname":"音乐爱好者","bio":"把喜欢的旋律留在这里","isAdmin":true,"title":"创作者","stat":{"likes":127,"favs":38,"playlists":12,"playDays":236,"listenMs":91800000}}""")
                "/cm/users/9/profile" -> json("""{"user":{"id":9,"nickname":"公开主页","bio":"分享听过的音乐"},"stat":{"likes":42,"playDays":18,"listenMs":7200000},"playlists":[{"id":73,"name":"夜晚的歌","count":16}]}""")
                "/cm/decorations/scales" -> json("""{"scales":{}}""")
                "/cm/playlists" -> json("""{"playlists":[{"id":71,"name":"收藏的旋律","count":28}]}""")
                "/cm/plays/recent" -> json("""{"songs":[]}""")
                else -> MockResponse().setResponseCode(404)
            }
        }; start() }
        container = AppContainer(app, "profile-layout-${UUID.randomUUID()}", useNativeNetease = false)
        container.ready.await(); container.sessionRestored.await()
        val url = server.url("/cm/").toString()
        container.musicSettings.setServer(url); container.accountRepository.server = url
        container.accountRepository.save("isolated-profile-layout", UserDto(7, nickname = "音乐爱好者"))
        container.updateSettings.setAutoCheck(false)
    }
    @After fun close() {
        store.clear()
        container.close()
        server.shutdown()
    }
    private fun show(userId: Long? = null): ProfileViewModel {
        val vm = ViewModelProvider(store, viewModelFactory { ProfileViewModel(container, userId) })[ProfileViewModel::class.java]
        compose.setContent { LeiTheme(appearance.value) { top.yukonga.miuix.kmp.basic.Scaffold {
            ProfileScreen(vm, { destination = it }, { _, _ -> }, onBack = if (userId != null) ({}) else null,
                onSettings = if (userId == null) ({ destination = "settings" }) else null)
        } } }
        compose.waitUntil(10000) { vm.state.value.profile != null }
        compose.waitForIdle()
        return vm
    }
    private fun scroll(tag: String) {
        val list = compose.onNodeWithTag("profile_screen")
        list.performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
        val viewport = list.fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val header = compose.onAllNodesWithTag("me_root_toolbar").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.bottom ?: viewport.top
        val margin = 16 * InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val delta = if (target.top < header + margin) target.top - header - margin
            else (target.center.y - (viewport.top + viewport.height * .45f)).coerceAtLeast(0f)
        if (delta != 0f) list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, delta) }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun ownPageKeepsStatisticsEditingAndEveryDestinationInTheGroupedLists() {
        val vm = show()
        compose.onNodeWithTag("profile_stats").assertIsDisplayed()
        compose.onNodeWithText("236 天").assertIsDisplayed()
        compose.onNodeWithText("25 小时 30 分").assertIsDisplayed()
        capture("profile-redesign-light.png")
        compose.onNodeWithTag("edit_profile").performClick()
        compose.runOnIdle { assertEquals("edit", vm.dialog.value); vm.dialog.value = null }
        compose.onNodeWithTag("me_announcements").performClick()
        assertEquals("user/announcements", destination)
        compose.onNodeWithTag("me_settings").performClick()
        assertEquals("settings", destination)
        for ((tag, route) in listOf("open_likes" to "lib/likes", "open_playlists" to "lib/playlists",
            "open_recent" to "lib/recent", "open_local_music" to "lib/local", "open_binding" to "user/binding",
            "profile_playlist_import" to "user/playlist-import", "profile_navidrome" to "navidrome",
            "open_decorations" to "user/decorations", "open_security" to "user/security", "profile_playlist_71" to "lib/playlist/71")) {
            scroll(tag)
            compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
            assertEquals(route, destination)
        }
        scroll("open_binding")
        capture("profile-redesign-services.png")
        compose.runOnIdle { appearance.value = AppearanceSettings(themeMode = ThemeMode.DARK,
            customTextScale = true, textScale = 1.4f) }
        compose.onNodeWithTag("profile_screen").performScrollToIndex(0)
        compose.waitForIdle()
        capture("profile-redesign-dark-large.png")
        scroll("open_security")
        compose.onNodeWithTag("open_security").assertIsDisplayed().performClick()
        assertEquals("user/security", destination)
    }

    @Test fun nativeAccountAndGuestKeepTheirDistinctIdentityAndSharedDestinations() {
        container.close()
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val base = server.url("/").toString()
        container = AppContainer(app, "native-profile-layout-${UUID.randomUUID()}", useNativeNetease = true,
            neteaseEndpoints = NeteaseEndpoints(base, base, base))
        runBlocking {
            container.ready.await(); container.sessionRestored.await()
            container.musicSettings.setServer(server.url("/cm/").toString())
            container.accountRepository.server = server.url("/cm/").toString()
            container.neteaseSessions.login(mapOf("MUSIC_U" to "synthetic-profile-cookie"),
                BoundProfile(42, "本机听友"), container.neteaseSessions.snapshot())
            container.neteaseTaskPreferences.enable(true)
        }
        val provider = ViewModelProvider(store, viewModelFactory { ProfileViewModel(container) })
        val vm = provider[ProfileViewModel::class.java]
        val auth = ViewModelProvider(store, viewModelFactory { AuthViewModel(container) })[AuthViewModel::class.java]
        compose.setContent { LeiTheme(appearance.value) { top.yukonga.miuix.kmp.basic.Scaffold {
            MeScreen(vm, auth, { destination = it }, { destination = "settings" }, { _, _ -> })
        } } }
        compose.onNodeWithText("本机听友").assertIsDisplayed()
        capture("profile-redesign-native.png")
        for ((tag, route) in listOf("open_likes" to "lib/likes", "open_netease_tasks" to "user/netease-tasks",
            "profile_navidrome" to "navidrome", "open_currentmusic_login" to "user/login")) {
            scroll(tag)
            compose.onNodeWithTag(tag).performClick()
            assertEquals(route, destination)
        }
        assertNull(container.accountRepository.state.value.account)
        runBlocking { container.neteaseSessions.clear() }
        compose.onNodeWithTag("profile_screen").performScrollToIndex(0)
        compose.onNodeWithTag("my_profile_avatar").assertDoesNotExist()
        compose.onNodeWithTag("open_currentmusic_login").assertIsDisplayed().performClick()
        assertEquals("user/login", destination)
        capture("profile-redesign-guest.png")
    }

    @Test fun publicProfileHasReadOnlyIdentityAndPublicPlaylists() {
        show(9)
        compose.onNodeWithTag("edit_profile").assertDoesNotExist()
        compose.onNodeWithTag("open_security").assertDoesNotExist()
        compose.onNodeWithTag("open_binding").assertDoesNotExist()
        compose.onNodeWithTag("my_profile_avatar").performClick()
        compose.onNodeWithTag("avatar_preview").assertIsDisplayed()
        compose.onNodeWithTag("avatar_preview_change").assertDoesNotExist()
        compose.onNodeWithTag("avatar_preview_close").performClick()
        scroll("profile_playlist_73")
        compose.onNodeWithTag("profile_playlist_73").performClick()
        assertEquals("lib/playlist/73", destination)
        capture("profile-redesign-public.png")
    }
}
