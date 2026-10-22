package io.github.currencortex.music.feature.library

import io.github.currencortex.music.data.song.Song
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class RecordShelfPreviewSelectionTest {
    private val songs = listOf(Song(1, "One"), Song(2, "Two"), Song(3, "Three"), Song(4, "Video", video = true))

    @Test fun shuffleCanChooseEveryOtherAudioRecordWithoutRepeatingTheCurrentRecord() {
        val random = Random(42)
        val choices = List(100) { nextRecordPreviewIndex(songs, 0, true, random) }
        assertEquals(setOf(1, 2), choices.toSet())
        for (selected in 0..2) {
            repeat(100) {
                val next = requireNotNull(nextRecordPreviewIndex(songs, selected, true, random))
                assertNotEquals(selected, next)
                assertFalse(songs[next].video)
            }
        }
    }

    @Test fun sequentialModeWrapsAndBothModesHandleEmptyAndSingleAudioLists() {
        assertEquals(1, nextRecordPreviewIndex(songs, 0, false))
        assertEquals(0, nextRecordPreviewIndex(songs, 2, false))
        for (shuffle in listOf(false, true)) {
            assertNull(nextRecordPreviewIndex(emptyList(), 0, shuffle))
            assertNull(nextRecordPreviewIndex(listOf(songs.last()), 0, shuffle))
            assertEquals(0, nextRecordPreviewIndex(listOf(songs.first()), 0, shuffle))
            assertEquals(0, nextRecordPreviewIndex(listOf(songs.first(), songs.last()), 0, shuffle))
        }
    }
}
