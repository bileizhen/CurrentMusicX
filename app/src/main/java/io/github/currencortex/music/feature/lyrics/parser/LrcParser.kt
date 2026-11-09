// Syntax/semantic separation and vocal tags informed by Gramophone, GPL-3.0-or-later.
// FoedusProgramme/Gramophone e6fa468f7a1e3e1c832afbd0dc67ef2966146e96:
// logic/utils/SemanticLyrics.kt. Copyright its original contributors; adapted for CurrentMusic.
package io.github.currencortex.music.feature.lyrics.parser

import io.github.currencortex.music.feature.lyrics.model.*

/** LRC syntax is read before timing or vocal roles are interpreted. No word duration is invented. */
internal object LrcParser {
    private const val MAX_TIME = 7L * 24 * 60 * 60 * 1000
    private const val MAX_TEXT = 2 * 1024 * 1024
    private const val MAX_LINES = 20_000
    private const val MAX_LINE_TEXT = 100_000
    private const val MAX_WORDS = 200_000
    private val timestamp = Regex("([\\[<])(\\d+):(\\d+)(?:[.:](\\d+))?([\\]>])")
    private val metadata = Regex("^\\[[A-Za-z][\\w-]*:.*]$", RegexOption.DOT_MATCHES_ALL)
    private val voice = Regex("^\\s*(v[1-9]\\d*):\\s*", RegexOption.IGNORE_CASE)
    private val offsetTag = Regex("\\[offset:([+-]?\\d+)\\]", RegexOption.IGNORE_CASE)

    private sealed interface Token {
        data class Time(val ms: Long?, val square: Boolean) : Token
        data class Text(val value: String) : Token
    }
    private data class Draft(var line: LyricLine, val backgrounds: MutableList<Draft> = mutableListOf())
    private data class Row(val starts: List<Long>, val body: List<Token>, val agent: String?, val background: Boolean)

    fun parse(source: String): LyricsDocument {
        require(source.length <= MAX_TEXT) { "LRC content is too large" }
        val drafts = mutableListOf<Draft>()
        var previous = emptyList<Draft>()
        var expandedText = 0L
        var expandedWords = 0L
        var expandedLines = 0L
        for (raw in source.lineSequence()) {
            val row = readRow(raw.removePrefix("\uFEFF"), previous.lastOrNull()?.line) ?: continue
            val rowText = row.body.sumOf { if (it is Token.Text) it.value.length.toLong() else 0L }
            expandedText += rowText * row.starts.size
            expandedWords += row.body.count { it is Token.Time }.toLong() * row.starts.size
            expandedLines += row.starts.size
            require(rowText <= MAX_LINE_TEXT && expandedText <= MAX_TEXT &&
                expandedWords <= MAX_WORDS && expandedLines <= MAX_LINES) { "Expanded LRC content is too large" }
            // Repeated timestamps share the same text; parse their body once, then shift real cues.
            val base = buildLine(row)
            val parsed = if (base == null) emptyList() else row.starts.map { repeatLine(base, it) }
            if (parsed.isEmpty()) {
                // An empty timed line is an explicit end marker, not visible lyric text.
                row.starts.firstOrNull()?.let { until -> previous.forEach { draft ->
                    if (until > draft.line.startTimeMs && draft.line.endTimeMs == 0L)
                        draft.line = draft.line.copy(endTimeMs = until)
                } }
                continue
            }
            if (row.background && previous.isNotEmpty()) {
                parsed.forEach { line ->
                    val parent = previous.filter { it.line.startTimeMs <= line.startTimeMs }
                        .maxByOrNull { it.line.startTimeMs } ?: previous.first()
                    parent.backgrounds += Draft(line.copy(agent = line.agent ?: parent.line.agent,
                        isDuet = if (line.agent == null) parent.line.isDuet else line.isDuet))
                }
            } else {
                previous = parsed.map(::Draft)
                drafts += previous
            }
        }
        val starts = drafts.map { it.line.startTimeMs }.distinct().sorted()
        val nextStarts = starts.zipWithNext().toMap()
        fun finish(draft: Draft, parentEnd: Long? = null): LyricLine {
            val line = draft.line.withInlineTranslationBlocks()
            val words = completeWords(line)
            val end = line.endTimeMs.takeIf { it > line.startTimeMs }
                ?: words.maxOfOrNull { it.endTimeMs }
                ?: parentEnd?.takeIf { it > line.startTimeMs }
                ?: nextStarts[line.startTimeMs]
                ?: (line.startTimeMs + 5000).coerceAtMost(MAX_TIME)
            return line.copy(endTimeMs = maxOf(end, words.maxOfOrNull { it.endTimeMs } ?: 0), words = words,
                backgroundVocals = draft.backgrounds.map { finish(it, end) })
        }
        val lines = drafts.map { finish(it) }.filter { it.text.isNotBlank() }.distinct().sortedBy { it.startTimeMs }
        val agents = (lines + lines.flatMap { it.backgroundVocals }).mapNotNull { it.agent }.distinct()
            .map { LyricAgent(it, if (it == "v3") "group" else "person") }
        // Supplementary metadata refers to the original timestamp, before offset/clamping.
        val document = applyLrcExtensions(LyricsDocument(lines, LyricsMetadata(agents = agents)), source)
        val offset = offsetTag.find(source)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        return if (offset == 0L) document else shift(document, offset)
    }

