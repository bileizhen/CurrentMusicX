package io.github.currencortex.music

import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.PageContainerNavigation
import io.github.currencortex.music.ui.component.PageContainerSceneStrategy
import io.github.currencortex.music.ui.component.nativeui.GlassIconButton
import io.github.currencortex.music.ui.component.nativeui.rememberGlassBackdrop
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop

/** Navigation layer regression without library/account/network dependencies. */
class PageContainerNavigationTest {
    @get:Rule val compose=createComposeRule()

    @Test @SdkSuppress(minSdkVersion = 33)
    fun glassButtonsKeepTheirBackdropDuringNavigationAndAfterCancelledBack() {
        val stack = mutableStateOf(listOf("root"))
        val childColor = mutableStateOf(Color.Blue)
        lateinit var dispatcher: androidx.activity.OnBackPressedDispatcher
        lateinit var host: android.view.View
        compose.setContent {
            LeiTheme(AppearanceSettings(blur = true, liquidGlass = true)) {
                host = LocalView.current
                dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                val strategy = remember { PageContainerSceneStrategy(PageContainerNavigation(), emptySet()) }
                NavDisplay(backStack = stack.value, sceneStrategies = listOf(strategy),
                    modifier = Modifier.fillMaxSize().then(strategy.blur.viewport).testTag("glass_viewport"),
                    onBack = { stack.value = stack.value.dropLast(1) },
                    entryProvider = entryProvider {
                        entry("root") { GlassPage("root_button", Color.Red, Alignment.Center) }
                        entry("child") { GlassPage("child_button", childColor.value, Alignment.CenterStart) }
                    })
            }
        }
        compose.waitForIdle()
        assumeTrue(host.isHardwareAccelerated)
        val viewport = compose.onNodeWithTag("glass_viewport")
        val bounds = viewport.fetchSemanticsNode().boundsInRoot
        val child = compose.onNodeWithTag("child_button", useUnmergedTree = true)
        fun assertGlass(tag: String, red: Boolean) {
            val center = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
            val pixels = viewport.captureToImage().toPixelMap()
            val ink = pixels[(center.x - bounds.left).toInt(), (center.y - bounds.top).toInt()]
            val colorSignal = if (red) ink.red - ink.blue else ink.blue - ink.red
            assertTrue("$tag must show its sampled backdrop color, got $ink", colorSignal > .1f)
        }
        assertGlass("root_button", red = true)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { stack.value = listOf("root", "child") }
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        val entering = child.fetchSemanticsNode().boundsInRoot
        assertTrue("Check the entering button during the slide", entering.left > bounds.left && entering.center.x < bounds.right)
        assertGlass("child_button", red = false)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val settled = child.fetchSemanticsNode().boundsInRoot
        assertGlass("child_button", red = false)

        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.waitForIdle()
        assertGlass("child_button", red = false)
        for (fraction in listOf(.2f, .65f)) {
            compose.runOnUiThread {
                dispatcher.dispatchOnBackProgressed(BackEventCompat(bounds.width * fraction, 500f, fraction, BackEventCompat.EDGE_LEFT))
            }
            compose.waitForIdle()
            assertTrue("The front scene must be moving", child.fetchSemanticsNode().boundsInRoot.left > settled.left)
            assertGlass("child_button", red = false)
        }
        assertGlass("root_button", red = true)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(settled, child.fetchSemanticsNode().boundsInRoot)
        assertGlass("child_button", red = false)
        compose.runOnIdle { childColor.value = Color.Red }
        assertGlass("child_button", red = true)
    }

