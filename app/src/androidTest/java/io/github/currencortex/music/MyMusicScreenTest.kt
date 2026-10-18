package io.github.currencortex.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.binding.BoundProfile
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.feature.profile.MyMusicScreen
import io.github.currencortex.music.feature.profile.ProfileViewModel
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.component.AvatarFlightState
import io.github.currencortex.music.ui.component.AvatarFlightOrigin
import io.github.currencortex.music.ui.component.AvatarFlightOverlay
import io.github.currencortex.music.ui.component.LocalAvatarFlight
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Isolated accounts and silent callbacks: UI tests never edit the real profile or start playback. */
class MyMusicScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var vm: ProfileViewModel
    private lateinit var server: MockWebServer
    private val store = ViewModelStore()
    private val routes = mutableListOf<String>()
    private var played: Pair<List<Long>, Int>? = null
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()

    @Before fun prepare(): Unit = runBlocking {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when (request.requestUrl!!.encodedPath) {
                        "/cm/auth/me" -> """{"id":7,"nickname":"我的音乐测试账户","stat":{"playDays":12,"listenMs":7200000}}"""
                        "/cm/ncmbind" -> """{"bound":false}"""
                        "/cm/playlists" -> """{"playlists":[]}"""
                        "/cm/plays/recent" -> """{"songs":[{"ncm_id":11,"name":"First song","artists":"Artist"},{"ncm_id":12,"name":"Second song","artists":"Artist"}]}"""
                        "/cm/decorations/scales" -> """{"scales":{}}"""
                        else -> "{}"
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            start()
        }
        val nativeEndpoint = server.url("/").toString()
        container = AppContainer(context, "my-ui-${UUID.randomUUID()}", useNativeNetease = true,
            neteaseEndpoints = io.github.currencortex.music.core.netease.NeteaseEndpoints(nativeEndpoint, nativeEndpoint, nativeEndpoint))
        container.ready.await(); container.sessionRestored.await()
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.updateSettings.setAutoCheck(false)
        vm = ProfileViewModel(container)
        store.put("profile", vm)
    }
    @After fun finish() { compose.mainClock.autoAdvance = true; store.clear(); container.close(); server.shutdown() }

    private fun show(dark: Boolean = false, fontScale: Float = 1f, flight: AvatarFlightState? = null) {
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, blur = false)) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalMusicBottomInset provides 130.dp,
                LocalAvatarFlight provides flight) {
                Box(Modifier.fillMaxSize().background(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background)) {
                MyMusicScreen(vm, { routes += it }, { songs, index -> played = songs.map { it.id } to index })
                flight?.let { AvatarFlightOverlay(it) }
                }
            }
        } }
    }
    private fun save(name: String) {
        compose.onNodeWithTag("my_music_list").performScrollToIndex(0)
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.externalCacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun guestKeepsMusicLandingAndEachAccountHasItsOwnLoginRoute() {
        show()
        compose.onNodeWithTag("profile_screen").assertIsDisplayed()
        compose.onNodeWithTag("login_card").assertDoesNotExist()
        compose.onNodeWithTag("open_likes").performClick()
        assertEquals("user/binding", routes.last())
        compose.onNodeWithTag("open_recent").performClick()
        assertEquals("lib/recent", routes.last())
        compose.onNodeWithTag("me_listen_together").performClick()
        assertEquals("room/list", routes.last())
        compose.onNodeWithTag("open_currentmusic_login").performScrollTo().performClick()
        assertEquals("user/login", routes.last())
        save("my-guest.png")
    }

    @Test fun nativeAccountAndExpiredLoginStayDistinctWithLargeTypeAndDarkAppearance(): Unit = runBlocking {
        container.neteaseSessions.login(mapOf("MUSIC_U" to "fixture-native-cookie"),
            BoundProfile(42, "网易云音乐与很长很长的中文昵称测试"), container.neteaseSessions.snapshot())
        show(dark = true, fontScale = 1.5f)
        compose.onNodeWithTag("open_likes").performScrollTo().performClick()
        assertEquals("lib/likes", routes.last())
        val likes = compose.onNodeWithTag("open_likes").fetchSemanticsNode().boundsInRoot
        val playlists = compose.onNodeWithTag("open_playlists").fetchSemanticsNode().boundsInRoot
        assertTrue("Large text switches music actions to vertical rows", playlists.top >= likes.bottom)
        container.neteaseSessions.markStale(container.neteaseSessions.snapshot())
        compose.onNodeWithTag("open_likes").performClick()
        assertEquals("user/binding", routes.last())
        compose.onNodeWithTag("edit_profile").assertDoesNotExist()
        compose.onNodeWithTag("open_binding").performScrollTo().performClick()
        assertEquals("user/binding", routes.last())
        save("my-native-large-dark.png")
    }

    @Test fun avatarFlightFindsTheNewIdentityAnchorAndCanReturnWithoutMovingTheLayout() {
        val flight = AvatarFlightState()
        show(flight = flight)
        compose.waitForIdle()
        val anchor = requireNotNull(flight.profileLanding)
        val center = anchor.center + androidx.compose.ui.geometry.Offset(500f, -100f)
        val source = androidx.compose.ui.geometry.Rect(center - androidx.compose.ui.geometry.Offset(40f, 40f),
            androidx.compose.ui.geometry.Size(80f, 80f))
        flight.homeLanding = source
        flight.homeOrigin = { AvatarFlightOrigin(null, source, null, 1.0) }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { flight.beginFromHome() }
        compose.mainClock.advanceTimeBy(180)
        compose.onRoot().captureToImage()
        compose.onNodeWithTag("home_avatar_transition").assertExists()
        val moving = requireNotNull(flight.renderedBounds)
        assertTrue(moving.center.x > anchor.center.x && moving.center.x < source.center.x)
        assertTrue(moving.width > source.width && moving.width < anchor.width)
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("home_avatar_transition").assertDoesNotExist()
        val placed = compose.onNodeWithTag("my_profile_avatar").fetchSemanticsNode().boundsInRoot
        assertEquals(anchor.center.x, placed.center.x, 1f); assertEquals(anchor.center.y, placed.center.y, 1f)
        compose.runOnUiThread { flight.beginFromProfile() }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("home_avatar_transition").assertExists()
        compose.mainClock.advanceTimeBy(800)
        assertFalse(flight.active)
    }

    @Test fun projectProfileRetainsEditingRecordsAndPlaybackAndDropsOldDataOnLogout(): Unit = runBlocking {
        container.accountRepository.save("isolated-profile-token", UserDto(7, nickname = "我的音乐测试账户"))
        show()
        compose.waitUntil(5000) { vm.state.value.profile?.user?.id == 7L }
        compose.onNodeWithTag("edit_profile").performClick()
        assertEquals("edit", vm.dialog.value)
        vm.dialog.value = null
        compose.onNodeWithTag("me_identity_action").performClick()
        assertEquals("user/profile/7", routes.last())
        compose.onNodeWithTag("open_security").performScrollTo().performClick()
        assertEquals("user/security", routes.last())
        compose.onNodeWithTag("my_music_list").performScrollToNode(hasTestTag("me_recent_12"))
        compose.onNodeWithTag("me_recent_12").performClick()
        assertEquals(listOf(11L, 12L) to 1, played)
        save("my-project.png")
        container.accountRepository.clear()
        compose.onNodeWithTag("edit_profile").assertDoesNotExist()
        compose.onNodeWithTag("me_recent_12").assertDoesNotExist()
        assertTrue(container.playbackQueue.state.value.songs.isEmpty())
    }
}
