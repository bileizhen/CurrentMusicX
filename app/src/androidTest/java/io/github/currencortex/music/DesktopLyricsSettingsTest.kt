package io.github.currencortex.music

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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
            top.yukonga.miuix.kmp.basic.Scaffold {
                if (showPage.value) DesktopLyricsSettingsScreen(container)
            }
        } }
    }
    @After fun finish() { container.close() }
    private fun scroll(tag: String) {
        val list = compose.onNodeWithTag("desktop_lyrics_settings")
        list.performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
        val viewport = list.fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, target.center.y - (viewport.top + viewport.height * .45f)) }
        compose.waitForIdle()
    }
    private fun expandGlow() {
        scroll("desktop_glow_details")
        compose.onNodeWithTag("desktop_glow_details").performClick()
    }
    private fun screenshot(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir, name)
            .outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun await(condition: () -> Boolean) { compose.waitUntil(5000, condition) }
    @Test fun previewChoicesAndTranslationWorkWithoutEnablingOverlay() {
        val appBefore = container.musicSettings.state.value
        compose.onNodeWithTag("desktop_preview").assertExists()
        await { container.desktopLyricsSettingsVisible.value }
        compose.onNodeWithText("半透明胶囊").assertDoesNotExist()
        compose.onNodeWithText("透明字幕").assertDoesNotExist()
        compose.onNodeWithTag("desktop_appearance_CAPTION").assertDoesNotExist()
        compose.onNodeWithTag("desktop_appearance_CAPSULE").assertDoesNotExist()
        scroll("desktop_effect")
        compose.onNodeWithTag("desktop_effect").performClick()
        compose.onNodeWithTag("desktop_effect_STAGE").performClick()
        await { container.desktopLyrics.state.value.effect == DesktopEffect.STAGE }
        compose.onNodeWithTag("desktop_effect").assertTextContains("舞台")
        scroll("desktop_preview")
        compose.onNodeWithTag("desktop_island").assertIsDisplayed()
        compose.onNodeWithTag("desktop_island_fps").assertExists()
        scroll("desktop_font")
        compose.onNodeWithTag("desktop_font").performSemanticsAction(SemanticsActions.SetProgress) { it(16f) }
        await { container.desktopLyrics.state.value.islandFontSize == 16f }
        scroll("desktop_opacity")
        compose.onNodeWithTag("desktop_opacity").performSemanticsAction(SemanticsActions.SetProgress) { it(.52f) }
        await { container.desktopLyrics.state.value.islandOpacity == .52f }
        scroll("desktop_island_height")
        compose.onNodeWithTag("desktop_island_height").performSemanticsAction(SemanticsActions.SetProgress) { it(.3f) }
        await { container.desktopLyrics.state.value.islandY == .3f }
        scroll("desktop_island_height_reset")
        compose.onNodeWithTag("desktop_island_height_reset").performClick()
        await { container.desktopLyrics.state.value.islandY == DESKTOP_ISLAND_HEIGHT }
        scroll("desktop_translation")
        compose.onNodeWithTag("desktop_translation").performClick()
        await { !container.desktopLyrics.state.value.translation }
        Assert.assertFalse(container.desktopLyrics.state.value.enabled)
        Assert.assertEquals(appBefore, container.musicSettings.state.value)
    }
    @Test fun parametersSaveAndPositionResetLeavesStyleAndAppLyricsAlone() {
        val before = container.musicSettings.state.value
        scroll("desktop_font")
        compose.onNodeWithTag("desktop_font").performSemanticsAction(SemanticsActions.SetProgress) { it(22f) }
        await { container.desktopLyrics.state.value.islandFontSize == 22f }
        scroll("desktop_opacity")
        compose.onNodeWithTag("desktop_opacity").performSemanticsAction(SemanticsActions.SetProgress) { it(.62f) }
        await { container.desktopLyrics.state.value.islandOpacity == .62f }
        scroll("desktop_offset_earlier")
        compose.onNodeWithTag("desktop_offset_earlier").performClick()
        await { container.desktopLyrics.state.value.offsetMs == 100L }
        compose.onNodeWithTag("desktop_offset_zero").performClick()
        await { container.desktopLyrics.state.value.offsetMs == 0L }
        runBlocking { container.desktopLyrics.edit { it.copy(islandY = .8f, offsetMs = -400) } }
        scroll("desktop_island_height_reset")
        compose.onNodeWithTag("desktop_island_height_reset").performClick()
        await { container.desktopLyrics.state.value.islandY == DESKTOP_ISLAND_HEIGHT }
        Assert.assertEquals(22f, container.desktopLyrics.state.value.islandFontSize, .001f)
        Assert.assertEquals(-400L, container.desktopLyrics.state.value.offsetMs)
        scroll("desktop_font_reset")
        compose.onNodeWithTag("desktop_font_reset").performClick()
        await { container.desktopLyrics.state.value.islandFontSize == DESKTOP_ISLAND_FONT_SIZE }
        Assert.assertEquals(-400L, container.desktopLyrics.state.value.offsetMs)
        Assert.assertEquals(before, container.musicSettings.state.value)
    }
    @Test fun darkThemeKeepsPreviewAndChosenStyleReadable() {
        compose.runOnIdle { appearance.value = AppearanceSettings(themeMode = ThemeMode.DARK) }
        compose.onNodeWithTag("desktop_preview").assertIsDisplayed()
        compose.onNodeWithTag("desktop_island").assertIsDisplayed()
        compose.onNodeWithTag("desktop_island_fps").assertExists()
        screenshot("desktop-layout-dark.png")
        expandGlow()
        scroll("desktop_glow_strength")
        screenshot("desktop-layout-glow.png")
        scroll("desktop_offset")
        screenshot("desktop-layout-sync.png")
        scroll("desktop_effect")
        compose.onNodeWithTag("desktop_effect").performClick()
        compose.onNodeWithTag("desktop_effect_PRECISE").assertIsSelected().assertIsDisplayed()
        compose.runOnIdle { showPage.value = false }
        await { !container.desktopLyricsSettingsVisible.value }
    }
    @Test fun separateTextAndGlowColorsValidateHexAndSaveGlowControls() {
        val appBefore = container.musicSettings.state.value
        scroll("desktop_text_color")
        compose.onNodeWithTag("desktop_text_color").performClick()
        compose.onNodeWithTag("desktop_text_color_80DEFF").performClick()
        compose.onNodeWithTag("desktop_text_color_apply").performClick()
        await { container.desktopLyrics.state.value.textColor == 0xFF80DEFFL }
        expandGlow()
        scroll("desktop_glow_strength")
        compose.onNodeWithTag("desktop_glow_strength").performSemanticsAction(SemanticsActions.SetProgress) { it(1.5f) }
        await { container.desktopLyrics.state.value.glowStrength == 1.5f }
        scroll("desktop_glow_spread")
        compose.onNodeWithTag("desktop_glow_spread").performSemanticsAction(SemanticsActions.SetProgress) { it(1.6f) }
        await { container.desktopLyrics.state.value.glowSpread == 1.6f }
        scroll("desktop_glow_color")
        compose.onNodeWithTag("desktop_glow_color").performClick()
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
        scroll("desktop_glow_color")
        compose.onNodeWithTag("desktop_glow_color").performClick()
        compose.onNodeWithTag("desktop_glow_color_reset").performClick()
        compose.onNodeWithTag("desktop_glow_color_apply").performClick()
        await { container.desktopLyrics.state.value.glowColor == 0xFFE0E0E0L }
        Assert.assertEquals(0xFF80DEFFL, container.desktopLyrics.state.value.textColor)
        Assert.assertEquals(appBefore, container.musicSettings.state.value)
    }
    @Test fun glowDragPreviewsBeforeReleaseAndSavedValueSurvivesReopening() {
        expandGlow()
        scroll("desktop_glow_strength")
        val before=container.desktopLyrics.state.value.glowStrength
        val slider=compose.onNodeWithTag("desktop_glow_strength")
        slider.performTouchInput {down(center);moveTo(Offset(width*.75f,centerY),delayMillis=240)}
        compose.waitForIdle()
        Assert.assertEquals(before,container.desktopLyrics.state.value.glowStrength,.0001f)
        slider.performTouchInput {up()}
        await {container.desktopLyrics.state.value.glowStrength>1.4f}
        val saved=container.desktopLyrics.state.value.glowStrength
        compose.runOnIdle {showPage.value=false};compose.waitForIdle()
        compose.runOnIdle {showPage.value=true};compose.waitForIdle()
        expandGlow()
        scroll("desktop_glow_strength")
        Assert.assertEquals(saved,container.desktopLyrics.state.value.glowStrength,.0001f)
        Assert.assertEquals(saved,compose.onNodeWithTag("desktop_glow_strength").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current,.0001f)
    }
    @Test fun colorDraftCanBeCancelledAndLargeTextKeepsActionsReachable() {
        screenshot("desktop-layout-light.png")
        val original = container.desktopLyrics.state.value.textColor
        compose.onNodeWithTag("desktop_text_color_hex").assertDoesNotExist()
        scroll("desktop_glow_details")
        compose.onNodeWithTag("desktop_glow_strength").assertDoesNotExist()
        compose.runOnIdle { appearance.value = AppearanceSettings(themeMode = ThemeMode.DARK,
            customTextScale = true, textScale = 1.4f) }
        scroll("desktop_text_color")
        compose.onNodeWithTag("desktop_text_color").performClick()
        compose.onNodeWithTag("desktop_text_color_80DEFF").performClick()
        compose.waitForIdle()
        Assert.assertEquals(original, container.desktopLyrics.state.value.textColor)
        screenshot("desktop-layout-color.png")
        compose.onNodeWithTag("desktop_text_color_cancel").performClick()
        compose.onNodeWithTag("desktop_text_color_hex").assertDoesNotExist()
        Assert.assertEquals(original, container.desktopLyrics.state.value.textColor)
        scroll("desktop_offset_earlier")
        compose.onNodeWithTag("desktop_offset_earlier").assertIsDisplayed().performClick()
        await { container.desktopLyrics.state.value.offsetMs == 100L }
        scroll("desktop_offset_zero")
        compose.onNodeWithTag("desktop_offset_zero").assertIsDisplayed().performClick()
        await { container.desktopLyrics.state.value.offsetMs == 0L }
        screenshot("desktop-layout-large-text.png")
        Assert.assertFalse(container.desktopLyrics.state.value.enabled)
    }
    @Test fun noSongPreviewUsesCircularAppIconAndFpsWithoutInventingLyrics() {
        compose.onNodeWithTag("desktop_preview").assertIsDisplayed()
        compose.onNodeWithTag("desktop_island_cover").assertIsDisplayed()
        compose.onNodeWithTag("desktop_island_fps").assertExists()
        compose.onNodeWithContentDescription("应用图标").assertExists()
        compose.onNodeWithText("示例歌词").assertDoesNotExist()
        compose.onNodeWithText("音乐一直陪着你").assertDoesNotExist()
        Assert.assertFalse(container.playerController.state.value.playing)
    }
}
