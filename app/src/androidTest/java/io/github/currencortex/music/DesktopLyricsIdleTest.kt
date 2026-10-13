package io.github.currencortex.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.feature.lyrics.desktop.*
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DesktopLyricsIdleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun idleHidesChromeAndIslandCanToggleAndRestoreControlsWithoutChangingLock() {
        val line = LyricLine(0, 10000, "音乐 Hello", translation = "Music beside you")
        val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(line)), songId = 42))
        val player = MutableStateFlow(PlayerState(song = Song(42, "Idle song"), positionMs = 2000))
        val options = MutableStateFlow(DesktopLyricsSettings(appearance = DesktopAppearance.ISLAND))
        compose.setContent {
            Box(Modifier.width(340.dp)) {
                DesktopLyricsSurface(lyrics, player, options, {}, {}, {})
            }
        }
        fun awaitHidden() = compose.waitUntil(6000) {
            compose.onAllNodesWithTag("desktop_edit_toolbar").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("desktop_edit_toolbar").assertIsDisplayed()
        // Holding a finger down must keep the editing controls visible.
        val coordinates = compose.onNodeWithTag("desktop_edit_toolbar").fetchSemanticsNode().layoutInfo.coordinates
        val point = coordinates.localToScreen(Offset(30f, coordinates.size.height / 2f))
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = android.os.SystemClock.uptimeMillis()
        fun touch(action: Int) {
            val event = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action, point.x, point.y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        touch(android.view.MotionEvent.ACTION_DOWN)
        android.os.SystemClock.sleep(3300)
        compose.onNodeWithTag("desktop_edit_toolbar").assertIsDisplayed()
        // Use real Android event timestamps across the wall-clock hold. Split
        // Compose injections can leave UP stale and the observer pressed forever.
        touch(android.view.MotionEvent.ACTION_UP)
        awaitHidden()
        compose.onNodeWithText(line.text).assertIsDisplayed()
        compose.onNodeWithTag("desktop_window_translation").assertIsDisplayed()
        assertFalse(options.value.locked)
        compose.onNodeWithTag("desktop_island").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
        compose.onNodeWithTag("desktop_edit_toolbar").assertDoesNotExist()
        compose.onNodeWithTag("desktop_island").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText(line.text).assertIsDisplayed()
        compose.onNodeWithTag("desktop_island").performTouchInput { longClick() }
        compose.onNodeWithTag("desktop_edit_toolbar").assertIsDisplayed()
        compose.runOnIdle { options.value = options.value.copy(locked = true) }
        compose.onNodeWithTag("desktop_edit_toolbar").assertDoesNotExist()
        compose.onNodeWithTag("desktop_island").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("desktop_island_fps").assertIsDisplayed()
        compose.onNodeWithTag("desktop_island").performTouchInput { longClick() }
        compose.onNodeWithTag("desktop_edit_toolbar").assertDoesNotExist()
        compose.runOnIdle { options.value = options.value.copy(locked = false) }
        compose.onNodeWithTag("desktop_edit_toolbar").assertIsDisplayed()
        assertFalse(options.value.locked)
    }
}
