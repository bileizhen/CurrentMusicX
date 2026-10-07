package io.github.currencortex.music.feature.lyrics.parser

import io.github.currencortex.music.feature.lyrics.model.*
import kotlinx.serialization.json.*

/** Accepts CurrentMusic's normalized response and upstream raw lyrics without inventing timing. */
object LyricsParser {
    private val stamp = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]")
    private val wordStamp = Regex("<(\\d+):(\\d+(?:\\.\\d+)?)>")
    private val yrcLine = Regex("^\\[(\\d+),(\\d+)\\](.*)$")
    private val yrcWord = Regex("\\((\\d+),(\\d+),\\d+\\)([^()]*)")

    fun parse(payload: JsonElement): LyricsDocument {
        if (payload is JsonArray) return normalized(payload)
        val obj = payload as? JsonObject ?: return LyricsDocument()
        val rawYrc = raw(obj["yrc"])
        val rawLrc = raw(obj["lrc"])
        val lines = when {
            rawYrc.isNotBlank() -> yrc(rawYrc).lines.ifEmpty { lrc(rawLrc).lines }
            obj["lines"] is JsonArray -> normalized(obj["lines"] as JsonArray).lines
            else -> lrc(rawLrc).lines
        }
        val translations = lrc(raw(obj["ytlrc"]).ifBlank { raw(obj["tlyric"]) }).lines.associateBy { it.startTimeMs }
        val romanizations = lrc(raw(obj["yromalrc"]).ifBlank { raw(obj["romalrc"]) }).lines.associateBy { it.startTimeMs }
        return LyricsDocument(lines.map { it.copy(
            translation = it.translation.ifBlank { translations[it.startTimeMs]?.text.orEmpty() },
            romanization = it.romanization.ifBlank { romanizations[it.startTimeMs]?.text.orEmpty() }) })
    }

    private fun raw(element: JsonElement?): String = when (element) {
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        is JsonObject -> (element["lyric"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        else -> ""
    }
    private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.time(key: String) = (get(key) as? JsonPrimitive)?.longOrNull

    private fun normalized(rows: JsonArray): LyricsDocument = finish(rows.mapNotNull { row ->
        val obj = row as? JsonObject ?: return@mapNotNull null
        val start = obj.time("t") ?: return@mapNotNull null
        val text = obj.string("txt")
        if (text.isBlank()) return@mapNotNull null
        var offset = 0
        val words = (obj["words"] as? JsonArray).orEmpty().mapNotNull { value ->
            val word = value as? JsonObject ?: return@mapNotNull null
            val token = word.string("text")
            val from = word.time("start") ?: return@mapNotNull null
            val to = word.time("end") ?: return@mapNotNull null
            val index = text.indexOf(token, offset)
            if (token.isEmpty() || index < 0 || to <= from) return@mapNotNull null
            offset = index + token.length
            LyricWord(token, from, to, index, offset)
        }.sortedBy { it.startTimeMs }
        LyricLine(start.coerceAtLeast(0), obj.time("end") ?: 0, text, words,
            obj.string("trans"), obj.string("romanization").ifBlank { obj.string("roma") })
    })

    fun lrc(source: String): LyricsDocument {
        val offset = Regex("\\[offset:([+-]?\\d+)\\]", RegexOption.IGNORE_CASE).find(source)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        return finish(source.lineSequence().flatMap { row ->
            val stamps = stamp.findAll(row).toList()
            if (stamps.isEmpty()) return@flatMap emptySequence()
            val rawBody = row.substring(stamps.last().range.last + 1)
            val body = if (wordStamp.containsMatchIn(rawBody)) rawBody else rawBody.trim()
            val wordTimes = wordStamp.findAll(body).toList()
            val text = body.replace(wordStamp, "")
            stamps.asSequence().map { time ->
                val start = (millis(time.groupValues[1], time.groupValues[2]) + offset).coerceAtLeast(0)
                var cursor = wordTimes.firstOrNull()?.range?.first ?: 0
                val words = wordTimes.mapIndexedNotNull { index, match ->
                    val token = body.substring(match.range.last + 1, wordTimes.getOrNull(index + 1)?.range?.first ?: body.length)
                    val begin = millis(match.groupValues[1], match.groupValues[2]) + offset
                    val end = wordTimes.getOrNull(index + 1)?.let { millis(it.groupValues[1], it.groupValues[2]) + offset }
                    val at = cursor
                    cursor += token.length
                    // Empty/whitespace intervals delimit silence, not fabricated lyric words.
                    if (token.isBlank() || end == null || end <= begin || begin < 0) null else LyricWord(token, begin, end, at, cursor)
                }
                val closing = wordTimes.lastOrNull()?.takeIf { body.substring(it.range.last + 1).isBlank() }
                    ?.let { millis(it.groupValues[1], it.groupValues[2]) + offset }
                LyricLine(start, closing ?: 0, text, words)
            }
        }.toList())
    }

    fun yrc(source: String): LyricsDocument = finish(source.lineSequence().mapNotNull { row ->
        val line = yrcLine.matchEntire(row.trim()) ?: return@mapNotNull null
        val start = line.groupValues[1].toLongOrNull() ?: return@mapNotNull null
        val duration = line.groupValues[2].toLongOrNull() ?: return@mapNotNull null
        val text = StringBuilder()
        val words = yrcWord.findAll(line.groupValues[3]).mapNotNull { match ->
            val token = match.groupValues[3]
            val offset = text.length
            text.append(token)
            val from = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val length = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            if (token.isEmpty() || length <= 0) null else LyricWord(token, from, from + length, offset, text.length)
        }.toList()
        LyricLine(start, start + duration, text.toString(), words)
    }.toList())

    private fun millis(minutes: String, seconds: String) = ((minutes.toLongOrNull() ?: 0) * 60000 + (seconds.toDoubleOrNull() ?: 0.0) * 1000).toLong()
    private fun finish(lines: List<LyricLine>): LyricsDocument {
        val sorted = lines.filter { it.text.isNotBlank() }.distinctBy { it.startTimeMs to it.text }.sortedBy { it.startTimeMs }
        val nextStarts = sorted.map { it.startTimeMs }.distinct().zipWithNext().toMap()
        return LyricsDocument(sorted.map { line -> line.copy(
            // Partial enhanced/normalized word data must not leave the rest of the line dim forever.
            words = line.words.takeIf { words -> line.text.indices.all { offset ->
                line.text[offset].isWhitespace() || words.any { offset in it.startOffset until it.endOffset }
            } }.orEmpty(),
            endTimeMs =
            line.endTimeMs.takeIf { it > line.startTimeMs }
                ?: nextStarts[line.startTimeMs] ?: maxOf(line.startTimeMs + 5000, line.words.maxOfOrNull { it.endTimeMs } ?: 0)) })
    }
}
