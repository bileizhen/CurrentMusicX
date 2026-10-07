package io.github.currencortex.music.core.download

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.AndroidArtwork
import org.jaudiotagger.tag.wav.WavTag
import java.io.File
import java.util.Locale

data class DownloadCover(val bytes: ByteArray, val width: Int, val height: Int)

/** Always export the original timeline, independent of playback offset / display preferences. */
object DownloadLyrics {
    fun lrc(song: Song, document: LyricsDocument): String {
        if (document.lines.isEmpty()) return ""
        fun clean(value: String) = value.replace(Regex("[\\r\\n]"), " ")
        fun stamp(ms: Long, open: Char, close: Char): String {
            val time = ms.coerceAtLeast(0)
            return String.format(Locale.ROOT, "%c%02d:%02d.%03d%c", open, time / 60000, time / 1000 % 60, time % 1000, close)
        }
        // Enhanced LRC needs closing timestamps, not just starts. Empty timestamp intervals
        // retain pauses without extending a word to the next word's start.
        fun body(line: LyricLine): String {
            val words = line.words.sortedBy { it.startOffset }
            if (words.isEmpty()) return clean(line.text)
            var cursor = 0
            for (word in words) {
                if (word.startOffset < cursor || word.endOffset <= word.startOffset || word.endOffset > line.text.length ||
                    word.startTimeMs < line.startTimeMs || word.endTimeMs <= word.startTimeMs || word.endTimeMs > line.endTimeMs ||
                    line.text.substring(word.startOffset, word.endOffset) != word.text ||
                    line.text.substring(cursor, word.startOffset).any { !it.isWhitespace() }) return clean(line.text)
                cursor = word.endOffset
            }
            if (line.text.substring(cursor).any { !it.isWhitespace() }) return clean(line.text)
            val text = StringBuilder()
            cursor = 0
            var lastStamp: Long? = null
            for (word in words) {
                val gap = line.text.substring(cursor, word.startOffset)
                text.append(gap)
                if (gap.isNotEmpty() || lastStamp != word.startTimeMs) text.append(stamp(word.startTimeMs, '<', '>'))
                text.append(word.text).append(stamp(word.endTimeMs, '<', '>'))
                lastStamp = word.endTimeMs
                cursor = word.endOffset
            }
            text.append(line.text, cursor, line.text.length)
            if (cursor != line.text.length || lastStamp != line.endTimeMs) text.append(stamp(line.endTimeMs, '<', '>'))
            return clean(text.toString())
        }
        return buildString {
            appendLine("[ti:${clean(song.name)}]"); appendLine("[ar:${clean(song.artists)}]")
            appendLine("[al:${clean(song.album)}]"); appendLine("[by:CurrentMusic]")
            document.lines.sortedBy { it.startTimeMs }.forEach { line ->
                (listOf(line) + line.backgroundVocals).sortedBy { it.startTimeMs }.forEach { row ->
                    appendLine(stamp(row.startTimeMs, '[', ']') + body(row))
                    row.translation.takeIf { it.isNotBlank() && it != row.text }?.let { appendLine(stamp(row.startTimeMs, '[', ']') + clean(it)) }
                }
            }
        }
    }
}

object SongDownloadNames {
    fun base(song: Song): String = (song.name + song.artists.takeIf(String::isNotBlank)?.let { " - $it" }.orEmpty())
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.')
        .take(80).ifBlank { "歌曲" } + " [${song.id}]"
}

/** Writes the existing container, preserving encoded audio. AndroidArtwork avoids java.awt. */
object SongTagWriter {
    @Synchronized fun write(file: File, song: Song, lrc: String, cover: DownloadCover?) {
        TagOptionSingleton.getInstance().isAndroid = true
        val audio = AudioFileIO.read(file)
        val tag = audio.tagOrCreateAndSetDefault
        // RIFF INFO cannot hold lyrics or artwork; WAV mirrors every field into an embedded ID3v2 chunk.
        val rich: Tag = (tag as? WavTag)?.let { wav -> wav.getID3Tag() ?: ID3v24Tag().also(wav::setID3Tag) } ?: tag
        val fields = listOf(FieldKey.TITLE to song.name, FieldKey.ARTIST to song.artists, FieldKey.ALBUM to song.album,
            FieldKey.COMMENT to "Downloaded with CurrentMusic · ${song.musicSource.name}:${song.id}")
        for ((key, value) in fields) {
            tag.setField(key, value)
            if (rich !== tag) rich.setField(key, value)
        }
        if (lrc.isNotBlank()) rich.setField(FieldKey.LYRICS, lrc)
        if (cover != null) {
            val artwork = object : AndroidArtwork() {
                override fun setImageFromData(): Boolean = width > 0 && height > 0 && binaryData.isNotEmpty()
            }.apply {
                binaryData = cover.bytes; mimeType = "image/jpeg"; pictureType = 3
                description = "Front cover"; width = cover.width; height = cover.height
            }
            rich.deleteArtworkField()
            rich.setField(artwork)
        }
        audio.commit()
        val saved = AudioFileIO.read(file).tag
        val savedRich = (saved as? WavTag)?.getID3Tag() ?: saved
        check(savedRich.getFirst(FieldKey.TITLE) == song.name && savedRich.getFirst(FieldKey.ARTIST) == song.artists &&
            savedRich.getFirst(FieldKey.ALBUM) == song.album) { "歌曲信息写入失败，请重试" }
        if (lrc.isNotBlank()) check(savedRich.getFirst(FieldKey.LYRICS) == lrc) { "歌词写入失败，请重试" }
        if (cover != null) check(savedRich.firstArtwork?.binaryData?.contentEquals(cover.bytes) == true) { "封面写入失败，请重试" }
    }
}
