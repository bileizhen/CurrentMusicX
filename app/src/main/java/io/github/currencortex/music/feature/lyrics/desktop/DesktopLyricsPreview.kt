package io.github.currencortex.music.feature.lyrics.desktop

import android.hardware.input.InputManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import io.github.currencortex.music.feature.lyrics.data.SongLyricsStore
import io.github.currencortex.music.feature.lyrics.ui.rememberLyricsPosition
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.Color

/** Uses the same island renderer and clock without requiring overlay permission. */
@Composable internal fun DesktopLyricsPreview(container: AppContainer, options: DesktopLyricsSettings) {
    val playback by container.playerController.state.collectAsStateWithLifecycle()
    val content by container.playbackLyrics.state.collectAsStateWithLifecycle()
    val songKey = remember(playback.song) { playback.song?.let(SongLyricsStore::identity) }
    val hasLyrics = content.matches(playback.song) &&
        remember(content.document) { desktopIslandHasLyrics(content.document) }
    var selectedFps by remember(songKey) { mutableStateOf(true) }
    val fpsMode = !hasLyrics || selectedFps
    val toggle by rememberUpdatedState({ if (hasLyrics) selectedFps = !selectedFps })
    val frameRate = rememberIslandFrameRate()
    val clock = rememberLyricsPosition(playback, fpsMode, if (fpsMode) frameRate::onFrame else null)
    val timeline = remember(content.document) { LyricsSynchronizer(content.document) }
    val totalOffset = options.offsetMs - content.songOffsetMs
    val time = remember(clock, totalOffset) { derivedStateOf { LyricsSynchronizer.lyricPosition(clock.value, totalOffset) } }
    val index by remember(timeline, time) { derivedStateOf { desktopIslandLineIndex(content.document, timeline, time.value) } }
    val line = content.document.lines.getOrNull(index).takeIf { content.matches(playback.song) }
    val motion = rememberDesktopMotion()
    val font = options.displayFontSize
    val context = LocalContext.current
    val maximum = remember(context) { if (Build.VERSION.SDK_INT >= 31) context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch else .8f }
    val alpha = desktopWindowAlpha(options.displayOpacity, options.locked, maximum)
    val size = rememberIslandSize(playback.song, if (fpsMode) "000.0 FPS" else line?.text.orEmpty(), font, fpsMode, false,
        line?.translation?.takeIf { options.translation && !fpsMode }, listeningPair = playback.listenTogether != null)
    val reveal = rememberIslandPresentation(songKey, fpsMode || (!content.loading && content.matches(playback.song)), motion).value
    val width = rememberIslandWidth(size.width + (size.headingWidth - size.width) *
        (1f - reveal / .55f).coerceIn(0f, 1f), motion)
    val colors = MiuixTheme.colorScheme
    Card(Modifier.fillMaxWidth().testTag("desktop_preview")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("实时预览", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(if (fpsMode) "实时帧采样" else if (playback.playing) "跟随播放" else "已暂停", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF303646), Color(0xFF161A25)))).padding(12.dp), contentAlignment = Alignment.Center) {
                DesktopIslandFrame(playback.song, Modifier.islandAnimatedWidth(width).fillMaxWidth().padding(vertical = desktopIslandDrawingSpace)
                    .graphicsLayer { this.alpha = alpha }.pointerInput(Unit) { detectTapGestures(onTap = { toggle() }) },
                    metadata = size.metadata, presentation = reveal, coverPosition = clock, motion = motion,
                    durationMs = playback.durationMs.takeIf { it > 0 } ?: playback.song?.durationMs ?: 0L,
                    coverPlaying = playback.showPause,
                    listenTogether = playback.listenTogether,
                    onCoverTap = { if (playback.song != null && playback.canControlPlayback) container.playerController.toggle() }) {
                    DesktopIslandDisplay(fpsMode, motion, lyrics = {
                        if (line != null) {
                            val next = remember(content.document, index) { desktopIslandNextLineIndex(content.document, index) }
                            DesktopIslandCueHost(songKey, index, line, next, content.document.lines.getOrNull(next), options to motion) { cue ->
                                DesktopIslandLyrics(cue, time, options, motion, "desktop_preview_translation")
                            }
                        }
                    }, fps = { DesktopIslandFrameRate(frameRate.rate, font, Color(options.textColor.toInt())) })
                }
            }
            Text("字号、颜色与透明度在这里预览，无需先开启悬浮窗。", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
        }
    }
}
