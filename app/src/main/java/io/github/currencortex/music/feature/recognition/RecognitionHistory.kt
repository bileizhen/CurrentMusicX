package io.github.currencortex.music.feature.recognition

import android.content.Context
import io.github.currencortex.music.data.song.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class RecognitionHistoryEntry(val song: Song, val recognizedAt: Long)

/** Stores matched song metadata only. Recordings and fingerprints are never persisted. */
class RecognitionHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("music-recognition", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    fun read(): List<RecognitionHistoryEntry> = runCatching {
        json.decodeFromString<List<RecognitionHistoryEntry>>(prefs.getString("matches", "[]")!!).take(30)
    }.getOrDefault(emptyList())
    fun add(song: Song): List<RecognitionHistoryEntry> {
        val next = (listOf(RecognitionHistoryEntry(song, System.currentTimeMillis())) + read().filter { it.song.id != song.id }).take(30)
        prefs.edit().putString("matches", json.encodeToString(next)).apply()
        return next
    }
    fun clear() { prefs.edit().remove("matches").apply() }
}
