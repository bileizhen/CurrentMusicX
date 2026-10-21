package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.update.UpdateDialogAction
import io.github.currencortex.music.feature.update.UpdateOptionsDialog
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import java.io.File

class UpdateDialogMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun panelMaterialAndContentsMoveTogetherThroughoutEntranceAndExit() {
        val visible = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent { LeiTheme(AppearanceSettings()) { Scaffold {
            if (visible.value) UpdateOptionsDialog("更新设置", onDismiss = { visible.value = false }, actions = {
                UpdateDialogAction("取消", { visible.value = false }, Modifier.weight(1f).testTag("surface_close"))
            }) { Text("背景和文字一起移动", Modifier.testTag("surface_body")) }
        } } }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { visible.value = true }
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(192); compose.waitForIdle()
        val surface = compose.onNodeWithTag("update_options_surface")
        val body = compose.onNodeWithTag("surface_body")
        val enteringPanel = surface.getUnclippedBoundsInRoot().top.value
        val enteringText = body.getUnclippedBoundsInRoot().top.value
        capture("update-panel-entering.png")
        compose.mainClock.advanceTimeBy(800); compose.waitForIdle()
        val restingPanel = surface.getUnclippedBoundsInRoot().top.value
        val restingText = body.getUnclippedBoundsInRoot().top.value
        assertTrue("The material surface must slide in, not appear in place", enteringPanel > restingPanel + 1f)
        assertEquals(enteringPanel - restingPanel, enteringText - restingText, 1.5f)
        compose.onNodeWithTag("surface_close").performClick()
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(128); compose.waitForIdle()
        val exitingPanel = surface.getUnclippedBoundsInRoot().top.value
        val exitingText = body.getUnclippedBoundsInRoot().top.value
        assertTrue(exitingPanel > restingPanel)
        assertEquals(exitingPanel - restingPanel, exitingText - restingText, 1.5f)
        capture("update-panel-exiting.png")
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        surface.assertDoesNotExist(); body.assertDoesNotExist()
    }

    @Test fun pressCompressesAndReleaseRecoversWithoutFiringOnCancelledDrag() {
        var clicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { LeiTheme(AppearanceSettings()) { Scaffold {
            UpdateOptionsDialog("按压反馈", onDismiss = {}, actions = {
                UpdateDialogAction("保存", { clicks++ }, Modifier.weight(1f).testTag("motion_action"))
            }) { Text("按钮按下时轻微压缩，松手后恢复。") }
        } } }
        compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle()
        val button = compose.onNodeWithTag("motion_action")
        val initial = button.fetchSemanticsNode().boundsInRoot.width
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(120); compose.waitForIdle()
        val pressed = button.fetchSemanticsNode().boundsInRoot.width
        assertTrue("Pressed button should compress slightly", pressed < initial && pressed > initial * .94f)
        button.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        assertEquals(initial, button.fetchSemanticsNode().boundsInRoot.width, 1f)
        assertEquals(1, clicks)
        button.performTouchInput { down(center); moveTo(center.copy(y = -100f), 120); up() }
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test fun shortSheetExitsOverItsOwnHeightAndRetainsContentUntilAnimationFinishes() {
        val visible = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { LeiTheme(AppearanceSettings()) { Scaffold {
            if (visible.value) UpdateOptionsDialog("取消下载？", onDismiss = { visible.value = false }, actions = {
                UpdateDialogAction("继续下载", { visible.value = false }, Modifier.weight(1f).testTag("motion_close"))
            }) { Text("只关闭弹窗，下载会在后台继续。", Modifier.testTag("motion_body")) }
        } } }
        compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle()
        val body = compose.onNodeWithTag("motion_body")
        val start = body.fetchSemanticsNode().boundsInRoot.top
        val sheetHeight = compose.onNodeWithTag("update_options_sheet").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("motion_close").performClick()
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
        val early = body.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Short sheet must remain visible early in exit", early > start && early - start < sheetHeight * .6f)
        compose.mainClock.advanceTimeBy(112); compose.waitForIdle()
        assertTrue(body.fetchSemanticsNode().boundsInRoot.top > early)
        compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
        body.assertDoesNotExist()
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
