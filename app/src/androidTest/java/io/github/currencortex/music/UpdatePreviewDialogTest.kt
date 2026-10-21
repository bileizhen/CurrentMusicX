package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.feature.update.UpdatePreviewDialog
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold
import java.io.File

class UpdatePreviewDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmsAfterDismissalAndReopensWithTheLastSelectedVersion() {
        val visible = mutableStateOf(true)
        val version = mutableStateOf("1.1.15")
        var confirms = 0
        compose.setContent { LeiTheme(AppearanceSettings()) { Scaffold {
            if (visible.value) UpdatePreviewDialog(version.value, { visible.value = false }) {
                assertFalse("The prompt must close before presenting the update page", visible.value)
                version.value = it; confirms++
            }
        } } }
        compose.onNodeWithTag("update_preview_version").performTextReplacement("17")
        compose.onNodeWithTag("update_preview_confirm").performClick()
        compose.waitUntil(5000) { confirms == 1 }
        compose.runOnIdle { assertEquals("17", version.value); visible.value = true }
        compose.onNodeWithTag("update_preview_version").assertTextContains("17")
            .performTextReplacement("2.0-beta")
        compose.onNodeWithTag("update_preview_version").performImeAction()
        compose.waitUntil(5000) { confirms == 2 }
        compose.runOnIdle { assertEquals("2.0-beta", version.value) }
    }

    @Test fun emptyOrOversizedInputCannotStartAndCancelNeverConfirms() {
        val visible = mutableStateOf(true)
        var confirms = 0
        compose.setContent { LeiTheme(AppearanceSettings()) { Scaffold {
            if (visible.value) UpdatePreviewDialog("1.1.15", { visible.value = false }) { confirms++ }
        } } }
        val field = compose.onNodeWithTag("update_preview_version")
        field.performTextReplacement("   ")
        compose.onNodeWithTag("update_preview_confirm").assertIsNotEnabled()
        field.performTextReplacement("1".repeat(33))
        compose.onNodeWithTag("update_preview_confirm").assertIsNotEnabled()
        field.performTextReplacement("1.1.999")
        compose.onNodeWithTag("update_preview_confirm").assertIsEnabled()
        compose.onNodeWithTag("update_preview_cancel").performClick()
        compose.onNodeWithTag("update_preview_dialog").assertDoesNotExist()
        compose.runOnIdle { assertFalse(visible.value); assertEquals(0, confirms) }
    }

    @Test fun clearAndCharacterCountWorkInLightAndDarkWithKeyboardVisible() {
        val theme = mutableStateOf(io.github.currencortex.music.data.settings.ThemeMode.LIGHT)
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = theme.value)) { Scaffold {
            UpdatePreviewDialog("1.1.19", {}, {})
        } } }
        compose.onNodeWithTag("update_preview_count").assertTextEquals("6/32")
        compose.onNodeWithTag("update_preview_version").assertIsFocused()
        compose.onNodeWithTag("update_preview_confirm").assertIsDisplayed()
        capture("update-preview-light.png")
        compose.onNodeWithTag("update_preview_clear").performClick()
        compose.onNodeWithTag("update_preview_version").assertTextEquals("")
        compose.onNodeWithTag("update_preview_count").assertTextEquals("0/32")
        compose.onNodeWithTag("update_preview_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("update_preview_version").performTextInput("2.0-beta")
        compose.onNodeWithTag("update_preview_count").assertTextEquals("8/32")
        compose.runOnIdle { theme.value = io.github.currencortex.music.data.settings.ThemeMode.DARK }
        compose.waitForIdle()
        compose.onNodeWithTag("update_preview_confirm").assertIsDisplayed().assertIsEnabled()
        capture("update-preview-dark.png")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
