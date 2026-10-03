package io.github.currencortex.music.core.media
import io.github.currencortex.music.data.song.Song
import org.junit.Assert.*
import org.junit.Test
class PlaybackModeTest {
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
