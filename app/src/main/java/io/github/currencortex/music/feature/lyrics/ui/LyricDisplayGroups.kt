package io.github.currencortex.music.feature.lyrics.ui

import io.github.currencortex.music.feature.lyrics.model.LyricWord
import java.text.BreakIterator
import java.util.Locale

/** Word-index ranges used for one highlight cue. Source word timing remains untouched. */
internal fun lyricDisplayGroups(text: String, words: List<LyricWord>): List<IntRange> {
    if (words.isEmpty()) return emptyList()
    val cjkSegments = cjkWordSegments(text)
    val grouped = mutableListOf<IntRange>()
    var previousSegment = -1
    words.forEachIndexed { index, word ->
        if (word.text.isBlank() && grouped.isNotEmpty()) {
            grouped[grouped.lastIndex] = grouped.last().first..index
            previousSegment = -1
            return@forEachIndexed
        }
        val segment = if (word.startOffset >= 0 && word.endOffset > word.startOffset)
            cjkSegments.indexOfFirst { word.startOffset >= it.first && word.endOffset <= it.last + 1 }
        else -1
        val previous = grouped.lastOrNull()
        if (previous != null && segment >= 0 && segment == previousSegment) {
            grouped[grouped.lastIndex] = previous.first..index
        } else {
            grouped += index..index
        }
        previousSegment = segment
    }

    val sticky = mutableListOf<IntRange>()
    grouped.forEach { range ->
        val current = words.slice(range).joinToString("") { it.text }.trim()
        val previous = sticky.lastOrNull()
        val previousText = previous?.let { words.slice(it).joinToString("") { word -> word.text }.trimEnd() }.orEmpty()
        val attach = previous != null && when {
            current.matches(TRAILING_PUNCTUATION) -> previousText.lastOrNull()?.let(Char::isLetterOrDigit) == true
            current.matches(APOSTROPHE) -> previousText.lastOrNull()?.let(Char::isLetterOrDigit) == true
            current.matches(DIRECT_CONTRACTION) -> previousText.lastOrNull()?.let(Char::isLetterOrDigit) == true
            current.matches(CONTRACTION_SUFFIX) -> previousText.endsWith("'") || previousText.endsWith("’")
            else -> false
        }
        if (attach) sticky[sticky.lastIndex] = requireNotNull(previous).first..range.last
        else sticky += range
    }
    return sticky
}

private fun cjkWordSegments(text: String): List<IntRange> {
    val iterator = BreakIterator.getWordInstance(Locale.ROOT).apply { setText(text) }
    val result = mutableListOf<IntRange>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        if ((start until end).any { index -> isCjkCodePoint(text.codePointAt(index)) }) result += start until end
        start = end
        end = iterator.next()
    }
    return result
}

private fun isCjkCodePoint(point: Int) = point in 0x3400..0x9fff || point in 0x3040..0x30ff || point in 0xac00..0xd7af
private val TRAILING_PUNCTUATION = Regex("""^[,.;:!?，。！？、：；）】》」』〉〕］)}\]\"'’”’]+$""")
private val APOSTROPHE = Regex("^['’]$")
private val DIRECT_CONTRACTION = Regex("^['’](s|t|m|d|ll|re|ve|em)$", RegexOption.IGNORE_CASE)
private val CONTRACTION_SUFFIX = Regex("^(s|t|m|d|ll|re|ve|em)$", RegexOption.IGNORE_CASE)
