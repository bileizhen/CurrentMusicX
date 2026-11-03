package io.github.currencortex.music

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.feature.about.*
import io.github.currencortex.music.feature.settings.SettingsScreen
import io.github.currencortex.music.feature.support.*
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold

class SupportScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun content(settings: AppearanceSettings = AppearanceSettings(), body: @Composable () -> Unit) {
        compose.setContent { LeiTheme(settings) { Scaffold {
            CompositionLocalProvider(LocalMusicBottomInset provides 80.dp, content = body)
        } } }
    }
    private fun open(code: SupportCode) {
        compose.onNodeWithTag("support_screen").performScrollToNode(hasTestTag("support_${code.name}"))
        compose.onNodeWithTag("support_${code.name}").performClick()
        compose.onNodeWithTag("support_qr_${code.name}").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("support_qr_${code.name}").fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.width, bounds.height, 1f)
        compose.onNodeWithText("请使用${code.scanner}扫描").assertExists()
        // Verify that Coil actually decoded the local raw PNG, not just an empty image node.
        compose.waitUntil(10000) {
            val image = compose.onNodeWithTag("support_qr_${code.name}").captureToImage().asAndroidBitmap()
            try {
                var dark = 0
                for (y in 0 until image.height step 8) for (x in 0 until image.width step 8) {
                    val pixel = image.getPixel(x, y)
                    if (android.graphics.Color.red(pixel) < 90 && android.graphics.Color.green(pixel) < 90 &&
                        android.graphics.Color.blue(pixel) < 90) dark++
                }
                dark > image.width * image.height / 64 / 30
            } finally { image.recycle() }
        }
    }
    private fun close() = compose.onNodeWithTag("support_close").performScrollTo().performClick()

    @Test fun unopenedLinkAndBothLocalCodePreviewsInLightStandardMode() {
        content(AppearanceSettings(themeMode = ThemeMode.LIGHT, liquidGlass = false)) { SupportScreen({}, afdianUrl = "") }
        compose.onNodeWithTag("support_screen").performScrollToNode(hasTestTag("support_afdian"))
        compose.onNodeWithTag("support_afdian").assertIsNotEnabled()
        open(SupportCode.WECHAT); close()
        open(SupportCode.ALIPAY)
        compose.onNodeWithTag("support_save").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("support_share").performScrollTo().assertIsEnabled()
        close()
        compose.onNodeWithTag("support_ALIPAY").assertIsDisplayed()
    }

    @Test fun darkLiquidMonetAndLargeTextRemainScrollable() {
        content(AppearanceSettings(themeMode = ThemeMode.DARK, liquidGlass = true, monet = true,
            customTextScale = true, textScale = 1.5f)) { SupportScreen({}) }
        compose.onNodeWithTag("support_screen").performScrollToNode(hasTestTag("support_afdian"))
        compose.onNodeWithTag("support_afdian").assertIsEnabled()
        open(SupportCode.WECHAT)
        compose.onNodeWithTag("support_save").performScrollTo().assertIsDisplayed()
        close()
        compose.onNodeWithTag("support_screen").performScrollToNode(hasText("所有赞助均为自愿行为", substring = true))
        compose.onNodeWithText("所有赞助均为自愿行为", substring = true).assertIsDisplayed()
    }

    @Test fun settingsEntryReturnsToSettingsAndAboutEntryReturnsToAbout() {
        var root by mutableStateOf("settings")
        content { var support by rememberSaveable { mutableStateOf(false) }
            if (support) SupportScreen({ support = false })
            else if (root == "settings") SettingsScreen({}, {}, {}, {}, {}, {}, onSupport = { support = true })
            else AboutScreenMiuix(AboutUiState(), AboutScreenActions({}, {}), false, { _, _ -> }, { support = true })
        }
        compose.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("open_support"))
        compose.onNodeWithTag("open_support").performClick()
        compose.onNodeWithTag("support_screen").assertExists()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("settings_screen").assertExists()
        compose.runOnIdle { root = "about" }
        compose.onNodeWithTag("about_screen").performScrollToNode(hasTestTag("about_open_support"))
        compose.onNodeWithTag("about_open_support").performClick()
        compose.onNodeWithTag("support_screen").assertExists()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("about_screen").assertExists()
    }

    @Test fun openPreviewRestoresAfterSavedStateRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LeiTheme(AppearanceSettings()) { Scaffold { SupportScreen({}) } } }
        open(SupportCode.ALIPAY)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("support_qr_ALIPAY").assertExists()
        close()
        compose.onNodeWithTag("support_ALIPAY").assertIsDisplayed()
    }
}
