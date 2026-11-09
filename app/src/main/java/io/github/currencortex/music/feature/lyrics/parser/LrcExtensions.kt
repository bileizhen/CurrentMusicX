// SpotiFLAC/Mornye LRC extension format informed by mornyeapp/Mornye, LGPL-3.0,
// 45df9860661e9c4e731ef26a60c44b2b5d8cf125: native_lyrics/{lrc,text}.rs and lyrics/lrc.rs.
// Original project and contributors retain attribution. See THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.feature.lyrics.parser

import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.Locale

/** Apply exporter-specific metadata to raw timestamps, before the LRC offset is applied. */
internal fun applyLrcExtensions(document: LyricsDocument, source: String): LyricsDocument {
    if (document.lines.isEmpty() || source.length > MAX_EXTENSION_SOURCE) return document
    // These tags contain no voice identifier. Never guess which simultaneous voice owns them.
    val unambiguousTimes = document.lines.filterNot { it.isBackground }.groupingBy { it.startTimeMs }.eachCount()
        .filterValues { it == 1 }.keys
    val translations = mutableMapOf<Long, String>()
    val readings = mutableMapOf<Long, String>()
    val readingWords = mutableMapOf<Long, List<LrcReadingWord>>()
    for (row in source.lineSequence()) {
        if (row.length > MAX_EXTENSION_ENCODED + 80) continue
        val match = extensionTag.matchEntire(row.trim().removePrefix("\uFEFF").trimStart()) ?: continue
        val time = match.groupValues[2].toLongOrNull()?.takeIf { it in unambiguousTimes } ?: continue
        val kind = match.groupValues[1].lowercase(Locale.ROOT)
        if (when (kind) {
            "translation" -> time in translations
            "romaji" -> time in readings
            else -> time in readingWords
        }) continue
        val decoded = decodeExtension(match.groupValues[3]) ?: continue
        when (kind) {
            "translation" -> decoded.takeIf { it.length <= MAX_EXTENSION_TEXT }?.let { translations[time] = it }
            "romaji" -> decoded.takeIf { it.length <= MAX_EXTENSION_TEXT }?.let { readings[time] = it }
            "romaji-words" -> parseReadingWords(decoded)?.let { readingWords[time] = it }
        }
    }
    if (translations.isEmpty() && readings.isEmpty() && readingWords.isEmpty()) return document
    return document.copy(lines = document.lines.map { line ->
        val time = line.startTimeMs
        if (line.isBackground || time !in unambiguousTimes) return@map line
        val words = readingWords[time].orEmpty()
        val joined = words.joinToString("") { it.text }.trim()
        val reading = line.romanization.ifBlank { readings[time] ?: joined }
        line.copy(
            translation = line.translation.ifBlank { translations[time].orEmpty() },
            romanization = reading,
            words = if (reading == joined && line.matchesReadingWords(words)) {
                line.words.zip(words) { original, annotation ->
                    original.copy(romanization = original.romanization.ifBlank { annotation.text })
                }
            } else line.words,
        )
    })
}

// A line-level reading is still useful when the exporter splits syllables differently.
// Word readings are assigned only when every original interval matches exactly.
private fun LyricLine.matchesReadingWords(reading: List<LrcReadingWord>): Boolean =
    words.isNotEmpty() && words.size == reading.size && words.indices.all { index ->
        words[index].startTimeMs == reading[index].start && words[index].endTimeMs == reading[index].end
    }

private data class LrcReadingWord(val text: String, val start: Long, val end: Long)

private val extensionTag = Regex("^\\[x-(romaji-words|romaji|translation):(\\d+):([^\\]]*)\\]$", RegexOption.IGNORE_CASE)
private const val MAX_EXTENSION_SOURCE = 2 * 1024 * 1024
private const val MAX_EXTENSION_BYTES = 256 * 1024
private const val MAX_EXTENSION_ENCODED = MAX_EXTENSION_BYTES * 4 / 3 + 16
private const val MAX_EXTENSION_TEXT = 100_000
private const val MAX_EXTENSION_WORDS = 20_000
private const val MAX_EXTENSION_TIME = 7L * 24 * 60 * 60 * 1000

private fun decodeExtension(encoded: String): String? {
    if (encoded.isEmpty() || encoded.length > MAX_EXTENSION_ENCODED) return null
    return try {
        val normalized = encoded.replace("%3D", "=", ignoreCase = true).replace('-', '+').replace('_', '/')
        val bytes = Base64.getDecoder().decode(normalized)
        if (bytes.size > MAX_EXTENSION_BYTES) return null
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
            .toString().trim().takeIf { it.isNotBlank() && '\u0000' !in it }
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: java.nio.charset.CharacterCodingException) {
        null
    }
}

private fun parseReadingWords(raw: String): List<LrcReadingWord>? {
    // Check depth before JSON parsing: optional, untrusted metadata must not exhaust the stack.
    var depth = 0
    var quoted = false
    var escaped = false
    for (character in raw) {
        if (quoted) {
            if (escaped) escaped = false
            else if (character == '\\') escaped = true
            else if (character == '"') quoted = false
        } else when (character) {
            '"' -> quoted = true
            '[', '{' -> if (++depth > 4) return null
            ']', '}' -> if (--depth < 0) return null
        }
    }
    if (depth != 0 || quoted) return null
    val array = try { Json.parseToJsonElement(raw) as? JsonArray } catch (_: IllegalArgumentException) { null }
        ?: return null
    if (array.isEmpty() || array.size > MAX_EXTENSION_WORDS) return null
    val words = ArrayList<LrcReadingWord>(array.size)
    var textLength = 0
    for (item in array) {
        val value = item as? JsonObject ?: return null
        val text = (value["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val start = (value["startTimeMs"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: return null
        val end = (value["endTimeMs"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: return null
        textLength += text.length
        if (text.isBlank() || '\u0000' in text || text.length > 10_000 || textLength > MAX_EXTENSION_TEXT ||
            start !in 0..MAX_EXTENSION_TIME || end !in start..MAX_EXTENSION_TIME ||
            words.lastOrNull()?.let { it.start > start } == true) return null
        words += LrcReadingWord(text, start, end)
    }
    return words
}
