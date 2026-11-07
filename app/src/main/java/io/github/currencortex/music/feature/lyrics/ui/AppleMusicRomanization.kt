package io.github.currencortex.music.feature.lyrics.ui

import io.github.currencortex.music.feature.lyrics.model.LyricLine

/** Hide the line reading only when the displayed word readings preserve all of it. */
internal fun appleWordReadingsCoverLine(line: LyricLine): Boolean {
    val reading = line.romanization.filterNot(Char::isWhitespace)
    if (reading.isEmpty()) return false
    val words = line.words.filter {
        it.romanization.isNotBlank() && it.startOffset >= 0 && it.endOffset > it.startOffset &&
            it.endOffset <= line.text.length && line.text.substring(it.startOffset, it.endOffset) == it.text
    }.sortedBy { it.startOffset }
    return words.joinToString("") { it.romanization }.filterNot(Char::isWhitespace) == reading
}
