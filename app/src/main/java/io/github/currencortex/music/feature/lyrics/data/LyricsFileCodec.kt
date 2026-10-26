// FIA envelope, file decoding, KRC and timed-text adapters informed by Folia Major, AGPL-3.0.
// chthollyphile/folia-major aecd330b720eb8fc728e6b10ebcec261b144d683:
// src/utils/lyrics/{foliaLyricDocument,lyricFileDecoding,parserCore,enhancedLrcSerializer}.ts
// src/services/onlineMusic/kugou/krcDecrypt.ts. Original project and contributors retain attribution.
package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import io.github.currencortex.music.feature.lyrics.ttml.TtmlParser
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.zip.InflaterInputStream
import kotlin.math.roundToLong

data class LyricsFileDocument(val document: LyricsDocument, val offsetMs: Long? = null)

object LyricsFileCodec {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_TIME = 7L * 24 * 60 * 60 * 1000
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val krcKey = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
    private val timedRow = Regex("^\\[(\\d+),(\\d+)\\](.*)$")
    private val krcWord = Regex("<(\\d+),(\\d+)(?:,\\d+)?>([^<]*)")
    private val qrcTag = Regex("\\((\\d+),(\\d+)(?:,\\d+)?\\)")

    fun decode(bytes: ByteArray, fileName: String): LyricsFileDocument {
        require(bytes.size <= MAX_BYTES) { "歌词文件过大，最多支持 2 MB" }
        require(bytes.isNotEmpty()) { "歌词文件为空" }
        val encryptedKrc = bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(107, 114, 99, 49))
        val raw = if (encryptedKrc) inflateKrc(bytes) else bytes
        val text = decodeText(raw).removePrefix("\uFEFF").replace(Regex("\\r+\\n?|\\n"), "\n")
        val trimmed = text.trimStart()
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val result = when {
            extension == "fia" || (trimmed.startsWith('{') && trimmed.contains("\"folia-lyricdata\"")) -> decodeFia(trimmed)
            extension == "ttml" || Regex("<(?:[A-Za-z_][\\w.-]*:)?tt(?:\\s|>)").containsMatchIn(trimmed.take(1024)) ->
                LyricsFileDocument(TtmlParser.parse(text))
            encryptedKrc || extension == "krc" || Regex("^\\[\\d+,\\d+\\]<\\d+,\\d+", RegexOption.MULTILINE).containsMatchIn(text) ->
                LyricsFileDocument(parseKrc(text))
            extension == "vtt" || trimmed.startsWith("WEBVTT") || text.contains(" --> ") -> LyricsFileDocument(parseVtt(text))
            extension == "qrc" || trimmed.contains("LyricContent=") -> LyricsFileDocument(parseQrc(text))
            extension == "yrc" || Regex("^\\[\\d+,\\d+\\]\\(\\d+,\\d+,\\d+\\)", RegexOption.MULTILINE).containsMatchIn(text) ->
                LyricsFileDocument(LyricsParser.yrc(text))
            else -> LyricsFileDocument(LyricsParser.lrc(text))
        }
        val clean = result.document.withoutEstimatedTiming()
        validate(clean)
        require(clean.lines.isNotEmpty() || extension == "fia" || trimmed.contains("\"folia-lyricdata\"")) { "没有找到可识别的时间轴歌词" }
        return result.copy(document = clean)
    }

    /** Raw timestamps stay unchanged; Folia reads this correction only as song metadata. */
    fun encodeFia(song: Song, document: LyricsDocument, origin: LyricsOrigin?, offsetMs: Long): String {
        val clean = document.withoutEstimatedTiming()
        validate(clean)
        val value = buildJsonObject {
            put("format", "folia-lyricdata"); put("version", 1); put("exportedAt", Instant.now().toString())
            put("song", buildJsonObject {
                put("key", SongLyricsStore.identity(song)); put("title", song.name); put("artist", song.artists)
                put("album", song.album); put("durationMs", song.durationMs.coerceAtLeast(0))
            })
            put("source", if (origin?.provider == "imported") "imported" else "online")
            put("offsetMs", offsetMs.coerceIn(-60_000, 60_000))
            put("lyrics", buildJsonObject {
                put("title", song.name); put("artist", song.artists); put("isWordByWord", clean.hasWordTiming)
                put("lines", JsonArray(clean.lines.map { encodeFoliaLine(it, false) }))
                put("ttml", buildJsonObject { put("agents", buildJsonObject {
                    clean.metadata.agents.forEach { agent -> put(agent.id, buildJsonObject {
                        put("id", agent.id); put("type", agent.type); put("name", agent.name)
                    }) }
                }) })
            })
            put("currentMusic", buildJsonObject {
                put("version", 1); put("document", json.encodeToJsonElement(clean))
                origin?.let { put("origin", json.encodeToJsonElement(it)) }
            })
        }
        return (json.encodeToString(value) + "\n").also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "导出的歌词文件超过 2 MB" } }
    }

    /** LRC exports original text and real word tags. It has no reliable auxiliary-role metadata. */
    fun encodeLrc(document: LyricsDocument): String {
        val clean = document.withoutEstimatedTiming()
        validate(clean)
        val output = buildString {
            clean.lines.forEach { line ->
                val stamp = "[${timestamp(line.startTimeMs)}]"
                append(stamp)
                val words = line.words.sortedBy { it.startOffset }
                if (words.isEmpty()) append(line.text.singleLine()) else {
                    var cursor = 0
                    words.forEach { word ->
                        append(line.text.substring(cursor, word.startOffset).singleLine())
                        append('<').append(timestamp(word.startTimeMs)).append('>')
                        append(line.text.substring(word.startOffset, word.endOffset).singleLine())
                        append('<').append(timestamp(word.endTimeMs)).append('>')
                        cursor = word.endOffset
                    }
                    append(line.text.substring(cursor).singleLine())
                }
                append('\n')
            }
        }
        require(output.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "导出的歌词文件超过 2 MB" }
        return output
    }

    internal fun validate(document: LyricsDocument) {
        require(document.lines.size <= 20_000) { "歌词行数过多" }
        require(document.lines.zipWithNext().all { it.first.startTimeMs <= it.second.startTimeMs }) { "歌词行时间顺序无效" }
        fun line(value: LyricLine, depth: Int) {
            require(depth <= 8 && value.startTimeMs in 0..MAX_TIME && value.endTimeMs in value.startTimeMs..MAX_TIME) { "歌词时间轴无效" }
            require(value.text.length <= 100_000 && value.words.size <= 20_000) { "歌词单行过长" }
            var cursor = 0
            var previousStart = value.startTimeMs
            value.words.forEach { word ->
                require(word.startTimeMs in previousStart..value.endTimeMs && word.endTimeMs in word.startTimeMs..value.endTimeMs &&
                    word.startOffset >= cursor && word.endOffset in word.startOffset..value.text.length &&
                    value.text.substring(word.startOffset, word.endOffset) == word.text) { "歌词逐字时间或文本范围无效" }
                cursor = word.endOffset
                previousStart = word.startTimeMs
            }
            value.backgroundVocals.forEach { line(it, depth + 1) }
        }
        document.lines.forEach { line(it, 0) }
    }

    private fun decodeFia(text: String): LyricsFileDocument {
        checkJsonDepth(text)
        val root = json.parseToJsonElement(text) as? JsonObject ?: error("FIA 歌词文件结构无效")
        require(root.string("format") == "folia-lyricdata" && root["version"]?.jsonPrimitive?.intOrNull == 1) { "不支持的 FIA 歌词版本" }
        val lyrics = root["lyrics"] as? JsonObject ?: error("FIA 歌词内容缺失")
        val rows = lyrics["lines"] as? JsonArray ?: error("FIA 歌词行缺失")
        require(rows.size <= 20_000)
        val isWordTimed = lyrics["isWordByWord"]?.jsonPrimitive?.booleanOrNull == true
        val parsed = rows.map { decodeFoliaLine(it as? JsonObject ?: error("FIA 歌词行无效"), isWordTimed, false) }
            .sortedBy { it.startTimeMs }
        val agents = (lyrics["ttml"] as? JsonObject)?.get("agents") as? JsonObject
        val meta = LyricsMetadata(agents = agents?.mapNotNull { (key, raw) ->
            (raw as? JsonObject)?.let { LyricAgent(it.string("id").ifBlank { key }, it.string("type"), it.string("name")) }
        }.orEmpty(), source = "Folia FIA")
        // A versioned extension preserves duet/role/multi-track details absent in Folia's public model.
        val extension = root["currentMusic"] as? JsonObject
        val document = if (extension?.get("version")?.jsonPrimitive?.intOrNull == 1 && extension["document"] != null)
            json.decodeFromJsonElement<LyricsDocument>(extension.getValue("document")) else {
                // Folia retains explicit agent ids (ttmlConversion.ts); do not infer voices from text.
                // Map the first encountered main voice to CurrentMusic's primary/left role.
                val primary = parsed.firstNotNullOfOrNull { it.agent }
                fun voice(line: LyricLine): LyricLine = line.copy(
                    isDuet = primary != null && line.agent != null && line.agent != primary,
                    backgroundVocals = line.backgroundVocals.map(::voice))
                LyricsDocument(parsed.map(::voice), meta)
            }
        val offset = (root["offsetMs"] as? JsonPrimitive)?.doubleOrNull?.let {
            require(it.isFinite()) { "FIA 时间偏移无效" }; it.roundToLong().coerceIn(-60_000, 60_000)
        }
        return LyricsFileDocument(document, offset)
    }

    private fun decodeFoliaLine(row: JsonObject, timed: Boolean, background: Boolean, depth: Int = 0): LyricLine {
        require(depth <= 8) { "FIA 和声嵌套过深" }
        val text = (row[if (background) "text" else "fullText"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: error("FIA 歌词文本无效")
        val start = seconds(row["startTime"]); val end = seconds(row["endTime"])
        val rawWords = row["words"] as? JsonArray ?: error("FIA 逐字数据无效")
        var cursor = 0
        var aligned = true
        val words = rawWords.map { raw ->
            val word = raw as? JsonObject ?: error("FIA 逐字数据无效")
            val token = (word["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("FIA 单字文本无效")
            val from = seconds(word["startTime"]); val until = seconds(word["endTime"])
            val offset = text.indexOf(token, cursor)
            if (offset < 0 || token.isEmpty()) { aligned = false; null } else {
                cursor = offset + token.length
                LyricWord(token, from, until, offset, cursor)
            }
        }.filterNotNull()
        val alternates = (row["alternateTexts"] as? JsonArray).orEmpty().mapNotNull { raw ->
            (raw as? JsonObject)?.let { value -> value.string("text").takeIf { it.isNotBlank() }?.let {
                Triple(value.string("role"), it, value.string("language").ifBlank { null })
            } }
        }
        val backgrounds = (row["backgroundVocals"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.toMutableList()
        (row["backgroundVocal"] as? JsonObject)?.let { if (it !in backgrounds) backgrounds += it }
        val agent = row.string("agentId").ifBlank { null }
        return LyricLine(start, end, text, if (timed && aligned) words else emptyList(),
            translation = row.string("translation").ifBlank {
                (alternates.firstOrNull { it.first == "translation" && it.third?.startsWith("zh") == true }
                    ?: alternates.firstOrNull { it.first == "translation" })?.second.orEmpty()
            }, romanization = row.string("romanization").ifBlank { alternates.firstOrNull { it.first == "romanization" }?.second.orEmpty() },
            isBackground = background, agent = agent, key = row.string("id").ifBlank { null },
            backgroundVocals = backgrounds.map { decodeFoliaLine(it, timed, true, depth + 1) },
            translations = alternates.filter { it.first == "translation" }.map { LyricAuxiliary(it.second, it.third) },
            romanizations = alternates.filter { it.first == "romanization" }.map { LyricAuxiliary(it.second, it.third) })
    }

    private fun encodeFoliaLine(line: LyricLine, background: Boolean): JsonObject = buildJsonObject {
        put(if (background) "text" else "fullText", line.text)
        put("startTime", line.startTimeMs / 1000.0); put("endTime", line.endTimeMs / 1000.0)
        put("words", JsonArray(line.words.map { word -> buildJsonObject {
            put("text", word.text); put("startTime", word.startTimeMs / 1000.0); put("endTime", word.endTimeMs / 1000.0)
        } }))
        if (line.translation.isNotBlank()) put("translation", line.translation)
        if (line.romanization.isNotBlank()) put("romanization", line.romanization)
        line.agent?.let { put("agentId", it) }; line.key?.let { put("id", it) }
        put("alternateTexts", JsonArray(line.translations.map { value -> buildJsonObject {
            put("role", "translation"); put("text", value.text); value.language?.let { put("language", it) }
        } } + line.romanizations.map { value -> buildJsonObject {
            put("role", "romanization"); put("text", value.text); value.language?.let { put("language", it) }
        } }))
        if (line.backgroundVocals.isNotEmpty()) put("backgroundVocals", JsonArray(line.backgroundVocals.map { encodeFoliaLine(it, true) }))
    }

    private fun parseKrc(source: String): LyricsDocument {
        val tracks = Regex("\\[language:([^]\\r\\n]+)]").find(source)?.groupValues?.get(1)?.let { encoded ->
            runCatching {
                val bytes = Base64.getDecoder().decode(encoded)
                require(bytes.size <= MAX_BYTES)
                val root = json.parseToJsonElement(decodeText(bytes)).jsonObject
                (root["content"] as? JsonArray).orEmpty().mapNotNull { raw ->
                    val track = raw as? JsonObject ?: return@mapNotNull null
                    val type = track["type"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    type to (track["lyricContent"] as? JsonArray).orEmpty().map { row ->
                        (row as? JsonArray)?.joinToString("") { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty().trim()
                    }
                }.toMap()
            }.getOrNull()
        }.orEmpty()
        val rows = source.lineSequence().mapNotNull { line ->
            val match = timedRow.matchEntire(line.trim()) ?: return@mapNotNull null
            val start = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val duration = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val body = match.groupValues[3]
            val text = StringBuilder()
            val words = krcWord.findAll(body).mapNotNull { tag ->
                val token = tag.groupValues[3]; val offset = text.length; text.append(token)
                val at = tag.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val length = tag.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                if (token.isEmpty() || length <= 0) null else LyricWord(token, safeAdd(start, at), safeAdd(safeAdd(start, at), length), offset, text.length)
            }.toList()
            if (words.isEmpty()) LyricLine(start, safeAdd(start, duration), body.trim())
            else LyricLine(start, safeAdd(start, duration), text.toString(), words)
        }.filter { it.text.isNotBlank() }.toList().sortedBy { it.startTimeMs }
        return LyricsDocument(rows.mapIndexed { index, row -> row.copy(translation = tracks[1]?.getOrNull(index).orEmpty(),
            romanization = tracks[0]?.getOrNull(index).orEmpty()) }, LyricsMetadata(source = "KRC"))
    }

    private fun parseQrc(source: String): LyricsDocument {
        val content = Regex("LyricContent\\s*=\\s*([\"'])([\\s\\S]*?)\\1").find(source)?.groupValues?.get(2)?.let(::unescapeXml) ?: source
        val rows = content.lineSequence().mapNotNull { raw ->
            val match = timedRow.matchEntire(raw.trim()) ?: return@mapNotNull null
            val start = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val duration = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val body = match.groupValues[3]; val tags = qrcTag.findAll(body).toList()
            if (tags.isEmpty()) return@mapNotNull LyricLine(start, safeAdd(start, duration), body)
            val chunks = buildList {
                var from = 0
                tags.forEach { add(body.substring(from, it.range.first)); from = it.range.last + 1 }
                add(body.substring(from))
            }
            val trailing = chunks.first().isBlank() && chunks.last().isNotBlank()
            val text = StringBuilder(if (trailing) chunks.first() else ""); val words = mutableListOf<LyricWord>()
            tags.forEachIndexed { index, tag ->
                val token = chunks[index + if (trailing) 1 else 0]
                val at = tag.groupValues[1].toLongOrNull() ?: return@forEachIndexed
                val length = tag.groupValues[2].toLongOrNull() ?: return@forEachIndexed
                val offset = text.length; text.append(token)
                if (token.isNotEmpty() && length > 0) words += LyricWord(token, at, safeAdd(at, length), offset, text.length)
            }
            if (!trailing) text.append(chunks.last())
            LyricLine(start, safeAdd(start, duration), text.toString(), words)
        }.filter { it.text.isNotBlank() }.toList().sortedBy { it.startTimeMs }
        return LyricsDocument(rows, LyricsMetadata(source = "QRC"))
    }

    private fun parseVtt(source: String): LyricsDocument {
        val rows = mutableListOf<LyricLine>()
        val lines = source.lines(); var index = 0
        while (index < lines.size) {
            val timing = lines[index++].split("-->")
            if (timing.size != 2) continue
            val start = cueTime(timing[0].trim()) ?: continue
            val end = cueTime(timing[1].trim().substringBefore(' ')) ?: continue
            val body = buildList { while (index < lines.size && lines[index].isNotBlank()) add(lines[index++]) }.joinToString("\n")
            val clean = unescapeXml(body.replace(Regex("<[^>]*>"), ""))
            if (clean.isNotBlank()) rows += LyricLine(start, end, clean)
        }
        return LyricsDocument(rows.sortedBy { it.startTimeMs }, LyricsMetadata(source = "WebVTT"))
    }

    private fun cueTime(value: String): Long? {
        val fields = value.replace(',', '.').split(':')
        if (fields.size !in 2..3) return null
        val values = fields.map { it.toDoubleOrNull() ?: return null }
        val seconds = if (values.size == 3) values[0] * 3600 + values[1] * 60 + values[2] else values[0] * 60 + values[1]
        return (seconds * 1000).takeIf { it.isFinite() && it >= 0 && it <= MAX_TIME }?.roundToLong()
    }

    private fun inflateKrc(bytes: ByteArray): ByteArray {
        val compressed = ByteArray(bytes.size - 4) { index -> (bytes[index + 4].toInt() xor krcKey[index % krcKey.size]).toByte() }
        return InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val size = input.read(buffer); if (size < 0) break
                require(output.size() + size <= MAX_BYTES) { "解压后的歌词超过 2 MB" }
                output.write(buffer, 0, size)
            }
            output.toByteArray()
        }
    }

    private fun decodeText(bytes: ByteArray): String {
        fun strict(charset: Charset, offset: Int = 0) = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
        return when {
            bytes.size >= 3 && bytes.take(3).map { it.toInt() and 255 } == listOf(239, 187, 191) -> strict(Charsets.UTF_8, 3)
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> strict(Charsets.UTF_16LE, 2)
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> strict(Charsets.UTF_16BE, 2)
            else -> try { strict(Charsets.UTF_8) } catch (_: CharacterCodingException) { strict(Charset.forName("GB18030")) }
        }
    }

    private fun seconds(value: JsonElement?): Long {
        val number = (value as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull ?: error("FIA 时间戳无效")
        require(number.isFinite() && number >= 0 && number <= MAX_TIME / 1000.0) { "FIA 时间戳超出范围" }
        return (number * 1000).roundToLong()
    }
    private fun safeAdd(first: Long, second: Long): Long {
        require(first >= 0 && second >= 0 && first <= MAX_TIME && second <= MAX_TIME - first) { "歌词时间戳超出范围" }
        return first + second
    }
    private fun checkJsonDepth(text: String) {
        var quoted = false; var escaped = false; var depth = 0
        text.forEach { char ->
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 64) { "FIA 文件嵌套过深" } }
                '}', ']' -> depth--
            }
        }
    }
    private fun JsonObject.string(key: String): String = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
    private fun timestamp(ms: Long): String = "%02d:%02d.%03d".format(Locale.ROOT, ms / 60_000, ms % 60_000 / 1000, ms % 1000)
    private fun String.singleLine() = replace(Regex("[\\r\\n]+"), " ")
    private fun unescapeXml(value: String): String = value.replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace(Regex("&#(x[0-9a-fA-F]+|[0-9]+);")) { match ->
            val raw = match.groupValues[1]
            val code = if (raw.startsWith('x')) raw.drop(1).toIntOrNull(16) else raw.toIntOrNull()
            code?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: match.value
        }.replace("&amp;", "&")
}
