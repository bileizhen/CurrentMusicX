package io.github.currencortex.music.feature.settings

import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.song.Song
import org.junit.Assert.*
import org.junit.Test

class EffectsPlaybackPresentationTest {
    @Test fun timelineAndBufferTicksDoNotInvalidateTheGalleryButPlaybackChangesDo() {
        val state = PlayerState(song = Song(123, "Track"), playing = true)
        assertEquals(state.effectsPresentation(), state.copy(positionMs = 12_000,
            bufferedPositionMs = 30_000, anchorRealtimeMs = 99, timelineRevision = 2).effectsPresentation())
        assertNotEquals(state.effectsPresentation(), state.copy(playing = false).effectsPresentation())
        assertNotEquals(state.effectsPresentation(), state.copy(mode = PlayerMode.CAST).effectsPresentation())
        assertNotEquals(state.effectsPresentation(), state.copy(song = Song(456, "Next")).effectsPresentation())
        assertNotEquals(state.effectsPresentation(), state.copy(song = state.song!!.copy(cover = "new")).effectsPresentation())
    }
}
