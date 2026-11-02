package io.github.currencortex.music.feature.lyrics.model

private val translationBlock = Regex("\\[tr\\]([\\s\\S]*?)\\[/tr\\]", RegexOption.IGNORE_CASE)

/** Normalize legacy inline translations, including documents already stored in the lyric cache. */
fun LyricsDocument.withInlineTranslationBlocks(): LyricsDocument = copy(lines = lines.map { line ->
    line.withInlineTranslationBlocks().copy(backgroundVocals =
        LyricsDocument(line.backgroundVocals).withInlineTranslationBlocks().lines)
})

internal fun LyricLine.withInlineTranslationBlocks(): LyricLine {
    val blocks = translationBlock.findAll(text).toList()
    if (blocks.isEmpty()) return this
    val removed = BooleanArray(text.length)
    blocks.forEach { match -> match.range.forEach { removed[it] = true } }
    val retained = text.indices.filterNot { removed[it] }
    val from = retained.indexOfFirst { !text[it].isWhitespace() }
    val until = retained.indexOfLast { !text[it].isWhitespace() }
    val translated = blocks.map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    // A standalone translator credit still needs a visible line; never discard its text.
    if (from < 0) return copy(text = translated, words = emptyList())
    val offsets = IntArray(text.length) { -1 }
    val primary = buildString {
        for (i in from..until) { val old = retained[i]; offsets[old] = length; append(text[old]) }
    }
    val remapped = words.mapNotNull { word ->
        val start = word.startOffset.coerceAtLeast(0)
        val end = word.endOffset.coerceAtMost(text.length)
        val kept = (start until end).filter { offsets[it] >= 0 }
        if (kept.isEmpty()) null else word.copy(text = kept.joinToString("") { text[it].toString() },
            startOffset = offsets[kept.first()], endOffset = offsets[kept.last()] + 1)
    }
    return copy(text = primary, words = remapped, translation = translation.ifBlank { translated })
}
