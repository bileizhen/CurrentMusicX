package io.github.currencortex.music.feature.lyrics.ui

import io.github.currencortex.music.feature.lyrics.model.LyricWord

internal const val LYRIC_GLYPH_RISE_MS = 160L
/** Sung glyphs stay raised. Media time also resets the lift when seeking backwards. */
internal fun lyricGlyphLift(now: Long, start: Long): Float =
    ((now - start).toFloat() / LYRIC_GLYPH_RISE_MS).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

internal const val LYRIC_PERSPECTIVE_ANGLE = -32f

private val vowelGroups = Regex("[aeiouy]+", RegexOption.IGNORE_CASE)
internal fun lyricGlowWeight(word: LyricWord, lineRoles: List<String>): Float {
    if ((word.roles + lineRoles).any { it.lowercase() in setOf("chorus", "x-chorus", "emphasis", "x-emphasis") }) return 1f
    // Approximate sung syllables, so an entire multi-character phrase is not mistaken
    // for one sustained note. This is a lyric timing cue, not audio chorus detection.
    val cjk = word.text.count { it in '\u3400'..'\u9fff' || it in '\u3040'..'\u30ff' || it in '\uac00'..'\ud7af' }
    val syllables = (cjk + vowelGroups.findAll(word.text).count()).coerceAtLeast(1)
    val duration = (word.endTimeMs - word.startTimeMs).toFloat() / syllables
    // Medium held notes can bloom softly; short ordinary syllables remain unlit.
    return ((duration - 550f) / 700f).coerceIn(0f, 1f)
}

/** Reveal a whole soft halo as a glyph is sung, without slicing its blur footprint. */
internal fun lyricGlowReveal(progress: Float): Float =
    (progress / .7f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

internal fun lyricGlowStrength(now: Long, start: Long, end: Long): Float {
    if (end <= start || now <= start || now >= end) return 0f
    val progress = (now - start).toFloat() / (end - start)
    fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    return smooth(progress / .55f) * smooth((1f - progress) / .18f)
}
