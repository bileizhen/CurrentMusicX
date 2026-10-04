package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.feature.player.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated controls: no playback service, account, network or production preferences. */
class PlayerControlsFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactActionsAnimateWhileKeepingTouchTargetsAndClickSemantics() {
        var playing by mutableStateOf(false)
        var selected by mutableStateOf(false)
        var toggles = 0
        var actions = 0
        var disabledClicks = 0
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            Column(Modifier.width(300.dp).background(Color.Black).padding(vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    PlayerTransportButton("上一首", {}, Modifier.testTag("previous"), direction = -1)
                    PlayerTransportButton(if (playing) "暂停" else "播放", { toggles++; playing = !playing },
                        Modifier.testTag("toggle"), playing = playing)
                    PlayerTransportButton("下一首", {}, Modifier.testTag("next"), direction = 1)
                }
                PlayerFunctionBar {
                    PlayerIconButton(PlayerIcon.LYRICS, "歌词", { actions++; selected = !selected },
                        Modifier.testTag("lyrics"), selected)
                    PlayerIconButton(PlayerIcon.CAST, "投屏", {}, Modifier.testTag("cast"))
                    PlayerIconButton(PlayerIcon.QUEUE, "队列", {}, Modifier.testTag("queue"))
                }
                PlayerTransportButton("不可用", { disabledClicks++ }, Modifier.testTag("disabled"), enabled = false)
            }
        }
        compose.waitForIdle()
        val toggle = compose.onNodeWithTag("toggle")
        val lyrics = compose.onNodeWithTag("lyrics")
        fun visual(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[PlayerButtonVisuals]
        fun brightPixels(node: SemanticsNodeInteraction): Int {
            val pixels = node.captureToImage().toPixelMap()
            var count = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width)
                if (pixels[x, y].red > .4f) count++
            return count
        }
        val bounds = toggle.fetchSemanticsNode().boundsInRoot
        val first = lyrics.fetchSemanticsNode().boundsInRoot
        val middle = compose.onNodeWithTag("cast").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("queue").fetchSemanticsNode().boundsInRoot
        assertTrue("Each secondary action retains a full touch target", first.width >= 48 * density - 1)
        assertTrue("The action gap is smaller than a touch target", middle.left - first.right < first.width)
        assertEquals("The actions form a centered group", (first.left + last.right) / 2,
            compose.onNodeWithTag("player_function_bar").fetchSemanticsNode().boundsInRoot.center.x, 1f)
        val normalPixels = brightPixels(toggle)
        compose.mainClock.autoAdvance = false
        toggle.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(120)
        assertTrue(visual("toggle").pressed)
        assertTrue("Held transport button visibly contracts", brightPixels(toggle) < normalPixels)
        assertEquals("Only the icon scales, not its touch target", bounds, toggle.fetchSemanticsNode().boundsInRoot)
        toggle.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithContentDescription("暂停").assertExists()
        assertEquals("Release invokes the transport action exactly once", 1, toggles)
        assertTrue("Play/pause has a visible transition instead of an abrupt swap",
            visual("toggle").glyphProgress > 0f && visual("toggle").glyphProgress < 1f)
        compose.mainClock.advanceTimeBy(400)
        assertEquals(1f, visual("toggle").glyphProgress, .001f)
        assertEquals(1f, visual("toggle").scale, .001f)
        assertEquals(bounds, toggle.fetchSemanticsNode().boundsInRoot)
        lyrics.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(120)
        assertTrue("Functional actions also respond to a held press", visual("lyrics").scale < .95f)
        lyrics.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(64)
        assertEquals(1, actions)
        assertTrue("Selected action brightens gradually", visual("lyrics").alpha > .65f && visual("lyrics").alpha < 1f)
        compose.mainClock.advanceTimeBy(400)
        lyrics.assertIsSelected()
        lyrics.performTouchInput { down(center); moveTo(Offset(-100f, -100f)); up() }
        compose.mainClock.advanceTimeBy(400)
        assertEquals("Moving out cancels the action", 1, actions)
        assertFalse(visual("lyrics").pressed)
        assertEquals(1f, visual("lyrics").scale, .001f)
        compose.onNodeWithTag("disabled").performTouchInput { down(center); up() }
        compose.mainClock.advanceTimeBy(200)
        assertEquals(0, disabledClicks)
        assertFalse(visual("disabled").pressed)
        toggle.performClick()
        compose.mainClock.advanceTimeBy(400)
        assertEquals(2, toggles)
        assertEquals("Pause/play also animates in reverse", 0f, visual("toggle").glyphProgress, .001f)
        compose.mainClock.autoAdvance = true
    }
}
