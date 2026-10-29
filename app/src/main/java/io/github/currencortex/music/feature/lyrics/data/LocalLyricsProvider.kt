package io.github.currencortex.music.feature.lyrics.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.wav.WavTag
import java.io.File
import java.util.Locale

/** Reads the audio tag through an existing media permission; never modifies or copies the audio. */
class LocalLyricsProvider(private val context: Context) {
    suspend fun load(song: Song): SavedLyrics? = withContext(Dispatchers.IO) {
        if (song.musicSource != MusicSource.LOCAL || song.localUri.isBlank()) return@withContext null
        val uri = Uri.parse(song.localUri)
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in setOf("mp3", "flac", "m4a", "mp4", "ogg", "opus", "wav", "wma", "aif", "aiff"))
            return@withContext null
        val text = context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            // readAs supplies the container type because a /proc descriptor has no file extension.
            val tag = AudioFileIO.readAs(File("/proc/self/fd/${descriptor.fd}"), extension).tag
            ((tag as? WavTag)?.getID3Tag() ?: tag)?.getFirst(FieldKey.LYRICS)
        }.orEmpty()
        ensureActive()
        decodeEmbeddedLyrics(text, song.durationMs)?.let {
            SavedLyrics(it, LyricsOrigin("本地内嵌歌词", "local", song.localUri))
        }
    }
}

/** Untimed tags remain one static block; no artificial line or word timestamps. */
internal fun decodeEmbeddedLyrics(raw: String, durationMs: Long): LyricsDocument? {
    if (raw.isBlank() || raw.toByteArray(Charsets.UTF_8).size > LyricsFileCodec.MAX_BYTES) return null
    val text = raw.removePrefix("\uFEFF").trim()
    val timed = runCatching { LyricsFileCodec.decode(text.toByteArray(Charsets.UTF_8), "embedded.lrc").document }.getOrNull()
    if (timed != null) return timed
    // A malformed timed document should fall back to other sources, not display markup as lyrics.
    if (Regex("\\[\\d+[:,]|<tt|WEBVTT|LyricContent=", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
    return runCatching {
        LyricsDocument(listOf(LyricLine(0, durationMs.coerceAtLeast(1), text))).also(LyricsFileCodec::validate)
    }.getOrNull()
}
