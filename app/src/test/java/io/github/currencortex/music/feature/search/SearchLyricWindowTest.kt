package io.github.currencortex.music.feature.search

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class SearchLyricWindowTest {
    @Test fun longQueriesRemainCompleteAndHighlighted() {
        for (length in listOf(21, 24, 32)) {
            val query = "Q".repeat(length)
            for (prefix in listOf("", "前文".repeat(20))) {
                val text = prefix + query + "后文".repeat(20)
                val window = lyricMatchWindow(text, query.lowercase())
                assertTrue("Query length $length must remain intact: $window", window.contains(query))
                val highlighted = searchMatchText(window, query.lowercase(), Color.Blue)
                val span = highlighted.spanStyles.single()
                assertEquals(query, window.substring(span.start, span.end))
                assertEquals(Color.Blue, span.item.color)
            }
        }
    }
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
