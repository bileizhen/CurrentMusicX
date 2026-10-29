package io.github.currencortex.music.feature.lyrics.data

/** Folia metadata eligibility and renderability, independent of a reference lyric. */
data class LyricsMatchAssessment(val matchPercent: Int? = null, val hasWordTiming: Boolean = false,
    val unavailableReason: String? = null, val metadataReliable: Boolean = false,
    val hasRenderableLyrics: Boolean = false) {
    val accepted: Boolean get() = metadataReliable && hasRenderableLyrics
    val preferredWord: Boolean get() = accepted && hasWordTiming
}

/** The exact winner and score from one automatic comparison; displaying it needs no second lookup. */
data class LyricsMatchResult(val saved: SavedLyrics, val assessment: LyricsMatchAssessment,
    val candidate: LyricsCandidate? = null)
