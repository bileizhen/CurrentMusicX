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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.currencortex.music.ui.util.collectAsPageState
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.currencortex.music.ui.component.RootTabTransition
import io.github.currencortex.music.ui.component.LocalRootTabSwipe
import io.github.currencortex.music.ui.component.RootElasticStrainKey
import io.github.currencortex.music.ui.component.RootMotionItem
import io.github.currencortex.music.ui.component.LocalRootTabPlacement
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Continuous page movement, drag, rapid changes and saved state, without accounts or network. */
class RootTabTransitionTest {
    private var reduced = false
    private var fade by mutableStateOf(false)
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float get() = if (reduced) 0f else 1f
    })
    private lateinit var select: (Int) -> Unit
    private var heroLanding: (() -> androidx.compose.ui.geometry.Rect?)? = null
    @Composable private fun Fixture() {
        var selected by rememberSaveable { mutableIntStateOf(0) }
        select = { selected = it }
        Column(Modifier.fillMaxSize().background(ComposeColor.White)) {
            Box(Modifier.weight(1f)) {
                RootTabTransition(selected, rememberSaveableStateHolder(), onSelected = { selected = it }, fade = fade) { tab ->
                    var count by rememberSaveable { mutableIntStateOf(0) }
                    val color = listOf(ComposeColor.Blue,ComposeColor.Red,ComposeColor.Green,ComposeColor.Magenta)[tab]
                    Box(Modifier.fillMaxSize().background(color)) {
                        Box(Modifier.fillMaxWidth().height(80.dp).then(LocalRootTabSwipe.current)) {
                            BasicText("page$tab:$count", Modifier.padding(20.dp).clickable { count++ })
                        }
                        Box(Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(top = 160.dp)) {
                            RootMotionItem {
                                val placement = LocalRootTabPlacement.current
                                Box(Modifier.size(64.dp, 32.dp).background(ComposeColor.Yellow)
                                    .testTag("upper_$tab").clickable { count++ }.onGloballyPositioned { coordinates ->
                                        if (tab == 1) heroLanding = { placement?.landingBounds(coordinates) }
                                    })
                            }
                        }
                        Box(Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(bottom = 160.dp)) {
                            RootMotionItem { Box(Modifier.size(64.dp, 32.dp).background(ComposeColor.Yellow).testTag("lower_$tab")) }
                        }
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

    private fun strain() = compose.onNodeWithTag("root_tab_transition").fetchSemanticsNode().config[RootElasticStrainKey]

    @Test fun componentsTrailIndependentlyWithRigidShapesAndStraightPageEdges() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture() }
        val dock = compose.onNodeWithTag("stationary_dock").fetchSemanticsNode().boundsInRoot
        change(1)
        compose.mainClock.advanceTimeBy(144)
        assertTrue("Moving components should trail", strain() > .001f)
        val upper = compose.onNodeWithTag("upper_1").fetchSemanticsNode().boundsInRoot
        val lower = compose.onNodeWithTag("lower_1").fetchSemanticsNode().boundsInRoot
        assertTrue("Lower components should trail independently", lower.left > upper.left + 2f)
        assertEquals("Hero landing excludes both the pager and temporary component motion", 0f, heroLanding?.invoke()?.left ?: -1f, 1f)
        val bitmap = compose.onNodeWithTag("root_tab_transition").captureToImage().asAndroidBitmap()
        fun seam(y: Int) = (0 until bitmap.width).first { x ->
            val color = bitmap.getPixel(x, y)
            Color.red(color) > 200 && Color.blue(color) < 50
        }
        assertEquals("The page itself must not bend", seam(bitmap.height / 2), seam(bitmap.height - 2))
        compose.onNodeWithTag("upper_1").performTouchInput { click() }
        var rebounded = false
        repeat(40) {
            compose.mainClock.advanceTimeBy(16)
            if (strain() < -.0001f) rebounded = true
        }
        assertTrue("The spring should return through a small opposite bend", rebounded)
        compose.mainClock.advanceTimeBy(600)
        assertEquals(0f, strain(), .0001f)
        assertEquals(Color.RED, pixel())
        compose.onNodeWithText("page1:1").assertExists()
        val restingUpper = compose.onNodeWithTag("upper_1").fetchSemanticsNode().boundsInRoot
        val restingLower = compose.onNodeWithTag("lower_1").fetchSemanticsNode().boundsInRoot
        assertEquals(restingUpper.left, restingLower.left, .1f)
        assertEquals("Components must not stretch", upper.width, restingUpper.width, .1f)
        assertEquals(upper.height, restingUpper.height, .1f)
        assertEquals(dock, compose.onNodeWithTag("stationary_dock").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun elasticMotionReversesContinuouslyAndHandlesRtl() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                Fixture()
            }
        }
        change(1); compose.mainClock.advanceTimeBy(112)
        assertTrue("RTL must mirror the deformation", strain() < 0f)
        change(0); compose.mainClock.advanceTimeBy(64)
        assertTrue(strain().isFinite() && kotlin.math.abs(strain()) <= .12f)
        compose.mainClock.advanceTimeBy(1200)
        assertEquals(0f, strain(), .0001f)
        assertEquals(Color.BLUE, pixel())
    }

    @Test fun fadeChoiceCrossfadesOnlyTheEndpointsAndSwitchingStylesKeepsPageState() {
        fade = true
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture() }
        compose.onNodeWithText("page0:0").performClick()
        val bounds = compose.onNodeWithTag("stationary_dock").fetchSemanticsNode().boundsInRoot
        change(3); compose.mainClock.advanceTimeBy(112)
        val middle = compose.onNodeWithTag("root_tab_transition").captureToImage().asAndroidBitmap()
        val left = middle.getPixel(5, middle.height / 2)
        val right = middle.getPixel(middle.width - 6, middle.height / 2)
        assertEquals("Fade must not expose a horizontal seam", left, right)
        assertNotEquals(Color.BLUE, left); assertNotEquals(Color.MAGENTA, left)
        assertTrue("A long fade must not reveal the intermediate green page", Color.green(left) < 100)
        compose.mainClock.advanceTimeBy(400)
        assertEquals(Color.MAGENTA, pixel())
        compose.runOnUiThread { fade = false }
        change(0); compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("page0:1").assertExists()
        assertEquals(Color.BLUE, pixel())
        assertEquals(bounds, compose.onNodeWithTag("stationary_dock").fetchSemanticsNode().boundsInRoot)
    }

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
        assertEquals(0f, strain(), 0f)
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
        compose.onNodeWithTag("root_swipe_header").performTouchInput { swipeLeft(durationMillis = 320) }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("page1:0").assertExists()
        assertEquals(Color.RED,pixel())
        compose.onNodeWithTag("root_swipe_header").performTouchInput { swipeRight(durationMillis = 320) }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("page0:1").assertExists()
        assertEquals(Color.BLUE,pixel())
    }

    @Test fun contentAreaCannotSwitchRootTabsAndHeaderTapsStillReachTheirAction() {
        compose.setContent { Fixture() }
        compose.onNodeWithTag("root_tab_transition").performTouchInput { swipeLeft(durationMillis = 240) }
        compose.onNodeWithText("page0:0").assertExists()
        assertEquals(Color.BLUE, pixel())
        compose.onNodeWithText("page0:0").performClick()
        compose.onNodeWithText("page0:1").assertExists()
        compose.onNodeWithTag("root_swipe_header").performTouchInput { swipeLeft(durationMillis = 240) }
        compose.onNodeWithText("page1:0").assertExists()
        assertEquals(Color.RED, pixel())
    }

    @Test fun selectionTracksTheCrossedPageBeforeFingerRelease() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture() }
        compose.onNodeWithTag("root_swipe_header").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .9f, centerY))
            moveBy(androidx.compose.ui.geometry.Offset(-width * .7f, 0f))
        }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("page1:0").assertExists()
        compose.onNodeWithText("page0:0").assertDoesNotExist()
        compose.onNodeWithTag("root_swipe_header").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(Color.RED, pixel())
    }

    @Test fun rootWaitsForSceneResumeToWarmTabsAndKeepsThemWhenScenePauses() {
        val scene = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        val entries = IntArray(4)
        val disposals = IntArray(4)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { scene.lifecycle.currentState = Lifecycle.State.STARTED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides scene) {
                RootTabTransition(0, rememberSaveableStateHolder(), onSelected = {}) { tab ->
                    DisposableEffect(tab) {
                        entries[tab]++
                        onDispose { disposals[tab]++ }
                    }
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertArrayEquals("Only the visible tab should be attached during scene entry",
                intArrayOf(1, 0, 0, 0), entries)
            scene.lifecycle.currentState = Lifecycle.State.RESUMED
        }

        var previous = 1
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                val attached = entries.sum()
                assertTrue("At most one neighbor should warm per frame", attached in previous..(previous + 1))
                previous = attached
            }
        }
        compose.runOnIdle {
            assertArrayEquals(intArrayOf(1, 1, 1, 1), entries)
            scene.lifecycle.currentState = Lifecycle.State.STARTED
        }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertArrayEquals("Pausing the scene must keep the warmed tabs attached", IntArray(4), disposals)
            assertArrayEquals(intArrayOf(1, 1, 1, 1), entries)
        }
    }

    @Test fun settingsToMeRetainsPagesAndResumesDatasetUpdatesOnlyAfterTheSlide() {
        compose.mainClock.autoAdvance = false
        var selected by mutableIntStateOf(3)
        val entries = IntArray(4)
        val owners = mutableMapOf<Int, androidx.lifecycle.LifecycleOwner>()
        val datasets = List(4) { MutableStateFlow(0) }
        compose.setContent {
            RootTabTransition(selected, rememberSaveableStateHolder(), { selected = it }) { tab ->
                val value by datasets[tab].collectAsPageState()
                val owner = LocalLifecycleOwner.current
                DisposableEffect(tab) { entries[tab]++; owners[tab] = owner; onDispose {} }
                BasicText("retained$tab:$value", Modifier.fillMaxSize())
            }
        }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnUiThread {
            assertEquals(Lifecycle.State.RESUMED, owners.getValue(3).lifecycle.currentState)
            assertEquals(Lifecycle.State.CREATED, owners.getValue(2).lifecycle.currentState)
            selected = 2
        }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnUiThread {
            assertEquals(Lifecycle.State.STARTED, owners.getValue(2).lifecycle.currentState)
            assertEquals(Lifecycle.State.CREATED, owners.getValue(1).lifecycle.currentState)
            datasets[2].value = 9
        }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("retained2:0").assertExists()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("retained2:9").assertExists()
        compose.runOnUiThread { assertEquals(Lifecycle.State.RESUMED, owners.getValue(2).lifecycle.currentState) }
        repeat(2) {
            compose.runOnUiThread { selected = 3 }
            compose.mainClock.advanceTimeBy(500)
            compose.runOnUiThread { selected = 2 }
            compose.mainClock.advanceTimeBy(500)
        }
        assertArrayEquals(intArrayOf(1, 1, 1, 1), entries)
        compose.mainClock.autoAdvance = true
    }
}
