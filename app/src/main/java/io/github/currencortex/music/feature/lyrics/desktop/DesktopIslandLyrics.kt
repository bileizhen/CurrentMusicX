package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.ui.*

/** Preview and overlay share the same single-line shaping, timing and overflow behavior. */
@Composable internal fun DesktopIslandLyrics(source: LyricLine, position: State<Long>,
    options: DesktopLyricsSettings, motion: Boolean, translationTag: String) {
    val line = remember(source) { source.copy(text = islandSingleLine(source.text)) }
    val font = options.displayFontSize
    val padding = desktopEffectPadding()
    val stage = options.effect == DesktopEffect.STAGE
    val color = Color(options.textColor.toInt())
    val opacity = remember(stage, options.glowStrength) { mutableFloatStateOf(options.glowStrength * if (stage) 1f else .5f) }
    val base = remember(stage) { mutableFloatStateOf(if (stage) .35f else .55f) }
    val style = TextStyle(fontFamily = LyricsFontFamily, fontWeight = FontWeight.Medium,
        fontSynthesis = lyricsFontSynthesis(FontWeight.Medium), fontSize = font.sp,
        lineHeight = (font * 1.2f).sp, textAlign = TextAlign.Center)
    val bilingual = options.translation && line.translation.isNotBlank()
    // Center visible text, rather than counting the primary canvas's halo margin
    // as ink. Keep that canvas intact and reserve space in both hosts for overflow.
    Column(Modifier.fillMaxWidth().padding(bottom = if (bilingual) desktopIslandTranslationBottomPadding else 0.dp)
        .graphicsLayer { translationY = if (bilingual) -((padding - desktopIslandTranslationBottomPadding) / 2).toPx() else 0f },
        verticalArrangement = Arrangement.spacedBy(desktopLineSpacing())) {
        DesktopIslandMarquee(line.text, style, position, line.startTimeMs, motion, padding) {
            KaraokeText(line, position, animateWords = line.words.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                fontSize = font, fontWeight = FontWeight.Medium, reservedFontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, singleLine = true, effectPadding = padding, prepareDrawResources = true,
                forceLineAnimation = false, glow = motion && options.glowEnabled && options.glowStrength > 0f,
                wordLift = motion && stage, accurateWordLift = true, glowOpacity = opacity, baseOpacity = base,
                color = color, glowColor = Color(options.glowColor.toInt()),
                glowRadiusScale = desktopGlowSpread(options.glowSpread),
                untimedLineGlow = options.glowEnabled && options.glowStrength > 0f)
        }
        if (options.translation && !line.translation.isNullOrBlank()) {
            val translation = remember(line.translation) { islandSingleLine(line.translation) }
            val subtitle = TextStyle(fontSize = desktopSubtitleSize(font).sp, lineHeight = (desktopSubtitleSize(font) * 1.2f).sp,
                color = color.copy(.75f), textAlign = TextAlign.Center)
            DesktopIslandMarquee(translation, subtitle, position, line.startTimeMs, motion, padding) {
                BasicText(translation, Modifier.fillMaxWidth().padding(horizontal = padding).testTag(translationTag),
                    style = subtitle, softWrap = false, maxLines = 1)
            }
        }
    }
}
