package io.github.currencortex.music.feature.lyrics.ui

import android.icu.text.BreakIterator
import java.util.Locale

/** UTF-16 offsets into the shaped paragraph, never individual surrogate/code-unit glyphs. */
internal fun lyricsClusters(text: String): List<IntRange> {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val ranges = mutableListOf<IntRange>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        ranges += start until end
        start = end; end = iterator.next()
    }
    return ranges
}

// Moving sliced ink would detach Arabic/Indic joining and combining marks from their
// neighbours. Keep the shaped paragraph intact and animate only timing/color/halo there.
internal fun lyricsCanLift(text: String): Boolean = text.codePoints().allMatch { point ->
    Character.getType(point) !in setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt()) &&
        Character.UnicodeScript.of(point) !in setOf(Character.UnicodeScript.ARABIC, Character.UnicodeScript.DEVANAGARI,
            Character.UnicodeScript.BENGALI, Character.UnicodeScript.TAMIL, Character.UnicodeScript.THAI)
}
