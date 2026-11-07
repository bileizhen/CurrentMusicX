package io.github.currencortex.music.core.media

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** A single IO worker serializes recording, migration and reads without blocking playback. */
internal class ListeningHistoryStore(context: Context, suffix: String, legacyDays: Map<String, Long>) : AutoCloseable {
    private sealed interface Command {
        data class Add(val milliseconds: Long, val endedAt: Long, val zone: ZoneId,
            val song: ListeningHistorySong?) : Command
        data class Read(val start: LocalDate, val end: LocalDate,
            val result: CompletableDeferred<ListeningHistoryReport>) : Command
    }
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val helper = HistoryDatabase(context.applicationContext, suffix)

    private val worker = CoroutineScope(Dispatchers.IO).launch {
        try {
            var initializationError: Exception? = null
            var recordingError: Exception? = null
            try { reconcileLegacy(helper.writableDatabase, legacyDays) }
            catch (error: Exception) { initializationError = error }
            for (command in commands) {
                try {
                    initializationError?.let { throw it }
                    when (command) {
                        is Command.Add -> {
                            append(helper.writableDatabase, command)
                            changes.value += 1
                        }
                        is Command.Read -> {
                            recordingError?.let { throw it }
                            command.result.complete(read(helper.readableDatabase, command.start, command.end))
                        }
                    }
                } catch (error: Exception) {
                    when (command) {
                        is Command.Read -> command.result.completeExceptionally(error)
                        is Command.Add -> {
                            recordingError = error
                            Log.w("ListeningHistory", "Unable to persist listening history", error)
                        }
                    }
                }
            }
        } finally { helper.close() }
    }

    /** Stop accepting work, but finish all accepted writes/reads before releasing SQLite. */
    override fun close() { commands.close() }

    internal suspend fun awaitClosed() { worker.join() }

    fun add(durationMs: Long, endedAt: Long, zone: ZoneId, song: ListeningHistorySong?) {
        if (durationMs > 0) commands.trySend(Command.Add(durationMs, endedAt, zone, song))
    }

    suspend fun read(start: LocalDate, endInclusive: LocalDate): ListeningHistoryReport {
        require(!endInclusive.isBefore(start))
        require(java.time.temporal.ChronoUnit.DAYS.between(start, endInclusive) <= 3660)
        val result = CompletableDeferred<ListeningHistoryReport>()
        commands.send(Command.Read(start, endInclusive, result))
        return result.await()
    }

    private fun reconcileLegacy(db: SQLiteDatabase, days: Map<String, Long>) = transaction(db) {
        days.forEach { (date, duration) ->
            if (runCatching { LocalDate.parse(date) }.isSuccess && duration > 0) {
                db.execSQL("INSERT OR IGNORE INTO daily(day, milliseconds) VALUES (?, 0)", arrayOf(date))
                // The old preferences are a durable daily-total mirror. MAX also recovers a
                // process death between its async write and the detailed SQLite transaction.
                db.execSQL("UPDATE daily SET milliseconds = MAX(milliseconds, ?) WHERE day = ?", arrayOf<Any>(duration, date))
            }
        }
    }

    private fun append(db: SQLiteDatabase, command: Command.Add) = transaction(db) {
        val song = command.song
        if (song != null) db.execSQL("""INSERT OR REPLACE INTO songs(song_key, name, artists, album, cover)
            VALUES (?, ?, ?, ?, ?)""", arrayOf(song.key, song.name, song.artists, song.album, song.cover))
        listeningHistorySlices(command.milliseconds, command.endedAt, command.zone).forEach { slice ->
            db.execSQL("INSERT OR IGNORE INTO daily(day, milliseconds) VALUES (?, 0)", arrayOf(slice.day))
            db.execSQL("UPDATE daily SET milliseconds = milliseconds + ? WHERE day = ?", arrayOf<Any>(slice.durationMs, slice.day))
            db.execSQL("INSERT OR IGNORE INTO hours(day, hour, milliseconds) VALUES (?, ?, 0)", arrayOf<Any>(slice.day, slice.hour))
            db.execSQL("UPDATE hours SET milliseconds = milliseconds + ? WHERE day = ? AND hour = ?",
                arrayOf<Any>(slice.durationMs, slice.day, slice.hour))
            if (song != null) {
                db.execSQL("INSERT OR IGNORE INTO day_songs(day, song_key, milliseconds) VALUES (?, ?, 0)", arrayOf(slice.day, song.key))
                db.execSQL("UPDATE day_songs SET milliseconds = milliseconds + ? WHERE day = ? AND song_key = ?",
                    arrayOf<Any>(slice.durationMs, slice.day, song.key))
            }
        }
    }

