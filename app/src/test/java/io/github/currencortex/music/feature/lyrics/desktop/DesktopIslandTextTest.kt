package io.github.currencortex.music.feature.lyrics.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopIslandTextTest {
    @Test fun hardBreaksBecomeSpacesWithoutChangingTimingOffsets() {
        val source = "Hello\r\n世界 👩🏽‍🚀 e\u0301\u2028مرحبا\u2029end"
        val display = islandSingleLine(source)
        assertEquals(source.length, display.length)
        assertEquals(source.indexOf("世界"), display.indexOf("世界"))
        assertEquals(source.indexOf("مرحبا"), display.indexOf("مرحبا"))
        assertTrue(display.contains("👩🏽‍🚀 e\u0301"))
        assertFalse(display.any { it == '\n' || it == '\r' || it == '\u2028' || it == '\u2029' })
    }
    @Test fun asymmetricCutoutsDoNotShiftTheIslandAndChangingHeightKeepsItsTop() {
        val area = DesktopSafeArea(130, 80, 1980, 900)
        val one = desktopIslandPlacement(area, 1900, 40, .2f, 1000)
        val two = desktopIslandPlacement(area, 1900, 70, .2f, 1000)
        assertEquals(1000, one.x + one.width / 2)
        assertTrue(one.x >= area.left && one.x + one.width <= area.right)
        assertEquals(one.y, two.y)
        assertEquals(one.x, two.x)
    }
    @Test fun firstCuePreviewsBeforeStartAndRealLyricsAreRetainedThroughGaps() {
        val document = io.github.currencortex.music.feature.lyrics.model.LyricsDocument(listOf(
            io.github.currencortex.music.feature.lyrics.model.LyricLine(1000, 2000, "First"),
            io.github.currencortex.music.feature.lyrics.model.LyricLine(3000, 5000, "♪"),
            io.github.currencortex.music.feature.lyrics.model.LyricLine(8000, 9000, "Next")))
        val timeline = io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer(document)
        assertEquals(0, desktopIslandLineIndex(document, timeline, 0))
        assertEquals(0, desktopIslandLineIndex(document, timeline, 1500))
        assertEquals(0, desktopIslandLineIndex(document, timeline, 4000))
        assertEquals(0, desktopIslandLineIndex(document, timeline, 6000))
        assertEquals(2, desktopIslandLineIndex(document, timeline, 8500))
        assertEquals(2, desktopIslandLineIndex(document, timeline, 12000))
    }

}
