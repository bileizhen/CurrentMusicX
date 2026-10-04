package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontWeight
import io.github.currencortex.music.data.settings.LyricsWeight
import io.github.currencortex.music.R

/** Unmodified LXGW WenKai v1.522, SIL OFL 1.1. Bundled for offline lyrics. */
val LyricsFontFamily = FontFamily(
    Font(R.font.lxgw_wenkai_regular, weight = FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.lxgw_wenkai_medium, weight = FontWeight.Medium, loadingStrategy = FontLoadingStrategy.Async),
)

// Upstream stops at Medium; heavier selections explicitly permit platform weight synthesis.
fun lyricsFontWeight(mode: LyricsWeight, current: Boolean, strength: Int = 500) =
    if (mode.isBold(current)) FontWeight(strength.coerceIn(400, 900)) else FontWeight.Normal

fun lyricsFontSynthesis(weight: FontWeight) = if (weight.weight > 500)
    androidx.compose.ui.text.font.FontSynthesis.Weight else androidx.compose.ui.text.font.FontSynthesis.None
