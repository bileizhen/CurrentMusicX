package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicOverlayDialog
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text

class DialogExitAnimationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun removingConditionalCallerRetainsContentAndMovesItDuringExit() {
        val show = mutableStateOf(true)
        var calls = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { LeiTheme(AppearanceSettings()) { Scaffold {
            if (show.value) MusicDialog("动画测试", { show.value = false }) {
                io.github.currencortex.music.ui.component.MusicTextAction("关闭", {
                    calls++; show.value = false
                }, Modifier.testTag("exit_action"))
                Text("保留到动画结束", Modifier.testTag("exit_body"))
            }
        } } }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        val before = compose.onNodeWithTag("exit_body").getUnclippedBoundsInRoot().top
        compose.onNodeWithTag("exit_action").performClick()
        // Commit caller disposal and start the root host's effect before sampling its motion.
        compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
        assertFalse(show.value)
        assertEquals(1, calls)
        val during = compose.onNodeWithTag("exit_body").getUnclippedBoundsInRoot().top
        assertTrue("Closing must move the retained dialog: before=$before, during=$during", during > before)
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        compose.onNodeWithTag("exit_body").assertDoesNotExist()
    }

    @Test fun explicitVisibilityClosesAndCanReopenWithoutStaleOverlay() {
        val show = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { LeiTheme(AppearanceSettings()) { Scaffold {
            MusicOverlayDialog(show.value, title = "保留宿主", onDismissRequest = { show.value = false }) {
                Text("状态弹窗", Modifier.testTag("explicit_body"))
            }
        } } }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        compose.runOnUiThread { show.value = false }
        compose.mainClock.advanceTimeBy(48); compose.waitForIdle()
        compose.onNodeWithTag("explicit_body").assertExists()
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        compose.onNodeWithTag("explicit_body").assertDoesNotExist()
        compose.runOnUiThread { show.value = true }
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        compose.onNodeWithTag("explicit_body").assertIsDisplayed()
        compose.runOnUiThread { show.value = false }
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        compose.onNodeWithTag("explicit_body").assertDoesNotExist()
    }
}
