package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.logging.AppLogger
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.feature.logs.LogExportDialog
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold
import java.io.File

class LogExportDialogLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exportActionsRemainSeparateAndCancelClosesInBothThemesAndLargeText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val logger = AppLogger(context)
        val theme = mutableStateOf(ThemeMode.LIGHT)
        val fontScale = mutableFloatStateOf(1f)
        val show = mutableStateOf(true)
        var dismissals = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale.floatValue)) {
                LeiTheme(AppearanceSettings(themeMode = theme.value)) {
                    Scaffold {
                        Box(Modifier.fillMaxSize().background(
                            if (theme.value == ThemeMode.DARK) Color(0xFF101112) else Color(0xFFF4F5F7)))
                        LogExportDialog(show.value, logger) { dismissals++; show.value = false }
                    }
                }
            }
        }
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            for (scale in listOf(1f, 1.3f)) {
                compose.runOnIdle { theme.value = mode; fontScale.floatValue = scale; show.value = true }
                val save = compose.onNodeWithTag("export_logs_save").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val share = compose.onNodeWithTag("export_logs_share").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val cancel = compose.onNodeWithTag("export_logs_cancel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val panel = compose.onNodeWithTag("music_dialog_surface").fetchSemanticsNode().boundsInRoot
                assertTrue("Save and share must not overlap: $save / $share", save.bottom <= share.top)
                assertTrue("Share and cancel must not overlap: $share / $cancel", share.bottom <= cancel.top)
                assertEquals(save.left, share.left, 1f)
                assertEquals(save.right, share.right, 1f)
                assertTrue("All actions must stay inside the panel", save.top >= panel.top && cancel.bottom <= panel.bottom)
                File(context.getExternalFilesDir(null), "log-export-${mode.name.lowercase()}-$scale.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                compose.onNodeWithTag("export_logs_cancel").performClick()
                compose.onNodeWithTag("export_logs_save").assertDoesNotExist()
                compose.onNodeWithTag("export_logs_share").assertDoesNotExist()
            }
        }
        compose.runOnIdle { assertEquals(4, dismissals) }
    }
}
