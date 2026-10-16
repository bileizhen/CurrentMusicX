package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsRecommendation
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.util.UUID

class DesktopStartupRecommendationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    @Before fun setup() = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(),
            "desktop-startup-${UUID.randomUUID()}", useNativeNetease = false)
        container.desktopLyrics.ready.await()
    }
    @After fun close() { container.close() }
    private fun open() { compose.setContent { LeiTheme(AppearanceSettings()) {
        top.yukonga.miuix.kmp.basic.Scaffold { DesktopLyricsRecommendation(container, eligible = true) }
    } } }
    @Test fun firstOfferHasShortcutAndSkippingDoesNotEnableOverlayOrRepeat() {
        open()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("desktop_recommendation_enable").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("desktop_recommendation_enable").assertIsDisplayed()
        compose.onNodeWithTag("desktop_recommendation_skip").performClick()
        compose.waitUntil(5000) { runBlocking { !container.desktopLyrics.shouldRecommend() } }
        compose.onNodeWithTag("desktop_recommendation").assertDoesNotExist()
        assertFalse(container.desktopLyrics.state.value.enabled)
    }
    @Test fun alreadyEnabledUsersAreNotPromptedToEnableAgain() = runBlocking {
        container.desktopLyrics.edit { it.copy(enabled = true) }
        open()
        compose.waitForIdle()
        compose.onNodeWithTag("desktop_recommendation").assertDoesNotExist()
        assertFalse(container.desktopLyrics.shouldRecommend())
    }
    @Test fun shortcutWithPermissionConnectsAndEnablesWithoutPlayingMusic() {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        assumeTrue(android.provider.Settings.canDrawOverlays(context))
        open()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("desktop_recommendation_enable").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("desktop_recommendation_enable").performClick()
        compose.waitUntil(15000) { container.desktopLyrics.state.value.enabled }
        compose.onNodeWithTag("desktop_recommendation").assertDoesNotExist()
        assertFalse(container.playerController.state.value.playing)
        assertFalse(runBlocking { container.desktopLyrics.shouldRecommend() })
    }
}
