package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.PlaylistTrack
import io.github.currencortex.music.ui.component.LocalRoomSongRequest
import io.github.currencortex.music.ui.component.LocalSongMenu
import io.github.currencortex.music.ui.component.RoomSongRequest
import io.github.currencortex.music.ui.component.SongMenu
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlaylistTrackLongPressTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pendingRoomRequestDisablesLongPressThenRoutesActionsToTheRoom() {
        var pending by mutableStateOf(true)
        var selected: SongMenu? = null
        var requests = 0
        var plays = 0
        compose.setContent {
            LeiTheme(AppearanceSettings()) {
                CompositionLocalProvider(
                    LocalRoomSongRequest provides RoomSongRequest({ requests++ }, { pending }),
                    LocalSongMenu provides { selected = it },
                ) {
                    PlaylistTrack(Song(7, "Room track"), false, false, { plays++ }, {}, {}) {}
                }
            }
        }
        compose.onNodeWithTag("playlist_song_7").assertIsNotEnabled().performTouchInput { longClick() }
        compose.runOnIdle { assertNull(selected); pending = false }
        compose.onNodeWithTag("playlist_song_7").performTouchInput { longClick() }
        compose.runOnIdle {
            val menu = requireNotNull(selected)
            assertTrue(menu.roomRequest)
            assertFalse(requireNotNull(menu.previewAnchor).isEmpty)
            assertEquals(0, requests)
            assertEquals(0, plays)
            menu.play()
            assertEquals(1, requests)
            assertEquals(0, plays)
        }
    }

    @Test fun tapStillPlaysAndMoreKeepsTheSheetWhileLongPressOnlyPreviews() {
        var selected: SongMenu? = null
        var plays = 0
        compose.setContent {
            LeiTheme(AppearanceSettings()) {
                CompositionLocalProvider(LocalSongMenu provides { selected = it }) {
                    PlaylistTrack(Song(7, "Track"), false, false, { plays++ }, {}, {}) {}
                }
            }
        }
        compose.onNodeWithTag("playlist_song_7").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, plays); assertNull(selected) }
        compose.onNodeWithTag("song_menu_7").performClick()
        compose.runOnIdle { assertNull(requireNotNull(selected).previewAnchor); selected = null }
        compose.onNodeWithTag("playlist_song_7").performTouchInput { longClick() }
        compose.runOnIdle {
            assertFalse(requireNotNull(requireNotNull(selected).previewAnchor).isEmpty)
            assertEquals(1, plays)
        }
    }
}
