package io.github.currencortex.music.core.media

import org.junit.Assert.*
import org.junit.Test

class BufferedProgressTest {
    @Test fun unknownDurationAndRemotePlaybackNeverPretendToBeFullyLoaded() {
        val state = PlayerState(bufferedPositionMs = 20_000)
        assertEquals(0f, state.bufferedProgress(), 0f)
        assertEquals(0f, state.copy(mode = PlayerMode.CAST, durationMs = 40_000).bufferedProgress(), 0f)
        assertEquals(0f, state.copy(resolving = true, durationMs = 40_000).bufferedProgress(), 0f)
        assertEquals(.5f, state.copy(mode = PlayerMode.ROOM, durationMs = 40_000).bufferedProgress(), 0f)
    }

    @Test fun playbackOrSeekPreviewDoesNotAdvanceTheReportedBuffer() {
        val state = PlayerState(durationMs = 40_000, positionMs = 10_000, bufferedPositionMs = 20_000)
        assertEquals(.5f, state.bufferedProgress(), 0f)
        assertEquals(.5f, state.copy(positionMs = 30_000).bufferedProgress(), 0f)
        assertEquals(0f, state.copy(bufferedPositionMs = -1).bufferedProgress(), 0f)
        assertEquals(1f, state.copy(bufferedPositionMs = Long.MAX_VALUE).bufferedProgress(), 0f)
    }
}
