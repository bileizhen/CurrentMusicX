package io.github.currencortex.music.feature.lyrics.desktop

import android.hardware.input.InputManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.ui.KaraokeText
import io.github.currencortex.music.feature.lyrics.ui.rememberLyricsPosition
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Real cues use the existing clock/renderer; the untimed fallback never invents word timing. */
@Composable internal fun DesktopLyricsPreview(container: AppContainer, options: DesktopLyricsSettings) {
    val playback by container.playerController.state.collectAsStateWithLifecycle()
    val content by container.playbackLyrics.state.collectAsStateWithLifecycle()
    val island = options.appearance == DesktopAppearance.ISLAND
    val fpsMode = island && (playback.song == null || content.songId != playback.song?.id ||
        !remember(content.document) { desktopIslandHasLyrics(content.document) })
    val frameRate = rememberIslandFrameRate()
    val clock = rememberLyricsPosition(playback, fpsMode, if (fpsMode) frameRate::onFrame else null)
    val timeline = remember(content.document) { LyricsSynchronizer(content.document) }
    val time = remember(clock, options.offsetMs) { derivedStateOf { LyricsSynchronizer.effectivePosition(clock.value, options.offsetMs) } }
    val index by remember(timeline, time, island) { derivedStateOf { if (island) desktopIslandLineIndex(content.document, timeline, time.value) else timeline.findCurrentLine(time.value) } }
    val interlude by remember(timeline, time, island) { derivedStateOf { !island && timeline.interlude(time.value) != null } }
    val actual = content.document.lines.getOrNull(index).takeIf { content.songId == playback.song?.id && !interlude }
    val sample = remember { LyricLine(text = "音乐一直陪着你", startTimeMs = 0, endTimeMs = 0,
        translation = "The music stays with you") }
    val line = actual ?: sample
    val still = remember { mutableLongStateOf(0) }
    val motion = rememberDesktopMotion()
    val displayFont = options.displayFontSize
    val effectPadding = desktopEffectPadding(island)
    val capsule = options.appearance == DesktopAppearance.CAPSULE
    val shape = RoundedCornerShape(22.dp)
    val context = LocalContext.current
    val maximum = remember(context) { if (Build.VERSION.SDK_INT >= 31)
        context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch else .8f }
    val alpha = desktopWindowAlpha(options.opacity, options.locked, maximum)
    val glow = remember(options.effect, options.glowStrength) { mutableFloatStateOf(options.glowStrength * if (options.effect == DesktopEffect.STAGE) 1f else .5f) }
    val textColor = Color(options.textColor.toInt())
    val islandSize = if (island) rememberIslandSize(playback.song, if (fpsMode) "000.0 FPS" else line.text, displayFont, fpsMode, false,
        line.translation.takeIf { options.translation && !fpsMode }) else null
    val islandReveal = if (island) rememberIslandPresentation(playback.song?.id, fpsMode || actual != null || !content.loading, motion).value else 1f
    val introWidth = ((islandSize?.metadata?.value ?: 120f) + 14f).coerceIn(48f, 640f)
    val islandWidth = rememberIslandWidth(if (islandReveal < .15f) introWidth else islandSize?.width ?: introWidth, motion)
    val colors = MiuixTheme.colorScheme
    Card(Modifier.fillMaxWidth().testTag("desktop_preview")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("实时预览", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(if (fpsMode) "实时帧采样" else if (actual == null) "示例歌词" else if (playback.playing) "跟随播放" else "已暂停",
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF303646), Color(0xFF161A25)))).padding(12.dp), contentAlignment = Alignment.Center) {
                val material = if (capsule) Modifier.background(Brush.linearGradient(listOf(Color(0xCC222538), Color(0xAA10131F))), shape)
                    .border(1.dp, Color.White.copy(alpha = .18f), shape) else Modifier
                val lyricBody: @Composable () -> Unit = {
                    if (fpsMode) DesktopIslandFrameRate(frameRate.rate, displayFont, textColor)
                    else if (island) {
                        val nextIndex = if (actual == null) -1 else remember(content.document, index) { desktopIslandNextLineIndex(content.document, index) }
                        DesktopIslandCueHost(playback.song?.id, index, line, nextIndex, content.document.lines.getOrNull(nextIndex), options to motion) { cue ->
                            DesktopIslandLyrics(cue, if (actual == null) still else time, options, motion, "desktop_preview_translation")
                        }
                    }
                    else Column(Modifier.fillMaxWidth().then(if (island) Modifier else Modifier.padding(horizontal = 4.dp, vertical = 8.dp)),
                        verticalArrangement = Arrangement.spacedBy(desktopLineSpacing(island))) {
                        if (!options.locked && !island) Text(if (actual == null) "CurrentMusic · 样式预览" else playback.song?.name.orEmpty(),
                            modifier = Modifier.padding(horizontal = desktopHaloPadding),
                            fontSize = 11.sp, color = Color.White.copy(alpha = .55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.heightIn(max = 180.dp).clipToBounds()) {
                            KaraokeText(line, if (actual == null) still else time, animateWords = line.words.isNotEmpty(),
                                textAlign = if (island) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                                fontSize = displayFont, fontWeight = FontWeight.Medium,
                                forceLineAnimation = false, glow = motion && options.glowEnabled && options.glowStrength > 0f,
                                wordLift = motion && options.effect == DesktopEffect.STAGE, accurateWordLift = true,
                                glowOpacity = glow, color = textColor, effectPadding = effectPadding,
                                glowColor = Color(options.glowColor.toInt()), glowRadiusScale = desktopGlowSpread(island, options.glowSpread),
                                untimedLineGlow = options.glowEnabled && options.glowStrength > 0f)
                        }
                        if (options.translation && !line.translation.isNullOrBlank()) Text(line.translation,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = effectPadding).testTag("desktop_preview_translation"),
                            textAlign = if (island) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                            fontSize = desktopSubtitleSize(island, displayFont).sp,
                            color = textColor.copy(alpha = .75f), maxLines = if (island) 1 else 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (island) DesktopIslandFrame(playback.song, Modifier.islandAnimatedWidth(islandWidth).fillMaxWidth().graphicsLayer { this.alpha = alpha }, metadata = islandSize?.metadata,
                    presentation = islandReveal, coverPosition = clock, motion = motion,
                    content = lyricBody)
                else if (!island) Box(Modifier.widthIn(max = 320.dp).fillMaxWidth().graphicsLayer { this.alpha = alpha }.then(material)) { lyricBody() }
            }
            Text("样式、字号与透明度在这里预览，无需先开启悬浮窗。", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
        }
    }
}
