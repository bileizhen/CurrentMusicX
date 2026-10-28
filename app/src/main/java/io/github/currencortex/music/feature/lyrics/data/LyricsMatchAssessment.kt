package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.feature.lyrics.domain.LyricsReferenceScore

/** Search metadata is a hint; only original lyric content and recording timing qualify a match. */
data class LyricsMatchAssessment(val matchPercent: Int? = null, val hasWordTiming: Boolean = false,
    val unavailableReason: String? = null, val score: LyricsReferenceScore? = null,
    val metadataReliable: Boolean = false) {
    val accepted: Boolean get() = metadataReliable && score?.accepted == true
    val preferredWord: Boolean get() = accepted && score?.hasWordTiming == true
}

/** The exact winner and score from one automatic comparison; displaying it needs no second lookup. */
data class LyricsMatchResult(val saved: SavedLyrics, val assessment: LyricsMatchAssessment)
