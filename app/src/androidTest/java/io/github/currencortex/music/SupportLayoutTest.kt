package io.github.currencortex.music

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.support.SupportScreen
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold

class SupportLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun landscapeWithDisabledAnimationsCanReachAllPreviewActions() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        try {
            compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.setContent { LeiTheme(AppearanceSettings(customTextScale = true, textScale = 1.5f)) {
                Scaffold { SupportScreen({}) }
            } }
            compose.onNodeWithTag("support_screen").performScrollToNode(hasTestTag("support_WECHAT"))
            compose.onNodeWithTag("support_WECHAT").performClick()
            compose.onNodeWithTag("support_qr_WECHAT").assertExists()
            compose.onNodeWithTag("support_save").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("support_share").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("support_close").performScrollTo().performClick()
            compose.onNodeWithTag("support_qr_WECHAT").assertDoesNotExist()
        } finally {
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}
