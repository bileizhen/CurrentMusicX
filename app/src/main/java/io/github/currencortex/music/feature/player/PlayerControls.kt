package io.github.currencortex.music.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.Text

@Composable internal fun PlayerTransport(vm: PlayerViewModel, onToggle: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val knownDuration = state.durationMs.takeIf { it > 0 } ?: queue.current?.durationMs ?: 0
    val enabled = state.canControlPlayback && queue.current != null
    var drag by remember(queue.current?.id) { mutableStateOf<Float?>(null) }
    val position = drag?.let { (it * knownDuration).toLong() } ?: state.positionMs
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("player_transport")) {
        PlayerSeekBar(drag ?: (state.positionMs.toFloat() / knownDuration.coerceAtLeast(1)).coerceIn(0f, 1f),
            enabled && knownDuration > 0, { drag = it }, {
                vm.player.seek((it * knownDuration).toLong()); drag = null
            }, { drag = null })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatPlayerTime(position), fontSize = 11.sp, color = Color.White.copy(alpha = .48f))
            Text("−" + formatPlayerTime((knownDuration - position).coerceAtLeast(0)), fontSize = 11.sp, color = Color.White.copy(alpha = .48f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            PlayerTransportButton("上一首", { vm.player.previous() }, enabled = enabled, direction = -1)
            PlayerTransportButton(if (state.showPause) "暂停" else "播放", onToggle,
                Modifier.testTag("player_toggle"), enabled, state.showPause)
            PlayerTransportButton("下一首", { vm.player.next() }, enabled = enabled, direction = 1)
        }
        // A fixed status slot keeps the controls still when buffering or permission changes.
        Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
            val message = when {
                state.error != null -> state.error
                !state.canControlPlayback -> "播放由房主或管理员控制"
                state.loading -> "正在加载音源…"
                else -> null
            }
            message?.let { Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = .55f), maxLines = 1) }
        }
    }
}

@Composable internal fun PlayerSeekBar(value: Float, enabled: Boolean, onPreview: (Float) -> Unit,
    onSeek: (Float) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val preview by rememberUpdatedState(onPreview)
    val seek by rememberUpdatedState(onSeek)
    val cancel by rememberUpdatedState(onCancel)
    var dragging by remember { mutableStateOf(false) }
    var target by remember { mutableFloatStateOf(value) }
    Canvas(modifier.fillMaxWidth().height(34.dp).testTag("player_seek").semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
        contentDescription = "播放进度"
        if (enabled) setProgress { seek(it.coerceIn(0f, 1f)); true } else disabled()
    }.pointerInput(enabled) {
        if (enabled) detectTapGestures { seek((it.x / size.width).coerceIn(0f, 1f)) }
    }.pointerInput(enabled) {
        if (enabled) detectHorizontalDragGestures(
            onDragStart = { dragging = true; target = (it.x / size.width).coerceIn(0f, 1f); preview(target) },
            onDragEnd = { dragging = false; seek(target) },
            onDragCancel = { dragging = false; cancel() },
            onHorizontalDrag = { change, _ -> change.consume(); target = (change.position.x / size.width).coerceIn(0f, 1f); preview(target) })
    }) {
        val y = size.height / 2
        val end = size.width * value.coerceIn(0f, 1f)
        val ink = Color.White.copy(alpha = if (enabled) .85f else .28f)
        drawLine(Color.White.copy(alpha = .18f), Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
        drawLine(ink, Offset(0f, y), Offset(end, y), 3.dp.toPx(), StrokeCap.Round)
        if (dragging) drawCircle(ink, 6.dp.toPx(), Offset(end, y))
    }
}

@Composable private fun PlayerTransportButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, playing: Boolean = false, direction: Int = 0) {
    Box(modifier.size(68.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label; if (!enabled) disabled() }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(if (direction == 0) 40.dp else 30.dp)) {
            val ink = Color.White.copy(alpha = if (enabled) 1f else .28f)
            if (playing && direction == 0) {
                drawRoundRect(ink, Offset(size.width * .2f, size.height * .12f), Size(size.width * .2f, size.height * .76f), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                drawRoundRect(ink, Offset(size.width * .6f, size.height * .12f), Size(size.width * .2f, size.height * .76f), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            } else if (direction == 0) {
                drawPath(Path().apply { moveTo(size.width * .24f, size.height * .09f); lineTo(size.width * .87f, size.height * .5f); lineTo(size.width * .24f, size.height * .91f); close() }, ink)
            } else {
                for (part in 0..1) {
                    val x = size.width * (.08f + part * .43f)
                    drawPath(Path().apply {
                        if (direction > 0) { moveTo(x, size.height * .14f); lineTo(x + size.width * .43f, size.height * .5f); lineTo(x, size.height * .86f) }
                        else { moveTo(x + size.width * .43f, size.height * .14f); lineTo(x, size.height * .5f); lineTo(x + size.width * .43f, size.height * .86f) }
                        close()
                    }, ink)
                }
            }
        }
    }
}

internal enum class PlayerIcon { COLLAPSE, MORE, LYRICS, QUEUE, CAST }

@Composable internal fun PlayerIconButton(icon: PlayerIcon, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, selected: Boolean = false) {
    Box(modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label; this.selected = selected }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val ink = Color.White.copy(alpha = if (selected) 1f else .65f)
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
            when (icon) {
                PlayerIcon.COLLAPSE -> drawPath(Path().apply { moveTo(size.width * .18f, size.height * .36f); lineTo(size.width * .5f, size.height * .67f); lineTo(size.width * .82f, size.height * .36f) }, ink, style = stroke)
                PlayerIcon.MORE -> listOf(.2f, .5f, .8f).forEach { drawCircle(ink, 1.6.dp.toPx(), point(it, .5f)) }
                PlayerIcon.QUEUE -> {
                    listOf(.22f, .5f, .78f).forEach { drawCircle(ink, 1.3.dp.toPx(), point(.1f, it)); drawLine(ink, point(.3f, it), point(.9f, it), stroke.width, StrokeCap.Round) }
                }
                PlayerIcon.LYRICS -> {
                    drawRoundRect(ink, point(.08f, .1f), Size(size.width * .84f, size.height * .67f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
                    drawPath(Path().apply { moveTo(size.width * .23f, size.height * .76f); lineTo(size.width * .23f, size.height * .92f); lineTo(size.width * .44f, size.height * .76f) }, ink, style = stroke)
                    drawLine(ink, point(.27f, .33f), point(.73f, .33f), stroke.width, StrokeCap.Round)
                    drawLine(ink, point(.27f, .54f), point(.55f, .54f), stroke.width, StrokeCap.Round)
                }
                PlayerIcon.CAST -> {
                    drawPath(Path().apply { moveTo(size.width * .1f, size.height * .67f); lineTo(size.width * .1f, size.height * .16f); lineTo(size.width * .9f, size.height * .16f); lineTo(size.width * .9f, size.height * .67f) }, ink, style = stroke)
                    drawPath(Path().apply { moveTo(size.width * .5f, size.height * .56f); lineTo(size.width * .22f, size.height * .9f); lineTo(size.width * .78f, size.height * .9f); close() }, ink)
                }
            }
        }
    }
}

internal fun formatPlayerTime(value: Long): String { val seconds = value.coerceAtLeast(0) / 1000; return "%d:%02d".format(seconds / 60, seconds % 60) }
