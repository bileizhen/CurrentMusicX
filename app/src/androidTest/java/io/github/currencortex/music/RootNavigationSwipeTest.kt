package io.github.currencortex.music

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.PlainFloatingBar
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RootNavigationSwipeTest {
    @get:Rule val compose = createComposeRule()
    private val selected = mutableIntStateOf(0)
    private val selections = mutableListOf<Int>()
    private fun show() {
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                PlainFloatingBar(selected.intValue, listOf("首页", "发现", "我的", "设置"),
                    List(4) { MusicIcons.Search },
                    { selected.intValue = it; selections += it })
            }
        } }
    }
    @Test fun tapsUpdateSelectionImmediatelyAndExternalChangesNeverEchoAsNewUserActions() {
        compose.mainClock.autoAdvance = false
        show()
        compose.onNodeWithTag("tab_3").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tab_3").assertIsSelected()
        assertEquals(listOf(3), selections)
        compose.runOnIdle { selected.intValue = 1 }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tab_1").assertIsSelected()
        assertEquals(listOf(3), selections)
    }
    @Test fun bottomRowSwipesSwitchOneTabAndDoNotWrapAtEitherEnd() {
        show()
        val bar = compose.onNodeWithTag("plain_floating_bar")
        bar.performTouchInput { swipeLeft(durationMillis = 220) }
        compose.onNodeWithTag("tab_1").assertIsSelected()
        bar.performTouchInput { swipeRight(durationMillis = 220) }
        compose.onNodeWithTag("tab_0").assertIsSelected()
        bar.performTouchInput { swipeRight(durationMillis = 220) }
        compose.onNodeWithTag("tab_0").assertIsSelected()
        assertEquals(listOf(1, 0), selections)
    }
}
