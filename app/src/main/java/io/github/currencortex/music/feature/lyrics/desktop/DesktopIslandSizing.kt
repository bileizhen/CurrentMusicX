package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.ui.LyricsFontFamily

internal data class DesktopIslandSize(val width: Float, val metadata: Dp)

/** Measure once per cue/style, never from word-progress frames or the current window width. */
@Composable internal fun rememberIslandSize(song: Song?, text: String, font: Float, compact: Boolean,
    editing: Boolean, translation: String? = null): DesktopIslandSize {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val resolver = androidx.compose.ui.platform.LocalFontFamilyResolver.current
    val face by resolver.resolve(LyricsFontFamily, fontWeight = FontWeight.Medium)
    return remember(song?.name, song?.artists, text, translation, font, compact, editing, density, face) {
        fun width(value: String, style: TextStyle) = with(density) {
            measurer.measure(AnnotatedString(islandSingleLine(value)), style, softWrap = false, maxLines = 1).size.width.toDp().value
        }
        val name = song?.name?.takeIf(String::isNotBlank) ?: "CurrentMusic"
        val artist = song?.artists.orEmpty()
        val title = name
        val titleWidth = width(title, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
        val artistWidth = width(artist.ifBlank { "未知歌手" }, TextStyle(fontSize = 9.sp))
        val metadata = (28f + maxOf(titleWidth, artistWidth)).coerceIn(28f, 626f)
        val lyricWidth = width(text, TextStyle(fontFamily = if (compact) androidx.compose.ui.text.font.FontFamily.Monospace else LyricsFontFamily,
            fontSize = font.sp, fontWeight = if (compact) FontWeight.Normal else FontWeight.Medium))
        val subtitleWidth = if (compact || translation.isNullOrBlank()) 0f else width(translation,
            TextStyle(fontSize = desktopSubtitleSize(true, font).sp))
        val primaryStyle = TextStyle(fontFamily = LyricsFontFamily, fontSize = font.sp,
            lineHeight = (font * 1.2f).sp, fontWeight = FontWeight.Medium)
        val primaryHeight = with(density) { measurer.measure(AnnotatedString(islandSingleLine(text)), primaryStyle,
            softWrap = false, maxLines = 1).size.height.toDp().value }
        val subtitleHeight = if (compact || translation.isNullOrBlank()) 0f else with(density) {
            measurer.measure(AnnotatedString(islandSingleLine(translation)), TextStyle(fontSize = desktopSubtitleSize(true, font).sp,
                lineHeight = (desktopSubtitleSize(true, font) * 1.2f).sp), softWrap = false, maxLines = 1).size.height.toDp().value + desktopLineSpacing(true).value + desktopIslandTranslationBottomPadding.value
        }
        val cover = if (compact) 22f else (maxOf(30f, primaryHeight + desktopEffectPadding(true).value * 2 + subtitleHeight) - 8f).coerceAtLeast(22f)
        val preferred = (cover + 6f) + maxOf(lyricWidth, subtitleWidth) + if (compact) 39f else 47f
        DesktopIslandSize(preferred.coerceIn(if (editing) 240f else 48f, 640f), metadata.dp)
    }
}
