package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument

/** Standalone LRC has no auxiliary fields. Recognize foreign/Chinese pairs and
 * non-Latin original/Latin reading/Chinese triples at exactly the same timestamp.
 * Keep ambiguous voices and the original word timing intact.
 */
internal fun LyricsDocument.withBilingualLrcTranslations(): LyricsDocument = copy(
    lines = lines.groupBy { it.startTimeMs }.values.flatMap { group ->
        if (group.size !in 2..3 || group.any { !it.hasUnspecifiedRole() }) return@flatMap group
        val original = group[0]
        val auxiliary = group.drop(1)
        if (!original.text.hasNonHanLetters() || auxiliary.any { it.words.isNotEmpty() || it.ruby.isNotEmpty() })
            return@flatMap group
        val translated = auxiliary.singleOrNull {
            !it.text.hasNonHanLetters() && it.text.codePoints().anyMatch(Character::isIdeographic)
        } ?: return@flatMap group
        val reading = auxiliary.singleOrNull { it !== translated }
        // A second Latin-script vocal alongside an English original is not a reading.
        if (reading != null && (!original.text.hasNonLatinLetters() || !reading.text.hasOnlyLatinLetters()))
            return@flatMap group
        listOf(original.copy(translation = translated.text, romanization = reading?.text.orEmpty()))
    }
)

private fun String.hasNonHanLetters(): Boolean = codePoints().anyMatch {
    Character.isLetter(it) && !Character.isIdeographic(it)
}

private fun String.hasNonLatinLetters(): Boolean = codePoints().anyMatch {
    Character.isLetter(it) && Character.UnicodeScript.of(it) != Character.UnicodeScript.LATIN
}

private fun String.hasOnlyLatinLetters(): Boolean {
    val letters = codePoints().filter(Character::isLetter).toArray()
    return letters.isNotEmpty() && letters.all { Character.UnicodeScript.of(it) == Character.UnicodeScript.LATIN }
}

private fun LyricLine.hasUnspecifiedRole(): Boolean = translation.isBlank() && romanization.isBlank() &&
    translations.isEmpty() && romanizations.isEmpty() && !isBackground && !isDuet &&
    agent == null && roles.isEmpty() && backgroundVocals.isEmpty()
