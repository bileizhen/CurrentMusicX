package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.feature.lyrics.domain.LyricsKey
import io.github.currencortex.music.feature.lyrics.ttml.TtmlParser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.nio.file.AtomicMoveNotSupportedException

/** Raw TTML cache. Caller holds the repository lock and runs on IO. */
class LyricsCache(private val directory: File) {
    fun read(key: LyricsKey): String? {
        val file = File(directory, key.cacheName)
        if (!file.isFile) return null
        if (file.length() > TtmlParser.MAX_BYTES) { check(file.delete()); return null }
        return file.readText(Charsets.UTF_8).also { file.setLastModified(System.currentTimeMillis()) }
    }
    fun remove(key: LyricsKey) { File(directory, key.cacheName).let { if (it.exists()) check(it.delete()) } }
    fun write(key: LyricsKey, raw: String) {
        check(directory.isDirectory || directory.mkdirs())
        val file = File(directory, key.cacheName)
        val temp = File.createTempFile("lyric-", ".tmp", directory)
        try {
            temp.outputStream().use { it.write(raw.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            try { Files.move(temp.toPath(), file.toPath(), REPLACE_EXISTING, ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), file.toPath(), REPLACE_EXISTING) }
        } finally { if (temp.exists()) temp.delete() }
        var bytes = 0L
        directory.listFiles()?.filter { it.extension == "ttml" }?.sortedByDescending { it.lastModified() }?.forEachIndexed { index, saved ->
            bytes += saved.length()
            if (index >= 64 || bytes > 32L * 1024 * 1024) check(saved.delete())
        }
    }
}
