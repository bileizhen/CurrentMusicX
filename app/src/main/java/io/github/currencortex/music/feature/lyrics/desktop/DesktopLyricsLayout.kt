package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.ui.unit.dp

// Treat the source's explicit instrumental message as a status, never infer it from missing lyrics.
internal fun desktopIslandStatusLine(line: io.github.currencortex.music.feature.lyrics.model.LyricLine?): Boolean =
    line != null && line.words.isEmpty() && line.text.trim().trimEnd('。', '.', '!', '！').replace('，', ',') == "纯音乐,请欣赏"

internal fun desktopEffectPadding() = 10.dp
internal val desktopIslandTranslationBottomPadding = 2.dp
// Reserve transparent room outside the pill so optical centering never clips its halo.
internal val desktopIslandDrawingSpace = 4.dp
internal fun desktopLineSpacing() = (-8).dp
internal fun desktopGlowSpread(spread: Float): Float = .5f + (spread - .5f) * .2f
internal fun desktopSubtitleSize(font: Float) = (font * .72f).coerceAtLeast(10f)

/** Preview the first real cue before singing, then retain the last cue through silence; never manufacture an interlude caption. */
internal fun desktopIslandLineIndex(document: io.github.currencortex.music.feature.lyrics.model.LyricsDocument,
    timeline: io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer, position: Long): Int {
    var index = timeline.findCurrentLine(position).takeIf { it >= 0 } ?: timeline.scrollTarget(position)
    while (index >= 0) {
        val line = document.lines.getOrNull(index) ?: return -1
        if (line.startTimeMs <= position && desktopIslandRealLine(line)) return index
        index--
    }
    return document.lines.indexOfFirst(::desktopIslandRealLine)
}
private fun desktopIslandRealLine(line: io.github.currencortex.music.feature.lyrics.model.LyricLine) =
    line.text.isNotBlank() && !desktopIslandStatusLine(line) && line.text.trim() !in islandPlaceholderText
private val islandPlaceholderText = setOf("♪", "♫", "♬", "前奏", "间奏", "尾奏")

internal fun desktopIslandHasLyrics(document: io.github.currencortex.music.feature.lyrics.model.LyricsDocument) =
    document.lines.any(::desktopIslandRealLine)

internal fun desktopIslandNextLineIndex(document: io.github.currencortex.music.feature.lyrics.model.LyricsDocument, index: Int): Int =
    ((index + 1).coerceAtLeast(0) until document.lines.size).firstOrNull { desktopIslandRealLine(document.lines[it]) } ?: -1
