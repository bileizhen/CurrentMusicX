package io.github.currencortex.music.core.media
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.core.network.ApiJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test
class PlaybackQueueTest {
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
