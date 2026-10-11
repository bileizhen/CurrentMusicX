package io.github.currencortex.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Text

/** Canvas frames, real measured title, and restoration only; no account or playback access. */
class LaunchBrandAnimationTest {
    private var reduced = false
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float get() = if (reduced) 0f else 1f
    })
    private lateinit var motion: LaunchBrandState
    private var clicks = 0
    @Composable private fun Fixture(theme: ThemeMode = ThemeMode.LIGHT, ready: Boolean = true, home: Boolean = true,
        windowReady: Boolean = true, blur: Boolean = false) {
        var done by rememberSaveable { mutableStateOf(false) }
        motion = remember { LaunchBrandState() }
        motion.active = !done
        LeiTheme(AppearanceSettings(themeMode = theme, blur = blur)) {
            CompositionLocalProvider(LocalLaunchBrand provides motion) {
                Box(Modifier.fillMaxSize().background(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background).testTag("launch_fixture")) {
                    Column(Modifier.statusBarsPadding().padding(20.dp)) {
                        HomeBrandTitle()
                        Text("Fixture action", Modifier.padding(top = 24.dp).clickable { clicks++ }.testTag("launch_fixture_action"),
                            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary)
                    }
                    if (!done) LaunchBrandOverlay(motion, ready, home, windowReady, enableBlur = blur) { motion.active = false; done = true }
                }
            }
        }
    }
    private fun save(name: String) {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        compose.onNodeWithTag("launch_fixture").captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(app.externalCacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun inkBounds(lightInk: Boolean = false): androidx.compose.ui.geometry.Rect {
        val bitmap = compose.onNodeWithTag("launch_fixture").captureToImage().asAndroidBitmap()
        val action = compose.onNodeWithTag("launch_fixture_action").fetchSemanticsNode().boundsInRoot
        var left = bitmap.width; var top = bitmap.height; var right = 0; var bottom = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(pixel)
            if (!action.contains(androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat())) &&
                (if (lightInk) luminance > .65 else luminance < .35)) {
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        assertTrue("The wordmark must be visibly drawn", right > left && bottom > top)
        return androidx.compose.ui.geometry.Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    @Test fun wordmarkUsesAboutBackgroundInkAndKeepsMovingWhileLoading() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture(ThemeMode.LIGHT, ready = false, blur = true) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals("The faster intro must already be complete", 1f, motion.reveal.value, .001f)
        val wordmark = compose.onNodeWithTag("launch_wordmark", useUnmergedTree = true)
        val bitmap = wordmark.captureToImage().asAndroidBitmap()
        var coloredLetters = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(pixel, hsv)
            if (android.graphics.Color.alpha(pixel) > 180 && hsv[1] > .3f && hsv[2] < .85f) coloredLetters++
        }
        assertTrue("The wordmark must carry About's colored background ink", coloredLetters > 200)
        compose.mainClock.advanceTimeBy(500)
        val after = wordmark.captureToImage().asAndroidBitmap()
        var changed = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) != after.getPixel(x, y)) changed++
        }
        assertTrue("The background ink must keep flowing across the letters", changed > 200)
        save("launch-about-ink.png")
        compose.onNodeWithTag("launch_animation").assertExists()
    }

    @Test fun textRevealFliesToMeasuredTitleAndDoesNotReplayAfterRestoration() {
        compose.mainClock.autoAdvance = false
        var ready by mutableStateOf(false)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Fixture(ThemeMode.DARK, ready = ready) }
        compose.mainClock.advanceTimeBy(350)
        save("launch-reveal.png")
        compose.mainClock.advanceTimeBy(210)
        val central = inkBounds(lightInk = true)
        val anchor = motion.anchor!!
        assertTrue(central.center.y > anchor.bottom + 100f)
        val actionCenter = compose.onNodeWithTag("launch_fixture_action").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("launch_animation").performTouchInput { click(actionCenter) }
        assertEquals(0, clicks)
        compose.runOnUiThread { ready = true }
        compose.mainClock.advanceTimeBy(200)
        assertEquals("Allow a brief pause before departure", 0f, motion.flight.value)
        compose.mainClock.advanceTimeBy(500)
        val middle = inkBounds(lightInk = true)
        assertTrue("Text must travel up rather than jump", middle.center.y < central.center.y && middle.center.y > anchor.center.y)
        assertTrue("Text must shrink while travelling", middle.width < central.width)
        save("launch-flight.png")
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("launch_animation").assertDoesNotExist()
        val landed = inkBounds(lightInk = true)
        assertTrue("Visible title must land inside its actual layout", landed.top >= anchor.top - 2 && landed.bottom <= anchor.bottom + 2)
        save("launch-landed.png")
        compose.onNodeWithTag("launch_fixture_action").performClick(); assertEquals(1, clicks)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("launch_animation").assertDoesNotExist()
    }

    @Test fun darkLaunchKeepsMovingUntilReadyThenFadesWithoutHome() {
        compose.mainClock.autoAdvance = false
        var ready by mutableStateOf(false)
        compose.setContent { Fixture(ThemeMode.DARK, ready = ready, home = false) }
        compose.mainClock.advanceTimeBy(600)
        save("launch-reveal-dark.png")
        val pixel = compose.onNodeWithTag("launch_fixture").captureToImage().asAndroidBitmap().getPixel(10, 10)
        assertTrue("Launch background must follow dark appearance", android.graphics.Color.red(pixel) < 40)
        compose.mainClock.advanceTimeBy(2400)
        compose.onNodeWithTag("launch_animation").assertExists()
        assertEquals(0f, motion.flight.value)
        compose.runOnUiThread { ready = true }
        compose.mainClock.advanceTimeBy(1600)
        compose.onNodeWithTag("launch_animation").assertDoesNotExist()
        assertFalse(motion.active)
    }

    @Test fun disabledAnimationsStillWaitForHomeData() {
        reduced = true
        var ready by mutableStateOf(false)
        compose.setContent { Fixture(ready = ready) }
        compose.onNodeWithTag("launch_animation").assertExists()
        assertEquals(0f, motion.flight.value)
        compose.runOnUiThread { ready = true }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("launch_animation").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun disabledSystemAnimationsSkipTheIntroAndFlight() {
        reduced = true
        compose.setContent { Fixture() }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("launch_animation").fetchSemanticsNodes().isEmpty() }
        assertEquals(0f, motion.flight.value)
        assertFalse(motion.active)
    }

    @Test fun textDoesNotAnimateUnderTheSystemSplash() {
        val windowReady = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture(windowReady = windowReady.value) }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(0f, motion.reveal.value)
        windowReady.value = true
        compose.mainClock.advanceTimeBy(400)
        assertTrue(motion.reveal.value > 0f && motion.reveal.value < 1f)
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("launch_animation").assertDoesNotExist()
    }
}
