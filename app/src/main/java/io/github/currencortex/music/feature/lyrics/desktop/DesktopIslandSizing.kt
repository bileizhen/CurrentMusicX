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
            TextStyle(fontSize = desktopSubtitleSize(font).sp))
        val primaryStyle = TextStyle(fontFamily = LyricsFontFamily, fontSize = font.sp,
            lineHeight = (font * 1.2f).sp, fontWeight = FontWeight.Medium)
        val primaryHeight = with(density) { measurer.measure(AnnotatedString(islandSingleLine(text)), primaryStyle,
            softWrap = false, maxLines = 1).size.height.toDp().value }
        val subtitleHeight = if (compact || translation.isNullOrBlank()) 0f else with(density) {
            measurer.measure(AnnotatedString(islandSingleLine(translation)), TextStyle(fontSize = desktopSubtitleSize(font).sp,
                lineHeight = (desktopSubtitleSize(font) * 1.2f).sp), softWrap = false, maxLines = 1).size.height.toDp().value + desktopLineSpacing().value + desktopIslandTranslationBottomPadding.value
        }
        val cover = if (compact) 22f else (maxOf(30f, primaryHeight + desktopEffectPadding().value * 2 + subtitleHeight) - 8f).coerceAtLeast(22f)
        // Match the frame's 2 dp outer + 5 dp inner horizontal insets exactly.
        // Extra spare width would get centered around the paragraph and widen its 6 dp gap.
        val textPadding = if (compact) 12f else desktopEffectPadding().value * 2
        val preferred = cover + desktopIslandCoverGap.value + desktopIslandProgressWidth.value +
            maxOf(lyricWidth, subtitleWidth) + textPadding + 14f - desktopIslandLyricInset.value * 2
        DesktopIslandSize(preferred.coerceIn(if (editing) 240f else 48f, 640f), metadata.dp)
    }
}
