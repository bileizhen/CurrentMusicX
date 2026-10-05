package io.github.currencortex.music.core.media
import io.github.currencortex.music.data.song.Song
import org.junit.Assert.*
import org.junit.Test
class PlaybackModeTest {
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
