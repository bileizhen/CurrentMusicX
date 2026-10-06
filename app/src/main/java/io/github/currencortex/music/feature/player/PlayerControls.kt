package io.github.currencortex.music.feature.player

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import io.github.currencortex.music.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.Text
import io.github.currencortex.music.data.song.formatNeteaseCount

internal data class PlayerButtonVisual(val pressed: Boolean, val scale: Float, val glyphProgress: Float, val alpha: Float)
internal val PlayerButtonVisuals = SemanticsPropertyKey<PlayerButtonVisual>("PlayerButtonVisuals")

/** Keep 48dp touch targets while grouping the secondary actions near the center. */
@Composable internal fun PlayerFunctionBar(modifier: Modifier = Modifier, actionCount: Int = 3, content: @Composable RowScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth().height(56.dp).testTag("player_function_bar")) {
        val gap = if (actionCount <= 3) 24.dp else ((maxWidth - 48.dp * actionCount) / (actionCount - 1)).coerceIn(0.dp, 12.dp)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable internal fun PlayerTransport(vm: PlayerViewModel, onToggle: () -> Unit,
    compact: Boolean = false) {
    val state by vm.state.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val knownDuration = state.durationMs.takeIf { it > 0 } ?: queue.current?.durationMs ?: 0
    val enabled = state.canControlPlayback && queue.current != null
    var drag by remember(queue.current?.id) { mutableStateOf<Float?>(null) }
    val position = drag?.let { (it * knownDuration).toLong() } ?: state.positionMs
    val progress: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            PlayerSeekBar(drag ?: (state.positionMs.toFloat() / knownDuration.coerceAtLeast(1)).coerceIn(0f, 1f),
                enabled && knownDuration > 0, { drag = it }, {
                    vm.player.seek((it * knownDuration).toLong()); drag = null
                }, { drag = null })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlayerTime(position), fontSize = 11.sp, color = Color.White.copy(alpha = .48f))
                Text("−" + formatPlayerTime((knownDuration - position).coerceAtLeast(0)), fontSize = 11.sp, color = Color.White.copy(alpha = .48f))
            }
        }
    }
    val buttons: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = if (compact) 6.dp else 12.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            PlayerTransportButton("上一首", { vm.player.previous() }, enabled = enabled, direction = -1, compact = compact)
            PlayerTransportButton(if (state.showPause) "暂停" else "播放", onToggle,
                Modifier.testTag("player_toggle"), enabled, state.showPause, compact = compact)
            PlayerTransportButton("下一首", { vm.player.next() }, enabled = enabled, direction = 1, compact = compact)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("player_transport")) {
        progress(); buttons()
        // A fixed status slot keeps the controls still when buffering or permission changes.
        Box(Modifier.fillMaxWidth().height(if (compact) 18.dp else 22.dp), contentAlignment = Alignment.Center) {
            val message = when {
                state.error != null -> state.error
                actions.error != null -> actions.error
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

@Composable internal fun PlayerTransportButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, playing: Boolean = false, direction: Int = 0, compact: Boolean = false) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed && enabled) .9f else 1f,
        tween(if (pressed) 90 else 140, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)), label = "transport press")
    val glyph = animateFloatAsState(if (playing) 1f else 0f, tween(180, easing = LinearEasing), label = "play pause glyph")
    Box(modifier.size(if (compact) 48.dp else 68.dp).clickable(enabled = enabled, role = Role.Button, interactionSource = interactions,
        indication = null, onClick = onClick).semantics {
            contentDescription = label; if (!enabled) disabled()
            this[PlayerButtonVisuals] = PlayerButtonVisual(pressed, scale.value, glyph.value, if (enabled) 1f else .28f)
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (compact) { if (direction == 0) 32.dp else 26.dp } else if (direction == 0) 40.dp else 30.dp).graphicsLayer {
            scaleX = scale.value; scaleY = scaleX
            translationX = direction * 3.dp.toPx() * ((1 - scale.value) / .1f)
        }) {
            val ink = Color.White.copy(alpha = if (enabled) 1f else .28f)
            if (direction == 0) {
                Image(painterResource(R.drawable.player_symbol_play_arrow_fill1), null,
                    Modifier.fillMaxSize().graphicsLayer { alpha = 1 - glyph.value }, colorFilter = ColorFilter.tint(ink))
                Image(painterResource(R.drawable.player_symbol_pause_fill1), null,
                    Modifier.fillMaxSize().graphicsLayer { alpha = glyph.value }, colorFilter = ColorFilter.tint(ink))
            } else Image(painterResource(if (direction < 0) R.drawable.player_symbol_skip_previous_fill1 else R.drawable.player_symbol_skip_next_fill1),
                null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(ink))
        }
    }
}

