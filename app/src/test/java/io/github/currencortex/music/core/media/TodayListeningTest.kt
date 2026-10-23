package io.github.currencortex.music.core.media

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayListeningTest {
    @Test fun playbackAcrossMidnightIsSplitIntoActualCalendarDays() {
        val zone = ZoneId.of("Asia/Shanghai")
        val end = ZonedDateTime.of(2026, 10, 24, 0, 0, 5, 0, zone).toInstant().toEpochMilli()
        assertEquals(mapOf("2026-10-23" to 10000L, "2026-10-24" to 5000L), listeningDays(15000, end, zone))
        assertEquals(emptyMap<String, Long>(), listeningDays(0, end, zone))
    }

    @Test fun daylightSavingDayUsesItsActualLength() {
        val zone = ZoneId.of("America/New_York")
        val end = ZonedDateTime.of(2026, 3, 9, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(mapOf("2026-03-08" to 23 * 3600000L), listeningDays(23 * 3600000L, end, zone))
    }
}
