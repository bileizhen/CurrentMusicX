package io.github.currencortex.music

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.core.media.ListenTogetherPair
import io.github.currencortex.music.core.media.ListenTogetherParticipant
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.desktop.DesktopIslandCover
import io.github.currencortex.music.feature.lyrics.desktop.DesktopIslandFrame
import io.github.currencortex.music.ui.component.ListeningPairArtwork
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ListeningPairArtworkTest {
    @get:Rule val compose = createComposeRule()
    private fun member(id: Long, name: String) = ListenTogetherParticipant(id, name, "")

    @Test fun equalCircularPortraitsOverlapAndMissingMemberUsesPersonPlaceholder() {
        val pair = mutableStateOf(ListenTogetherPair(listOf(member(1, "我"))))
        compose.setContent { ListeningPairArtwork(pair.value, Modifier.size(64.dp, 40.dp)) }
        compose.onNodeWithContentDescription("一起听：我、等待好友加入").assertExists()
        val left = compose.onNodeWithTag("listening_pair_avatar_0", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val right = compose.onNodeWithTag("listening_pair_avatar_1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((left.right - left.left).value, (left.bottom - left.top).value, .1f)
        assertEquals(left.right - left.left, right.right - right.left)
        assertEquals(left.bottom - left.top, right.bottom - right.top)
        assertTrue("Portraits must overlap", left.right > right.left)
        assertEquals(left.top, right.top)
        assertEquals(left.bottom, right.bottom)
        compose.runOnIdle { pair.value = ListenTogetherPair(listOf(member(1, "我"), member(2, "好友"), member(3, "第三人"))) }
        compose.onNodeWithContentDescription("一起听：我、好友").assertExists()
        compose.onNodeWithTag("listening_pair_avatar_2", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("歌曲封面", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun islandSwitchesLiveBetweenCoverAndPairWithoutAnimatingFacesOnTrackChanges() {
        compose.mainClock.autoAdvance = false
        val pair = mutableStateOf<ListenTogetherPair?>(null)
        val song = mutableStateOf(Song(1, "First", "Artist"))
        compose.setContent { DesktopIslandCover(song.value, null, motion = true,
            modifier = Modifier.size(64.dp, 40.dp), listenTogether = pair.value) }
        compose.onNodeWithTag("desktop_island_cover").assertExists()
        compose.runOnIdle { pair.value = ListenTogetherPair(listOf(member(1, "我"), member(2, "好友"))) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("desktop_island_cover").assertDoesNotExist()
        compose.onNodeWithTag("desktop_island_pair_artwork").assertExists()
        val before = compose.onNodeWithTag("listening_pair_avatar_1", true).getUnclippedBoundsInRoot()
        compose.runOnIdle { song.value = Song(2, "Next", "Other artist") }
        compose.mainClock.advanceTimeBy(210)
        assertEquals(before, compose.onNodeWithTag("listening_pair_avatar_1", true).getUnclippedBoundsInRoot())
        compose.runOnIdle { pair.value = pair.value!!.copy(participants = listOf(member(1, "我"), member(4, "新好友"))) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("一起听：我、新好友").assertExists()
        compose.runOnIdle { pair.value = null }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("desktop_island_pair_artwork").assertDoesNotExist()
        compose.onNodeWithTag("desktop_island_cover").assertExists()
    }

    @Test fun islandFrameReservesBothPortraitsBeforeTheSongHeading() {
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            DesktopIslandFrame(Song(1, "Short title", "Artist"), Modifier.width(220.dp),
                metadata = 120.dp, presentation = 0f,
                listenTogether = ListenTogetherPair(listOf(member(1, "我"), member(2, "好友")))) {}
        } }
        val left = compose.onNodeWithTag("listening_pair_avatar_0", true).getUnclippedBoundsInRoot()
        val right = compose.onNodeWithTag("listening_pair_avatar_1", true).getUnclippedBoundsInRoot()
        val metadata = compose.onNodeWithTag("desktop_island_metadata").getUnclippedBoundsInRoot()
        assertEquals((left.right - left.left).value, (right.right - right.left).value, .1f)
        assertEquals((left.bottom - left.top).value, (right.bottom - right.top).value, .1f)
        assertTrue(right.right < metadata.left)
        compose.onNodeWithTag("desktop_island_cover").assertDoesNotExist()
    }
}