internal enum class PlayerIcon { COLLAPSE, MORE, LYRICS, QUEUE, CAST, LIKE, COMMENT, REPEAT, REPEAT_ONE, SHUFFLE, HEART_MODE }

@Composable internal fun PlayerIconButton(icon: PlayerIcon, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, selected: Boolean = false, count: Long? = null, showCount: Boolean = false,
    enabled: Boolean = true, loading: Boolean = false) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed && enabled) .88f else 1f,
        tween(if (pressed) 90 else 140, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)), label = "player action press")
    val opacity = animateFloatAsState(if (selected) 1f else .65f, tween(160), label = "player action selection")
    val rotation = if (loading) rememberInfiniteTransition(label = "action loading").animateFloat(0f, 360f,
        infiniteRepeatable(tween(900, easing = LinearEasing)), label = "action spinner").value else 0f
    Box(modifier.size(48.dp).clickable(enabled = enabled, role = Role.Button, interactionSource = interactions,
        indication = null, onClick = onClick).semantics {
            contentDescription = label + if (count != null) "，网易云数量 $count" else ""; this.selected = selected
            if (!enabled) disabled()
            this[PlayerButtonVisuals] = PlayerButtonVisual(pressed, scale.value, 0f, opacity.value)
        }, contentAlignment = Alignment.Center) {
        val ink = (if (icon == PlayerIcon.LIKE && selected) Color(0xFFFF647C) else Color.White)
            .copy(alpha = opacity.value * if (enabled) 1f else .4f)
        val iconModifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale.value; scaleY = scaleX }
        if (loading) Canvas(iconModifier) {
            drawArc(ink, rotation, 270f, false, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        } else Image(painterResource(when (icon) {
            PlayerIcon.COLLAPSE -> R.drawable.player_symbol_keyboard_arrow_down
            PlayerIcon.MORE -> R.drawable.player_symbol_more_horiz
            PlayerIcon.LYRICS -> R.drawable.player_symbol_lyrics
            PlayerIcon.QUEUE -> R.drawable.player_symbol_queue_music
            PlayerIcon.CAST -> R.drawable.player_symbol_airplay
            PlayerIcon.LIKE -> if (selected) R.drawable.player_symbol_favorite_fill1 else R.drawable.player_symbol_favorite
            PlayerIcon.COMMENT -> R.drawable.player_symbol_chat_bubble
            PlayerIcon.REPEAT -> R.drawable.player_symbol_repeat
            PlayerIcon.REPEAT_ONE -> R.drawable.player_symbol_repeat_one
            PlayerIcon.SHUFFLE -> R.drawable.player_symbol_shuffle
            PlayerIcon.HEART_MODE -> R.drawable.player_symbol_ecg_heart
        }), null, iconModifier, colorFilter = ColorFilter.tint(ink))
        if (showCount) Text(count?.let(::formatNeteaseCount) ?: "—", fontSize = 9.sp,
            color = Color.White.copy(alpha = .8f), maxLines = 1,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp).testTag("${icon.name.lowercase()}_count"))
    }
}

internal fun formatPlayerTime(value: Long): String { val seconds = value.coerceAtLeast(0) / 1000; return "%d:%02d".format(seconds / 60, seconds % 60) }
