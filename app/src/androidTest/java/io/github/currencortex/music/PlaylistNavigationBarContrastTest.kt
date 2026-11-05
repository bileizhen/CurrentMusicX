package io.github.currencortex.music

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets as ComposeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Injects insets into a Compose test view, without changing the device's navigation mode. */
@SdkSuppress(minSdkVersion = 29)
class PlaylistNavigationBarContrastTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var container: AppContainer
    private lateinit var server: MockWebServer
    private lateinit var artwork: File
    private var size by mutableStateOf(DpSize(960.dp, 420.dp))
    private var injectedInsets by mutableStateOf(navigationInsets(Insets.NONE, Insets.NONE))
    private var observedNavigation = Insets.NONE
    private var observedTappable = Insets.NONE

    @Before fun prepare(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        container = AppContainer(context, "playlist-contrast-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        container.desktopLyrics.markRecommendationSeen()
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { AppearanceSettings(themeMode = ThemeMode.LIGHT, blur = false) }
        artwork = File(context.cacheDir, "playlist-contrast-${UUID.randomUUID()}.png")
        InstrumentationRegistry.getInstrumentation().context.assets.open("release-preview.png").use { input ->
            artwork.outputStream().use { output -> input.copyTo(output) }
        }
        val cover = artwork.toURI().toString()
        val tracks = (1..80).joinToString(",") {
            """{"ncm_id":$it,"name":"Contrast track $it","artists":"Test artist","pic":"$cover","duration":180000}"""
        }
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when (request.requestUrl!!.encodedPath) {
                        "/cm/daily" -> """{"daily":[],"forYou":[]}"""
                        "/cm/playlists" -> """{"playlists":[{"id":3,"name":"Contrast playlist","cover":"$cover","source":"ncm","track_count":80}]}"""
                        "/cm/playlists/3" -> """{"id":3,"name":"Contrast playlist","cover":"$cover","source":"ncm","tracks":[$tracks]}"""
                        "/cm/ncmbind" -> """{"bound":false}"""
                        "/cm/plays/recent", "/cm/likes/mine" -> """{"songs":[]}"""
                        "/cm/songs/status" -> """{"liked":[],"faved":[]}"""
                        "/cm/room/active" -> """{"room":null}"""
                        else -> "{}"
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            start()
        }
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base)
        container.accountRepository.server = base
        container.accountRepository.save("isolated-contrast-token", UserDto(7, nickname = "Contrast fixture"))
        val paused = Song(99, "Paused contrast track", artists = "Test artist", cover = cover)
        container.playbackQueue.replace(listOf(paused), 0)
        container.playerController.state.value = PlayerState(song = paused)
    }

    @After fun finish() {
        if (::container.isInitialized) container.close()
        if (::server.isInitialized) server.shutdown()
        if (::artwork.isInitialized) artwork.delete()
    }

    private fun show(navigation: Insets, tappable: Insets = navigation) {
        injectedInsets = navigationInsets(navigation, tappable)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.WindowInsets(injectedInsets)) {
                    // ForcedSize wraps the context; retain the real activity for window side effects.
                    CompositionLocalProvider(LocalContext provides compose.activity) {
                        val density = LocalDensity.current
                        val direction = LocalLayoutDirection.current
                        fun ComposeWindowInsets.snapshot() = Insets.of(getLeft(density, direction), getTop(density),
                            getRight(density, direction), getBottom(density))
                        val nav = ComposeWindowInsets.navigationBars.snapshot()
                        val taps = ComposeWindowInsets.tappableElement.snapshot()
                        SideEffect { observedNavigation = nav; observedTappable = taps }
                        CurrentMusicApp(container)
                    }
                }
            }
        }
        await("music_home")
        assertInjectedInsets(navigation, tappable)
    }

    private fun await(tag: String) {
        compose.waitUntil(15000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun assertInjectedInsets(navigation: Insets, tappable: Insets) {
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("Navigation insets must reach the real app composition", navigation, observedNavigation)
            assertEquals("The fixture must distinguish three-button and gesture areas", tappable, observedTappable)
        }
    }

    private fun enterPlaylist() {
        compose.onNodeWithTag("music_home").performScrollToNode(hasTestTag("open_playlists"))
        compose.onNodeWithTag("open_playlists").performClick()
        await("playlist_card_3")
        compose.onNodeWithTag("playlist_card_3").performClick()
        await("playlist_song_1")
    }

    private fun assertWindow(contrast: Boolean, lightBackground: Boolean) {
        compose.waitForIdle()
        compose.runOnIdle {
            val window = compose.activity.window
            assertEquals("Window contrast protection must follow the active page", contrast, window.isNavigationBarContrastEnforced)
            assertEquals("Navigation icon appearance must remain legible",
                lightBackground, WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars)
        }
    }

    private fun assertFullWidthCover() {
        val window = compose.onNodeWithTag("music_window").fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("playlist_artwork_background").fetchSemanticsNode().boundsInRoot
        assertEquals("Protection must not inset the cover's left edge", window.left, cover.left, 1f)
        assertEquals("Protection must not inset the cover's right edge", window.right, cover.right, 1f)
    }

    private fun returnHome() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        await("playlist_card_3")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        await("music_home")
    }

    @Test fun rightThreeButtonBarStaysProtectedWhilePlaylistScrollsAndClearsOnReturn() {
        show(Insets.of(0, 0, 48, 0))
        assertWindow(contrast = false, lightBackground = true)
        enterPlaylist()
        assertWindow(contrast = true, lightBackground = true)
        assertFullWidthCover()
        compose.onNodeWithTag("playlist_tracks").performScrollToNode(hasTestTag("playlist_song_60"))
        compose.onNodeWithTag("playlist_song_60").assertIsDisplayed()
        assertWindow(contrast = true, lightBackground = true)
        compose.onNodeWithTag("playlist_more").performClick()
        compose.onNodeWithTag("playlist_record_shelf_menu").performClick()
        await("record_shelf_canvas")
        assertWindow(contrast = false, lightBackground = false)
        compose.onNodeWithTag("record_shelf_close").performClick()
        await("playlist_tracks")
        assertWindow(contrast = true, lightBackground = true)
        returnHome()
        assertWindow(contrast = false, lightBackground = true)
        compose.onNodeWithTag("mini_cover").performClick()
        await("player_screen")
        assertWindow(contrast = false, lightBackground = false)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        await("music_home")
        assertWindow(contrast = false, lightBackground = true)
    }

    @Test fun leftThreeButtonBarWorksInDarkThemeAndClearsWhenNavigationMovesToBottom() {
        runBlocking { container.settings.edit { it.copy(themeMode = ThemeMode.DARK) } }
        show(Insets.of(48, 0, 0, 0))
        assertWindow(contrast = false, lightBackground = false)
        enterPlaylist()
        assertWindow(contrast = true, lightBackground = false)
        assertFullWidthCover()
        val gesture = Insets.of(0, 0, 0, 24)
        compose.runOnIdle { injectedInsets = navigationInsets(gesture, Insets.NONE) }
        assertInjectedInsets(gesture, Insets.NONE)
        assertWindow(contrast = false, lightBackground = false)
        returnHome()
        assertWindow(contrast = false, lightBackground = false)
    }

    @Test fun portraitBottomButtonsAndGesturesDoNotEnableSideProtection() {
        size = DpSize(420.dp, 960.dp)
        show(Insets.of(0, 0, 0, 48))
        assertWindow(contrast = false, lightBackground = true)
        enterPlaylist()
        assertWindow(contrast = false, lightBackground = true)
        val gesture = Insets.of(0, 0, 0, 24)
        compose.runOnIdle { injectedInsets = navigationInsets(gesture, Insets.NONE) }
        assertInjectedInsets(gesture, Insets.NONE)
        assertWindow(contrast = false, lightBackground = true)
        returnHome()
        assertWindow(contrast = false, lightBackground = true)
    }

    private companion object {
        fun navigationInsets(navigation: Insets, tappable: Insets): WindowInsetsCompat = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), navigation)
            .setInsets(WindowInsetsCompat.Type.tappableElement(), tappable)
            .setInsets(WindowInsetsCompat.Type.systemGestures(), Insets.NONE)
            .setInsets(WindowInsetsCompat.Type.mandatorySystemGestures(), Insets.NONE)
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
            .setVisible(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars(), true)
            .setVisible(WindowInsetsCompat.Type.ime(), false)
            .build()
    }
}
