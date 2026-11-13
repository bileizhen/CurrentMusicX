package io.github.currencortex.music.feature.lyrics.ui

import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricRuby
import java.text.BreakIterator
import java.util.Locale

internal data class ClassicPhoneticCell(
    val startOffset: Int,
    val endOffset: Int,
    val romanization: String = "",
    val ruby: String = "",
)

internal data class ClassicPhoneticLine(
    val cells: List<ClassicPhoneticCell>,
    val fallbackRomanization: String,
)

/** Build display groups without changing source text, offsets, or playback cues. */
internal fun classicLyricPhonetics(line: LyricLine): ClassicPhoneticLine {
    // Keep pathological imported lines on the existing whole-paragraph renderer.
    if (line.text.length > 4096 || line.words.size > 256 || line.ruby.size > 256 ||
        line.text.any { it == '\n' || it == '\r' })
        return ClassicPhoneticLine(emptyList(), line.romanization)
    val clusters = classicTextClusters(line.text)
    val boundaries = clusters.mapTo(mutableSetOf(0)) { it.last + 1 }
    fun validRange(start: Int, end: Int) = start >= 0 && end > start && end <= line.text.length &&
        start in boundaries && end in boundaries

    val words = line.words.filter {
        validRange(it.startOffset, it.endOffset) && line.text.substring(it.startOffset, it.endOffset) == it.text
    }.sortedBy { it.startOffset }
    if (line.ruby.isEmpty() && words.sumOf { it.ruby.size.toLong() } > 256)
        return ClassicPhoneticLine(emptyList(), line.romanization)
    val ruby = line.ruby.ifEmpty { words.flatMap { it.ruby }.distinct() }.filter {
        it.text.isNotBlank() && validRange(it.startOffset, it.endOffset)
    }
    var readings = words.filter { it.romanization.isNotBlank() }.map {
        ClassicPhoneticCell(it.startOffset, it.endOffset, romanization = it.romanization.trim())
    }
    if (readings.isEmpty() && line.romanization.isNotBlank())
        readings = alignJapaneseRomanization(line.text, line.romanization)
    if (readings.isEmpty() && line.romanization.isNotBlank()) {
        val tokens = line.romanization.trim().split(Regex("\\s+"))
        val sungWords = words.filter { it.text.any(Char::isLetterOrDigit) }
        val characters = clusters.filter { range ->
            line.text.substring(range).any(Char::isLetterOrDigit)
        }
        // A provider's word segmentation is preferable to character segmentation. Only
        // complete, one-to-one token sets can be placed without losing a reading.
        val ranges = when {
            !line.wordsEstimated && sungWords.isNotEmpty() && sungWords.size == tokens.size &&
                sungWords.zipWithNext().all { (a, b) -> a.endOffset <= b.startOffset } &&
                characters.all { range -> sungWords.any { range.first >= it.startOffset && range.last < it.endOffset } } ->
                sungWords.map { it.startOffset until it.endOffset }
            words.isEmpty() && characters.size == tokens.size && characters.all { range ->
                line.text.substring(range).codePoints().allMatch { point ->
                    Character.UnicodeScript.of(point) in setOf(Character.UnicodeScript.HAN,
                        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA) ||
                        Character.getType(point) in setOf(Character.NON_SPACING_MARK.toInt(),
                            Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt())
                }
            } -> characters
            else -> emptyList()
        }
        readings = ranges.zip(tokens) { range, token ->
            ClassicPhoneticCell(range.first, range.last + 1, romanization = token)
        }
    }
    if (readings.isEmpty() && ruby.isEmpty()) return ClassicPhoneticLine(emptyList(), line.romanization)

    // Include whole source words so a ruby boundary never splits a real karaoke cue.
    val ranges = (words.map { it.startOffset until it.endOffset } +
        readings.map { it.startOffset until it.endOffset } + ruby.map { it.startOffset until it.endOffset } +
        japanesePhoneticGroups(line.text))
        .sortedBy { it.first }
    val groups = mutableListOf<IntRange>()
    for (range in ranges) {
        val previous = groups.lastOrNull()
        if (previous != null && range.first <= previous.last) {
            groups[groups.lastIndex] = previous.first..maxOf(previous.last, range.last)
        } else groups += range
    }
    val cells = mutableListOf<ClassicPhoneticCell>()
    fun appendGap(start: Int, end: Int) {
        if (end <= start) return
        // Preserve word shaping in unannotated text while allowing CJK text to wrap.
        val iterator = BreakIterator.getLineInstance(Locale.ROOT).apply { setText(line.text.substring(start, end)) }
        var from = iterator.first()
        var to = iterator.next()
        while (to != BreakIterator.DONE) {
            cells += ClassicPhoneticCell(start + from, start + to)
            from = to
            to = iterator.next()
        }
    }
    var cursor = 0
    for (range in groups) {
        appendGap(cursor, range.first)
        val reading = readings.filter { it.startOffset >= range.first && it.endOffset <= range.last + 1 }
            .joinToString(" ") { it.romanization }
        val annotations = ruby.filter { it.startOffset >= range.first && it.endOffset <= range.last + 1 }
            .sortedWith(compareBy<LyricRuby> { it.startOffset }.thenBy { it.startTimeMs ?: Long.MIN_VALUE })
            .joinToString("") { it.text }
        cells += ClassicPhoneticCell(range.first, range.last + 1, reading, annotations)
        cursor = range.last + 1
    }
    appendGap(cursor, line.text.length)
    if (cells.size > 256) return ClassicPhoneticLine(emptyList(), line.romanization)
    val coveredReading = readings.sortedBy { it.startOffset }.joinToString("") { it.romanization }
        .filterNot(Char::isWhitespace)
    val fallback = if (coveredReading == line.romanization.filterNot(Char::isWhitespace)) "" else line.romanization
    return ClassicPhoneticLine(cells, fallback)
}

private fun classicTextClusters(text: String): List<IntRange> {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val ranges = mutableListOf<IntRange>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        ranges += start until end
        start = end
        end = iterator.next()
    }
    return ranges
}
