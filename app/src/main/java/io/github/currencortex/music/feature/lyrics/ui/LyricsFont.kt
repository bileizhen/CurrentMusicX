package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontWeight
import io.github.currencortex.music.data.settings.LyricsWeight

/** Follow the device's default typeface unless the user imports a custom font. */
val LyricsFontFamily = FontFamily.Default
val WenkaiLyricsFontFamily = FontFamily(
    Font(io.github.currencortex.music.R.font.lxgw_wenkai_regular, weight = FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(io.github.currencortex.music.R.font.lxgw_wenkai_medium, weight = FontWeight.Medium, loadingStrategy = FontLoadingStrategy.Async),
)
val LocalLyricsFont = androidx.compose.runtime.staticCompositionLocalOf<FontFamily> { LyricsFontFamily }

@androidx.compose.runtime.Composable
fun rememberLyricsFont(settings: io.github.currencortex.music.data.settings.MusicSettings): FontFamily {
    val imported = io.github.currencortex.music.ui.theme.rememberCustomFont(settings.lyricsFontFile)
    return when (settings.lyricsFont) {
        io.github.currencortex.music.data.settings.LyricsFont.SYSTEM -> LyricsFontFamily
        io.github.currencortex.music.data.settings.LyricsFont.WENKAI -> WenkaiLyricsFontFamily
        io.github.currencortex.music.data.settings.LyricsFont.IMPORTED -> imported ?: LyricsFontFamily
    }
}
@androidx.compose.runtime.Composable
fun currentLyricsFontFamily(): FontFamily = LocalLyricsFont.current

// Permit platform weight synthesis for heavier selections.
fun lyricsFontWeight(mode: LyricsWeight, current: Boolean, strength: Int = 500) =
    if (mode.isBold(current)) FontWeight(strength.coerceIn(400, 900)) else FontWeight.Normal

fun lyricsFontSynthesis(weight: FontWeight) = if (weight.weight > 500)
    androidx.compose.ui.text.font.FontSynthesis.Weight else androidx.compose.ui.text.font.FontSynthesis.None
