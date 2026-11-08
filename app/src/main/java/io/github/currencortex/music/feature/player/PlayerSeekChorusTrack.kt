package io.github.currencortex.music.feature.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import io.github.currencortex.music.data.song.ChorusSegment

internal val PlayerSeekChorusGold = Color(0xFFFFD166)

/** Build the edge gradients only when chorus data, track width or density changes. */
internal fun playerSeekChorusTracks(segments: List<ChorusSegment>, durationMs: Long,
    width: Float, edgeWidth: Float): List<PlayerSeekChorusTrack> {
    if (durationMs <= 0 || width <= 0f) return emptyList()
    // Overlapping metadata represents one continuous highlighted region, not stacked gold layers.
    val ranges = mutableListOf<Pair<Float, Float>>()
    segments.forEach { segment ->
        val start = width * (segment.startMs.toDouble() / durationMs).toFloat()
        val end = width * (segment.endMs.toDouble() / durationMs).toFloat()
        if (end <= start) return@forEach
        val previous = ranges.lastOrNull()
        if (previous != null && start <= previous.second) ranges[ranges.lastIndex] = previous.first to maxOf(previous.second, end)
        else ranges += start to end
    }
    return ranges.map { (start, end) ->
        val fade = (edgeWidth / (end - start)).coerceIn(0f, .5f)
        PlayerSeekChorusTrack(start, end, Brush.horizontalGradient(
            0f to PlayerSeekChorusGold.copy(alpha = if (start <= 0f) 1f else 0f),
            fade to PlayerSeekChorusGold,
            (1f - fade) to PlayerSeekChorusGold,
            1f to PlayerSeekChorusGold.copy(alpha = if (end >= width) 1f else 0f),
            startX = start, endX = end,
        ))
    }
}

internal class PlayerSeekChorusTrack(private val start: Float, private val end: Float, private val brush: Brush) {
    fun DrawScope.draw(playhead: Float, stroke: Float, enabled: Boolean) {
        val y = size.height * .5f
        val strength = if (enabled) 1f else .4f
        // Clip the same gradient into played/unplayed pieces so their edge fades never stack.
        if (playhead > start) clipRect(left = start, right = minOf(playhead, end)) {
            drawLine(brush, Offset(start, y), Offset(end, y), stroke, StrokeCap.Butt, alpha = strength)
        }
        if (playhead < end) clipRect(left = maxOf(playhead, start), right = end) {
            drawLine(brush, Offset(start, y), Offset(end, y), stroke, StrokeCap.Butt, alpha = strength * .5f)
        }
    }
}