    private fun read(db: SQLiteDatabase, start: LocalDate, end: LocalDate): ListeningHistoryReport {
        val bounds = arrayOf(start.toString(), end.toString())
        val recorded = mutableMapOf<LocalDate, Pair<Long, Int>>()
        db.rawQuery("""SELECT d.day, d.milliseconds, COUNT(s.song_key) FROM daily d
            LEFT JOIN day_songs s ON s.day = d.day WHERE d.day BETWEEN ? AND ? GROUP BY d.day ORDER BY d.day""", bounds).use {
            while (it.moveToNext()) recorded[LocalDate.parse(it.getString(0))] = it.getLong(1) to it.getInt(2)
        }
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { date ->
            ListeningHistoryDay(date, recorded[date]?.first ?: 0, recorded[date]?.second ?: 0)
        }.toList()
        val songs = mutableListOf<ListeningHistorySong>()
        db.rawQuery("""SELECT s.song_key, s.name, s.artists, s.album, s.cover, SUM(d.milliseconds) AS duration
            FROM day_songs d JOIN songs s ON s.song_key = d.song_key WHERE d.day BETWEEN ? AND ?
            GROUP BY s.song_key ORDER BY duration DESC, s.song_key LIMIT 100""", bounds).use {
            while (it.moveToNext()) songs += ListeningHistorySong(it.getString(0), it.getString(1),
                it.getString(2), it.getString(3), it.getString(4), it.getLong(5))
        }
        val songCount = db.rawQuery("SELECT COUNT(DISTINCT song_key) FROM day_songs WHERE day BETWEEN ? AND ?", bounds).use {
            it.moveToFirst(); it.getInt(0)
        }
        val hours = MutableList(24) { 0L }
        db.rawQuery("SELECT hour, SUM(milliseconds) FROM hours WHERE day BETWEEN ? AND ? GROUP BY hour", bounds).use {
            while (it.moveToNext()) hours[it.getInt(0)] = it.getLong(1)
        }
        val total = db.rawQuery("SELECT COALESCE(SUM(milliseconds), 0), MIN(day) FROM daily", null).use {
            it.moveToFirst(); it.getLong(0) to if (it.isNull(1)) null else LocalDate.parse(it.getString(1))
        }
        return ListeningHistoryReport(start, end, days, songs, songCount, hours, total.first, total.second, hours.sum())
    }

    private inline fun transaction(db: SQLiteDatabase, action: () -> Unit) {
        db.beginTransaction()
        try { action(); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }

    private class HistoryDatabase(context: Context, suffix: String) :
        SQLiteOpenHelper(context, "listening-history$suffix.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE daily(day TEXT NOT NULL PRIMARY KEY, milliseconds INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE hours(day TEXT NOT NULL, hour INTEGER NOT NULL, milliseconds INTEGER NOT NULL, PRIMARY KEY(day, hour))")
            db.execSQL("CREATE TABLE songs(song_key TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, artists TEXT NOT NULL, album TEXT NOT NULL, cover TEXT NOT NULL)")
            db.execSQL("CREATE TABLE day_songs(day TEXT NOT NULL, song_key TEXT NOT NULL, milliseconds INTEGER NOT NULL, PRIMARY KEY(day, song_key))")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
