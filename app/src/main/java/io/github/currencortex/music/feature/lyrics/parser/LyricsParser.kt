package io.github.currencortex.music.feature.lyrics.parser

import io.github.currencortex.music.feature.lyrics.model.*
import kotlinx.serialization.json.*

/** Accepts CurrentMusic's normalized response and upstream raw lyrics without inventing timing. */
object LyricsParser {
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
        val merged = lines.map { it.copy(
            translation = it.translation.ifBlank { translations[it.startTimeMs]?.text.orEmpty() },
            romanization = it.romanization.ifBlank { romanizations[it.startTimeMs]?.text.orEmpty() }) }
        // Preserve an explicit provider verdict; an empty lyric alone never means instrumental.
        val pure = (listOf(obj) + listOf("lrc", "yrc", "ytlrc", "tlyric").mapNotNull { obj[it] as? JsonObject })
            .any { (it["pureMusic"] as? JsonPrimitive)?.booleanOrNull == true } ||
            (obj["instrumental"] as? JsonPrimitive)?.booleanOrNull == true || obj.string("source") == "ncm:inst"
        val metadata = LyricsMetadata(
            values = if (pure) mapOf("pureMusic" to listOf("true")) else emptyMap(),
            source = obj.string("source").ifBlank { "CurrentMusic" },
            wordLevel = (obj["wordLevel"] as? JsonPrimitive)?.booleanOrNull,
            instrumental = if (pure) true else (obj["instrumental"] as? JsonPrimitive)?.booleanOrNull)
        return if (pure) LyricsDocument(merged.ifEmpty { listOf(LyricLine(0, 5000, "纯音乐，请欣赏")) },
            metadata) else LyricsDocument(merged, metadata)
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
        var words = (obj["words"] as? JsonArray).orEmpty().mapNotNull { value ->
            val word = value as? JsonObject ?: return@mapNotNull null
            val token = word.string("text")
            val from = word.time("start") ?: return@mapNotNull null
            val to = word.time("end") ?: return@mapNotNull null
            val index = text.indexOf(token, offset)
            if (token.isEmpty() || index < 0 || to <= from) return@mapNotNull null
            offset = index + token.length
            LyricWord(token, from, to, index, offset)
        }.sortedBy { it.startTimeMs }
        val estimated = (obj["wSynth"] as? JsonPrimitive)?.booleanOrNull == true
        val next = rows.mapNotNull { (it as? JsonObject)?.time("t") }.filter { it > start }.minOrNull()
        val end = obj.time("end")?.takeIf { it > start } ?: next ?: (start + 5000)
        if (words.isEmpty() && !estimated) {
            val tokens = (obj["w"] as? JsonArray).orEmpty().mapNotNull { value ->
                val pair = value as? JsonArray ?: return@mapNotNull null
                val at = (pair.getOrNull(0) as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
                val token = (pair.getOrNull(1) as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                (at to token).takeIf { at >= start && token.isNotEmpty() }
            }
            if (tokens.joinToString("") { it.second } == text && tokens.zipWithNext().all { it.first.first < it.second.first }) {
                var cursor = 0
                words = tokens.mapIndexedNotNull { i, (at, token) ->
                    val until = tokens.getOrNull(i + 1)?.first ?: end
                    val offset = cursor; cursor += token.length
                    if (until <= at) null else LyricWord(token, at, until, offset, cursor)
                }
            }
        }
        LyricLine(start.coerceAtLeast(0), end, text, if (estimated) emptyList() else words,
            obj.string("trans"), obj.string("romanization").ifBlank { obj.string("roma") })
    })

    fun lrc(source: String): LyricsDocument = LrcParser.parse(source)

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

    private fun finish(lines: List<LyricLine>): LyricsDocument {
        val sorted = lines.map { it.withInlineTranslationBlocks() }.filter { it.text.isNotBlank() }
            .distinctBy { it.startTimeMs to it.text }.sortedBy { it.startTimeMs }
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
