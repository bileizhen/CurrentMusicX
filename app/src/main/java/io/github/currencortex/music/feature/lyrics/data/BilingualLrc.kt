package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument

/** Standalone LRC has no translation field. Recognize the common foreign/Chinese
 * pair at exactly the same timestamp, without treating all simultaneous voices
 * (or nearby timestamps) as translations. Keep the original word timing intact.
 */
internal fun LyricsDocument.withBilingualLrcTranslations(): LyricsDocument = copy(
    lines = lines.groupBy { it.startTimeMs }.values.flatMap { group ->
        if (group.size != 2) return@flatMap group
        val original = group[0]
        val translated = group[1]
        if (!original.hasUnspecifiedRole() || !translated.hasUnspecifiedRole() ||
            translated.words.isNotEmpty() || !original.text.hasNonHanLetters() ||
            translated.text.hasNonHanLetters() || translated.text.codePoints().noneMatch(Character::isIdeographic)) {
            group
        } else listOf(original.copy(translation = translated.text))
    }
)

private fun String.hasNonHanLetters(): Boolean = codePoints().anyMatch {
    Character.isLetter(it) && !Character.isIdeographic(it)
}

private fun LyricLine.hasUnspecifiedRole(): Boolean = translation.isBlank() && romanization.isBlank() &&
    translations.isEmpty() && romanizations.isEmpty() && !isBackground && !isDuet &&
    agent == null && roles.isEmpty() && backgroundVocals.isEmpty()
