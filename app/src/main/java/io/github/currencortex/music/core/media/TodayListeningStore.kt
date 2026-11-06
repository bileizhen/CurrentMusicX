package io.github.currencortex.music.core.media

import android.content.Context
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Local audible playback time, independent of network reporting and track seek positions. */
class TodayListeningStore(context: Context, suffix: String) : AutoCloseable {
    private val preferences = context.getSharedPreferences("today-listening$suffix", Context.MODE_PRIVATE)
    private val milliseconds = MutableStateFlow(readToday())
    val today = milliseconds.asStateFlow()
    private val history = ListeningHistoryStore(context, suffix,
        preferences.all.mapNotNull { (day, value) -> (value as? Long)?.let { day to it } }.toMap())
    val revision = history.revision
    private var closed = false

    suspend fun history(start: LocalDate, endInclusive: LocalDate): ListeningHistoryReport = history.read(start, endInclusive)

    @Synchronized override fun close() {
        closed = true
        history.close()
    }

    internal suspend fun awaitClosed() { history.awaitClosed() }

    private fun readToday() = preferences.getLong(LocalDate.now().toString(), 0L)

    @Synchronized fun refresh() { milliseconds.value = readToday() }

    @Synchronized fun add(durationMs: Long, endedAt: Long = System.currentTimeMillis(), song: Song? = null) {
        if (durationMs <= 0 || closed) return
        val zone = ZoneId.systemDefault()
        val days = listeningDays(durationMs, endedAt, zone)
        val editor = preferences.edit()
        days.forEach { (day, duration) -> editor.putLong(day, preferences.getLong(day, 0L) + duration) }
        editor.apply()
        history.add(durationMs, endedAt, zone, song?.let(::listeningHistorySong))
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
