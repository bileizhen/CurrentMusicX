package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.song.Song
import org.junit.Test
import org.junit.Assert.*

class RoamingQueueTest {
    private val songs = (1L..3L).map { Song(it, "$it") }
    @Test fun stationDoesNotWrapAndAcceptsOnlyItsOwnNewRecommendations() {
        val q = PlaybackQueue()
        q.startRadio(songs, "station")
        assertNull(q.state.value.shuffleHistory.firstOrNull())
        assertTrue(q.next()); assertTrue(q.next())
        assertNull(q.previewNext()); assertFalse(q.next(automatic = true))
        assertEquals(3L, q.state.value.current!!.id)
        assertFalse(q.appendRadio(listOf(Song(4, "4")), "old station"))
        assertTrue(q.appendRadio(listOf(Song(3, "duplicate"), Song(4, "4")), "station"))
        assertEquals(listOf(1L, 2L, 3L, 4L), q.state.value.songs.map { it.id })
        assertTrue(q.next()); assertEquals(4L, q.state.value.current!!.id)
    }
    @Test fun explicitPlaybackAndRestoredSnapshotsNeverKeepAnOrphanStation() {
        val q = PlaybackQueue()
        q.startRadio(songs, "station")
        q.previous(); assertEquals(1L, q.state.value.current!!.id)
        val saved = q.state.value
        q.playSong(Song(9, "search")); assertNull(q.state.value.radioSession)
        q.restore(saved); assertNull(q.state.value.radioSession)
        q.startRadio(songs, "new"); q.replace(songs, 0); assertNull(q.state.value.radioSession)
        q.startRadio(songs, "new"); q.setMode(PlaybackMode.SHUFFLE); assertNull(q.state.value.radioSession)
    }
    @Test fun longStationsBoundHistoryWithoutChangingTheCurrentTrack() {
        val q = PlaybackQueue()
        q.startRadio((1L..40L).map { Song(it, "$it") }, "station")
        repeat(35) { q.next() }
        val current = q.state.value.current
        q.appendRadio((41L..43L).map { Song(it, "$it") }, "station")
        assertEquals(current, q.state.value.current)
        assertEquals(20, q.state.value.index)
        assertEquals(28, q.state.value.songs.size)
    }
}
