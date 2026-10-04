package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontSynthesis
import io.github.currencortex.music.data.settings.LyricsTypography
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.feature.lyrics.model.LyricLine

/** One text layout, with timing mapped to actual glyph boxes, including wrapped words. */
@Composable fun KaraokeText(line: LyricLine, position: State<Long>, animateWords: Boolean, modifier: Modifier = Modifier,
    fontSize: Float = LyricsTypography.DEFAULT_SIZE, fontWeight: FontWeight = FontWeight.Normal) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val size = LyricsTypography.normalize(fontSize)
    // Async resource resolution must invalidate the cached glyph geometry once the font loads.
    val typeface by LocalFontFamilyResolver.current.resolve(LyricsFontFamily, fontWeight = fontWeight, fontSynthesis = FontSynthesis.None)
    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.roundToPx() }
        val style = TextStyle(fontFamily = LyricsFontFamily, fontSynthesis = FontSynthesis.None,
            fontSize = size.sp, lineHeight = (size * 1.3f).sp, fontWeight = fontWeight)
        val layout = remember(line.text, width, density.density, density.fontScale, size, fontWeight, typeface) {
            measurer.measure(AnnotatedString(line.text), style, constraints = Constraints(maxWidth = width))
        }
        val boxes = remember(line.words, layout) { line.words.map { word ->
            (word.startOffset until word.endOffset.coerceAtMost(line.text.length)).map { layout.getBoundingBox(it) }
                .distinct().filter { it.width > 0 }
        } }
        Canvas(Modifier.fillMaxWidth().height(with(density) { layout.size.height.toDp() })
            .semantics {
                text = AnnotatedString(line.text)
                getTextLayoutResult { it.add(layout); true }
            }) {
            val timed = animateWords && line.words.isNotEmpty()
            drawText(layout, color = Color.White.copy(alpha = if (timed) .32f else 1f))
            if (timed) {
                val now = position.value // Draw-only subscription: no per-frame text measurement.
                val sung = Path()
                val glowing = Path()
                line.words.forEachIndexed { index, word ->
                    val progress = ((now - word.startTimeMs).toFloat() / (word.endTimeMs - word.startTimeMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                    var remaining = boxes[index].sumOf { it.width.toDouble() }.toFloat() * progress
                    boxes[index].forEach { box ->
                        val filled = remaining.coerceIn(0f, box.width)
                        if (filled > 0) {
                            val rect = Rect(box.left, box.top, box.left + filled, box.bottom)
                            sung.addRect(rect)
                            if (progress > 0 && progress < 1) glowing.addRect(rect.inflate(3f))
                        }
                        remaining -= box.width
                    }
                }
                clipPath(glowing) { drawText(layout, color = Color.White.copy(alpha = .13f), shadow = Shadow(Color.White.copy(alpha = .18f), blurRadius = 6f)) }
                clipPath(sung) { drawText(layout, color = Color.White) }
            }
        }
    }
}
