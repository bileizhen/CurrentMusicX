package io.github.currencortex.music.feature.lyrics.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable @Serializable
data class LyricsDocument(val lines: List<LyricLine> = emptyList(), val metadata: LyricsMetadata = LyricsMetadata()) {
    val hasWordTiming: Boolean get() = lines.any { it.words.isNotEmpty() || it.backgroundVocals.any { bg -> bg.words.isNotEmpty() } }

    /** Compatibility with cached documents written by the old estimation adapter. */
    fun withoutEstimatedTiming(): LyricsDocument {
        fun clean(line: LyricLine): LyricLine = line.copy(
            words = if (line.wordsEstimated) emptyList() else line.words,
            wordsEstimated = false, backgroundVocals = line.backgroundVocals.map(::clean))
        return copy(lines = lines.map(::clean))
    }
}

@Immutable @Serializable
data class LyricLine(
    val startTimeMs: Long, val endTimeMs: Long, val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String = "", val romanization: String = "",
    val isBackground: Boolean = false, val isDuet: Boolean = false, val agent: String? = null,
    val roles: List<String> = emptyList(), val key: String? = null,
    val backgroundVocals: List<LyricLine> = emptyList(),
    val translations: List<LyricAuxiliary> = emptyList(), val romanizations: List<LyricAuxiliary> = emptyList(),
    val wordsEstimated: Boolean = false,
)

/** Offsets refer to UTF-16 ranges in the complete, laid-out line (including spaces). */
@Immutable @Serializable
data class LyricWord(val text: String, val startTimeMs: Long, val endTimeMs: Long,
    val startOffset: Int, val endOffset: Int, val agent: String? = null, val roles: List<String> = emptyList())

@Immutable @Serializable
data class LyricAuxiliary(val text: String, val language: String? = null)

@Immutable @Serializable
data class LyricAgent(val id: String, val type: String = "", val name: String = "")

@Immutable @Serializable
data class LyricsMetadata(val values: Map<String, List<String>> = emptyMap(),
    val agents: List<LyricAgent> = emptyList(), val language: String? = null,
    val durationMs: Long? = null, val source: String = "CurrentMusic")
