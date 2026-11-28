package io.github.currencortex.music.feature.lyrics.domain

import io.github.currencortex.music.feature.lyrics.model.LyricsDocument

/** Mark the most repeated complete lyric text as an understated chorus cue. */
internal fun LyricsDocument.withDetectedChorus(): LyricsDocument {
    if (lines.any { line -> (line.roles + line.backgroundVocals.flatMap { it.roles }).any { it == CHORUS_ROLE } }) return this
    val counts = lines.asSequence()
        .filterNot { it.isBackground || it.text.isBlank() }
        .map { chorusKey(it.text) }
        .filter { it.length >= 2 }
        .groupingBy { it }
        .eachCount()
    val maximum = counts.values.maxOrNull() ?: return this
    if (maximum < 2) return this
    val chorusKeys = counts.filterValues { it == maximum }.keys
    return copy(lines = lines.map { line ->
        val key = chorusKey(line.text)
        if (line.isBackground || key !in chorusKeys) line
        else line.copy(roles = (line.roles + CHORUS_ROLE).distinct())
    })
}

private fun chorusKey(text: String): String = buildString(text.length) {
    var previousWasSpace = false
    text.codePoints().forEach { point ->
        if (Character.isLetterOrDigit(point)) {
            appendCodePoint(Character.toLowerCase(point))
            previousWasSpace = false
        } else if (!previousWasSpace) {
            append(' ')
            previousWasSpace = true
        }
    }
}.trim()

internal const val CHORUS_ROLE = "chorus"
