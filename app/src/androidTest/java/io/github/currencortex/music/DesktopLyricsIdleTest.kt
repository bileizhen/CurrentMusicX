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

    @Test fun idleHidesOnlyChromeAndCaptionCanRestoreAndDragWithoutChangingLock() {
        val line = LyricLine(0, 10000, "音乐 Hello", translation = "Music beside you")
        val lyrics = MutableStateFlow(PlaybackLyricsState(LyricsDocument(listOf(line)), songId = 42))
        val player = MutableStateFlow(PlayerState(song = Song(42, "Idle song"), positionMs = 2000))
        val options = MutableStateFlow(DesktopLyricsSettings(appearance = DesktopAppearance.CAPTION))
        var moved = 0f
        var released = 0
        compose.setContent {
            Box(Modifier.width(340.dp)) {
                DesktopLyricsSurface(lyrics, player, options, { 200f },
                    { x, y -> moved += kotlin.math.abs(x) + kotlin.math.abs(y) }, { released++ }, {}, {}, {})
            }
        }
        fun awaitHidden() = compose.waitUntil(6000) {
            compose.onAllNodesWithTag("desktop_edit_toolbar").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("desktop_edit_toolbar").assertIsDisplayed()
        // Holding a finger down must not collapse the drag target underneath it.
        compose.onNodeWithTag("desktop_edit_toolbar").performTouchInput { down(centerLeft + Offset(30f, 0f)) }
        android.os.SystemClock.sleep(3300)
        compose.onNodeWithTag("desktop_edit_toolbar").assertIsDisplayed()
        compose.onNodeWithTag("desktop_edit_toolbar").performTouchInput { up() }
        awaitHidden()
        compose.onNodeWithTag("desktop_window_title").assertDoesNotExist()
        compose.onNodeWithText(line.text).assertIsDisplayed()
        compose.onNodeWithTag("desktop_window_translation").assertIsDisplayed()
        assertFalse(options.value.locked)
        compose.onNodeWithText(line.text).performTouchInput { click() }
        compose.onNodeWithTag("desktop_edit_toolbar").assertIsDisplayed()
        awaitHidden()
        compose.onNodeWithText(line.text).performTouchInput {
            swipe(center, center + Offset(60f, 40f), 500)
        }
        compose.onNodeWithTag("desktop_edit_toolbar").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue("Compact lyrics remain draggable", moved > 0f)
            assertEquals(1, released)
            assertFalse("Auto hiding must not persist a touch-through lock", options.value.locked)
            options.value = options.value.copy(locked = true)
        }
        compose.onNodeWithTag("desktop_edit_toolbar").assertDoesNotExist()
        compose.onNodeWithText(line.text).performTouchInput { click() }
        compose.onNodeWithTag("desktop_edit_toolbar").assertDoesNotExist()
        compose.runOnIdle { options.value = options.value.copy(locked = false) }
        compose.onNodeWithTag("desktop_edit_toolbar").assertIsDisplayed()
    }
}
