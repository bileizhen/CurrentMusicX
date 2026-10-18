package io.github.currencortex.music

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.*
import java.io.File

/** Shared form/confirmation layout, with callbacks that cannot modify real accounts or settings. */
class DialogAlignmentTest {
    @get:Rule val compose = createComposeRule()
    private var confirms = 0

    private fun show(dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent { LeiTheme(AppearanceSettings(blur = false, themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Scaffold {
                    var input by remember { mutableStateOf("") }
                    MusicDialog("确认当前歌曲与播放设置", {}) {
                        Text("标题、表单和操作区应使用相同的左右边界。", Modifier.testTag("alignment_body"))
                        TextField(input, { input = it }, Modifier.fillMaxWidth().testTag("alignment_input"), label = "名称")
                        MusicDialogActions(Modifier.testTag("alignment_actions")) {
                            MusicDialogButton("暂时保留", {}, Modifier.weight(1f).testTag("alignment_cancel"))
                            MusicDialogButton("确认并继续使用当前设置", { confirms++ }, Modifier.weight(1f).testTag("alignment_confirm"))
                        }
                        MusicDialogButton("关闭", {}, Modifier.testTag("alignment_close"))
                    }
                }
            }
        } }
    }

    private fun verifyBounds() {
        val title = compose.onNodeWithTag("music_dialog_title").fetchSemanticsNode().boundsInRoot
        val input = compose.onNodeWithTag("alignment_input").fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("alignment_actions").fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithTag("alignment_cancel").fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNodeWithTag("alignment_confirm").fetchSemanticsNode().boundsInRoot
        val close = compose.onNodeWithTag("alignment_close").fetchSemanticsNode().boundsInRoot
        assertEquals(title.left, input.left, 1f); assertEquals(title.right, input.right, 1f)
        assertEquals(input.left, row.left, 1f); assertEquals(input.right, row.right, 1f)
        assertEquals(input.width, close.width, 1f)
        assertEquals(cancel.height, confirm.height, 1f)
        assertEquals(cancel.top, confirm.top, 1f)
        assertTrue(cancel.right < confirm.left)
        compose.onNodeWithTag("alignment_confirm").performClick()
        assertEquals(1, confirms)
    }

    private fun save(name: String) {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.externalCacheDir, name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun formAndActionsShareContentEdges() { show(); verifyBounds(); save("dialog-light.png") }
    @Test fun longActionsStayEqualInDarkModeWithLargeText() { show(dark = true, fontScale = 1.5f); verifyBounds(); save("dialog-large-dark.png") }
    @Test fun keyboardKeepsFormAndActionsReachable() {
        show()
        compose.onNodeWithTag("alignment_input").performClick().performTextInput("示例名称")
        compose.onNodeWithTag("alignment_confirm").assertIsDisplayed()
        compose.onNodeWithTag("alignment_close").assertIsDisplayed()
        verifyBounds()
        save("dialog-keyboard.png")
    }
}
