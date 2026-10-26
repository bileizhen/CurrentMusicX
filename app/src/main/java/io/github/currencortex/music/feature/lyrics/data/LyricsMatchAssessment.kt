package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.feature.lyrics.domain.LyricsReferenceScore

/** Content/recording assessment against a real lyric reference, distinct from metadata matching. */
data class LyricsMatchAssessment(val score: LyricsReferenceScore? = null, val unavailableReason: String? = null)

/** The exact winner and score from one automatic comparison; displaying it needs no second lookup. */
data class LyricsMatchResult(val saved: SavedLyrics, val assessment: LyricsMatchAssessment)
