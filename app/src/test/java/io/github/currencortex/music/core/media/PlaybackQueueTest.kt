package io.github.currencortex.music.core.media
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.core.network.ApiJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test
class PlaybackQueueTest {
    @Test fun shufflePreviousRetracesHistoryAndNextReusesForwardHistoryAndPreload() {
        val queue = PlaybackQueue()
        queue.replace((1L..10L).map { Song(it, "$it") }, 0)
        queue.setMode(PlaybackMode.SHUFFLE)
        val played = mutableListOf(queue.state.value.current)
        repeat(3) { queue.next(random = kotlin.random.Random(it + 7)); played += queue.state.value.current }
        queue.previewNext(kotlin.random.Random(20)) // An abandoned preload must not affect going back.
        queue.previous()
        assertEquals(played[2], queue.state.value.current)
        assertEquals(played[3], queue.previewNext(kotlin.random.Random(99)))
        queue.previous()
        assertEquals(played[1], queue.state.value.current)
        assertEquals(played[2], queue.previewNext())
        queue.next(); assertEquals(played[2], queue.state.value.current)
        queue.next(automatic = true); assertEquals(played[3], queue.state.value.current)
        repeat(5) { queue.previous() }
        assertEquals("No earlier history restarts the first played song instead of picking a random neighbor", played[0], queue.state.value.current)
        assertEquals(0L, queue.state.value.positionMs)
        queue.next(); assertEquals(played[1], queue.state.value.current)
    }

    @Test fun shuffleHistorySurvivesRestoreAndInvalidatesOnQueueSelectionOrModeChanges() {
        val songs = (1L..8L).map { Song(it, "$it") }
        val queue = PlaybackQueue()
        queue.replace(songs, 0); queue.setMode(PlaybackMode.SHUFFLE)
        queue.next(random = kotlin.random.Random(7))
        val first = queue.state.value.current
        queue.next(random = kotlin.random.Random(9))
        val second = queue.state.value.current
        queue.previous()
        val restored = PlaybackQueue()
        restored.restore(ApiJson.decodeFromString<QueueSnapshot>(ApiJson.encodeToString(queue.state.value)))
        assertEquals(first, restored.state.value.current)
        assertEquals(second, restored.previewNext())
        restored.next(); assertEquals(second, restored.state.value.current)
        restored.select(0)
        restored.previous(); assertEquals(songs[0], restored.state.value.current)
        restored.next(); restored.setMode(PlaybackMode.LIST)
        assertTrue(restored.state.value.shuffleHistory.isEmpty())
        restored.setMode(PlaybackMode.SHUFFLE)
        val before = restored.state.value.current
        restored.previous(); assertEquals(before, restored.state.value.current)
        restored.remove(0)
        assertTrue(restored.state.value.shuffleHistory.isEmpty())
        restored.replace(songs, 0)
        assertTrue(restored.state.value.shuffleHistory.isEmpty())
        restored.restore(QueueSnapshot(songs, 0, mode = PlaybackMode.SHUFFLE,
            shuffleHistory = listOf(99, 0), shuffleHistoryIndex = 1))
        assertTrue("Invalid restored indices cannot be replayed", restored.state.value.shuffleHistory.isEmpty())
        restored.previous(); assertEquals(songs[0], restored.state.value.current)
    }

    @Test fun preloadPreviewDoesNotMoveQueueAndUsesActualShuffleTargetAfterPositionUpdates() {
        val queue = PlaybackQueue()
        queue.replace((1L..10L).map { Song(it, "$it") }, 8)
        queue.state.value = queue.state.value.copy(mode = PlaybackMode.SHUFFLE)
        val before = queue.state.value
        val next = queue.previewNext(kotlin.random.Random(7))!!
        assertEquals(before, queue.state.value)
        queue.state.value = queue.state.value.copy(positionMs = 1500)
        assertEquals(next, queue.previewNext(kotlin.random.Random(9)))
        queue.next(random = kotlin.random.Random(11))
        assertEquals(next, queue.state.value.current)
        assertEquals(0L, queue.state.value.positionMs)
    }
    @Test fun preloadPreviewRespectsRepeatAndChangedQueue() {
        val queue = PlaybackQueue()
        queue.replace(listOf(Song(1, "one"), Song(2, "two"), Song(3, "video", video = true)), 0)
        assertEquals(2L, queue.previewNext()!!.id)
        queue.state.value = queue.state.value.copy(mode = PlaybackMode.ONE)
        assertNull(queue.previewNext())
        queue.next(); assertEquals(2L, queue.state.value.current!!.id)
        queue.state.value = queue.state.value.copy(mode = PlaybackMode.LIST)
        assertNull(queue.previewNext())
        queue.replace(listOf(Song(9, "nine"), Song(10, "ten")), 1)
        assertEquals(9L, queue.previewNext()!!.id)
        queue.next(); assertEquals(9L, queue.state.value.current!!.id)
    }
    @Test fun preservesCurrentSongWhenRemovingEarlierTrackAndKeepsPlayNextOrder() {
        val queue = PlaybackQueue()
        val songs = (1L..3L).map { Song(it, "$it") }
        queue.replace(songs, 1)
        queue.add(Song(4, "4"), next = true)
        assertEquals(listOf(1L, 2L, 4L, 3L), queue.state.value.songs.map { it.id })
        assertFalse(queue.remove(0)); assertEquals(2L, queue.state.value.current!!.id)
        queue.next(); assertEquals(4L, queue.state.value.current!!.id)
        assertTrue(queue.remove(1)); assertEquals(3L, queue.state.value.current!!.id)
        queue.clear(); queue.next(); queue.previous(); assertNull(queue.state.value.current)
    }
    @Test fun serializedRestoreIncludesPositionAndNoAudioUrl() {
        val snapshot = QueueSnapshot(listOf(Song(7, "name")), 0, 4567, PlaybackMode.ONE, AudioQuality.LOSSLESS)
        val json = ApiJson.encodeToString(snapshot)
        val queue = PlaybackQueue(); queue.restore(ApiJson.decodeFromString(json))
        assertEquals(snapshot, queue.state.value); assertFalse(json.contains("url"))
    }
}
