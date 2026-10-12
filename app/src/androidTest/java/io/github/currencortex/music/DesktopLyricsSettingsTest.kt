package io.github.currencortex.music

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.feature.lyrics.desktop.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import org.junit.*
import java.util.UUID

class DesktopLyricsSettingsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private val appearance = mutableStateOf(AppearanceSettings())
    private val showPage = mutableStateOf(true)
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName.endsWith(".verification"))
        container = AppContainer(app, "desktop-settings-${UUID.randomUUID()}", useNativeNetease = false)
        runBlocking { container.ready.await(); container.desktopLyrics.ready.await() }
        compose.setContent { LeiTheme(appearance.value) {
            if (showPage.value) DesktopLyricsSettingsScreen(container)
        } }
    }
    @After fun finish() { container.close() }
    private fun scroll(tag: String) {
        compose.onNodeWithTag("desktop_lyrics_settings").performScrollToNode(hasTestTag(tag))
    }
    private fun await(condition: () -> Boolean) { compose.waitUntil(5000, condition) }
    @Test fun previewChoicesAndTranslationWorkWithoutEnablingOverlay() {
        compose.onNodeWithTag("desktop_preview").assertExists()
        await { container.desktopLyricsSettingsVisible.value }
        scroll("desktop_effect_STAGE")
        compose.onNodeWithTag("desktop_effect_STAGE").performClick()
        await { container.desktopLyrics.state.value.effect == DesktopEffect.STAGE }
        compose.onNodeWithTag("desktop_effect_STAGE").assertIsSelected()
        scroll("desktop_appearance_CAPTION")
        compose.onNodeWithTag("desktop_appearance_CAPTION").performClick()
        await { container.desktopLyrics.state.value.appearance == DesktopAppearance.CAPTION }
        compose.onNodeWithTag("desktop_appearance_CAPTION").assertIsSelected()
        scroll("desktop_appearance_ISLAND")
        compose.onNodeWithTag("desktop_appearance_ISLAND").performClick()
        await { container.desktopLyrics.state.value.appearance == DesktopAppearance.ISLAND }
        compose.onNodeWithTag("desktop_appearance_ISLAND").assertIsSelected()
        scroll("desktop_preview")
        compose.onNodeWithTag("desktop_island").assertIsDisplayed()
        scroll("desktop_font")
        compose.onNodeWithTag("desktop_font").performSemanticsAction(SemanticsActions.SetProgress) { it(16f) }
        await { container.desktopLyrics.state.value.islandFontSize == 16f }
        Assert.assertEquals(20f, container.desktopLyrics.state.value.fontSize, .001f)
        scroll("desktop_opacity")
        compose.onNodeWithText("灵动岛不透明度").assertExists()
        compose.onNodeWithTag("desktop_opacity").performSemanticsAction(SemanticsActions.SetProgress) { it(.52f) }
        await { container.desktopLyrics.state.value.islandOpacity == .52f }
        Assert.assertEquals(.85f, container.desktopLyrics.state.value.opacity, .001f)
        scroll("desktop_appearance_CAPTION")
        compose.onNodeWithTag("desktop_appearance_CAPTION").performClick()
        await { container.desktopLyrics.state.value.appearance == DesktopAppearance.CAPTION }
        Assert.assertEquals(.85f, container.desktopLyrics.state.value.displayOpacity, .001f)
        scroll("desktop_appearance_ISLAND")
        compose.onNodeWithTag("desktop_appearance_ISLAND").performClick()
        await { container.desktopLyrics.state.value.appearance == DesktopAppearance.ISLAND }
        Assert.assertEquals(.52f, container.desktopLyrics.state.value.displayOpacity, .001f)
        val originalPosition = container.desktopLyrics.state.value.let { it.x to it.y }
        scroll("desktop_island_height")
        compose.onNodeWithTag("desktop_island_height").performSemanticsAction(SemanticsActions.SetProgress) { it(.3f) }
        await { container.desktopLyrics.state.value.islandY == .3f }
        Assert.assertEquals(originalPosition, container.desktopLyrics.state.value.let { it.x to it.y })
        compose.onNodeWithTag("desktop_island_height_reset").performClick()
        await { container.desktopLyrics.state.value.islandY == DESKTOP_ISLAND_HEIGHT }
        scroll("desktop_translation")
        compose.onNodeWithTag("desktop_translation").performClick()
        await { !container.desktopLyrics.state.value.translation }
        scroll("desktop_preview")
        compose.onNodeWithTag("desktop_preview_translation").assertDoesNotExist()
        Assert.assertFalse(container.desktopLyrics.state.value.enabled)
    }
    @Test fun parametersSaveAndPositionResetLeavesStyleAndAppLyricsAlone() {
        val before = container.musicSettings.state.value
        scroll("desktop_font")
        compose.onNodeWithTag("desktop_font").performSemanticsAction(SemanticsActions.SetProgress) { it(32f) }
        await { container.desktopLyrics.state.value.fontSize == 32f }
        scroll("desktop_opacity")
        compose.onNodeWithTag("desktop_opacity").performSemanticsAction(SemanticsActions.SetProgress) { it(.62f) }
        await { container.desktopLyrics.state.value.opacity == .62f }
        scroll("desktop_offset_earlier")
        compose.onNodeWithTag("desktop_offset_earlier").performClick()
        await { container.desktopLyrics.state.value.offsetMs == 100L }
        compose.onNodeWithTag("desktop_offset_zero").performClick()
        await { container.desktopLyrics.state.value.offsetMs == 0L }
        runBlocking { container.desktopLyrics.edit { it.copy(x = .1f, y = .8f, offsetMs = -400) } }
        scroll("desktop_reset_position")
        compose.onNodeWithTag("desktop_reset_position").performClick()
        await { container.desktopLyrics.state.value.x == .5f && container.desktopLyrics.state.value.y == .2f }
        Assert.assertEquals(32f, container.desktopLyrics.state.value.fontSize, .001f)
        Assert.assertEquals(-400L, container.desktopLyrics.state.value.offsetMs)
        scroll("desktop_font_reset")
        compose.onNodeWithTag("desktop_font_reset").performClick()
        await { container.desktopLyrics.state.value.fontSize == 20f }
        Assert.assertEquals(-400L, container.desktopLyrics.state.value.offsetMs)
        Assert.assertEquals(before, container.musicSettings.state.value)
    }
    @Test fun darkThemeKeepsPreviewAndChosenStyleReadable() {
        compose.runOnIdle { appearance.value = AppearanceSettings(themeMode = ThemeMode.DARK) }
        compose.onNodeWithTag("desktop_preview").assertIsDisplayed()
        scroll("desktop_appearance_CAPSULE")
        compose.onNodeWithTag("desktop_appearance_CAPSULE").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("desktop_appearance_CAPTION").performClick()
        await { container.desktopLyrics.state.value.appearance == DesktopAppearance.CAPTION }
        scroll("desktop_preview")
        compose.onNodeWithText("音乐一直陪着你").assertIsDisplayed()
        compose.runOnIdle { showPage.value = false }
        await { !container.desktopLyricsSettingsVisible.value }
    }
    @Test fun separateTextAndGlowColorsValidateHexAndSaveGlowControls() {
        val appBefore = container.musicSettings.state.value
        scroll("desktop_text_color_80DEFF")
        compose.onNodeWithTag("desktop_text_color_80DEFF").performClick()
        await { container.desktopLyrics.state.value.textColor == 0xFF80DEFFL }
        scroll("desktop_glow_strength")
        compose.onNodeWithTag("desktop_glow_strength").performSemanticsAction(SemanticsActions.SetProgress) { it(1.5f) }
        await { container.desktopLyrics.state.value.glowStrength == 1.5f }
        scroll("desktop_glow_spread")
        compose.onNodeWithTag("desktop_glow_spread").performSemanticsAction(SemanticsActions.SetProgress) { it(1.6f) }
        await { container.desktopLyrics.state.value.glowSpread == 1.6f }
        scroll("desktop_glow_color_hex")
        compose.onNodeWithTag("desktop_glow_color_hex").performTextReplacement("GGHHII")
        compose.onNodeWithTag("desktop_glow_color_apply").assertIsNotEnabled()
        compose.onNodeWithTag("desktop_glow_color_hex").performTextReplacement("#00FF66")
        compose.onNodeWithTag("desktop_glow_color_apply").performClick()
        await { container.desktopLyrics.state.value.glowColor == 0xFF00FF66L }
        Assert.assertEquals(0xFF80DEFFL, container.desktopLyrics.state.value.textColor)
        scroll("desktop_glow_enabled")
        compose.onNodeWithTag("desktop_glow_enabled").performClick()
        await { !container.desktopLyrics.state.value.glowEnabled }
        compose.onNodeWithTag("desktop_glow_strength").assertDoesNotExist()
        Assert.assertEquals(0xFF00FF66L, container.desktopLyrics.state.value.glowColor)
        compose.onNodeWithTag("desktop_glow_enabled").performClick()
        await { container.desktopLyrics.state.value.glowEnabled }
        scroll("desktop_glow_color_reset")
        compose.onNodeWithTag("desktop_glow_color_reset").performClick()
        await { container.desktopLyrics.state.value.glowColor == 0xFFE0E0E0L }
        Assert.assertEquals(0xFF80DEFFL, container.desktopLyrics.state.value.textColor)
        Assert.assertEquals(appBefore, container.musicSettings.state.value)
    }
    @Test fun staticPreviewShowsGlowWithoutInventingWordTiming() {
        val lit = compose.onNodeWithTag("desktop_preview").captureToImage().toPixelMap()
        runBlocking { container.desktopLyrics.edit { it.copy(glowEnabled = false) } }
        await { !container.desktopLyrics.state.value.glowEnabled }
        val unlit = compose.onNodeWithTag("desktop_preview").captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until lit.height) for (x in 0 until lit.width)
            if (lit[x, y].red > unlit[x, y].red + .025f) changed++
        Assert.assertTrue("An untimed preview must visibly demonstrate the default glow: $changed", changed > 100)
        compose.onNodeWithText("示例歌词").assertExists()
        compose.onNodeWithText("音乐一直陪着你").assertExists()
        Assert.assertFalse(container.playerController.state.value.playing)
    }
}
