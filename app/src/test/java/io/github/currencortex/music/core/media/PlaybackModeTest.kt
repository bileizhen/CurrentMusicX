package io.github.currencortex.music.core.media
import io.github.currencortex.music.data.song.Song
import org.junit.Assert.*
import org.junit.Test
class PlaybackModeTest {
    @Test fun heartRecommendationsPreserveCurrentPlaybackAndAppendInServerOrder() {
        val queue = PlaybackQueue()
        val songs = (1L..5L).map { Song(it, "$it") }
        queue.replace(songs, 1)
        queue.startHeart(listOf(songs[1], songs[3], songs[4], songs[3]), 42, 1500)
        assertEquals(PlaybackMode.HEART, queue.state.value.mode)
        assertEquals(listOf(2L, 4L, 5L), queue.state.value.songs.map { it.id })
        assertEquals(2L, queue.state.value.current!!.id); assertEquals(1500L, queue.state.value.positionMs)
        assertEquals(4L, queue.previewNext()!!.id)
        queue.next(automatic = true); assertEquals(4L, queue.state.value.current!!.id)
        queue.appendHeart(listOf(songs[4], songs[0]), 42)
        assertEquals(listOf(2L, 4L, 5L, 1L), queue.state.value.songs.map { it.id })
        queue.previous(); assertEquals(2L, queue.state.value.current!!.id)
        queue.setMode(PlaybackMode.LIST)
        queue.appendHeart(listOf(songs[2]), 42)
        assertEquals(4, queue.state.value.songs.size); assertEquals(0L, queue.state.value.heartPlaylistId)
        queue.startHeart(listOf(songs[4]), 42, 2000)
        queue.replace(songs, 0)
        assertEquals(PlaybackMode.LIST, queue.state.value.mode)
    }
    @Test fun explicitPreviousAndNextFollowListOrderInListAndSingleRepeatModes() {
        val songs = (1L..4L).map { Song(it, "$it") }
        for (mode in listOf(PlaybackMode.LIST, PlaybackMode.ONE)) {
            val queue = PlaybackQueue(); queue.replace(songs, 0); queue.setMode(mode)
            queue.previous(); assertEquals(4L, queue.state.value.current!!.id)
            queue.next(); assertEquals(1L, queue.state.value.current!!.id)
            queue.next(); assertEquals(2L, queue.state.value.current!!.id)
            queue.previous(); assertEquals(1L, queue.state.value.current!!.id)
        }
    }

    @Test fun automaticSingleRepeatAndExplicitNextAreDifferentAndShuffleDoesNotRepeat() {
        val queue = PlaybackQueue(); queue.replace((1L..3L).map { Song(it, "$it") }, 2)
        queue.state.value = queue.state.value.copy(mode = PlaybackMode.ONE)
        queue.next(true); assertEquals(2, queue.state.value.index)
        queue.next(); assertEquals(0, queue.state.value.index)
        queue.previous(); assertEquals(2, queue.state.value.index)
        queue.state.value = queue.state.value.copy(mode = PlaybackMode.SHUFFLE)
        repeat(20) { val old = queue.state.value.index; queue.next(); assertNotEquals(old, queue.state.value.index) }
    }
}
