// SPDX-License-Identifier: GPL-3.0-only
package io.github.currencortex.music.feature.lyrics.ui

import io.github.currencortex.music.data.settings.KaraokeScope
import io.github.currencortex.music.data.settings.LyricsDisplayOptions
import io.github.currencortex.music.data.settings.LyricsRenderQuality

/**
 * Resolve the saved preference against what the device can actually draw, before the first frame.
 * AUTO keeps the full stage on capable phones and drops to POWER_SAVER on low-RAM devices or below
 * API 31, where blur and per-word animation cost the most.
 */
internal fun effectiveLyricsRenderQuality(
    selected: LyricsRenderQuality,
    lowRam: Boolean,
    sdk: Int,
): LyricsRenderQuality = when (selected) {
    LyricsRenderQuality.AUTO ->
        if (lowRam || sdk < 31) LyricsRenderQuality.POWER_SAVER else LyricsRenderQuality.BALANCED
    else -> selected
}

/** Reduce the stage to what the resolved quality allows; layout and timing are untouched. */
internal fun LyricsDisplayOptions.withRenderQuality(quality: LyricsRenderQuality): LyricsDisplayOptions = when (quality) {
    LyricsRenderQuality.FULL, LyricsRenderQuality.AUTO -> this
    LyricsRenderQuality.BALANCED -> copy(blur = true, glow = true, wordLift = true, smoke = false, particles = false)
    LyricsRenderQuality.POWER_SAVER -> copy(
        blur = false, glow = false, wordLift = false, smoke = false, particles = false,
        stagger = false, karaokeScope = KaraokeScope.CURRENT,
        appleMusic = appleMusic.copy(blurStrength = 0f),
    )
}
