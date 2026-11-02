package io.github.currencortex.music.feature.player

import io.github.currencortex.music.feature.lyrics.model.*
import org.junit.Assert.*
import org.junit.Test

class LyricManagementErrorTest {
    @Test fun invalidTimelineExplainsTheFailureWithoutExposingRawPayload() {
        assertEquals("歌词时间轴有误，请选择其他候选", lyricManagementError(
            LyricsException(LyricsErrorCode.INVALID_TIMELINE, "private upstream content")))
    }
}
