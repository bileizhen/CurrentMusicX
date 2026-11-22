package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.data.song.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

/**
 * Automatic matching compares every provider on each load, so its result was never persisted.
 * This keeps the winning document on disk as a fallback for the next load when matching fails
 * (offline playback), and makes the storage page's lyrics row reflect real usage.
 */
class MatchedLyricsCache(private val directory: File, private val now: () -> Long = System::currentTimeMillis) {
    @Serializable private data class Entry(val saved: SavedLyrics, val fetchedAtMillis: Long)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun read(song: Song): SavedLyrics? = runCatching {
        val file = file(song)
        if (!file.isFile || file.length() > MAX_FILE_BYTES) return null
        val entry = json.decodeFromString<Entry>(file.readText(Charsets.UTF_8))
        if (now() < entry.fetchedAtMillis || now() - entry.fetchedAtMillis > MAX_AGE_MILLIS) return null
        entry.saved.takeIf { it.document.lines.isNotEmpty() }
    }.getOrNull()

    fun write(song: Song, saved: SavedLyrics) {
        if (saved.document.lines.isEmpty()) return
        runCatching {
            check(directory.isDirectory || directory.mkdirs())
            val payload = json.encodeToString(Entry(saved, now()))
            val target = file(song)
            val temporary = File.createTempFile("match-", ".tmp", directory)
            try {
                temporary.outputStream().use { it.write(payload.toByteArray(Charsets.UTF_8)); it.fd.sync() }
                try { Files.move(temporary.toPath(), target.toPath(), REPLACE_EXISTING, ATOMIC_MOVE) }
                catch (_: AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), target.toPath(), REPLACE_EXISTING) }
            } finally { temporary.delete() }
            prune()
        }
    }

    private fun file(song: Song) = File(directory, hash(SongLyricsStore.identity(song)) + ".json")

    /** Keep the newest entries within the size and count budget; the cache is disposable. */
    private fun prune() {
        var bytes = 0L
        directory.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?.forEachIndexed { index, saved ->
                bytes += saved.length()
                if (index >= MAX_ENTRIES || bytes > MAX_BYTES) saved.delete()
            }
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    private companion object {
        const val MAX_ENTRIES = 200
        const val MAX_BYTES = 16L * 1024 * 1024
        const val MAX_FILE_BYTES = 2L * 1024 * 1024
        const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
