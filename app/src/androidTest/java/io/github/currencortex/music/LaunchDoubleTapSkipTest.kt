package io.github.currencortex.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.LaunchStyle
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class LaunchDoubleTapSkipTest(private val style: LaunchStyle) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun styles() = listOf(LaunchStyle.CLASSIC, LaunchStyle.HALO, LaunchStyle.SIGNATURE)
    }
    @get:Rule val compose = createComposeRule()
    private var finishes = 0
    private var pageClicks = 0
    private var touchSlop = 0f

    private fun show(removeOnFinish: Boolean, preview: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            val configuration = LocalViewConfiguration.current
            SideEffect { touchSlop = configuration.touchSlop }
            var done by remember { mutableStateOf(false) }
            val brand = remember { LaunchBrandState() }
            Box(Modifier.fillMaxSize().clickable { pageClicks++ }) {
                if (!done || !removeOnFinish) {
                    val finish = { finishes++; done = true }
                    when (style) {
                        LaunchStyle.CLASSIC -> LaunchBrandOverlay(brand, ready = false,
                            canLandOnHome = false, enableBlur = false, onFinished = finish)
                        LaunchStyle.HALO -> HaloLaunchOverlay("双击测试", ready = false,
                            preview = preview, onFinished = finish)
                        LaunchStyle.SIGNATURE -> SignatureLaunchOverlay("双击测试", preview = preview, onFinished = finish)
                        else -> error("No animation")
                    }
                }
            }
        } }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test fun singleTapAndSwipeDoNotSkipButDoubleTapDoesWithoutClickThrough() {
        show(removeOnFinish = true)
        val overlay = compose.onNodeWithTag("launch_animation", useUnmergedTree = true)
        overlay.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(350)
        assertEquals(0, finishes)
        assertEquals("Single tap must stay in the overlay", 0, pageClicks)
        overlay.performTouchInput { swipeLeft(durationMillis = 120) }
        compose.mainClock.advanceTimeBy(150)
        assertEquals(0, finishes)
        assertEquals("Swipe must stay in the overlay", 0, pageClicks)
        overlay.performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeByFrame()
        overlay.assertDoesNotExist()
        assertEquals(1, finishes)
        assertEquals(0, pageClicks)
    }

    @Test fun doubleTapWithSmallFingerMovementsStillSkipsWithoutClickThrough() {
        show(removeOnFinish = true)
        val overlay = compose.onNodeWithTag("launch_animation", useUnmergedTree = true)
        overlay.performTouchInput {
            val drift = Offset(touchSlop * .25f, touchSlop * .25f)
            down(center)
            moveTo(center + drift, delayMillis = 40)
            up()
            advanceEventTime(100)
            down(center)
            moveTo(center - drift, delayMillis = 40)
            up()
        }
        compose.mainClock.advanceTimeByFrame()
        overlay.assertDoesNotExist()
        assertEquals(1, finishes)
        assertEquals(0, pageClicks)
    }

    @Test fun swipeFollowedByOneTapDoesNotCountAsDoubleTap() {
        show(removeOnFinish = false)
        val overlay = compose.onNodeWithTag("launch_animation", useUnmergedTree = true)
        overlay.performTouchInput {
            swipeLeft(durationMillis = 100)
            advanceEventTime(60)
            click()
        }
        compose.mainClock.advanceTimeBy(350)
        assertEquals(0, finishes)
        assertEquals(0, pageClicks)
    }

    @Test fun previewCanBeSkippedAndCompletionCannotRunTwice() {
        show(removeOnFinish = false, preview = true)
        val overlay = compose.onNodeWithTag("launch_animation", useUnmergedTree = true)
        overlay.performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, finishes)
        overlay.performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(15_000)
        assertEquals(1, finishes)
        assertEquals(0, pageClicks)
    }
}
