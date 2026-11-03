package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.settings.AdaptiveAudioSettings
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun gainLabel(value: Float) = String.format(Locale.ROOT, "%+.1f dB", value)

/** Ten lightweight faders share the width. The full-width fine slider offers a larger touch
 * target; narrow windows / larger system fonts use two rows without horizontal scrolling. */
@Composable internal fun AdaptiveEqBands(gains: List<Float>, selected: Int, enabled: Boolean,
    onSelect: (Int) -> Unit, onPreview: (Int, Float) -> Unit, onSave: (Int, Float) -> Unit, onCancel: () -> Unit,
    liveGains: List<Float>? = null) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp).testTag("adaptive_bands")) {
        val count = if (maxWidth / 10 < 27.dp * fontScale) 5 else 10
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AdaptiveAudioSettings.FREQUENCIES.indices.chunked(count).forEach { indices ->
                Row(Modifier.fillMaxWidth()) {
                    indices.forEach { index ->
                        EqFader(index, gains[index], liveGains?.getOrNull(index), selected == index, enabled, Modifier.weight(1f),
                            onSelect = { onSelect(index) }, onPreview = { onPreview(index, it) },
                            onSave = { onSave(index, it) }, onCancel = onCancel)
                    }
                }
            }
        }
    }
}

private data class EqGainDrag(val manual: Float, val displayed: Float, val limit: Float) {
    fun manualAt(target: Float) = (manual + target - displayed).coerceIn(-6f, 6f)
    fun displayAt(target: Float) = displayed + target - manual
}

@Composable private fun EqFader(index: Int, value: Float, liveValue: Float?, selected: Boolean, enabled: Boolean, modifier: Modifier,
    onSelect: () -> Unit, onPreview: (Float) -> Unit, onSave: (Float) -> Unit, onCancel: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val ink = if (enabled) colors.primary else colors.onSurfaceVariantSummary.copy(alpha = .35f)
    val frequency = AdaptiveAudioSettings.FREQUENCIES[index]
    var preview by remember(value) { mutableFloatStateOf(value) }
    var drag by remember { mutableStateOf<EqGainDrag?>(null) }
    // Numeric label, semantic progress and knob share one value. Never write measured DSP
    // gains back as manual gains: that would repeatedly add the dynamic correction.
    val limit = drag?.limit ?: if (liveValue != null) 9f else 6f
    val displayed = ((drag?.displayAt(preview) ?: liveValue ?: preview).coerceIn(-limit, limit) * 10).roundToInt() / 10f
    val current by rememberUpdatedState(value)
    val currentDisplay by rememberUpdatedState(displayed)
    val currentLimit by rememberUpdatedState(limit)
    val select by rememberUpdatedState(onSelect)
    val change by rememberUpdatedState(onPreview)
    val save by rememberUpdatedState(onSave)
    val cancel by rememberUpdatedState(onCancel)
    fun resolve(v: Float) = (v.coerceIn(-6f, 6f) * 10).roundToInt() / 10f
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(String.format(Locale.ROOT, "%+.1f", displayed), Modifier.testTag("adaptive_band_value_$index"), fontSize = 10.sp, maxLines = 1,
            color = if (selected && enabled) ink else colors.onSurfaceVariantSummary)
        Canvas(Modifier.fillMaxWidth().height(148.dp).testTag("adaptive_band_$index")
            .semantics {
                contentDescription = "$frequency Hz"
                stateDescription = gainLabel(displayed)
                this.selected = selected
                progressBarRangeInfo = ProgressBarRangeInfo(displayed, -limit..limit)
                if (!enabled) disabled()
                setProgress { target ->
                    if (!enabled || !target.isFinite()) false else {
                        select(); preview = resolve(current + target.coerceIn(-limit, limit) - currentDisplay)
                        change(preview); save(preview); true
                    }
                }
            }.onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) false else {
                    val target = when (event.key) {
                        Key.DirectionUp, Key.DirectionRight -> current + .1f
                        Key.DirectionDown, Key.DirectionLeft -> current - .1f
                        Key.MoveHome -> -6f
                        Key.MoveEnd -> 6f
                        else -> null
                    }
                    if (target == null) false else { select(); preview = resolve(target); change(preview); save(preview); true }
                }
            }.focusable(enabled).pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = current
                    val anchor = EqGainDrag(start, currentDisplay, currentLimit)
                    val inset = 12.dp.toPx()
                    val travel = (size.height - 2 * inset).coerceAtLeast(1f)
                    val thumbY = inset + (anchor.limit - anchor.displayed) / (2 * anchor.limit) * travel
                    // Only grabbing the knob edits a band. Swiping an empty rail belongs to
                    // the parent list, so the EQ panel cannot become a scrolling dead zone.
                    if (abs(down.position.y - thumbY) > 18.dp.toPx()) {
                        if (waitForUpOrCancellation() != null) select()
                        return@awaitEachGesture
                    }
                    val grab = down.position.y - thumbY
                    var committed = false
                    drag = anchor
                    select(); down.consume()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (c.isConsumed || event.changes.count { it.pressed } > 1) break
                            val target = (anchor.limit - (c.position.y - grab - inset) / travel * 2 * anchor.limit)
                                .coerceIn(-anchor.limit, anchor.limit)
                            preview = resolve(anchor.manualAt(target))
                            change(preview)
                            c.consume()
                            if (!c.pressed) {
                                if (c.changedToUpIgnoreConsumed()) { committed = true; save(preview) }
                                break
                            }
                        }
                    } finally {
                        if (!committed) { preview = start; cancel() }
                        drag = null
                    }
                }
            }) {
            val top = 12.dp.toPx()
            val bottom = size.height - top
            val x = size.width / 2
            val zero = size.height / 2
            val y = top + (limit - displayed) / (2 * limit) * (bottom - top)
            drawLine(colors.onSurface.copy(alpha = .10f), Offset(x, top), Offset(x, bottom), 5.dp.toPx(), StrokeCap.Round)
            drawLine(colors.onSurface.copy(alpha = .12f), Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
            drawLine(ink.copy(alpha = .5f), Offset(x, zero), Offset(x, y), 5.dp.toPx(), StrokeCap.Round)
            if (selected && enabled) drawCircle(ink.copy(alpha = .12f), 12.dp.toPx(), Offset(x, y))
            drawCircle(ink, 7.dp.toPx(), Offset(x, y))
            drawCircle(colors.onPrimary, 2.5.dp.toPx(), Offset(x, y))
        }
        Text(if (frequency < 1000) "$frequency" else "${frequency / 1000}k", fontSize = 11.sp, maxLines = 1,
            color = if (selected && enabled) ink else colors.onSurfaceVariantSummary)
    }
}

@Composable internal fun AdaptiveEqCurve(gains: List<Float>, live: Boolean) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(if (live) "当前输出曲线 · 含动态调整" else "手动曲线 · 播放后显示动态调整", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
        Canvas(Modifier.fillMaxWidth().height(56.dp).padding(vertical = 8.dp).testTag("adaptive_eq_curve")) {
            drawLine(colors.onSurface.copy(alpha = .12f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
            val points = gains.mapIndexed { i, v -> Offset(size.width * i / 9, size.height * (.5f - v.coerceIn(-9f, 9f) / 18)) }
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.zipWithNext { a, b -> cubicTo((a.x + b.x) / 2, a.y, (a.x + b.x) / 2, b.y, b.x, b.y) }
            }
            drawPath(path, colors.primary.copy(alpha = if (live) 1f else .45f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
