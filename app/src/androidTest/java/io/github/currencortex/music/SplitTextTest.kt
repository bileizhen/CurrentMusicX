package io.github.currencortex.music

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.ui.component.SplitText
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Only welcome-text drawing, layout and restoration; no account or player access. */
class SplitTextTest {
    private var reduced = false
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float get() = if (reduced) 0f else 1f
    })
    private val style = TextStyle(fontSize = 12.sp, color = ComposeColor.Black)
    @Composable private fun Fixture(text: String, ready: Boolean, narrow: Boolean = false) {
        Column(Modifier.fillMaxSize().background(ComposeColor.White).padding(20.dp)) {
            val width = if (narrow) Modifier.width(110.dp) else Modifier
            SplitText(text, ready, style, width.testTag("split"))
            Spacer(Modifier.height(24.dp))
            BasicText(text, width.testTag("reference"), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    private fun bitmap(tag: String) = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
    private fun ink(image: Bitmap, left: Int = 0, right: Int = image.width): Double {
        var amount = 0.0
        for (y in 0 until image.height) for (x in left until right) amount += (255 - Color.red(image.getPixel(x, y))) / 255.0
        return amount
    }
    private fun assertFinalMatchesNormalText() {
        val actual = bitmap("split"); val expected = bitmap("reference")
        assertEquals(expected.width, actual.width); assertEquals(expected.height, actual.height)
        var difference = 0.0
        for (y in 0 until actual.height) for (x in 0 until actual.width) {
            difference += kotlin.math.abs(Color.red(actual.getPixel(x,y)) - Color.red(expected.getPixel(x,y))) / 255.0
        }
        assertTrue("Final shaping and ellipsis must match ordinary text", difference < ink(expected) * .03 + 1)
    }

    @Test fun waitsForReadinessThenRevealsCharactersInOrderWithoutMovingLayout() {
        compose.mainClock.autoAdvance = false
        var ready by mutableStateOf(false)
        compose.setContent { Fixture("欢迎，bileizhen", ready) }
        val bounds = compose.onNodeWithTag("split").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("split").assertTextEquals("欢迎，bileizhen")
        compose.mainClock.advanceTimeBy(1200)
        assertEquals(0.0, ink(bitmap("split")), .01)
        compose.runOnUiThread { ready = true }
        compose.mainClock.advanceTimeBy(420)
        val middle = bitmap("split"); val reference = bitmap("reference")
        val third = middle.width / 3
        val first = ink(middle, 0, third) / ink(reference, 0, third)
        val last = ink(middle, third * 2, middle.width) / ink(reference, third * 2, reference.width)
        assertTrue("Early letters must become visible before trailing letters", first > last + .2)
        assertTrue("Fade must include a partially visible frame", first > .15 && first < .98)
        assertEquals(bounds, compose.onNodeWithTag("split").fetchSemanticsNode().boundsInRoot)
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        File(app.externalCacheDir, "split-text-mid.png").outputStream().use { middle.compress(Bitmap.CompressFormat.PNG,100,it) }
        compose.mainClock.advanceTimeBy(2200)
        assertFinalMatchesNormalText()
    }

    @Test fun unicodeAndEllipsisKeepNormalShapingAndRestorationDoesNotReplay() {
        compose.mainClock.autoAdvance = false
        val text = "欢迎，Lei👩‍💻é 超长昵称测试不会越界"
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Fixture(text, true, narrow = true) }
        compose.mainClock.advanceTimeBy(6000)
        assertFinalMatchesNormalText()
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(32)
        assertFinalMatchesNormalText()
    }

    @Test fun disabledAnimationsStillWaitForReadinessThenShowImmediately() {
        reduced = true
        var ready by mutableStateOf(false)
        compose.setContent { Fixture("欢迎，bileizhen", ready) }
        assertEquals(0.0, ink(bitmap("split")), .01)
        compose.runOnUiThread { ready = true }
        compose.waitForIdle()
        assertFinalMatchesNormalText()
    }
}
