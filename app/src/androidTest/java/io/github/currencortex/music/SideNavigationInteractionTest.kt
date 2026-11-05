package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.FloatingSideBar
import io.github.currencortex.music.ui.component.HighApiFloatingNavigation
import io.github.currencortex.music.ui.component.SideWaterDropVisual
import io.github.currencortex.music.ui.component.nativeui.ElasticPressGeometry
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises the actual rail and its nested tab clicks, including the short-window fallback. */
@android.annotation.TargetApi(33)
class SideNavigationInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var selected by mutableIntStateOf(0)
    private var availableHeight by mutableStateOf(280.dp)
    private val selections = mutableListOf<Int>()
    private val labels = listOf("首页", "发现", "我的", "设置")
    private val icons = List(4) { Icons.Default.Home }

    private fun select(index: Int) {
        selected = index
        selections += index
    }

    private fun show(glass: Boolean = false) {
        compose.setContentWithPressDiagnostics {
            LeiTheme(AppearanceSettings(blur = glass, liquidGlass = glass)) {
                val rail: @Composable BoxScope.() -> Unit = {
                    FloatingSideBar(selected, labels, icons, ::select,
                        Modifier.align(Alignment.Center).heightIn(max = availableHeight))
                }
                if (glass) HighApiFloatingNavigation(selected, labels, icons, ::select, blur = true, glass = true,
                    content = { Box(Modifier.fillMaxSize().background(Color.Black)) }, overlay = rail)
                else Box(Modifier.fillMaxSize(), content = rail)
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1000)
    }

    private fun tabPosition(index: Int): Offset {
        val surface = compose.onNodeWithTag("side_surface_press").fetchSemanticsNode().boundsInRoot
        return compose.onNodeWithTag("tab_$index").fetchSemanticsNode().boundsInRoot.center - surface.topLeft
    }

    @Test fun unselectedGlassTabPullsWholeCapsuleAndCancellationRestoresSelection() {
        show(glass = true)
        val rail = compose.onNodeWithTag("wide_navigation")
        val surface = compose.onNodeWithTag("side_surface_press")
        val restingBounds = rail.fetchSemanticsNode().boundsInRoot
        val start = tabPosition(2)
        surface.performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(600)
        val held = surface.fetchSemanticsNode().config[ElasticPressGeometry]
        val lens = compose.onNodeWithTag("side_water_drop").fetchSemanticsNode().config[SideWaterDropVisual]
        assertTrue("An unselected tab grows the whole capsule", held.scaleX > 1f && held.scaleY > 1f)
        assertTrue("The lens follows a held unselected tab", lens.press > .9f && lens.scaleY > 1.2f)
        assertEquals(2f, lens.index, .02f)
        surface.performTouchInput { moveBy(Offset(30f, -60f), delayMillis = 32) }
        compose.mainClock.advanceTimeBy(320)
        val pulled = surface.fetchSemanticsNode().config[ElasticPressGeometry]
        assertTrue("The entire capsule follows the finger on both axes", pulled.pull.x > 1f && pulled.pull.y < -1f)
        assertEquals("Painted deformation must not move the reserved layout", restingBounds, rail.fetchSemanticsNode().boundsInRoot)
        surface.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1800)
        val settled = surface.fetchSemanticsNode().config[ElasticPressGeometry]
        val restoredLens = compose.onNodeWithTag("side_water_drop").fetchSemanticsNode().config[SideWaterDropVisual]
        assertEquals(1f, settled.scaleX, .001f)
        assertEquals(1f, settled.scaleY, .001f)
        assertEquals(0f, settled.pull.getDistance(), .1f)
        assertEquals(0f, restoredLens.index, .001f)
        assertEquals(0, selected)
        assertEquals(emptyList<Int>(), selections)
        compose.mainClock.autoAdvance = true
    }

    @Test fun draggingFromUnselectedTabSelectsOnlyTheReleaseDestinationWithoutScrollingLabels() {
        show()
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        val restingFirst = compose.onNodeWithTag("tab_0").fetchSemanticsNode().boundsInRoot
        val start = tabPosition(1)
        val end = tabPosition(3)
        val surface = compose.onNodeWithTag("side_surface_press")
        surface.performTouchInput {
            down(start)
            repeat(10) { step -> moveTo(start + (end - start) * ((step + 1) / 10f), delayMillis = 16) }
        }
        compose.mainClock.advanceTimeBy(300)
        assertEquals("Previewing a destination must not navigate during the drag", 0, selected)
        assertEquals(emptyList<Int>(), selections)
        surface.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1800)
        assertEquals(3, selected)
        assertEquals(listOf(3), selections)
        assertEquals(restingFirst, compose.onNodeWithTag("tab_0").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.autoAdvance = true
    }

    @Test fun tappingAnUnselectedTabWithSmallHandMovementKeepsItsClick() {
        show()
        compose.onNodeWithTag("tab_2").performTouchInput {
            down(center)
            moveBy(Offset(1f, 1f), delayMillis = 16)
            up()
        }
        compose.mainClock.advanceTimeBy(1800)
        assertEquals(2, selected)
        assertEquals(listOf(2), selections)
        compose.mainClock.autoAdvance = true
    }

    @Test fun draggingAwayAndBackStillSelectsTheUnselectedTabUnderTheFinger() {
        show()
        val start = tabPosition(1)
        val end = tabPosition(3)
        compose.onNodeWithTag("side_surface_press").performTouchInput {
            down(start)
            moveTo(end, delayMillis = 64)
            moveTo(start, delayMillis = 64)
            up()
        }
        compose.mainClock.advanceTimeBy(1800)
        assertEquals(1, selected)
        assertEquals(listOf(1), selections)
        compose.mainClock.autoAdvance = true
    }

    @Test fun shortWindowsScrollToEveryTabAndDisableScrollingAfterGrowing() {
        availableHeight = 150.dp
        show()
        compose.mainClock.autoAdvance = true
        compose.onAllNodes(hasScrollAction()).assertCountEquals(1)
        compose.onNodeWithTag("side_surface_press").performTouchInput { swipeUp(durationMillis = 400) }
        compose.runOnIdle {
            assertEquals("Scrolling a short rail must not also select another page", 0, selected)
            assertEquals(emptyList<Int>(), selections)
        }
        compose.onNodeWithTag("tab_3").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(3, selected) }
        compose.onNodeWithTag("tab_0").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(0, selected)
            availableHeight = 280.dp
        }
        compose.waitForIdle()
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
        for (index in labels.indices) compose.onNodeWithTag("tab_$index").assertIsDisplayed()
        compose.onNodeWithTag("tab_3").performClick()
        compose.runOnIdle { assertEquals(listOf(3, 0, 3), selections) }
    }
}
