// TTML annotation handling informed by AMLL f32285864056587c4afa59d310208b0aa829cb53,
// packages/ttml/src/{parser.ts,utils/amll-converter.ts}. AGPL-3.0-only.
// Copyright SteveXMH and AMLL contributors; native adaptations, November 2026.
package io.github.currencortex.music.feature.lyrics.ttml

import io.github.currencortex.music.feature.lyrics.model.*

internal data class TtmlBaseRange(val start: Int, val end: Int)
internal data class TtmlTimedText(val text: String, val start: Long, val end: Long)

/** Missing timing remains missing; a timed annotation is not an estimated syllable. */
internal fun TtmlNode.explicitTiming(): Pair<Long, Long>? {
    if (attr("begin") == null && attr("end") == null && attr("dur") == null) return null
    val start = attr("begin")?.let(TtmlTimeParser::parse)
        ?: throw LyricsException(LyricsErrorCode.INVALID_TIMELINE, "Timed span is missing begin")
    val end = attr("end")?.let(TtmlTimeParser::parse)
        ?: attr("dur")?.let {
            try { Math.addExact(start, TtmlTimeParser.parse(it)) }
            catch (error: ArithmeticException) { throw LyricsException(LyricsErrorCode.INVALID_TIMELINE, "Span duration exceeds millisecond range", error) }
        }
        ?: throw LyricsException(LyricsErrorCode.INVALID_TIMELINE, "Timed span is missing end")
    if (end < start) throw LyricsException(LyricsErrorCode.INVALID_TIMELINE, "Span end must not precede begin")
    return start to end
}

internal fun TtmlNode.hasRole(role: String): Boolean = role in attr("role").orEmpty().split(' ')

/** Ruby groups are associated structurally, never by dividing base text into guessed characters. */
internal fun ttmlRuby(container: TtmlNode, bases: List<TtmlBaseRange>): List<LyricRuby> {
    if (bases.isEmpty()) return emptyList()
    val groups = container.children.mapNotNull { child ->
        when (child.attr("ruby")) {
            "text" -> listOf(child)
            "textContainer" -> child.descendants("span").filter { it.attr("ruby") == "text" }
                .ifEmpty { listOf(child).takeIf { child.text().isNotBlank() }.orEmpty() }
            else -> null
        }
    }.filter { it.isNotEmpty() }
    val aligned = when {
        bases.size == 1 -> listOf(bases.single() to groups.flatten())
        groups.size == bases.size -> bases.zip(groups)
        groups.size == 1 && groups.single().size == bases.size -> bases.zip(groups.single().map { listOf(it) })
        groups.size == 1 && groups.single().size == 1 ->
            listOf(TtmlBaseRange(bases.first().start, bases.last().end) to groups.single())
        else -> emptyList() // Ambiguous complex ruby must not be attached to the wrong base.
    }
    return aligned.flatMap { (base, annotations) -> annotations.mapNotNull { annotation ->
        val text = cleanTtmlText(annotation.text()).trim()
        if (text.isEmpty() || base.end <= base.start) null else {
            val timing = annotation.explicitTiming()
            LyricRuby(text, base.start, base.end, timing?.first, timing?.second)
        }
    } }
}

/** Timed transliteration can be inline or a Head sidecar; main/background voices stay separate. */
internal fun ttmlRomanWords(node: TtmlNode, background: Boolean = false): List<TtmlTimedText> {
    val result = mutableListOf<TtmlTimedText>()
    fun mainText(part: TtmlNode): String = part.content.joinToString("") {
        when {
            it is String -> if (it.isBlank() && ('\n' in it || '\r' in it)) "" else cleanTtmlText(it)
            it is TtmlNode && !it.hasRole("x-bg") -> mainText(it)
            else -> ""
        }
    }
    fun visit(part: TtmlNode, inheritedBackground: Boolean) {
        val isBackground = inheritedBackground || part.hasRole("x-bg")
        if (isBackground && !background) return
        val count = result.size
        part.children.forEach { visit(it, isBackground) }
        if (isBackground == background && count == result.size) {
            val timing = part.explicitTiming() ?: return
            val text = cleanTtmlText(mainText(part)).trim()
            if (timing.second > timing.first && text.isNotEmpty()) result.add(TtmlTimedText(text, timing.first, timing.second))
        }
    }
    visit(node, false)
    return result.sortedBy { it.start }
}

/** Only unambiguous real time containment may associate a reading with a word. */
internal fun alignTtmlRomanization(words: List<LyricWord>, tracks: List<List<TtmlTimedText>>): List<LyricWord> {
    for (track in tracks) {
        val readings = Array(words.size) { mutableListOf<TtmlTimedText>() }
        for (reading in track) {
            val matches = words.indices.filter { reading.start >= words[it].startTimeMs && reading.end <= words[it].endTimeMs }
            if (matches.size == 1) readings[matches.single()].add(reading)
        }
        if (readings.any { it.isNotEmpty() }) return words.mapIndexed { index, word ->
            if (word.romanization.isNotEmpty() || readings[index].isEmpty()) word
            else word.copy(romanization = readings[index].joinToString("") { it.text })
        }
    }
    return words
}
