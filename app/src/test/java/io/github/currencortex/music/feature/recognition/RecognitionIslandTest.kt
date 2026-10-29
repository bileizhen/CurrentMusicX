package io.github.currencortex.music.feature.recognition

import io.github.currencortex.music.data.song.Song
import org.junit.Assert.*
import org.junit.Test

class RecognitionIslandTest {
    @Test fun islandDistinguishesCaptureMatchingResultsAndFailures() {
        val capture = RecognitionState(RecognitionPhase.RECORDING, seconds = 2)
        assertEquals("正在聆听 · 2 秒", recognitionIslandTitle(capture))
        assertTrue(recognitionIslandSubtitle(capture).contains("最长 10 秒"))
        assertTrue(recognitionIslandSubtitle(capture.copy(seconds = 3)).contains("立即识别"))
        assertEquals("正在识别音乐…", recognitionIslandTitle(capture.copy(phase = RecognitionPhase.RECOGNIZING)))
        val matched = RecognitionState(RecognitionPhase.MATCHED, songs = listOf(Song(1, "测试歌曲", "测试歌手")))
        assertEquals("测试歌曲", recognitionIslandTitle(matched))
        assertEquals("测试歌手", recognitionIslandSubtitle(matched))
        assertEquals("暂未识别到歌曲", recognitionIslandTitle(RecognitionState(RecognitionPhase.NO_MATCH)))
        assertEquals("需要麦克风权限", recognitionIslandSubtitle(RecognitionState(RecognitionPhase.ERROR, message = "需要麦克风权限")))
    }

    @Test fun completedResultSurvivesDetachButNewAttemptExplicitlyClearsIt() {
        try {
            RecognitionDesktopSession.attach()
            val matched = RecognitionState(RecognitionPhase.MATCHED, songs = listOf(Song(9, "Result")))
            RecognitionDesktopSession.publish(matched)
            RecognitionDesktopSession.clearResult()
            assertEquals(matched, RecognitionDesktopSession.state.value)
            RecognitionDesktopSession.detach()
            assertEquals(matched, RecognitionDesktopSession.state.value)
            RecognitionDesktopSession.clearResult()
            assertEquals(RecognitionState(), RecognitionDesktopSession.state.value)
        } finally { RecognitionDesktopSession.detach(); RecognitionDesktopSession.clearResult() }
    }
}
