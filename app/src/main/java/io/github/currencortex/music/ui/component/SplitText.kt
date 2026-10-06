package io.github.currencortex.music.ui.component

import android.icu.text.BreakIterator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.coroutines.coroutineContext

/** Keep normal text shaping/semantics; stagger grapheme drawing without relaying out the header. */
@Composable fun SplitText(text: String, ready: Boolean, style: TextStyle, modifier: Modifier = Modifier) {
    var finished by rememberSaveable(text) { mutableStateOf(false) }
    val clock = remember(text) { Animatable(if (finished) 1f else 0f) }
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val clusters = remember(text) {
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
        buildList {
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                add(start until end)
                start = end; end = iterator.next()
            }
        }
    }
    val totalMs = 1300 + (clusters.size - 1).coerceAtLeast(0) * 50
    LaunchedEffect(text, ready) {
        if (ready && !finished) {
            if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) clock.snapTo(1f)
            else clock.animateTo(1f, tween(totalMs, easing = LinearEasing))
            finished = true
        }
    }
    BasicText(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it }, modifier = modifier.drawWithCache {
            val result = layout
            val visibleEnd = result?.getLineEnd(0, visibleEnd = true) ?: 0
            val bands = if (result == null) emptyList() else clusters.mapNotNull { range ->
                if (range.first >= visibleEnd) null else {
                    val boxes = range.takeWhile { it < visibleEnd }.map(result::getBoundingBox)
                    Rect(boxes.minOf { it.left }, 0f, boxes.maxOf { it.right }, size.height)
                }
            }
            val travel = 12.dp.toPx()
            onDrawWithContent {
                if (finished || clock.value >= 1f) drawContent()
                else if (result != null) {
                    val elapsed = clock.value * totalMs
                    bands.forEachIndexed { index, band ->
                        val t = ((elapsed - index * 50) / 1300f).coerceIn(0f, 1f)
                        val eased = 1f - (1f - t) * (1f - t) * (1f - t)
                        if (eased > 0f) clipRect(band.left, 0f, band.right, size.height) {
                            translate(top = travel * (1f - eased)) { drawText(result, alpha = eased) }
                        }
                    }
                    // Preserve the normal ellipsis instead of drawing hidden trailing glyphs.
                    if (result.isLineEllipsized(0) && bands.isNotEmpty()) {
                        val edge = bands.maxOf { it.right }
                        val t = ((elapsed - bands.size * 50) / 1300f).coerceIn(0f, 1f)
                        clipRect(edge, 0f, size.width, size.height) { drawText(result, alpha = t) }
                    }
                }
            }
        })
}
