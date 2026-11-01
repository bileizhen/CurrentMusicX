package io.github.currencortex.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.settings.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class AppearancePageTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private val models = ViewModelStore()
    @Before fun setup(): Unit = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "appearance-page-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await(); container.updateSettings.setAutoCheck(false)
        container.settings.edit { AppearanceSettings(themeMode = ThemeMode.LIGHT) }
        lateinit var vm: SettingsViewModel
        compose.runOnIdle { vm = SettingsViewModel(container.settings); models.put("settings", vm) }
        compose.setContent {
            val settings by container.settings.state.collectAsState()
            LeiTheme(settings) { top.yukonga.miuix.kmp.basic.Scaffold { AppearanceScreen(vm, {}, {}) } }
        }
    }
    @After fun close() { compose.runOnIdle { models.clear() }; container.close() }
    private fun reach(tag: String) = compose.onNodeWithTag("appearance_screen").performScrollToNode(hasTestTag(tag))
    private fun click(tag: String) {
        reach(tag)
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }
    private fun capture(name: String, tag: String? = null) {
        val bitmap = (if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)).captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun resetActionAppearingAndDisappearingKeepsPaletteBoundsFixed(): Unit = runBlocking {
        reach("appearance_palette")
        val chip = compose.onNodeWithTag("appearance_color_ffcb30e0")
        val headerBottom = compose.onNodeWithTag("settings_floating_header").fetchSemanticsNode().boundsInRoot.bottom
        val viewport = compose.onNodeWithTag("appearance_screen").fetchSemanticsNode().boundsInRoot
        val delta = chip.fetchSemanticsNode().boundsInRoot.center.y - (headerBottom + (viewport.bottom - headerBottom) * .4f)
        compose.onNodeWithTag("appearance_screen").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, delta) }
        compose.waitForIdle()
        val before = compose.onNodeWithTag("appearance_palette").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("appearance_color_reset").assertDoesNotExist()
        chip.assertIsDisplayed().performClick()
        compose.waitUntil(5000) { container.settings.state.value.primaryColor == 0xFFCB30E0 }
        compose.onNodeWithTag("appearance_color_reset").assertExists()
        val after = compose.onNodeWithTag("appearance_palette").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f); assertEquals(before.height, after.height, 1f)
        capture("appearance-nativeui-flat-reset.png")
        click("appearance_color_reset")
        compose.onNodeWithTag("appearance_color_reset_confirm").performClick()
        compose.waitUntil(5000) { container.settings.state.value.primaryColor == DefaultThemeColors.first() }
        compose.onNodeWithTag("appearance_color_reset").assertDoesNotExist()
        val restored = compose.onNodeWithTag("appearance_palette").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, restored.top, 1f); assertEquals(before.height, restored.height, 1f)
    }
    @Test fun previewIsInteractiveAndThemeAndGlassChoicesSaveImmediately(): Unit = runBlocking {
        capture("appearance-nativeui-light.png")
        repeat(4) { index ->
            compose.onNodeWithTag("appearance_preview_tab_$index").performClick()
            compose.onNodeWithTag("appearance_preview_tab_$index").assertIsSelected()
            capture("appearance-currentmusic-page-$index.png", "theme_preview")
        }
        assertEquals(ThemeMode.LIGHT, container.settings.snapshot().themeMode)
        click("appearance_theme_2")
        compose.waitUntil(5000) { container.settings.state.value.themeMode == ThemeMode.DARK }
        compose.onNodeWithTag("appearance_theme_2").assertIsSelected()
        capture("appearance-nativeui-dark.png")
        click("appearance_black_1")
        compose.waitUntil(5000) { container.settings.state.value.pureBlack }
        compose.onNodeWithTag("appearance_navigation").assertDoesNotExist()
        assertTrue(container.settings.snapshot().floatingBar)
        click("appearance_tab_animation_1")
        compose.waitUntil(5000) { container.settings.state.value.tabAnimation == RootTabAnimation.FADE }
        click("setting_liquid_0")
        compose.waitUntil(5000) { !container.settings.state.value.liquidGlass }
        compose.onNodeWithTag("setting_liquid_0").assertIsSelected()
        click("setting_liquid_1")
        compose.waitUntil(5000) { container.settings.state.value.liquidGlass }
        compose.onNodeWithTag("setting_liquid_1").assertIsSelected()
        click("setting_launch_style_1")
        compose.waitUntil(5000) { container.settings.state.value.launchStyle == LaunchStyle.HALO }
        compose.onNodeWithTag("setting_launch_style_1").assertIsSelected()
        click("setting_launch_style_2")
        compose.waitUntil(5000) { container.settings.state.value.launchStyle == LaunchStyle.NONE }
        compose.onNodeWithTag("setting_launch_preview").assertIsNotEnabled()
        click("setting_launch_style_0")
        compose.waitUntil(5000) { container.settings.state.value.launchStyle == LaunchStyle.CLASSIC }
        compose.onNodeWithTag("setting_launch_style_0").assertIsSelected()
        assertTrue(container.settings.snapshot().pureBlack)
        assertTrue(container.settings.snapshot().floatingBar)
        capture("appearance-nativeui-navigation.png")
    }
    @Test fun customPaletteValidatesHexSavesAndEditsUsingTheSameSurface(): Unit = runBlocking {
        click("appearance_color_add")
        compose.onNodeWithTag("appearance_color_hex").performTextReplacement("invalid")
        compose.onNodeWithTag("appearance_color_save").assertIsNotEnabled()
        compose.onNodeWithTag("appearance_color_hex").performTextReplacement("CC2266")
        compose.onNodeWithTag("appearance_color_save").performClick()
        compose.waitUntil(5000) { container.settings.state.value.primaryColor == 0xFFCC2266 }
        compose.onNodeWithTag("appearance_color_ffcc2266").assertIsSelected()
        click("appearance_color_style")
        compose.onNodeWithTag("appearance_color_style_TINTED").performClick()
        compose.waitUntil(5000) { container.settings.state.value.colorStyle == ThemeColorStyle.TINTED }
        compose.onNodeWithTag("appearance_color_ffcc2266").performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithTag("appearance_color_hex").performTextReplacement("337744")
        compose.onNodeWithTag("appearance_color_save").performClick()
        compose.waitUntil(5000) { container.settings.state.value.primaryColor == 0xFF337744 }
        assertFalse(container.settings.snapshot().primaryColors.contains(0xFFCC2266))
        capture("appearance-nativeui-colors.png")
    }
    @Test fun textScaleIsIndependentFromDisplayScaleAndExistingControlsRemainReachable(): Unit = runBlocking {
        reach("appearance_text_scale_slider")
        compose.onNodeWithTag("appearance_text_scale_slider").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("appearance_text_scale_mode_1").performScrollTo().performClick()
        compose.waitUntil(5000) { container.settings.state.value.customTextScale }
        compose.onNodeWithTag("appearance_text_scale_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(1.2f) }
        compose.waitUntil(5000) { container.settings.state.value.textScale == 1.2f }
        container.settings.edit { it.copy(themeMode = ThemeMode.DARK) }
        compose.waitUntil(5000) { container.settings.state.value.themeMode == ThemeMode.DARK }
        capture("appearance-nativeui-text-dark.png")
        assertEquals(1f, container.settings.snapshot().uiScale)
        click("appearance_text_scale_reset")
        compose.waitUntil(5000) { container.settings.state.value.textScale == 1f }
        compose.onNodeWithTag("appearance_screen").performScrollToNode(hasTestTag("setting_launch_style"))
        compose.onNodeWithTag("setting_launch_style").assertIsDisplayed()
        compose.onNodeWithTag("appearance_screen").performScrollToNode(hasTestTag("setting_record_shelf"))
        click("setting_record_shelf")
        compose.waitUntil(5000) { container.settings.state.value.playlistRecordShelf }
        click("setting_depth_ribbon")
        compose.waitUntil(5000) { container.settings.state.value.playlistDepthRibbon && !container.settings.state.value.playlistRecordShelf }
        reach("setting_scale")
        compose.onNodeWithTag("setting_scale").performScrollTo().assertExists()
        compose.onNodeWithTag("appearance_screen").performScrollToNode(hasTestTag("refresh_rate_slider"))
        compose.onNodeWithTag("refresh_rate_slider").assertIsDisplayed()
    }
}
