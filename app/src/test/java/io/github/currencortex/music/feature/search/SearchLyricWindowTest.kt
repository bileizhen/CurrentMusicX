package io.github.currencortex.music.feature.search

import org.junit.Assert.*
import org.junit.Test

class SearchLyricWindowTest {
    @Test fun shortLinesPassThroughUnchanged() {
        assertEquals("matching lyric & words", lyricMatchWindow("matching lyric & words", "matching"))
    }
    @Test fun longTailMatchIsWindowedSoTheQueryStaysVisible() {
        val text = "A".repeat(40) + "海阔天空" + "B".repeat(40)
        val window = lyricMatchWindow(text, "海阔天空")
        assertTrue(window.length <= 26) // 24 + two ellipses
        assertTrue(window.contains("海阔天空"))
        assertTrue(window.startsWith("…"))
        assertTrue(window.endsWith("…"))
    }
    @Test fun leadingMatchNeedsNoFrontEllipsis() {
        val text = "海阔天空" + "C".repeat(40)
        val window = lyricMatchWindow(text, "海阔天空")
        assertFalse(window.startsWith("…"))
        assertTrue(window.contains("海阔天空"))
    }
    @Test fun missingQueryFallsBackToHeadTruncation() {
        val window = lyricMatchWindow("D".repeat(40), "海阔天空")
        assertEquals("D".repeat(24) + "…", window)
    }
}
