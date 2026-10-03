package io.github.currencortex.music.core.media
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.core.network.ApiJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test
class PlaybackQueueTest {
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
