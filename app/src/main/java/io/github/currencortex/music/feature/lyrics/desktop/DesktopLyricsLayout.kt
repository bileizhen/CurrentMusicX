package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.ui.unit.dp

// Reuse transparent halo margins as spacing without clipping the bitmap.
// Only adjacent title/translation layouts overlap; the canvas keeps all 20 dp.
internal val desktopHaloPadding = 20.dp
internal val desktopCaptionSpacing = (-14).dp
internal fun desktopTranslationSize(font: Float): Float = (font * .62f).coerceAtLeast(12f)

internal fun desktopWindowWidthDp(appearance: DesktopAppearance): Int = if (appearance == DesktopAppearance.ISLAND) 640 else 320

// Treat the source's explicit instrumental message as a status, never infer it from missing lyrics.
internal fun desktopIslandStatusLine(line: io.github.currencortex.music.feature.lyrics.model.LyricLine?): Boolean =
    line != null && line.words.isEmpty() && line.text.trim().trimEnd('。', '.', '!', '！').replace('，', ',') == "纯音乐,请欣赏"

internal fun desktopEffectPadding(island: Boolean) = if (island) 10.dp else desktopHaloPadding
internal val desktopIslandTranslationBottomPadding = 2.dp
internal fun desktopLineSpacing(island: Boolean) = if (island) (-8).dp else desktopCaptionSpacing
internal fun desktopGlowSpread(island: Boolean, spread: Float): Float = if (island) .5f + (spread - .5f) * .2f else spread
internal fun desktopSubtitleSize(island: Boolean, font: Float) = if (island) (font * .72f).coerceAtLeast(10f) else desktopTranslationSize(font)

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
