// Monet sweep/glow adapter: Folia contributors, AGPL-3.0-only; see THIRD_PARTY_NOTICES.md.
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.style.ResolvedTextDirection
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    baseOpacity: State<Float>? = null, accurateWordLift: Boolean = false,
    outline: Boolean = false, onLayout: ((TextLayoutResult) -> Unit)? = null,
    glowColor: Color = color, glowRadiusScale: Float = 1f, untimedLineGlow: Boolean = false,
    effectPadding: Dp = 0.dp, singleLine: Boolean = false, prepareDrawResources: Boolean = false,
    prewarmGlow: Boolean = false, monet: Boolean = false,
    smokeColors: LyricsSmokeColors? = null, smokeFlight: (() -> LyricsSmokeFlight?)? = null,
    particleColors: LyricsSmokeColors? = null, particleExitAt: Long? = null) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val size = if (fontSize.isFinite()) fontSize.coerceIn(8f, LyricsTypography.MAX_SIZE) else LyricsTypography.DEFAULT_SIZE
    // Async resource resolution must invalidate the cached glyph geometry once the font loads.
    val synthesis = lyricsFontSynthesis(fontWeight)
    val normalTypeface by LocalFontFamilyResolver.current.resolve(currentLyricsFontFamily(), fontWeight = FontWeight.Normal,
        fontSynthesis = lyricsFontSynthesis(FontWeight.Normal))
    val emphasisTypeface by LocalFontFamilyResolver.current.resolve(currentLyricsFontFamily(), fontWeight = reservedFontWeight,
        fontSynthesis = lyricsFontSynthesis(reservedFontWeight))
    BoxWithConstraints(modifier) {
        // Reserve the entire blur footprint inside layout/layer/scroll bounds.
        // Keep this margin fixed across strengths so adjusting glow never rewraps text.
        val padding = with(density) { effectPadding.value.takeIf(Float::isFinite)?.coerceIn(0f, 24f)?.dp?.roundToPx() ?: 0 }
        val width = (with(density) { maxWidth.roundToPx() } - padding * 2).coerceAtLeast(1)
        val style = TextStyle(fontFamily = currentLyricsFontFamily(), fontSynthesis = synthesis,
            fontSize = size.sp, lineHeight = (size * lineHeightRatio).sp, fontWeight = fontWeight, textAlign = textAlign)
        // Cache both geometries together. Changing the playing line selects a cached layout;
        // it does not measure each visible lyric again or change the reserved row height.
        val layouts = remember(line.text, width, density.density, density.fontScale, size, reservedFontWeight,
            normalTypeface, emphasisTypeface, textAlign, lineHeightRatio, direction, singleLine) {
            val constraints = Constraints(minWidth = if (textAlign == TextAlign.End || textAlign == TextAlign.Center) width else 0,
                maxWidth = width)
            listOf(FontWeight.Normal, reservedFontWeight).map { weight ->
                measurer.measure(AnnotatedString(line.text), style.copy(fontWeight = weight,
                    fontSynthesis = lyricsFontSynthesis(weight)), constraints = constraints,
                    softWrap = !singleLine, maxLines = if (singleLine) 1 else Int.MAX_VALUE)
            }
        }
        val layoutIndex = if (fontWeight == FontWeight.Normal) 0 else 1
        val layout = layouts[layoutIndex]
        SideEffect { onLayout?.invoke(layout) }
        val clusters = remember(line.text) { lyricsClusters(line.text) }
        // A display-only line sweep in Always mode; real word timing and cached data remain authoritative.
        val words = remember(line, forceLineAnimation, monet) {
            if (line.words.isNotEmpty() && !line.wordsEstimated) {
                if (monet) monetDisplayWords(line) else line.words
            }
            else if (forceLineAnimation && line.text.isNotBlank() && line.endTimeMs > line.startTimeMs)
                listOf(LyricWord(line.text, line.startTimeMs, line.endTimeMs, 0, line.text.length))
            else emptyList()
        }
        val geometries = remember(words, layouts, clusters, accurateWordLift) { layouts.map { layout -> words.mapIndexed { wordIndex, word ->
            val segments = clusters.filter { it.first >= word.startOffset && it.first < word.endOffset }.mapNotNull { range ->
                val boxes = range.map { layout.getBoundingBox(it) }.filter { it.width > 0 }
                if (boxes.isEmpty()) null else {
                    val box = boxes.reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
                    TimedSegment(box, layout.getBidiRunDirection(range.first) == ResolvedTextDirection.Rtl,
                        line.text.substring(range).any { !it.isWhitespace() })
                }
            }
            val total = segments.sumOf { it.box.width.toDouble() }.toFloat().coerceAtLeast(1f)
            var offset = 0f
            val glyphs = segments.mapNotNull { segment ->
                val from = offset; offset += segment.box.width
                if (!segment.ink) return@mapNotNull null
                // Desktop uses the source word cue, not invented per-character times.
                GlyphGeometry(segment.box, wordIndex, from,
                    if (accurateWordLift) word.startTimeMs else word.startTimeMs + ((word.endTimeMs - word.startTimeMs) * from / total).toLong(),
                    segment.rtl)
            }
            WordGeometry(segments, total, Path().apply { segments.forEach { addRect(it.box) } }, glyphs)
        } } }
        val geometry = geometries[layoutIndex]
        val timed = animateWords && words.isNotEmpty()
        // Retain the lift when Current-line karaoke stops animating a completed row.
        val safeRaster = layout.size.width.toLong() * layout.size.height <= 4_000_000 && layout.size.height < 8192 && layout.size.width < 8192
        val liftTimed = wordLift && smokeColors == null && safeRaster && line.words.isNotEmpty() && lyricsCanLift(line.text)
        val glyphs = remember(geometry) { geometry.flatMap { it.glyphs } }
        val liftedInk = remember(glyphs) { Path().apply { glyphs.forEach { addRect(it.box) } } }
        val lifts = remember(glyphs) { FloatArray(glyphs.size) }
        val glowWeights = remember(words, line.roles, monet) { words.map {
            if (monet) { if (it.endTimeMs > it.startTimeMs) 1f else 0f } else lyricGlowWeight(it, line.roles)
        } }
        val wordGlows = remember(words) { FloatArray(words.size) }
        val glowTimed = glow && safeRaster && line.words.isNotEmpty() && glowWeights.any { it > 0f }
        val glowLine = untimedLineGlow && safeRaster && line.words.isEmpty() && !forceLineAnimation
        val liftHeight = 2f * density.density
        val radius = glowRadiusScale.takeIf(Float::isFinite)?.coerceIn(.5f, 2f) ?: 1f
        // Keep both weights' raster caches when focus changes, just like text measurement.
        val rasters = remember(layouts, density, direction, radius, monet) {
            layouts.map { LyricsTextRaster(it, density, direction, radius, monet) }
        }
        val raster = rasters[layoutIndex]
        val untimedInk = remember(liftTimed, raster, glyphs, clusters, words) {
            if (liftTimed && clusters.any { range ->
                line.text.substring(range).any { !it.isWhitespace() } &&
                    words.none { range.first >= it.startOffset && range.first < it.endOffset }
            }) raster.untimedInk(glyphs.map { it.box }) else null
        }
        val smokeGeometry = remember(line, layout, smokeColors != null, safeRaster) {
            if (smokeColors != null && safeRaster && layout.size.width.toLong() * layout.size.height <= 1_000_000)
                smokeGlyphs(line, layout) else emptyList()
        }
        val smokeActive by remember(position, line, smokeGeometry) { derivedStateOf {
            smokeGeometry.isNotEmpty() && position.value >= line.startTimeMs && position.value < line.endTimeMs
        } }
        val smokeInk = if (smokeActive && smokeColors != null) remember(smokeGeometry, raster) {
            LyricsSmokeInk(smokeGeometry, raster.ink)
        } else null
        val tint = remember(color) { ColorFilter.tint(color) }
        val particleGeometry = remember(line, layout, particleColors != null, safeRaster) {
            if (particleColors != null && safeRaster && layout.size.width.toLong() * layout.size.height <= 1_000_000)
                smokeGlyphs(line, layout) else emptyList()
        }
        val particlesActive by remember(position, line, particleGeometry, particleExitAt) { derivedStateOf {
            particleGeometry.isNotEmpty() && (particleExitAt != null ||
                position.value >= line.startTimeMs && position.value < line.endTimeMs)
        } }
        val particleInk = if (particlesActive) remember(particleGeometry, raster) {
            LyricsParticleInk(particleGeometry, raster.ink)
        } else null
        val haloTint = remember(glowColor) { ColorFilter.tint(glowColor) }
        // Prepare once when enabled, never run a blur filter or text rasterization per frame.
        if (liftTimed) {
            if (prepareDrawResources) rasters.forEach { it.prepareForDrawing(it.ink) }
            else raster.ink
        }
        LaunchedEffect(prewarmGlow, safeRaster, geometries, rasters, glowWeights, monet) {
            if (prewarmGlow && safeRaster && line.words.isNotEmpty()) {
                // Warm the next paragraph over separate display frames, before its entrance.
                // Both cached weights are needed when Current-line bold changes at that cue.
                geometries.forEachIndexed { index, shapes ->
                    shapes.filterIndexed { wordIndex, _ -> glowWeights[wordIndex] > 0f }.forEach { shape ->
                        shape.glyphs.forEach { glyph ->
                            withFrameNanos { }
                            val halo = rasters[index].glyphHalo(glyph.box).first
                            if (prepareDrawResources) rasters[index].prepareForDrawing(halo)
                            if (monet) {
                                val wide = rasters[index].wideGlyphHalo(glyph.box).first
                                if (prepareDrawResources) rasters[index].prepareForDrawing(wide)
                            }
                        }
                    }
                }
            }
        }
        if (glowLine) raster.lineHalo.first.let { if (prepareDrawResources) raster.prepareForDrawing(it) }
        if (glowTimed) glyphs.filter { glowWeights[it.wordIndex] > 0f }.forEach {
            val halo = raster.glyphHalo(it.box).first
            if (prepareDrawResources) raster.prepareForDrawing(halo)
            if (monet) raster.wideGlyphHalo(it.box).first.let { wide -> if (prepareDrawResources) raster.prepareForDrawing(wide) }
        }
        val monetSweeps = if (monet && safeRaster) remember(rasters, color) { rasters.map { MonetSweepInk(it.ink, color) } } else emptyList()
        val monetHalo = if (monet && glowTimed) remember(raster, glyphs, glowColor) {
            MonetHaloInk(glyphs.filter { glowWeights[it.wordIndex] > 0f }.flatMap { glyph -> listOf(
                glyph.wordIndex to raster.glyphHalo(glyph.box),
                glyph.wordIndex to raster.wideGlyphHalo(glyph.box)) }, glowColor)
        } else null
        val monetWidths = remember(geometry) { geometry.map { shape -> shape.segments.map { it.box.width } } }
        val begin = words.minOfOrNull { it.startTimeMs } ?: 0
        val finish = maxOf(if (monet && glowTimed) maxOf(line.endTimeMs, (words.maxOfOrNull { it.endTimeMs } ?: 0) + 1050)
            else words.maxOfOrNull { it.endTimeMs } ?: 0,
            if (liftTimed) (glyphs.maxOfOrNull { it.start } ?: 0) + LYRIC_GLYPH_RISE_MS else 0)
        // Static future/past lines do not subscribe their Canvas to the per-frame clock.
        val phase by remember(begin, finish, position, timed, glowTimed, liftTimed, monet) { derivedStateOf {
            when {
                !monet && !timed && !glowTimed && !liftTimed -> 2
                position.value < begin -> 0
                position.value >= finish -> 2
                else -> 1
            }
        } }
        val sung = remember { Path() }
        Canvas(Modifier.fillMaxWidth().height(with(density) { (layouts.maxOf { it.size.height } + padding * 2).toDp() })
            .semantics {
                if (exposeText) {
                    text = AnnotatedString(line.text)
                    getTextLayoutResult { it.add(layout); true }
                }
            }) {
            translate(left = padding.toFloat(), top = padding.toFloat()) {
                if (particleInk != null) {
                    with(particleInk) { draw(position.value, particleColors!!, color, particleExitAt) }
                    return@translate
                }
                if (smokeInk != null) {
                    with(smokeInk) { draw(position.value, smokeColors!!, color, smokeFlight?.invoke()) }
                    return@translate
                }
                if (monet && safeRaster && words.isNotEmpty()) {
                    // Folia's media-time envelope survives line changes, pause and backward seeks.
                    val now = when (phase) { 0 -> begin - 1; 1 -> position.value; else -> Long.MAX_VALUE }
                    val sweep = monetSweeps[layoutIndex]
                    val softness = monetSweepSoftness(size * density.fontScale) * density.density
                    val base = baseOpacity?.value ?: .34f
                    if (monetHalo != null) {
                        words.forEachIndexed { index, word ->
                            wordGlows[index] = if (glowWeights[index] > 0f)
                                monetGlow(now, word.startTimeMs, word.endTimeMs, line.endTimeMs) else 0f
                        }
                        monetHalo.draw(this, wordGlows)
                    }
                    drawImage(raster.ink, alpha = base, colorFilter = tint)
                    sweep.begin()
                    words.forEachIndexed { wordIndex, word ->
                        val shape = geometry[wordIndex]
                        val progress = LyricsSynchronizer.wordProgress(word, now)
                        // Equal-time graphemes interpolate their measured positions, including wrapped words.
                        val filled = monetFillWidth(progress, monetWidths[wordIndex])
                        val edgeEnd = monetSweepEnd(filled, shape.width, softness)
                        if (now >= word.endTimeMs) {
                            shape.segments.forEach { sweep.opaque(it.box) }
                        } else if (timed && now > word.startTimeMs) {
                            var offset = 0f
                            shape.segments.forEach { segment ->
                                sweep.add(segment.box, edgeEnd - offset, softness, segment.rtl)
                                offset += segment.box.width
                            }
                        }
                    }
                    sweep.draw(this)
                    return@translate
                }
                if (glowLine) {
                    val halo = raster.lineHalo
                    drawImage(halo.first, topLeft = halo.second,
                        alpha = (.9f * (glowOpacity?.value ?: 1f)).coerceIn(0f, 1f), colorFilter = haloTint)
                }
                if (outline) drawText(layout, color = (if (color.luminance() < .35f) Color.White else Color.Black).copy(alpha = .85f), drawStyle = Stroke(width = 1.6f * density.density))
                if (phase == 2 && liftTimed) {
                    // Draw the completed paragraph in two cached masks, without reading the
                    // frame clock or returning sung glyphs to their original baseline.
                    clipPath(liftedInk, ClipOp.Difference) { drawImage(raster.ink, colorFilter = tint) }
                    translate(top = -liftHeight) {
                        clipPath(liftedInk) { drawImage(raster.ink, colorFilter = tint) }
                    }
                    return@translate
                }
                val baseAlpha = if (timed && phase < 2) baseOpacity?.value ?: .32f else 1f
                val glowAlpha = glowOpacity?.value ?: 1f
                val now = if (phase == 1) position.value else 0L
                if (phase == 1 && liftTimed) glyphs.forEachIndexed { index, glyph ->
                    lifts[index] = lyricGlyphLift(now, glyph.start) * liftHeight
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
                            drawImage(halo.first, topLeft = halo.second, alpha = (.75f * intensity * reveal).coerceIn(0f, 1f), colorFilter = haloTint)
                        }
                    }
                }
                if (phase == 1 && liftTimed) {
                    // Timed glyphs share the cached paragraph texture. Only simple rectangle
                    // clips move per frame; no whole-line cutout or swept union is rebuilt.
                    untimedInk?.let { drawImage(it, alpha = baseAlpha, colorFilter = tint) }
                    glyphs.forEachIndexed { index, glyph ->
                        val shape = geometry[glyph.wordIndex]
                        val progress = LyricsSynchronizer.wordProgress(words[glyph.wordIndex], now)
                        val filled = (shape.width * progress - glyph.widthOffset).coerceIn(0f, glyph.box.width)
                        translate(top = -lifts[index]) {
                            val box = glyph.box
                            clipRect(box.left, box.top, box.right, box.bottom) {
                                // A completed glyph needs one opaque pass. Painting the dim
                                // ink underneath it both costs a draw and brightens its AA edge.
                                if (timed && (progress >= 1f || filled >= box.width)) {
                                    drawImage(raster.ink, colorFilter = tint)
                                } else {
                                    drawImage(raster.ink, alpha = baseAlpha, colorFilter = tint)
                                    if (timed && filled > 0f) clipRect(if (glyph.rtl) box.right - filled else box.left, box.top,
                                        if (glyph.rtl) box.right else box.left + filled, box.bottom) {
                                        drawImage(raster.ink, colorFilter = tint)
                                    }
                                }
                            }
                        }
                    }
                    return@translate
                }
                drawText(layout, color = color.copy(alpha = color.alpha * baseAlpha), drawStyle = Fill)
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
                        shape.segments.forEach { segment ->
                            val box = segment.box
                            val filled = remaining.coerceIn(0f, box.width)
                            if (filled > 0) {
                                val rect = if (segment.rtl) Rect(box.right - filled, box.top, box.right, box.bottom)
                                    else Rect(box.left, box.top, box.left + filled, box.bottom)
                                sung.addRect(rect)
                            }
                            remaining -= box.width
                        }
                    }
                    if (timed) clipPath(sung) { drawText(layout, color = color, drawStyle = Fill) }

                }
            }
        }
    }
}

private data class TimedSegment(val box: Rect, val rtl: Boolean, val ink: Boolean)
private data class WordGeometry(val segments: List<TimedSegment>, val width: Float, val complete: Path,
    val glyphs: List<GlyphGeometry>)
private data class GlyphGeometry(val box: Rect, val wordIndex: Int, val widthOffset: Float, val start: Long, val rtl: Boolean)
