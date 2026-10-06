package io.github.currencortex.music

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.RootTabTransition
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Continuous page movement, drag, rapid changes and saved state, without accounts or network. */
class RootTabTransitionTest {
    private var reduced = false
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float get() = if (reduced) 0f else 1f
    })
    private lateinit var select: (Int) -> Unit
    @Composable private fun Fixture() {
        var selected by rememberSaveable { mutableIntStateOf(0) }
        select = { selected = it }
        Column(Modifier.fillMaxSize().background(ComposeColor.White)) {
            Box(Modifier.weight(1f)) {
                RootTabTransition(selected, rememberSaveableStateHolder(), onSelected = { selected = it }) { tab ->
                    var count by rememberSaveable { mutableIntStateOf(0) }
                    val color = listOf(ComposeColor.Blue,ComposeColor.Red,ComposeColor.Green,ComposeColor.Magenta)[tab]
                    Box(Modifier.fillMaxSize().background(color).clickable { count++ }) {
                        BasicText("page$tab:$count", Modifier.padding(20.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(72.dp).testTag("stationary_dock"))
        }
    }
    private fun pixel(): Int {
        val bitmap = compose.onNodeWithTag("root_tab_transition").captureToImage().asAndroidBitmap()
        return bitmap.getPixel(bitmap.width/2, bitmap.height/2)
    }
    private fun change(tab: Int) = compose.runOnUiThread { select(tab) }

    @Test fun switchesThroughIntermediateFrameAndKeepsDockAndPageBoundsFixed() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture() }
        val pageBounds = compose.onNodeWithTag("root_tab_transition").fetchSemanticsNode().boundsInRoot
        val dockBounds = compose.onNodeWithTag("stationary_dock").fetchSemanticsNode().boundsInRoot
        assertEquals(Color.BLUE, pixel())
        change(1)
        compose.mainClock.advanceTimeBy(112)
        val middle = compose.onNodeWithTag("root_tab_transition").captureToImage().asAndroidBitmap()
        assertEquals("Outgoing page must slide left at full opacity", Color.BLUE, middle.getPixel(5,middle.height/2))
        assertEquals("Incoming page must slide in from the right at full opacity", Color.RED, middle.getPixel(middle.width-6,middle.height/2))
        compose.onNodeWithText("page0:0").assertDoesNotExist()
        compose.onNodeWithText("page1:0").assertExists()
        assertEquals(pageBounds, compose.onNodeWithTag("root_tab_transition").fetchSemanticsNode().boundsInRoot)
        assertEquals(dockBounds, compose.onNodeWithTag("stationary_dock").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(400)
        assertEquals(Color.RED, pixel())
    }

    @Test fun rapidSwitchesKeepOnlyLatestPageInteractiveAndRestoreItsState() {
        compose.mainClock.autoAdvance = false
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Fixture() }
        compose.onNodeWithText("page0:0").performClick()
        change(1); compose.mainClock.advanceTimeBy(80)
        change(2); compose.mainClock.advanceTimeBy(80)
        change(3); compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText("page3:0").assertExists()
        compose.onNodeWithText("page1:0").assertDoesNotExist()
        compose.onNodeWithText("page2:0").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        change(0); compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("page0:1").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("page0:1").assertExists()
        assertEquals(Color.BLUE, pixel())
    }

    @Test fun disabledSystemAnimationsSwitchImmediately() {
        reduced = true
        compose.setContent { Fixture() }
        change(3); compose.waitForIdle()
        compose.onNodeWithText("page0:0").assertDoesNotExist()
        compose.onNodeWithText("page3:0").assertExists()
        assertEquals(Color.MAGENTA, pixel())
    }

    @Test fun longJumpPassesThroughIntermediatePagesWithoutTeleporting() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture() }
        change(3)
        compose.mainClock.advanceTimeBy(48)
        val first = compose.onNodeWithTag("root_tab_transition").captureToImage().asAndroidBitmap()
        assertEquals("Jump must begin on the original page", Color.BLUE, first.getPixel(5,first.height/2))
        var sawIntermediate = false
        repeat(8) {
            compose.mainClock.advanceTimeBy(32)
            if (pixel() == Color.RED || pixel() == Color.GREEN) sawIntermediate = true
        }
        assertTrue("A long jump must travel across intermediate tabs", sawIntermediate)
        compose.mainClock.advanceTimeBy(500)
        assertEquals(Color.MAGENTA,pixel())
    }

    @Test fun fingerSwipeChangesSelectedTabAndKeepsPageState() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture() }
        compose.onNodeWithText("page0:0").performClick()
        compose.onNodeWithTag("root_tab_transition").performTouchInput { swipeLeft(durationMillis = 320) }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("page1:0").assertExists()
        assertEquals(Color.RED,pixel())
        compose.onNodeWithTag("root_tab_transition").performTouchInput { swipeRight(durationMillis = 320) }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("page0:1").assertExists()
        assertEquals(Color.BLUE,pixel())
    }
}