    private fun readRow(raw: String, previous: LyricLine?): Row? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        val background = text.startsWith("[bg:", ignoreCase = true) && text.endsWith(']')
        if (background) text = text.substring(4, text.length - 1).trim()
        else if (metadata.matches(text)) return null

        val leading = mutableListOf<Long>()
        var cursor = 0
        while (true) {
            val match = timestamp.matchAt(text, cursor) ?: break
            if (match.groupValues[1] != "[" || match.groupValues[5] != "]") break
            leading += time(match) ?: return null
            cursor = match.range.last + 1
            // Spaces between repeated line timestamps do not become lyric text.
            val next = text.indexOfFirstFrom(cursor) { !it.isWhitespace() }
            if (next >= 0 && text[next] == '[') cursor = next else break
        }
        var body = text.substring(cursor)
        val speaker = voice.find(body)
        var agent = speaker?.groupValues?.get(1)?.lowercase()
        if (speaker != null) body = body.substring(speaker.range.last + 1)
        val tokens = tokenize(body).toMutableList()
        // Some exporters place the speaker after the first word timestamp. It is
        // still structural only at the start of the first non-whitespace text token.
        if (agent == null) {
            val firstText = tokens.indexOfFirst { it is Token.Text && it.value.isNotBlank() }
            if (firstText >= 0) {
                val token = tokens[firstText] as Token.Text
                voice.find(token.value)?.let { match ->
                    agent = match.groupValues[1].lowercase()
                    tokens[firstText] = Token.Text(token.value.substring(match.range.last + 1))
                }
            }
        }
        if (leading.isEmpty()) {
            val first = tokens.firstOrNull() as? Token.Time
            val start = first?.ms ?: if (background) previous?.startTimeMs else null
            if (start == null) return null
            leading += start
        } else if (tokens.any { it is Token.Time && it.square }) {
            // In square-word LRC the first line timestamp also begins its first word.
            tokens.add(0, Token.Time(leading.first(), true))
        }
        return Row(leading, tokens, agent, background)
    }

    private fun tokenize(text: String): List<Token> = buildList {
        var cursor = 0
        for (match in timestamp.findAll(text)) {
            val square = match.groupValues[1] == "["
            if (match.groupValues[5] != if (square) "]" else ">") continue
            if (match.range.first > cursor) add(Token.Text(text.substring(cursor, match.range.first)))
            add(Token.Time(time(match), square))
            cursor = match.range.last + 1
        }
        if (cursor < text.length) add(Token.Text(text.substring(cursor)))
    }

    private fun time(match: MatchResult): Long? {
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        val fraction = match.groupValues[4].take(3).padEnd(3, '0').toLongOrNull() ?: return null
        // Check before multiplication; malformed timestamps never overflow into valid times.
        if (minutes > MAX_TIME / 60000 || seconds > MAX_TIME / 1000) return null
        return (minutes * 60000 + seconds * 1000 + fraction).takeIf { it in 0..MAX_TIME }
    }

    private fun buildLine(row: Row): LyricLine? {
        val start = row.starts.first()
        val text = StringBuilder()
        val words = mutableListOf<LyricWord>()
        var begin: Long? = null
        var from = 0
        var closing: Long? = null
        var validTiming = true
        for (token in row.body) when (token) {
            is Token.Text -> { text.append(token.value); if (token.value.isNotBlank()) closing = null }
            is Token.Time -> {
                val end = token.ms
                if (text.length > from && text.substring(from).isNotBlank()) {
                    val at = begin
                    // Untimed text may be an inline translation removed in finish(); test
                    // coverage there, once offsets refer to the actual primary lyric.
                    if (at != null) {
                        if (end == null || end <= at || at < start) validTiming = false
                        else words += LyricWord(text.substring(from), at, end, from, text.length)
                    }
                }
                if (end == null || begin?.let { end < it } == true) validTiming = false
                begin = end
                closing = end
                from = text.length
            }
        }
        if (text.isBlank()) return null
        val raw = text.toString()
        val trimStart = raw.indexOfFirst { !it.isWhitespace() }
        val trimEnd = raw.indexOfLast { !it.isWhitespace() } + 1
        val clean = raw.substring(trimStart, trimEnd)
        val clippedWords = if (!validTiming) emptyList() else words.mapNotNull { word ->
            val a = maxOf(word.startOffset, trimStart)
            val b = minOf(word.endOffset, trimEnd)
            if (a >= b || raw.substring(a, b).isBlank()) null else word.copy(text = raw.substring(a, b),
                startOffset = a - trimStart, endOffset = b - trimStart)
        }
        return LyricLine(start, closing?.takeIf { it > start } ?: 0, clean, clippedWords,
            isBackground = row.background, isDuet = row.agent == "v2", agent = row.agent,
            roles = if (row.background) listOf("x-bg") else emptyList())
    }

    private fun repeatLine(line: LyricLine, start: Long): LyricLine {
        val delta = start - line.startTimeMs
        if (delta == 0L) return line
        val words = if (line.words.any { it.startTimeMs + delta !in 0..MAX_TIME || it.endTimeMs + delta !in 0..MAX_TIME })
            emptyList() else line.words.map { it.copy(startTimeMs = it.startTimeMs + delta, endTimeMs = it.endTimeMs + delta) }
        val end = if (line.endTimeMs == 0L) 0L else (line.endTimeMs + delta).takeIf { it in start..MAX_TIME } ?: 0L
        return line.copy(startTimeMs = start, endTimeMs = end, words = words)
    }

    /** Incomplete/invalid word timing degrades this line only; real text and other lines survive. */
    private fun completeWords(line: LyricLine): List<LyricWord> {
        var cursor = 0
        var previousTime = line.startTimeMs
        for (word in line.words) {
            if (word.startOffset < cursor || word.endOffset > line.text.length || word.endOffset <= word.startOffset ||
                word.startTimeMs < previousTime || word.endTimeMs <= word.startTimeMs || word.endTimeMs > MAX_TIME ||
                line.text.substring(word.startOffset, word.endOffset) != word.text ||
                line.text.substring(cursor, word.startOffset).any { !it.isWhitespace() }) return emptyList()
            cursor = word.endOffset
            previousTime = word.startTimeMs
        }
        return if (line.text.substring(cursor).any { !it.isWhitespace() }) emptyList() else line.words
    }

    private fun shift(document: LyricsDocument, offset: Long): LyricsDocument {
        fun time(value: Long): Long = when {
            offset > 0 && offset > MAX_TIME - value -> MAX_TIME
            offset < 0 && offset < -value -> 0
            else -> value + offset
        }
        fun line(value: LyricLine): LyricLine {
            // Clipping a word at zero would invent a shorter duration. Keep the line text instead.
            val words = if (value.words.any { time(it.startTimeMs) == 0L && offset < -it.startTimeMs ||
                    time(it.endTimeMs) == MAX_TIME && offset > MAX_TIME - it.endTimeMs }) emptyList()
                else value.words.map { word -> word.copy(startTimeMs = time(word.startTimeMs), endTimeMs = time(word.endTimeMs),
                    ruby = word.ruby.map { it.copy(startTimeMs = it.startTimeMs?.let(::time), endTimeMs = it.endTimeMs?.let(::time)) }) }
            return value.copy(startTimeMs = time(value.startTimeMs), endTimeMs = time(value.endTimeMs), words = words,
                backgroundVocals = value.backgroundVocals.map(::line),
                ruby = value.ruby.map { it.copy(startTimeMs = it.startTimeMs?.let(::time), endTimeMs = it.endTimeMs?.let(::time)) })
        }
        return document.copy(lines = document.lines.map(::line))
    }

    private inline fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
        for (index in start until length) if (predicate(this[index])) return index
        return -1
    }
}
