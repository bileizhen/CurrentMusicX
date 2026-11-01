package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.recognition.*
import io.github.currencortex.music.ui.component.MusicIcons
import top.yukonga.miuix.kmp.basic.Icon

/** A temporary cue inside the original island frame, with the same cover, progress and natural height. */
@Composable internal fun RecognitionIslandContent(state: RecognitionState, options: DesktopLyricsSettings,
    maximum: Float, onSize: (IntSize) -> Unit, onTop: (Int) -> Unit, onAction: () -> Unit, onClose: () -> Unit) {
    val song = state.songs.firstOrNull() ?: remember { Song(0, "听歌识曲") }
    val title = when (state.phase) {
        RecognitionPhase.RECORDING -> if (state.source == RecognitionAudioSource.SYSTEM) "识别系统音频" else "正在聆听"
        else -> recognitionIslandTitle(state)
    }
    val size = rememberIslandSize(song, title, options.displayFontSize, compact = true, editing = false)
    val width = rememberIslandWidth((size.width + 24f).coerceIn(156f, minOf(320f, maximum)), rememberDesktopMotion())
    val position = remember(state.seconds) { mutableLongStateOf(state.seconds * 1000L) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        DesktopIslandFrame(song, Modifier.islandAnimatedWidth(width).fillMaxWidth().padding(vertical = desktopIslandDrawingSpace)
            .onSizeChanged(onSize).onGloballyPositioned { onTop(kotlin.math.round(it.positionInRoot().y).toInt()) }
            .testTag("recognition_island"), coverPosition = position, durationMs = 15_000,
            leading = if (state.phase == RecognitionPhase.MATCHED && !song.cover.isNullOrBlank()) null else { modifier ->
                Box(modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF203049)), contentAlignment = Alignment.Center) {
                    Icon(if (state.source == RecognitionAudioSource.SYSTEM) MusicIcons.Headphones else MusicIcons.Microphone,
                        null, Modifier.size(16.dp), tint = Color(0xFF79AAFF))
                }
            }) {
            Row(Modifier.heightIn(min = 30.dp).padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(title, Modifier.weight(1f).clickable(role = Role.Button, onClick = onAction)
                    .semantics { contentDescription = "${recognitionIslandTitle(state)}，${recognitionIslandSubtitle(state)}" }
                    .testTag("recognition_island_action"), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Color.White, fontSize = options.displayFontSize.sp, fontWeight = FontWeight.Medium))
                Box(Modifier.width(28.dp).height(30.dp).clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = "关闭听歌识曲" }.testTag("recognition_island_close"), contentAlignment = Alignment.Center) {
                    Icon(MusicIcons.Close, null, Modifier.size(13.dp), tint = Color.White.copy(alpha = .65f))
                }
            }
        }
    }
}
