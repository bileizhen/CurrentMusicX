package io.github.currencortex.music.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import io.github.currencortex.music.feature.lyrics.ui.*

/** A small timeline preview uses the already loaded lyrics, without another request or seek. */
@Composable internal fun CoverLyricPreview(vm: PlayerViewModel, modifier: Modifier, active: Boolean) {
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val player by vm.state.collectAsStateWithLifecycle()
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val clock = rememberLyricsPosition(if (active) player else player.copy(playing = false))
    val timeline = remember(lyrics.document) { LyricsSynchronizer(lyrics.document) }
    val matches = lyrics.songId == song?.id && player.song?.id == song?.id
    val index by remember(timeline, clock, matches, settings.lyricsOffsetMs) { derivedStateOf {
        if (matches) timeline.findCurrentLine(LyricsSynchronizer.effectivePosition(clock.value, settings.lyricsOffsetMs)) else -1
    } }
    val fontSize = (settings.lyricsFontSize * .52f).coerceIn(12f, 20f)
    AnimatedContent(index, modifier.testTag("player_cover_lyrics"), transitionSpec = {
        (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 }) togetherWith
            (fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 4 })
    }, label = "cover lyric preview") { current ->
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            for (slot in -1..1) {
                val focused = slot == 0
                val text = if (matches && current >= 0) lyrics.document.lines.getOrNull(current + slot)?.text.orEmpty()
                    else if (!focused) "" else when {
                        lyrics.loading || !matches -> "正在加载歌词"
                        lyrics.document.lines.isEmpty() -> "暂无歌词"
                        else -> timeline.interlude(LyricsSynchronizer.effectivePosition(clock.value, settings.lyricsOffsetMs))?.label ?: "音乐正在继续"
                    }
                val weight = lyricsFontWeight(settings.lyricsWeight, focused, settings.lyricsDisplay.fontWeight)
                BasicText(text.ifBlank { " " }, Modifier.fillMaxWidth().height(22.dp)
                    .testTag(if (focused) "player_cover_lyric_current" else "player_cover_lyric_context_$slot"),
                    style = TextStyle(color = Color.White.copy(alpha = if (focused) .95f else .35f),
                        fontFamily = LyricsFontFamily, fontWeight = weight, fontSynthesis = lyricsFontSynthesis(weight),
                        fontSize = fontSize.sp, lineHeight = 22.sp,
                        textAlign = if (settings.lyricsDisplay.centered) TextAlign.Center else TextAlign.Start),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
