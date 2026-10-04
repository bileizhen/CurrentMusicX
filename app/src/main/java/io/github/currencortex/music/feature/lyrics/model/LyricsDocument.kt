package io.github.currencortex.music.feature.lyrics.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable @Serializable
data class LyricsDocument(val lines: List<LyricLine> = emptyList()) {
    val hasWordTiming: Boolean get() = lines.any { it.words.isNotEmpty() }
}

@Immutable @Serializable
data class LyricLine(
    val startTimeMs: Long, val endTimeMs: Long, val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String = "", val romanization: String = "",
)

/** Offsets refer to UTF-16 ranges in the complete, laid-out line (including spaces). */
@Immutable @Serializable
data class LyricWord(val text: String, val startTimeMs: Long, val endTimeMs: Long,
    val startOffset: Int, val endOffset: Int)
