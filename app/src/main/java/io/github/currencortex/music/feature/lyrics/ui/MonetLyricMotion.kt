// SPDX-License-Identifier: AGPL-3.0-only
// Ported from Folia (chthollyphile and contributors), commit 26c57f9b1b26b402a09e8943171a332215418ce6.
// monetLyricMotion.ts, monetRailLayout.ts and monetLyricsModel.ts. See THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.feature.lyrics.ui

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.ceil
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricWord

internal const val MONET_SCROLL_STIFFNESS = 142f / .82f
internal val MONET_SCROLL_DAMPING = 28f / (2f * sqrt(142f * .82f))
internal const val MONET_SCALE_STIFFNESS = 150f / .78f
internal val MONET_SCALE_DAMPING = 30f / (2f * sqrt(150f * .78f))
// Compose's default .01 scale threshold snaps roughly 1% of a whole paragraph at ~600ms.
// Keep subpixel scale motion continuous while the independent position spring finishes.
internal fun monetScaleSpring(): SpringSpec<Float> =
    spring(MONET_SCALE_DAMPING, MONET_SCALE_STIFFNESS, visibilityThreshold = .0001f)
internal const val MONET_IDLE_RESET_MS = 1800L

/** Extra tracks fill a taller lyric viewport without mounting the entire song. */
internal fun monetViewportRadius(height: Float, fontPx: Float): Int =
    (ceil(height.coerceAtLeast(0f) * .54f / (fontPx * 1.1f).coerceAtLeast(1f)).toInt() + 1).coerceIn(4, 24)

internal data class MonetTone(val opacity: Float, val scale: Float, val blur: Float, val baseAlpha: Float)

internal fun monetTone(active: Boolean, offset: Int): MonetTone {
    if (active) return MonetTone(1f, 1f, 0f, .34f)
    val distance = abs(offset).coerceAtLeast(1)
    val waiting = offset >= 0
    return MonetTone(
        if (waiting) (.72f - (distance - 1) * .18f).coerceIn(.36f, .72f)
        else (.52f - (distance - 1) * .12f).coerceIn(.28f, .52f),
        (.72f * .9f.pow(distance - 1)).coerceIn(.68f, .92f),
        if (waiting) { if (distance == 1) .7f else 1.8f + (distance - 2) * .8f }
        else 1.1f + (distance - 1) * .7f,
        if (waiting) .46f else .36f)
}

/** Fixed transform tracks: position is calculated from target heights, never animated layout. */
internal fun monetTrackPositions(heights: List<Float>, scales: List<Float>, anchor: Int,
    focusY: Float, fontPx: Float, active: Set<Int>): List<Float> {
    if (heights.isEmpty()) return emptyList()
    val y = MutableList(heights.size) { 0f }
    val safeAnchor = anchor.coerceIn(heights.indices)
    fun gap(a: Int, b: Int) = if (a in active || b in active) fontPx * .49f else fontPx * .38f
    y[safeAnchor] = focusY - heights[safeAnchor] * scales[safeAnchor] / 2f
    for (i in safeAnchor + 1..heights.lastIndex)
        y[i] = y[i - 1] + heights[i - 1] * scales[i - 1] + gap(i - 1, i)
    for (i in safeAnchor - 1 downTo 0)
        y[i] = y[i + 1] - heights[i] * scales[i] - gap(i, i + 1)
    return y
}

internal fun monetGlow(now: Long, start: Long, end: Long, lineEnd: Long): Float {
    if (now <= start) return 0f
    val rise = (end - start).coerceAtLeast(1) * 1.18f
    val peak = start + rise
    val tail = maxOf(lineEnd.toFloat(), end + 1050f)
    val value = if (now <= peak) (now - start) / rise
        else 1f - ((now - peak) / (tail - peak).coerceAtLeast(180f)).coerceIn(0f, 1f)
    return value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}

internal fun monetSweepSoftness(fontPx: Float): Float = (fontPx * .45f).coerceIn(6f, 16f)
internal fun monetSweepEnd(filled: Float, width: Float, softness: Float): Float =
    if (width <= 0f) 0f else filled + softness * (filled / width).coerceIn(0f, 1f)

/** Folia's parser-derived fallback gives equal media time to each grapheme, not equal pixel width. */
internal fun monetFillWidth(progress: Float, widths: List<Float>): Float {
    if (widths.isEmpty()) return 0f
    val index = progress.coerceIn(0f, 1f) * widths.size
    val whole = index.toInt().coerceAtMost(widths.size)
    var width = 0f
    for (i in 0 until whole) width += widths[i]
    return width + if (whole < widths.size) widths[whole] * (index - whole) else 0f
}

/**
 * Folia graphemeTiming.ts, aecd330b720eb8fc728e6b10ebcec261b144d683 (AGPL-3.0).
 * Reveal punctuation omitted by a timed source at the adjacent real cue. These zero-duration
 * masks are display-only: they are never written into the document or exported as word timing.
 */
internal fun monetDisplayWords(line: LyricLine): List<LyricWord> {
    if (line.wordsEstimated || line.words.isEmpty()) return emptyList()
    val words = line.words.filter { it.startOffset >= 0 && it.endOffset > it.startOffset &&
        it.endOffset <= line.text.length && line.text.substring(it.startOffset, it.endOffset) == it.text }
        .sortedBy { it.startOffset }
    if (words.isEmpty() || words.zipWithNext().any { (a, b) -> a.endOffset > b.startOffset }) return words
    val display = mutableListOf<LyricWord>()
    var cursor = 0
    fun gap(until: Int, at: Long) {
        if (cursor < until) display += LyricWord(line.text.substring(cursor, until), at, at,
            cursor, until, roles = listOf("x-display-gap"))
    }
    words.forEach { word ->
        gap(word.startOffset, word.startTimeMs)
        display += word
        cursor = word.endOffset
    }
    gap(line.text.length, words.maxOf { it.endTimeMs })
    return display
}
