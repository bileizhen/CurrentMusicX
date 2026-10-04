package io.github.currencortex.music.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

@Composable fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit, onToggle: () -> Unit, modifier: Modifier = Modifier,
    onNext: () -> Unit = { vm.player.next(vm.state.value.playing) },
    onPrevious: () -> Unit = { vm.player.previous(vm.state.value.playing) }, onQueue: () -> Unit = onOpen) {
    val state by vm.state.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val song = queue.current
    if (song == null && state.mode == PlayerMode.LOCAL) return
    val enabled = state.canControlPlayback && song != null
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val allowed by rememberUpdatedState(enabled)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    var dragged by remember { mutableFloatStateOf(0f) }
    val subtitle = when (state.mode) {
        PlayerMode.ROOM -> if (state.canControlPlayback) "房间控制" else "跟随房间"
        PlayerMode.CAST -> "正在投屏"
        else -> song?.artists.orEmpty()
    }
    val surface = LocalMusicGlassSurface.current
    surface(modifier.fillMaxWidth().testTag("mini_player")) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            MusicCover(song?.cover.orEmpty(), Modifier.size(42.dp).clip(CircleShape)
                .border(2.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = .85f), CircleShape).clickable(onClick = onOpen))
            Row(Modifier.weight(1f).heightIn(min = 48.dp).padding(horizontal = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .testTag("mini_song_gesture")
                .pointerInput(enabled, threshold) {
                    detectHorizontalDragGestures(onDragStart = { dragged = 0f },
                        onHorizontalDrag = { change, dx -> change.consume(); if (allowed) dragged += dx },
                        onDragCancel = { dragged = 0f }, onDragEnd = {
                            val offset = dragged; dragged = 0f
                            if (allowed && abs(offset) >= threshold) { if (offset < 0f) next() else previous() }
                        })
                }.clickable(onClick = onOpen).semantics {
                    if (enabled) customActions = listOf(CustomAccessibilityAction("上一首") { previous(); true }, CustomAccessibilityAction("下一首") { next(); true })
                }, verticalAlignment = Alignment.CenterVertically) {
                val artistColor = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f)
                Text(buildAnnotatedString {
                    append(song?.name ?: "等待房间点歌")
                    if (subtitle.isNotBlank()) withStyle(SpanStyle(color = artistColor, fontSize = 12.sp, fontWeight = FontWeight.Normal)) {
                        append(" - $subtitle")
                    }
                }, Modifier.fillMaxWidth().graphicsLayer {
                    translationX = dragged.coerceIn(-threshold * 2, threshold * 2) * .15f
                }, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MiniControl(if (state.playing) "暂停" else "播放", onToggle, Modifier.testTag("mini_toggle"), enabled) { ink ->
                val stroke = Stroke(1.6.dp.toPx())
                drawCircle(ink.copy(alpha = .45f), radius = size.minDimension * .45f, style = stroke)
                if (state.playing) {
                    drawRect(ink, Offset(size.width * .34f, size.height * .29f), Size(size.width * .1f, size.height * .42f))
                    drawRect(ink, Offset(size.width * .56f, size.height * .29f), Size(size.width * .1f, size.height * .42f))
                } else drawPath(Path().apply { moveTo(size.width * .4f, size.height * .28f); lineTo(size.width * .7f, size.height * .5f); lineTo(size.width * .4f, size.height * .72f); close() }, ink)
            }
            MiniControl("播放队列", onQueue, Modifier.testTag("mini_queue")) { ink ->
                val line = 1.8.dp.toPx()
                listOf(.25f, .5f, .75f).forEach { y -> drawLine(ink, Offset(size.width * .42f, size.height * y), Offset(size.width * .9f, size.height * y), line) }
                drawPath(Path().apply { moveTo(size.width * .06f, size.height * .15f); lineTo(size.width * .32f, size.height * .3f); lineTo(size.width * .06f, size.height * .45f); close() }, ink)
            }
        }
    }
}

@Composable private fun MiniControl(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.(androidx.compose.ui.graphics.Color) -> Unit) {
    val ink = MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) .9f else .3f)
    Box(modifier.size(48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label; if (!enabled) disabled() }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(25.dp)) { draw(ink) }
    }
}
