// SPDX-License-Identifier: AGPL-3.0-only
// AMLL float/emphasize/mask motion adapted to media time; see THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.feature.lyrics.ui

import io.github.currencortex.music.feature.lyrics.model.LyricWord
import kotlin.math.pow
import kotlin.math.sqrt

private fun appleEase(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

private fun appleMotionSpeed(speed: Float) = if (speed.isFinite()) speed.coerceIn(.5f, 2f) else 1f

/** Only visual envelopes use this rate; source cues and the karaoke sweep keep media time. */
internal fun appleVisualDuration(durationMs: Int, speed: Float): Int =
    (durationMs / appleMotionSpeed(speed)).toInt().coerceAtLeast(1)

/** The outer halo is independent of the word's fill/highlight alpha. */
internal fun appleGlowAlpha(wave: Float, emphasis: Float, intensity: Float): Float {
    val strength = if (intensity.isFinite()) intensity.coerceIn(0f, 2f) else 1f
    return (wave * emphasis * .65f * strength).coerceIn(0f, 1f)
}

/** Media-time curves make pause and backwards seeks deterministic, without per-word jobs. */
internal fun appleWordLiftEm(now: Long, start: Long, end: Long, speed: Float = 1f): Float {
    if (end <= start || now <= start) return 0f
    val duration = (end.toDouble() - start).coerceAtLeast(1000.0) / appleMotionSpeed(speed)
    return .05f * appleCurve(appleEaseOut, ((now.toDouble() - start) / duration).toFloat())
}

/** AMLL emphasizes CJK cues and short non-CJK words, without changing their source timing. */
@Suppress("UNUSED_PARAMETER")
internal fun appleSustainedWord(text: String, start: Long, end: Long, roles: List<String> = emptyList()): Boolean {
    val duration = end.toDouble() - start
    if (duration < 1000 || text.isBlank()) return false
    return text.any { it in '\u3400'..'\u9fff' || it in '\u3040'..'\u30ff' || it in '\uac00'..'\ud7af' } ||
        text.trim().codePointCount(0, text.trim().length) in 2..7
}

/** Visual tails may finish after a cue. Selection/seek continue to use the untouched source cue. */
internal fun appleSustainWave(now: Long, start: Long, end: Long, clusterFraction: Float, last: Boolean = false,
    speed: Float = 1f): Float {
    if (end <= start || now <= start) return 0f
    val duration = (end.toDouble() - start).coerceAtLeast(1000.0) * (if (last) 1.2 else 1.0) / appleMotionSpeed(speed)
    val progress = ((now.toDouble() - start) / duration - clusterFraction.coerceIn(0f, 1f) * .4).toFloat()
    if (progress <= 0f || progress >= 1f) return 0f
    return if (progress < .5f) appleCurve(appleRise, progress * 2f) else 1f - appleCurve(appleFall, (progress - .5f) * 2f)
}

internal fun appleEmphasisAmount(durationMs: Long, last: Boolean = false): Float {
    val duration = durationMs.coerceAtLeast(1000).toFloat() / 2000f
    return (.6f * (if (duration > 1f) sqrt(duration) else duration.pow(3)) * if (last) 1.6f else 1f).coerceAtMost(1.2f)
}

internal fun appleSustainScale(now: Long, start: Long, end: Long, clusterFraction: Float, speed: Float = 1f): Float =
    1f + .1f * appleEmphasisAmount(end - start) * appleSustainWave(now, start, end, clusterFraction, speed = speed)

internal fun appleMotionEnd(start: Long, end: Long, sustained: Boolean, last: Boolean, speed: Float = 1f): Long =
    (start.toDouble() + (end.toDouble() - start).coerceAtLeast(1000.0) *
        (if (sustained) (if (last) 1.2 else 1.0) * 1.4 else 1.0) /
        appleMotionSpeed(speed)).coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()

/** One continuous line sweep pauses during real word gaps instead of restarting at every word. */
internal fun appleLineSweep(now: Long, words: List<LyricWord>, widths: FloatArray, feather: Float): Float {
    var sweep = 0f
    words.forEachIndexed { index, word ->
        if (now < word.startTimeMs) return sweep
        val progress = applePhoneticProgress(now, word.startTimeMs, word.endTimeMs) ?: 0f
        sweep += ((widths.getOrNull(index) ?: 0f) + (if (index == 0) feather * 1.5f else 0f) +
            (if (index == words.lastIndex) feather * .5f else 0f)) * progress
        if (progress < 1f) return sweep
    }
    return sweep
}

internal fun appleLineBrightness(sweep: Float, logicalX: Float, feather: Float): Float =
    (1f - .6f * ((logicalX - (sweep - feather * 2f)) / feather.coerceAtLeast(.01f)).coerceIn(0f, 1f)).coerceIn(.4f, 1f)

/** Overlapping source words cannot share one moving edge without postponing the later cue. */
internal fun appleWordTimingsOverlap(words: List<LyricWord>): Boolean {
    var latestEnd = Long.MIN_VALUE
    for (word in words) {
        if (word.startTimeMs < latestEnd) return true
        latestEnd = maxOf(latestEnd, word.endTimeMs)
    }
    return false
}

internal fun appleIndependentWordBrightness(now: Long, start: Long, end: Long, logicalX: Float,
    width: Float, feather: Float): Float {
    val progress = applePhoneticProgress(now, start, end) ?: 0f
    return appleLineBrightness(progress * (width + 2f * feather), logicalX.coerceIn(0f, width), feather)
}

private fun appleBezierTable(x1: Float, y1: Float, x2: Float, y2: Float) = FloatArray(257) { index ->
    val x = index / 256f
    var low = 0f; var high = 1f
    repeat(18) {
        val t = (low + high) * .5f; val one = 1f - t
        val sample = 3f * one * one * t * x1 + 3f * one * t * t * x2 + t * t * t
        if (sample < x) low = t else high = t
    }
    val t = (low + high) * .5f; val one = 1f - t
    3f * one * one * t * y1 + 3f * one * t * t * y2 + t * t * t
}.also { it[0] = 0f; it[256] = 1f }
private val appleEaseOut = appleBezierTable(0f, 0f, .58f, 1f)
private val appleRise = appleBezierTable(.2f, .4f, .58f, 1f)
private val appleFall = appleBezierTable(.3f, 0f, .58f, 1f)
private fun appleCurve(table: FloatArray, progress: Float): Float {
    val index = progress.coerceIn(0f, 1f) * 256f
    val from = index.toInt().coerceAtMost(255)
    return table[from] + (table[from + 1] - table[from]) * (index - from)
}

/** A vocal's layer moves only near its real cue; seeking recomputes the same layer immediately. */
internal fun appleBackgroundPresence(now: Long, start: Long, end: Long, paused: Boolean,
    browsing: Boolean, motionEnabled: Boolean, speed: Float = 1f): Float {
    if (paused || browsing) return 1f
    if (end <= start) return 0f
    if (now >= start && now < end) return 1f
    if (!motionEnabled) return 0f
    return if (now < start) appleEase((1.0 - (start.toDouble() - now) * appleMotionSpeed(speed) / 250.0).toFloat())
        else 1f - appleEase(((now.toDouble() - end) * appleMotionSpeed(speed) / 300.0).toFloat())
}

/** Null timing stays static; a zero-length source syllable is an instantaneous cue. */
internal fun applePhoneticProgress(now: Long, start: Long?, end: Long?): Float? {
    if (start == null || end == null || end < start) return null
    if (end == start) return if (now >= end) 1f else 0f
    return ((now.toDouble() - start) / (end.toDouble() - start)).toFloat().coerceIn(0f, 1f)
}
