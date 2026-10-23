package io.github.currencortex.music.core.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Local audible playback time, independent of network reporting and track seek positions. */
class TodayListeningStore(context: Context, suffix: String) {
    private val preferences = context.getSharedPreferences("today-listening$suffix", Context.MODE_PRIVATE)
    private val milliseconds = MutableStateFlow(readToday())
    val today = milliseconds.asStateFlow()

    private fun readToday() = preferences.getLong(LocalDate.now().toString(), 0L)

    @Synchronized fun refresh() { milliseconds.value = readToday() }

    @Synchronized fun add(durationMs: Long, endedAt: Long = System.currentTimeMillis()) {
        if (durationMs <= 0) return
        val days = listeningDays(durationMs, endedAt, ZoneId.systemDefault())
        val editor = preferences.edit()
        days.forEach { (day, duration) -> editor.putLong(day, preferences.getLong(day, 0L) + duration) }
        // Keep only a short local history; this is not a remote account statistic.
        val oldest = LocalDate.now().minusDays(7).toString()
        preferences.all.keys.filter { it < oldest }.forEach(editor::remove)
        editor.apply()
        refresh()
    }
}

internal fun listeningDays(durationMs: Long, endedAt: Long, zone: ZoneId): Map<String, Long> {
    val result = mutableMapOf<String, Long>()
    var start = endedAt - durationMs.coerceAtLeast(0)
    while (start < endedAt) {
        val day = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val end = minOf(endedAt, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        result[day.toString()] = end - start
        start = end
    }
    return result
}
