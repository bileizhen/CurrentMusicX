package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.feature.lyrics.ttml.TtmlTimeParser
import io.github.currencortex.music.feature.lyrics.model.*
import org.junit.Assert.*
import org.junit.Test

class TtmlTimeParserTest {
    @Test fun clocksUseExactLongMilliseconds() {
        assertEquals(120456L, TtmlTimeParser.parse("120.456"))
        assertEquals(15500L, TtmlTimeParser.parse("00:15.5"))
        assertEquals(3723001L, TtmlTimeParser.parse("01:02:03.001"))
        assertEquals(1L, TtmlTimeParser.parse("0.0005"))
    }
    @Test fun offsetsSupportUnits() {
        assertEquals(123L, TtmlTimeParser.parse("123ms"))
        assertEquals(2500L, TtmlTimeParser.parse("2.5s"))
        assertEquals(90000L, TtmlTimeParser.parse("1.5m"))
        assertEquals(3600000L, TtmlTimeParser.parse("1h"))
    }
    @Test fun malformedNegativeAndOverflowTimesAreTypedFailures() {
        listOf("", "abc", "-1", "00:61", "1:60:00", "1:2:3:4", "1.5:02", "NaN", "9999999999999999999999999999").forEach { time ->
            try { TtmlTimeParser.parse(time); fail(time) }
            catch (e: LyricsException) { assertEquals(time, LyricsErrorCode.INVALID_TIMELINE, e.code) }
        }
    }
}
