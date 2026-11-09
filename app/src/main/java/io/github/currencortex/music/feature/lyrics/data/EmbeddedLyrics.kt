package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.KeyNotFoundException
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagTextField
import org.jaudiotagger.tag.wav.WavTag
import java.io.IOException
import java.util.Locale

internal class EmbeddedLyricsReadException(cause: Throwable) :
    IOException("无法读取音频文件中的歌词标签", cause)

internal class EmbeddedLyricsDecodeException :
    IOException("音频文件包含歌词，但格式或时间轴无法识别")

/** Keep extraction separate from parsing: a damaged first tag must not hide a valid later tag. */
internal fun embeddedLyricsCandidates(audioTag: Tag?): List<String> {
    val tag = (audioTag as? WavTag)?.getID3Tag() ?: audioTag ?: return emptyList()
    val candidates = linkedSetOf<String>()
    fun add(value: String) {
        if (candidates.size < MAX_EMBEDDED_CANDIDATES && value.isNotBlank()) candidates.add(value)
    }
    // The generic key handles container-specific ID3/MP4 fields and multiple language entries.
    try {
        tag.getAll(FieldKey.LYRICS).asSequence().filterNotNull().take(MAX_EMBEDDED_CANDIDATES).forEach(::add)
    } catch (_: KeyNotFoundException) {
        // Some containers only expose their custom textual keys.
    }
    val fields = tag.fields
    while (fields.hasNext() && candidates.size < MAX_EMBEDDED_CANDIDATES) {
        val field = fields.next()
        if (field is TagTextField && field.id?.uppercase(Locale.ROOT)?.filterNot {
                it == ' ' || it == '_' || it == '-'
            } in EMBEDDED_LYRIC_KEYS) field.content?.let(::add)
    }
    return candidates.toList()
}

/** Prefer a usable time axis to a plain-text copy, while preserving tag order among equal candidates. */
internal fun decodeEmbeddedLyricsCandidates(candidates: List<String>, durationMs: Long,
    checkActive: () -> Unit = {}): LyricsDocument? {
    var plain: LyricsDocument? = null
    var timed: LyricsDocument? = null
    var sawContent = false
    for (raw in candidates.take(MAX_EMBEDDED_CANDIDATES)) {
        checkActive()
        if (raw.isBlank()) continue
        // Check character length first so a huge tag is not copied just to reject it.
        if (raw.length > LyricsFileCodec.MAX_BYTES) { sawContent = true; continue }
        if (raw.lineSequence().all {
                val row = it.removePrefix("\uFEFF").trim()
                row.isBlank() || embeddedHeader.matches(row) || emptyBackground.matches(row)
            }) continue
        sawContent = true
        val bytes = raw.toByteArray(Charsets.UTF_8)
        if (bytes.size > LyricsFileCodec.MAX_BYTES) continue
        val parsed = runCatching { LyricsFileCodec.decode(bytes, "embedded.lrc").document }.getOrNull()
        checkActive()
        if (parsed != null && parsed.lines.isNotEmpty()) {
            if (parsed.hasWordTiming) return parsed
            if (timed == null) timed = parsed
        } else if (plain == null) {
            plain = decodeUntimedEmbeddedLyrics(raw, durationMs)
        }
    }
    checkActive()
    return timed ?: plain ?: if (sawContent) throw EmbeddedLyricsDecodeException() else null
}

/** Untimed tags remain one static block; no artificial line or word timestamps. */
internal fun decodeEmbeddedLyrics(raw: String, durationMs: Long): LyricsDocument? {
    if (raw.isBlank() || raw.length > LyricsFileCodec.MAX_BYTES ||
        raw.toByteArray(Charsets.UTF_8).size > LyricsFileCodec.MAX_BYTES) return null
    val timed = runCatching { LyricsFileCodec.decode(raw.toByteArray(Charsets.UTF_8), "embedded.lrc").document }.getOrNull()
    return timed?.takeIf { it.lines.isNotEmpty() } ?: decodeUntimedEmbeddedLyrics(raw, durationMs)
}

private fun decodeUntimedEmbeddedLyrics(raw: String, durationMs: Long): LyricsDocument? {
    val text = raw.removePrefix("\uFEFF").trim()
    // A malformed timed document should fall back to other sources, not display markup as lyrics.
    if (timedEmbeddedMarkup.containsMatchIn(text) || text.contains("\"folia-lyricdata\"")) return null
    val plain = text.lineSequence().filterNot { embeddedHeader.matches(it.trim()) }.joinToString("\n").trim()
    if (plain.isBlank()) return null
    return runCatching {
        LyricsDocument(listOf(LyricLine(0, durationMs.coerceAtLeast(1), plain))).also(LyricsFileCodec::validate)
    }.getOrNull()
}

private const val MAX_EMBEDDED_CANDIDATES = 16
private val EMBEDDED_LYRIC_KEYS = setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS")
private val timedEmbeddedMarkup = Regex(
    "(?:\\[|<)\\d+[:,]|\\[bg:|<(?:[\\w.-]+:)?tt(?:\\s|>)|WEBVTT|LyricContent=", RegexOption.IGNORE_CASE,
)
private val emptyBackground = Regex("\\[bg:\\s*]", RegexOption.IGNORE_CASE)
private val embeddedHeader = Regex(
    "\\[(?:ti|ar|al|by|offset|length|re|ve|tool|au|la|encoder|instrumental|x-[a-z0-9_-]+):.*]",
    RegexOption.IGNORE_CASE,
)

/** A small container hint for providers with neither a filename extension nor an audio MIME type. */
internal fun embeddedAudioExtension(header: ByteArray): String? {
    fun at(offset: Int, value: String): Boolean = offset + value.length <= header.size &&
        value.indices.all { header[offset + it].toInt() == value[it].code }
    return when {
        at(0, "fLaC") -> "flac"
        at(4, "ftyp") -> "m4a"
        at(0, "RIFF") && at(8, "WAVE") -> "wav"
        at(0, "FORM") && (at(8, "AIFF") || at(8, "AIFC")) -> "aiff"
        at(0, "OggS") -> if ((0..(header.size - 8)).any { at(it, "OpusHead") }) "opus" else "ogg"
        at(0, "ID3") -> "mp3"
        header.size >= 2 && header[0].toInt() and 0xff == 0xff &&
            header[1].toInt() and 0xe0 == 0xe0 && header[1].toInt() and 0x06 != 0 &&
            header[1].toInt() and 0x18 != 0x08 -> "mp3"
        else -> null
    }
}
