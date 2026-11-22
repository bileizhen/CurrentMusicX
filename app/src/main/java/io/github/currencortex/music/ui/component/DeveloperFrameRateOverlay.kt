// SPDX-License-Identifier: GPL-3.0-only
// Adapted from the COUI example app's FPSMonitor (github.com/suqi8/coui, Apache-2.0).
package io.github.currencortex.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

/**
 * Developer frame-rate pill: average FPS and the 1% low over a rolling 5-second window,
 * refreshed twice a second. Colors compare against the best average seen in the last 30
 * seconds, so a change of display rate or thermal throttling moves the reference too.
 * The pill can be dragged anywhere inside its bounds.
 */
@Composable
fun DeveloperFrameRateOverlay(modifier: Modifier = Modifier) {
    var stats by remember { mutableStateOf(FpsStats.Empty) }
    var referenceFps by remember { mutableIntStateOf(0) }
    var parentSize by remember { mutableStateOf(IntSize.Zero) }
    var pillSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val avgColor by animateColorAsState(healthColor(stats.avg, referenceFps), label = "fps avg color")
    val lowColor by animateColorAsState(healthColor(stats.low, referenceFps), label = "fps low color")

    fun clamp(raw: Offset): Offset {
        val xRange = ((parentSize.width - pillSize.width) / 2f).coerceAtLeast(0f)
        val yMax = (parentSize.height - pillSize.height).toFloat().coerceAtLeast(0f)
        return Offset(raw.x.coerceIn(-xRange, xRange), raw.y.coerceIn(0f, yMax))
    }

    // Full-window bounds, so the pill can be dragged anywhere; the box itself draws nothing
    // outside the pill and takes no touches of its own.
    Box(modifier.fillMaxSize().statusBarsPadding().onSizeChanged { parentSize = it; offset = clamp(offset) },
        contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .onSizeChanged { pillSize = it; offset = clamp(offset) }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag -> change.consume(); offset = clamp(offset + drag) }
                }
                .background(COUITheme.colorScheme.cardPressed.copy(alpha = .5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val secondary = COUITheme.colorScheme.labelSecondary
            if (stats.avg == 0) {
                Text("--", style = COUITheme.textStyles.bodyM, color = secondary)
            } else {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = secondary)) { append("AVG ") }
                    withStyle(SpanStyle(color = avgColor)) { append(stats.avg.toString()) }
                }, style = COUITheme.textStyles.bodyM)
                Text("·", style = COUITheme.textStyles.bodyM, color = secondary)
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = secondary)) { append("LOW ") }
                    withStyle(SpanStyle(color = lowColor)) { append(stats.low.toString()) }
                }, style = COUITheme.textStyles.bodyM)
            }
        }
    }

    LaunchedEffect(Unit) {
        val samples = ArrayDeque<Long>(WINDOW_FRAME_CAP)
        val averages = ArrayDeque<AverageTick>(REFERENCE_HISTORY_CAP)
        var sumNanos = 0L
        var lastFrameNanos = 0L
        var nextRefreshNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val delta = frameNanos - lastFrameNanos
                    if (delta in 1L..IDLE_NANOS) {
                        samples.addLast(delta)
                        sumNanos += delta
                        while (sumNanos > WINDOW_NANOS && samples.size > MIN_SAMPLES) sumNanos -= samples.removeFirst()
                        while (samples.size > WINDOW_FRAME_CAP) sumNanos -= samples.removeFirst()
                        if (frameNanos >= nextRefreshNanos && samples.size >= MIN_SAMPLES) {
                            val updated = computeStats(samples, sumNanos)
                            stats = updated
                            averages.addLast(AverageTick(frameNanos, updated.avg))
                            while (averages.size > 1 && frameNanos - averages.first().nanos > REFERENCE_NANOS) averages.removeFirst()
                            while (averages.size > REFERENCE_HISTORY_CAP) averages.removeFirst()
                            referenceFps = averages.maxOf { it.avg }
                            nextRefreshNanos = frameNanos + REFRESH_NANOS
                        }
                    }
                }
                lastFrameNanos = frameNanos
            }
        }
    }
}

private data class FpsStats(val avg: Int, val low: Int) {
    companion object { val Empty = FpsStats(0, 0) }
}

private data class AverageTick(val nanos: Long, val avg: Int)

private fun computeStats(samples: ArrayDeque<Long>, sumNanos: Long): FpsStats {
    val count = samples.size
    val averageNanos = sumNanos.toDouble() / count
    val sorted = samples.toLongArray().also { it.sort() }
    val lowCount = (count / 100).coerceAtLeast(1)
    var lowSum = 0L
    for (index in count - lowCount until count) lowSum += sorted[index]
    val lowAverageNanos = lowSum.toDouble() / lowCount
    return FpsStats((SECOND_NANOS / averageNanos).toInt(), (SECOND_NANOS / lowAverageNanos).toInt())
}

/** Health colors are diagnostic, not themed: a green/yellow/red readout reads the same in any theme. */
private fun healthColor(value: Int, reference: Int): Color {
    if (reference == 0 || value == 0) return Color.Gray
    val ratio = (value.toFloat() / reference).coerceIn(0f, 1f)
    return when {
        ratio >= .75f -> lerp(HealthYellow, HealthGreen, (ratio - .75f) / .25f)
        ratio >= .5f -> lerp(HealthRed, HealthYellow, (ratio - .5f) / .25f)
        else -> HealthRed
    }
}

private val HealthGreen = Color(0xFF36D167)
private val HealthYellow = Color(0xFFFFB21D)
private val HealthRed = Color(0xFFFF5B29)

private const val SECOND_NANOS = 1_000_000_000.0
private const val WINDOW_NANOS = 5L * 1_000_000_000L
private const val WINDOW_FRAME_CAP = 1_200
private const val MIN_SAMPLES = 30
private const val REFRESH_NANOS = 500_000_000L
private const val IDLE_NANOS = 500_000_000L
private const val REFERENCE_NANOS = 30L * 1_000_000_000L
private const val REFERENCE_HISTORY_CAP = 120