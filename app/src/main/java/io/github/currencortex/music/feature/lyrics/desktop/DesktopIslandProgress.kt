package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val desktopIslandProgressWidth = 18.dp
internal val desktopIslandCoverGap = 6.dp
internal val desktopIslandProgressLyricGap = 6.dp
// Preserve the full halo canvas while keeping the visible lyric margin at 6 dp.
internal val desktopIslandLyricInset = desktopEffectPadding() - desktopIslandProgressLyricGap

/** One shared playback clock; fill redraws per frame, timestamp reshapes once per second. */
@Composable internal fun DesktopIslandProgress(position: State<Long>?, durationMs: Long, modifier: Modifier = Modifier) {
    val seconds by remember(position, durationMs) { derivedStateOf {
        val elapsed = (position?.value ?: 0L).coerceAtLeast(0)
        (if (durationMs > 0) elapsed.coerceAtMost(durationMs) else elapsed) / 1000
    } }
    val label = remember(seconds) { "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" }
    val measurer = rememberTextMeasurer()
    val text = measurer.measure(label, TextStyle(color = Color.White.copy(.9f), fontSize = 7.5.sp,
        fontFamily = FontFamily.Monospace), softWrap = false, maxLines = 1)
    Canvas(modifier.testTag("desktop_island_progress").semantics {
        contentDescription = "已播放 $label"
        if (durationMs > 0) progressBarRangeInfo = ProgressBarRangeInfo(
            ((position?.value ?: 0L).coerceAtLeast(0).toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat(), 0f..1f)
    }) {
        val stroke = 1.5.dp.toPx()
        val x = 2.dp.toPx()
        val top = stroke / 2
        val bottom = size.height - stroke / 2
        drawLine(Color.White.copy(.2f), Offset(x, top), Offset(x, bottom), stroke, StrokeCap.Round)
        val fraction = if (durationMs > 0)
            ((position?.value ?: 0L).coerceAtLeast(0).toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat() else 0f
        if (fraction > 0f) drawLine(Color.White.copy(.95f), Offset(x, bottom),
            Offset(x, bottom - (bottom - top) * fraction), stroke, StrokeCap.Round)
        val textScale = minOf(1f, size.height / text.size.width.coerceAtLeast(1))
        val textX = 10.dp.toPx()
        withTransform({
            translate(textX, size.height / 2)
            rotate(-90f, Offset.Zero)
            scale(textScale, textScale, Offset.Zero)
        }) {
            drawText(text, topLeft = Offset(-text.size.width / 2f, -text.size.height / 2f))
        }
    }
}