    @Composable private fun GlassPage(buttonTag: String, color: Color, alignment: Alignment) {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().then(if (backdrop == null) Modifier else Modifier.layerBackdrop(backdrop))
                .background(color))
            // The page itself is black; only real backdrop sampling can put color inside the button.
            Box(Modifier.matchParentSize().background(Color.Black))
            GlassIconButton({}, buttonTag, Modifier.align(alignment).testTag(buttonTag), backdrop = backdrop, size = 64.dp) {}
        }
    }

    @Test @SdkSuppress(minSdkVersion = 31)
    fun predictiveBackBlursOnlyTheRevealedPageAndRestoresSharpInk() {
        val stack = mutableStateOf(listOf("root"))
        lateinit var dispatcher: androidx.activity.OnBackPressedDispatcher
        lateinit var host: android.view.View
        compose.setContent {
            LeiTheme(AppearanceSettings(blur = true)) {
                host = LocalView.current
                dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                val strategy = remember { PageContainerSceneStrategy(PageContainerNavigation(), emptySet()) }
                NavDisplay(backStack = stack.value, sceneStrategies = listOf(strategy),
                    modifier = Modifier.fillMaxSize().then(strategy.blur.viewport).testTag("viewport"),
                    onBack = { stack.value = stack.value.dropLast(1) },
                    entryProvider = entryProvider {
                        entry("root") { StripedPage("rear") }
                        entry("child") { StripedPage("front") }
                    })
            }
        }
        compose.waitForIdle()
        assumeTrue(host.isHardwareAccelerated)
        val viewport = compose.onNodeWithTag("viewport")
        // Horizontal stripes keep the same edges as either page slides horizontally.
        // Normalize by brightness so Miuix's black dim scrim cannot masquerade as a blur.
        fun contrastAt(x: Int): Float {
            val pixels = viewport.captureToImage().toPixelMap()
            val start = pixels.height / 3
            val levels = (start until minOf(start + 64, pixels.height)).map { pixels[x, it].red }
            return (levels.max() - levels.min()) / (levels.max() + levels.min()).coerceAtLeast(.001f)
        }
        val bounds = viewport.fetchSemanticsNode().boundsInRoot
        val middle = (bounds.width / 2).toInt()
        val initial = contrastAt(middle)
        assertTrue("Settled rear stripes must be sharp", initial > .9f)
        compose.runOnIdle { stack.value = listOf("root", "child") }
        val child = compose.onNodeWithTag("front")
        child.assertIsDisplayed()
        assertTrue("Settled front stripes must be sharp", contrastAt(middle) > .9f)

        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(bounds.width * .45f, 500f, .45f, BackEventCompat.EDGE_LEFT)) }
        compose.waitForIdle()
        val edge = child.fetchSemanticsNode().boundsInRoot.left - bounds.left
        assertTrue("Both scenes must be visible during the back gesture", edge > 16 && edge < bounds.width - 16)
        val rear = contrastAt((edge / 2).toInt())
        val front = contrastAt(((edge + bounds.width) / 2).toInt())
        assertTrue("Blur must soften the revealed stripes (contrast=$rear)", rear < initial * .5f)
        assertTrue("The foreground must stay sharp (contrast=$front)", front > .9f)

        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(bounds, child.fetchSemanticsNode().boundsInRoot)
        assertTrue("Cancelling must restore the sharp foreground", contrastAt(middle) > .9f)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        child.assertDoesNotExist()
        compose.onNodeWithTag("rear").assertIsDisplayed()
        assertEquals("The revealed page must release its blur after pop", initial, contrastAt(middle), .05f)
    }

    @Composable private fun StripedPage(tag: String) {
        Canvas(Modifier.fillMaxSize().background(Color.Black).testTag(tag)) {
            var y = 0f
            while (y < size.height) {
                drawRect(Color.White, Offset(0f, y), Size(size.width, 4f))
                y += 8f
            }
        }
    }

    @Test fun ordinaryPagesKeepTheirPredictiveSlideAndCancelWithoutPopping() {
        lateinit var dispatcher:androidx.activity.OnBackPressedDispatcher
        compose.setContent {LeiTheme(AppearanceSettings(blur=false)) {
            dispatcher=LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            var stack by remember {mutableStateOf(listOf("root"))}
            val strategy=remember {PageContainerSceneStrategy(PageContainerNavigation(),emptySet())}
            NavDisplay(backStack=stack,sceneStrategies=listOf(strategy),onBack={stack=stack.dropLast(1)},
                entryProvider=entryProvider {
                    entry("root") {Box(Modifier.fillMaxSize().background(Color.White).testTag("normal_root")) {
                        TextButton("打开",onClick={stack=stack+"child"},modifier=Modifier.testTag("normal_open"))
                    }}
                    entry("child") {Box(Modifier.fillMaxSize().background(Color.Blue).testTag("normal_child"))}
                })
        }}
        compose.onNodeWithTag("normal_open").performClick();compose.waitForIdle()
        val child=compose.onNodeWithTag("normal_child")
        val original=child.fetchSemanticsNode().boundsInRoot
        fun start()=compose.runOnUiThread {dispatcher.dispatchOnBackStarted(BackEventCompat(0f,500f,0f,BackEventCompat.EDGE_LEFT))}
        fun progress(p:Float) {
            compose.runOnUiThread {dispatcher.dispatchOnBackProgressed(BackEventCompat(p*original.width,500f,p,BackEventCompat.EDGE_LEFT))}
            compose.waitForIdle()
        }
        start();progress(.2f)
        val first=child.fetchSemanticsNode().boundsInRoot.left
        progress(.6f)
        assertTrue("Normal pages still follow predictive slide progress",child.fetchSemanticsNode().boundsInRoot.left>first+1f)
        compose.runOnUiThread {dispatcher.dispatchOnBackCancelled()};compose.waitForIdle()
        assertEquals(original,child.fetchSemanticsNode().boundsInRoot)
        start();progress(.5f)
        compose.runOnUiThread {dispatcher.onBackPressed()};compose.waitForIdle()
        child.assertDoesNotExist();compose.onNodeWithTag("normal_root").assertIsDisplayed()
    }
}
