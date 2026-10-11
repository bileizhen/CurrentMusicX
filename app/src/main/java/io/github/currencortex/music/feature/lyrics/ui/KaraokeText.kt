package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
import io.github.currencortex.music.feature.lyrics.model.LyricWord
import io.github.currencortex.music.feature.lyrics.domain.LyricsSynchronizer
import androidx.compose.ui.text.style.TextAlign

/** One text layout, with timing mapped to actual glyph boxes, including wrapped words. */
@Composable fun KaraokeText(line: LyricLine, position: State<Long>, animateWords: Boolean, modifier: Modifier = Modifier,
    fontSize: Float = LyricsTypography.DEFAULT_SIZE, fontWeight: FontWeight = FontWeight.Normal, textAlign: TextAlign = TextAlign.Start,
    forceLineAnimation: Boolean = false, reservedFontWeight: FontWeight = fontWeight,
    color: Color = Color.White, lineHeightRatio: Float = 1.2f, exposeText: Boolean = true,
    glow: Boolean = false, wordLift: Boolean = false, glowOpacity: State<Float>? = null,
    baseOpacity: State<Float>? = null) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val size = if (fontSize.isFinite()) fontSize.coerceIn(8f, LyricsTypography.MAX_SIZE) else LyricsTypography.DEFAULT_SIZE
    // Async resource resolution must invalidate the cached glyph geometry once the font loads.
    val synthesis = lyricsFontSynthesis(fontWeight)
    val normalTypeface by LocalFontFamilyResolver.current.resolve(LyricsFontFamily, fontWeight = FontWeight.Normal,
        fontSynthesis = lyricsFontSynthesis(FontWeight.Normal))
    val emphasisTypeface by LocalFontFamilyResolver.current.resolve(LyricsFontFamily, fontWeight = reservedFontWeight,
        fontSynthesis = lyricsFontSynthesis(reservedFontWeight))
    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.roundToPx() }
        val style = TextStyle(fontFamily = LyricsFontFamily, fontSynthesis = synthesis,
            fontSize = size.sp, lineHeight = (size * lineHeightRatio).sp, fontWeight = fontWeight, textAlign = textAlign)
        // Cache both geometries together. Changing the playing line selects a cached layout;
        // it does not measure each visible lyric again or change the reserved row height.
        val layouts = remember(line.text, width, density.density, density.fontScale, size, reservedFontWeight,
            normalTypeface, emphasisTypeface, textAlign, lineHeightRatio) {
            val constraints = Constraints(minWidth = if (textAlign == TextAlign.End || textAlign == TextAlign.Center) width else 0,
                maxWidth = width)
            listOf(FontWeight.Normal, reservedFontWeight).map { weight ->
                measurer.measure(AnnotatedString(line.text), style.copy(fontWeight = weight,
                    fontSynthesis = lyricsFontSynthesis(weight)), constraints = constraints)
            }
        }
        val layout = layouts[if (fontWeight == FontWeight.Normal) 0 else 1]
        // A display-only line sweep in Always mode; real word timing and cached data remain authoritative.
        val words = remember(line, forceLineAnimation) {
            if (line.words.isNotEmpty()) line.words
            else if (forceLineAnimation && line.text.isNotBlank() && line.endTimeMs > line.startTimeMs)
                listOf(LyricWord(line.text, line.startTimeMs, line.endTimeMs, 0, line.text.length))
            else emptyList()
        }
        val geometry = remember(words, layout) { words.mapIndexed { wordIndex, word ->
            val segments = mutableListOf<Rect>()
            val offsets = word.startOffset.coerceAtLeast(0) until word.endOffset.coerceAtMost(line.text.length)
            val boxes = offsets
                .map { layout.getBoundingBox(it) }.distinct().filter { it.width > 0 }
            val inkBoxes = offsets.filterNot { line.text[it].isWhitespace() }.map { layout.getBoundingBox(it) }.toSet()
            boxes.forEach { box ->
                    val previous = segments.lastOrNull()
                    if (previous != null && previous.top == box.top && previous.bottom == box.bottom &&
                        kotlin.math.abs(previous.right - box.left) < .5f) {
                        segments[segments.lastIndex] = Rect(previous.left, box.top, box.right, box.bottom)
                    } else segments += box
                }
            val total = boxes.sumOf { it.width.toDouble() }.toFloat()
            var offset = 0f
            val glyphs = boxes.mapNotNull { box ->
                val from = offset; offset += box.width
                // Spaces retain their measured advance but never lift or cut the halo of
                // a neighboring glyph. Paragraph kerning and word boundaries stay intact.
                if (box !in inkBoxes) return@mapNotNull null
                GlyphGeometry(box, wordIndex, from,
                    word.startTimeMs + ((word.endTimeMs - word.startTimeMs) * from / total).toLong(),
                    word.startTimeMs + ((word.endTimeMs - word.startTimeMs) * offset / total).toLong())
            }
            WordGeometry(segments, total, Path().apply { segments.forEach(::addRect) }, glyphs)
        } }
        val timed = animateWords && words.isNotEmpty()
        // Retain the lift when Current-line karaoke stops animating a completed row.
        val liftTimed = wordLift && line.words.isNotEmpty()
        val glyphs = remember(geometry) { geometry.flatMap { it.glyphs } }
        val liftedInk = remember(glyphs) { Path().apply { glyphs.forEach { addRect(it.box) } } }
        val lifts = remember(glyphs) { FloatArray(glyphs.size) }
        val glowWeights = remember(words, line.roles) { words.map { lyricGlowWeight(it, line.roles) } }
        val wordGlows = remember(words) { FloatArray(words.size) }
        val glowTimed = glow && line.words.isNotEmpty() && glowWeights.any { it > 0f }
        val liftHeight = 2f * density.density
        val raster = remember(layout, density, direction) { LyricsTextRaster(layout, density, direction) }
        val tint = remember(color) { ColorFilter.tint(color) }
        // Prepare once when enabled, never run a blur filter or text rasterization per frame.
        if (liftTimed) raster.ink
        if (glowTimed) glyphs.filter { glowWeights[it.wordIndex] > 0f }.forEach { raster.glyphHalo(it.box) }
        val begin = words.minOfOrNull { it.startTimeMs } ?: 0
        val finish = maxOf(words.maxOfOrNull { it.endTimeMs } ?: 0,
            if (liftTimed) (glyphs.maxOfOrNull { it.start } ?: 0) + LYRIC_GLYPH_RISE_MS else 0)
        // Static future/past lines do not subscribe their Canvas to the per-frame clock.
        val phase by remember(begin, finish, position, timed, glowTimed, liftTimed) { derivedStateOf {
            when {
                !timed && !glowTimed && !liftTimed -> 2
                position.value < begin -> 0
                position.value >= finish -> 2
                else -> 1
            }
        } }
        val sung = remember { Path() }
        val cutouts = remember { Path() }
        Canvas(Modifier.fillMaxWidth().height(with(density) { layouts.maxOf { it.size.height }.toDp() })
            .semantics {
                if (exposeText) {
                    text = AnnotatedString(line.text)
                    getTextLayoutResult { it.add(layout); true }
                }
            }) {
            if (phase == 2 && liftTimed) {
                // Draw the completed paragraph in two cached masks, without reading the
                // frame clock or returning sung glyphs to their original baseline.
                clipPath(liftedInk, ClipOp.Difference) { drawText(layout, color = color) }
                translate(top = -liftHeight) {
                    clipPath(liftedInk) { drawText(layout, color = color) }
                }
                return@Canvas
            }
            val baseAlpha = if (timed && phase < 2) baseOpacity?.value ?: .32f else 1f
            val glowAlpha = glowOpacity?.value ?: 1f
            cutouts.reset()
            val now = if (phase == 1) position.value else 0L
            if (phase == 1 && liftTimed) glyphs.forEachIndexed { index, glyph ->
                lifts[index] = lyricGlyphLift(now, glyph.start) * liftHeight
                if (lifts[index] > .01f) cutouts.addRect(glyph.box)
            }
            // Only the currently sung sustained/emphasized word can glow. Completed
            // words, future words and untimed ordinary lines never receive a halo.
            if (phase == 1 && glowTimed) {
                words.forEachIndexed { index, word ->
                    wordGlows[index] = glowWeights[index] * lyricGlowStrength(now, word.startTimeMs, word.endTimeMs)
                }
                glyphs.forEachIndexed { index, glyph ->
                    val intensity = wordGlows[glyph.wordIndex] * glowAlpha
                    if (intensity <= .001f) return@forEachIndexed
                    val shape = geometry[glyph.wordIndex]
                    val progress = LyricsSynchronizer.wordProgress(words[glyph.wordIndex], now)
                    val filled = (shape.width * progress - glyph.widthOffset).coerceIn(0f, glyph.box.width)
                    if (filled <= 0f) return@forEachIndexed
                    val halo = raster.glyphHalo(glyph.box)
                    val reveal = lyricGlowReveal(filled / glyph.box.width)
                    translate(top = if (liftTimed) -lifts[index] else 0f) {
                        // Highlight ink still follows exact timing below. The cached
                        // OUTER halo fades in as one shape, never through a hard clip
                        // at the moving karaoke boundary or an arbitrary blur margin.
                        drawImage(halo.first, topLeft = halo.second, alpha = .75f * intensity * reveal, colorFilter = tint)
                    }
                }
            }
            clipPath(cutouts, ClipOp.Difference) {
                drawText(layout, color = color.copy(alpha = color.alpha * baseAlpha))
            }
            if (phase == 1) {
                sung.reset()
                words.forEachIndexed { index, word ->
                    val progress = LyricsSynchronizer.wordProgress(word, now)
                    val shape = geometry[index]
                    if (progress >= 1f) {
                        sung.addPath(shape.complete)
                        return@forEachIndexed
                    }
                    if (progress <= 0f) return@forEachIndexed
                    var remaining = shape.width * progress
                    shape.segments.forEach { box ->
                        val filled = remaining.coerceIn(0f, box.width)
                        if (filled > 0) {
                            val rect = Rect(box.left, box.top, box.left + filled, box.bottom)
                            sung.addRect(rect)
                        }
                        remaining -= box.width
                    }
                }
                clipPath(cutouts, ClipOp.Difference) {
                    if (timed) clipPath(sung) { drawText(layout, color = color) }
                }
                if (liftTimed) glyphs.forEachIndexed { index, glyph ->
                    if (lifts[index] <= .01f) return@forEachIndexed
                    val shape = geometry[glyph.wordIndex]
                    val progress = LyricsSynchronizer.wordProgress(words[glyph.wordIndex], now)
                    val filled = (shape.width * progress - glyph.widthOffset).coerceIn(0f, glyph.box.width)
                    translate(top = -lifts[index]) {
                        val box = glyph.box
                        clipRect(box.left, box.top, box.right, box.bottom) {
                            drawImage(raster.ink, alpha = baseAlpha, colorFilter = tint)
                            if (timed && filled > 0f) clipRect(box.left, box.top, box.left + filled, box.bottom) {
                                drawImage(raster.ink, colorFilter = tint)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class WordGeometry(val segments: List<Rect>, val width: Float, val complete: Path,
    val glyphs: List<GlyphGeometry>)
private data class GlyphGeometry(val box: Rect, val wordIndex: Int, val widthOffset: Float, val start: Long, val end: Long)
