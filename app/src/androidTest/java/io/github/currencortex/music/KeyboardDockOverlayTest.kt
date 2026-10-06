package io.github.currencortex.music

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Only the IME overlay transition: no accounts, network or playback. */
class KeyboardDockOverlayTest {
    @get:Rule val compose = createComposeRule()
    private var keyboard by mutableStateOf(false)
    private var mounts = 0
    private var disposals = 0

    @Composable private fun Fixture(unified: Boolean) {
        LeiTheme(AppearanceSettings(blur = false)) {
            Box(Modifier.fillMaxSize().background(ComposeColor.White).testTag("overlay_viewport")) {
                KeyboardDockOverlay(keyboard) {
                    val mini: @Composable () -> Unit = {
                        DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                        Box(Modifier.fillMaxWidth().height(48.dp).background(ComposeColor.Red).testTag("retained_mini"))
                    }
                    if (unified) UnifiedMusicDock(false, true, true, false, Modifier.align(Alignment.BottomCenter), mini = mini, navigation = {})
                    else Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) { mini() }
                }
            }
        }
    }
    private fun alpha() = compose.onNodeWithTag("keyboard_dock_overlay").fetchSemanticsNode().config[KeyboardDockAlpha]
    @Test fun unifiedDockRetainsMiniAcrossKeyboardFadeAndRapidReversal() = verify(unified = true)
    @Test fun plainDockRetainsMiniAcrossKeyboardFadeAndRapidReversal() = verify(unified = false)

    private fun verify(unified: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixture(unified) }
        val initial = compose.onNodeWithTag("retained_mini").fetchSemanticsNode()
        val viewport = compose.onNodeWithTag("overlay_viewport").fetchSemanticsNode().boundsInRoot
        fun pixel(): Int {
            val bitmap = compose.onNodeWithTag("overlay_viewport").captureToImage().asAndroidBitmap()
            return bitmap.getPixel((initial.boundsInRoot.center.x-viewport.left).toInt(), (initial.boundsInRoot.center.y-viewport.top).toInt())
        }
        assertEquals(1f, alpha(), .001f)
        assertEquals(Color.RED, pixel())
        compose.runOnUiThread { keyboard = true }
        compose.mainClock.advanceTimeBy(64)
        val leaving = alpha()
        assertTrue("Hiding must have intermediate opacity", leaving > 0f && leaving < 1f)
        assertTrue("The rendered mini must fade rather than vanish", Color.green(pixel()) in 1..254)
        // A quick focus reversal must continue from its current opacity.
        compose.runOnUiThread { keyboard = false }
        compose.mainClock.advanceTimeBy(64)
        assertTrue(alpha() > leaving && alpha() < 1f)
        compose.mainClock.advanceTimeBy(300)
        assertEquals(1f, alpha(), .001f)
        compose.runOnUiThread { keyboard = true }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag("retained_mini").assertDoesNotExist()
        assertEquals(Color.WHITE, pixel())
        assertEquals("Artwork must stay mounted behind the keyboard", 1, mounts)
        assertEquals(0, disposals)
        compose.runOnUiThread { keyboard = false }
        compose.mainClock.advanceTimeBy(64)
        assertTrue("Closing the keyboard must reveal gradually", alpha() > 0f && alpha() < 1f)
        assertTrue(Color.green(pixel()) in 1..254)
        compose.mainClock.advanceTimeBy(350)
        val restored = compose.onNodeWithTag("retained_mini").fetchSemanticsNode()
        assertEquals(initial.id, restored.id)
        assertEquals(initial.boundsInRoot, restored.boundsInRoot)
        assertEquals(viewport, compose.onNodeWithTag("overlay_viewport").fetchSemanticsNode().boundsInRoot)
        assertEquals(Color.RED, pixel())
        assertEquals(1, mounts); assertEquals(0, disposals)
    }
}
