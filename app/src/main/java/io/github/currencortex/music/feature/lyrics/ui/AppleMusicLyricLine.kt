package io.github.currencortex.music.feature.lyrics.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.settings.LyricsDisplayOptions
import io.github.currencortex.music.data.settings.AppleMusicLyricsOptions
import io.github.currencortex.music.data.settings.LyricsTypography
import io.github.currencortex.music.data.settings.LyricsWeight
import io.github.currencortex.music.feature.lyrics.model.LyricLine

/** Independent Apple-style vocal layers; the parent owns scrolling, blur and seek gestures. */
@Composable internal fun AppleMusicLyricLine(line: LyricLine, index: Int, position: State<Long>,
    modifier: Modifier = Modifier, fontSize: Float, translation: Boolean, romanization: Boolean,
    wordAnimation: Boolean, display: LyricsDisplayOptions, weightMode: LyricsWeight,
    motionEnabled: Boolean, browsing: Boolean, paused: Boolean = false, held: Boolean = false,
    prepareInk: Boolean = false) {
    Column(modifier.fillMaxWidth()) {
        AppleMusicVocal(line, position, fontSize * if (line.isBackground) .7f else 1f,
            translation, romanization, wordAnimation, display, weightMode, motionEnabled, browsing,
            "apple_lyric_vocal_$index", paused = paused, held = held, prepareInk = prepareInk)
        line.backgroundVocals.forEachIndexed { bgIndex, background ->
            AppleMusicVocal(background, position, fontSize * .7f, translation, romanization,
                wordAnimation, display, weightMode, motionEnabled, browsing,
                "apple_lyric_bg_${index}_$bgIndex", Modifier.padding(top = 8.dp),
                paused = paused, background = true, prepareInk = prepareInk)
        }
    }
}

@Composable private fun AppleMusicVocal(line: LyricLine, position: State<Long>, fontSize: Float,
    translation: Boolean, romanization: Boolean, wordAnimation: Boolean, display: LyricsDisplayOptions,
    weightMode: LyricsWeight, motionEnabled: Boolean, browsing: Boolean, tag: String,
    modifier: Modifier = Modifier, paused: Boolean = false,
    background: Boolean = line.isBackground, held: Boolean = false, prepareInk: Boolean = false) {
    val active by remember(line, position, browsing) { derivedStateOf {
        !browsing && position.value >= line.startTimeMs && position.value < line.endTimeMs
    } }
    val size = fontSize.takeIf(Float::isFinite)?.coerceIn(8f, LyricsTypography.MAX_SIZE) ?: LyricsTypography.DEFAULT_SIZE
    val customization = remember(display.appleMusic) { display.appleMusic.normalized() }
    val focused = if (browsing) LocalAppleLyricsBrowseSelected.current else active || held && !background
    val align = if (display.centered) TextAlign.Center else if (line.isDuet) TextAlign.End else TextAlign.Start
    val fontPx = with(LocalDensity.current) { size.sp.toPx() }
    val presence = remember(line, position, paused, browsing, motionEnabled, customization.animationSpeed) { derivedStateOf {
        appleBackgroundPresence(position.value, line.startTimeMs, line.endTimeMs, paused, browsing, motionEnabled,
            customization.animationSpeed)
    } }
    val hasWordPhonetics = romanization && line.words.any { it.romanization.isNotBlank() &&
        it.startOffset >= 0 && it.endOffset > it.startOffset && it.endOffset <= line.text.length &&
        line.text.substring(it.startOffset, it.endOffset) == it.text }
    val hasRuby = romanization && line.ruby.ifEmpty { line.words.flatMap { it.ruby } }.any {
        it.text.isNotBlank() && it.startOffset >= 0 && it.endOffset > it.startOffset && it.endOffset <= line.text.length
    }
    val showTranslation = translation && line.translation.isNotBlank()
    val roman = if (hasWordPhonetics && appleWordReadingsCoverLine(line)) "" else line.romanization
    val showRomanization = romanization && roman.isNotBlank()
    val auxiliaryInset = with(LocalDensity.current) { (size * if (hasWordPhonetics || hasRuby) .8f else .35f).sp.toDp() }
    val family = currentLyricsFontFamily()
    val auxiliaryFace by LocalFontFamilyResolver.current.resolve(family, fontWeight = FontWeight.Normal)
    var leadingInk by remember(line, size, family, customization.lineHeight) { mutableFloatStateOf(Float.NaN) }
    var trailingInk by remember(line, size, family, customization.lineHeight, auxiliaryFace,
        showTranslation, showRomanization) { mutableFloatStateOf(Float.NaN) }
    val outerSpace = with(LocalDensity.current) { auxiliaryInset.toPx() }
    val onAuxiliaryLayout: (TextLayoutResult) -> Unit = { result ->
        trailingInk = appleTextInkEdges(result, auxiliaryFace, fontPx * .5f)?.let { edges ->
            result.size.height - edges.second + outerSpace
        } ?: Float.NaN
    }
    // Vocal motion is isolated to the layer: no clock-driven measurement or row-height changes.
    Column(modifier.fillMaxWidth().graphicsLayer {
        if (background) {
            val amount = presence.value
            alpha = amount
            scaleX = .75f + .25f * amount; scaleY = scaleX
            translationX = (1f - amount) * fontPx * .6f * if (line.isDuet) 1f else -1f
            transformOrigin = TransformOrigin(if (display.centered) .5f else if (line.isDuet) 1f else 0f, .5f)
        } else alpha = 1f
        // Different font sizes have different cap/descender whitespace. Move only the ink
        // layer, using cached real font bounds; selection and row allocation stay unchanged.
        translationY = if ((showTranslation || showRomanization) && leadingInk.isFinite() && trailingInk.isFinite())
            ((trailingInk - leadingInk) * .5f).coerceIn(-outerSpace * .75f, outerSpace * .75f) else 0f
    }) {
        if (line.text.isNotBlank()) AppleMusicVocalInk(line, position, size, align,
            lyricsFontWeight(weightMode, focused, display.fontWeight),
            lyricsFontWeight(LyricsWeight.ALL, true, display.fontWeight), wordAnimation,
            motionEnabled, display.wordLift, display.glow, browsing, focused, background, romanization, "${tag}_phonetics",
            Modifier.fillMaxWidth().testTag(tag).semantics { selected = active }, customization,
            auxiliaryFollows = showTranslation || showRomanization, onLeadingInk = { leadingInk = it },
            prepareInk = prepareInk)
        val auxiliaryStyle = TextStyle(color = Color.White.copy(alpha = .3f), fontSize = (size * .5f).sp,
            lineHeight = (size * .5f * (customization.lineHeight + .1f)).sp, fontFamily = currentLyricsFontFamily(),
            fontWeight = FontWeight.Normal, textAlign = align)
        // Auxiliary rows remain in the measured paragraph, including during vocal hand-off.
        if (showTranslation) BasicText(line.translation,
            Modifier.fillMaxWidth().padding(horizontal = auxiliaryInset).padding(top = 4.dp).testTag("${tag}_translation"), style = auxiliaryStyle,
            onTextLayout = { if (!showRomanization) onAuxiliaryLayout(it) })
        if (showRomanization) BasicText(roman,
            Modifier.fillMaxWidth().padding(horizontal = auxiliaryInset).padding(top = 4.dp).testTag("${tag}_romanization"), style = auxiliaryStyle,
            onTextLayout = onAuxiliaryLayout)
        // Move the main canvas's trailing safety space after the last auxiliary line.
        // This balances the selection without increasing row height or adding a glyph clip.
        // The layout is identical in selected and unselected states.
        if (line.text.isNotBlank() && (showTranslation || showRomanization))
            Spacer(Modifier.height(auxiliaryInset))
    }
}

/** Cached whole-paragraph shaping. Moving a word never measures or reshapes its characters. */
@Composable private fun AppleMusicVocalInk(line: LyricLine, position: State<Long>, fontSize: Float,
    align: TextAlign, weight: FontWeight, reservedWeight: FontWeight, wordAnimation: Boolean,
    motionEnabled: Boolean, wordLift: Boolean, glow: Boolean, browsing: Boolean, active: Boolean, background: Boolean, romanization: Boolean,
    phoneticTag: String,
    modifier: Modifier, customization: AppleMusicLyricsOptions = AppleMusicLyricsOptions(), auxiliaryFollows: Boolean = false,
    onLeadingInk: (Float) -> Unit = {}, prepareInk: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val family = currentLyricsFontFamily()
    val measurer = rememberTextMeasurer()
    val emphasis = animateFloatAsState(if (active) 1f else if (browsing) .45f else 0f,
        if (motionEnabled) tween(appleVisualDuration(if (active) 300 else 450, customization.animationSpeed),
            easing = CubicBezierEasing(0f, 0f, .58f, 1f)) else snap(),
        label = "Apple vocal ink")
    // CURRENT changes glyph advances and sometimes wrapping. Blend the two already
    // shaped paragraphs in draw, keeping that change out of cue-boundary geometry.
    val weightBlend = animateFloatAsState(if (weight == FontWeight.Normal) 0f else 1f,
        if (motionEnabled) tween(appleVisualDuration(if (active) 300 else 450, customization.animationSpeed),
            easing = CubicBezierEasing(0f, 0f, .58f, 1f)) else snap(),
        label = "Apple vocal weight")
    val normalFace by LocalFontFamilyResolver.current.resolve(family, fontWeight = FontWeight.Normal,
        fontSynthesis = lyricsFontSynthesis(FontWeight.Normal))
    val emphasizedFace by LocalFontFamilyResolver.current.resolve(family, fontWeight = reservedWeight,
        fontSynthesis = lyricsFontSynthesis(reservedWeight))
    val words = remember(line) {
        if (line.wordsEstimated) emptyList() else line.words.filter {
            it.startOffset >= 0 && it.endOffset > it.startOffset && it.endOffset <= line.text.length &&
                line.text.substring(it.startOffset, it.endOffset) == it.text
        }
    }
    val ruby = remember(line, romanization) { (if (!romanization) emptyList() else
        line.ruby.ifEmpty { line.words.flatMap { it.ruby }.distinct() }).filter {
        it.text.isNotBlank() && it.startOffset >= 0 && it.endOffset > it.startOffset && it.endOffset <= line.text.length
    }.groupBy { it.startOffset to it.endOffset } }
    val wordPhonetics = remember(line, romanization) { if (!romanization) emptyList() else line.words.filter {
        it.romanization.isNotBlank() && it.startOffset >= 0 && it.endOffset > it.startOffset && it.endOffset <= line.text.length &&
            line.text.substring(it.startOffset, it.endOffset) == it.text
    } }
    val phoneticAnchors = remember(ruby, wordPhonetics, line.wordsEstimated) {
        ruby.map { (range, syllables) -> ApplePhoneticAnchor(range.first, range.second, true,
            syllables.map { ApplePhoneticSyllable(it.text, it.startTimeMs, it.endTimeMs) }) } +
            wordPhonetics.map { word -> ApplePhoneticAnchor(word.startOffset, word.endOffset, false,
                listOf(ApplePhoneticSyllable(word.romanization, word.startTimeMs.takeUnless { line.wordsEstimated },
                    word.endTimeMs.takeUnless { line.wordsEstimated }))) }
    }
    val canLift = remember(line.text) { lyricsCanLift(line.text) }
    val sustained = remember(words, line.roles) { words.map {
        appleSustainedWord(it.text, it.startTimeMs, it.endTimeMs, it.roles + line.roles)
    } }
    BoxWithConstraints(modifier) {
        val fontPx = with(density) { fontSize.sp.toPx() }
        // Space for a 1.12 scale crest, the held-note halo, and optional phonetic annotation.
        val padding = fontPx * if (phoneticAnchors.isEmpty()) .35f else .8f
        val width = (with(density) { maxWidth.toPx() } - padding * 2f).toInt().coerceAtLeast(1)
        val layouts = remember(line.text, width, density.density, density.fontScale, fontSize, family,
            normalFace, emphasizedFace, reservedWeight, align, direction, ruby.isNotEmpty(), wordPhonetics.isNotEmpty(),
            customization.lineHeight) {
            listOf(FontWeight.Normal, reservedWeight).map { face ->
                measurer.measure(AnnotatedString(line.text), TextStyle(fontFamily = family,
                    fontSize = fontSize.sp, lineHeight = (fontSize *
                        (customization.lineHeight + (if (ruby.isEmpty()) 0f else .6f) + (if (wordPhonetics.isEmpty()) 0f else .65f))).sp,
                    fontWeight = face, fontSynthesis = lyricsFontSynthesis(face), textAlign = align),
                    constraints = Constraints(minWidth = width, maxWidth = width))
            }
        }
        val layoutIndex = if (weight == FontWeight.Normal) 0 else 1
        val layout = layouts[layoutIndex]
        val safeRaster = layouts.all { it.size.width.toLong() * it.size.height <= 4_000_000 && it.size.width < 8192 && it.size.height < 8192 }
        // Only the current/next vocals prepare ink. Shaped layouts reserve stable height
        // for the entire reading window, without rasterizing it on the pager's first frame.
        val wordInkCache = remember(layouts, words, sustained, density, direction, safeRaster) {
            mutableStateMapOf<Int, AppleMusicWordInk?>()
        }
        val visibleEmphasis by remember(emphasis) { derivedStateOf { emphasis.value > 0f } }
        // Browsing gives every row .45 emphasis. Its exit must not enqueue the entire
        // window; only a row with an existing atlas may finish preparing while fading.
        val prepare = safeRaster && wordAnimation && words.isNotEmpty() && !browsing &&
            (prepareInk || active || visibleEmphasis && wordInkCache.isNotEmpty())
        LaunchedEffect(wordInkCache, prepare, context) {
            if (!prepare) return@LaunchedEffect
            // Prepare the singing weight first for an upcoming cue. Both weights must be
            // ready: CURRENT changes to normal while the previous cue is still fading.
            val first = if (active || visibleEmphasis) layoutIndex else 1
            for (index in listOf(first, 1 - first)) {
                if (!wordInkCache.containsKey(index)) {
                    // Publication stays on Main. A changed layout owns a different cache,
                    // and cancelling this effect discards its unfinished worker result.
                    wordInkCache[index] = prepareAppleMusicWordInk(context, layouts[index].layoutInput,
                        words, sustained, index)
                }
            }
        }
        val phoneticLayouts = remember(layouts, phoneticAnchors, fontSize, family, normalFace, width, density, direction,
            customization.lineHeight) {
            layouts.map { base ->
                val notes = phoneticAnchors.mapNotNull {
                    ApplePhoneticInk.create(it, base, measurer, fontSize, family, width, density,
                        customization.lineHeight)
                }
                var next = maxOf(base.size.height.toFloat(), notes.filterNot { it.fallback }.maxOfOrNull { it.bottom } ?: 0f) + fontPx * .2f
                notes.filter { it.fallback }.forEach { note ->
                    note.placeFallback(next)
                    next = note.bottom + fontPx * .2f
                }
                notes
            }
        }
        val phoneticInkCache = remember(phoneticLayouts) {
            mutableStateMapOf<ApplePhoneticInk, AppleMusicWordInk?>()
        }
        val preparePhonetics = wordAnimation && !browsing &&
            (prepareInk || active || visibleEmphasis && phoneticInkCache.isNotEmpty())
        LaunchedEffect(phoneticInkCache, preparePhonetics, context) {
            if (!preparePhonetics) return@LaunchedEffect
            val first = if (active || visibleEmphasis) layoutIndex else 1
            for (index in listOf(first, 1 - first)) {
                for (note in phoneticLayouts[index]) {
                    val size = note.layout.size
                    if (note.cues.isEmpty() || size.width.toLong() * size.height > 4_000_000 ||
                        size.width >= 8192 || size.height >= 8192 || phoneticInkCache.containsKey(note)) continue
                    // The worker owns its paragraph and bitmap. Publish only into this
                    // layout's cache; cancellation reclaims any unpublished atlas.
                    phoneticInkCache[note] = prepareAppleMusicWordInk(context, note.layout.layoutInput,
                        note.cues, List(note.cues.size) { false }, index)
                }
            }
        }
        val phoneticInk = phoneticLayouts[layoutIndex]
        val leadingInk = remember(layouts, normalFace, emphasizedFace, fontPx, padding, phoneticLayouts) {
            layouts.mapIndexedNotNull { index, base ->
                val baseTop = appleTextInkEdges(base, if (index == 0) normalFace else emphasizedFace, fontPx)?.first
                val noteTop = phoneticLayouts[index].mapNotNull { note ->
                    val matching = phoneticAnchors.firstOrNull { anchor ->
                        anchor.syllables.joinToString("") { it.text } == note.layout.layoutInput.text.text
                    } ?: return@mapNotNull null
                    val first = base.getLineForOffset(matching.startOffset)
                    val boxes = (matching.startOffset until matching.endOffset).filter { base.getLineForOffset(it) == first }
                        .map { base.getBoundingBox(it) }.filter { it.width > 0f }
                    if (boxes.isEmpty()) return@mapNotNull null
                    val scale = if (note.fallback) 1f else ((boxes.maxOf { it.right } - boxes.minOf { it.left }) /
                        note.layout.size.width.coerceAtLeast(1)).coerceAtMost(1f)
                    appleTextInkEdges(note.layout, normalFace, fontPx * .5f)?.first?.let {
                        note.bottom - note.layout.size.height * scale + it * scale
                    }
                }.minOrNull()
                listOfNotNull(baseTop, noteTop).minOrNull()?.plus(padding)
            }.minOrNull() ?: Float.NaN
        }
        SideEffect { onLeadingInk(leadingInk) }
        // Reserve both cached font weights, including complete wrapped phonetic blocks.
        val contentHeight = maxOf(layouts.maxOf { it.size.height }.toFloat(),
            phoneticLayouts.maxOfOrNull { notes -> notes.maxOfOrNull { it.bottom } ?: 0f } ?: 0f)
        val phoneticBounds = remember(phoneticAnchors) {
            phoneticAnchors.flatMap { it.syllables }.filter { it.start != null && it.end != null && it.end >= it.start }
                .let { cues -> cues.minOfOrNull { it.start!! } to cues.maxOfOrNull { it.end!! } }
        }
        val phoneticNow = remember(position, phoneticBounds) { derivedStateOf {
            val start = phoneticBounds.first; val end = phoneticBounds.second
            if (start == null || end == null) 0L else position.value.coerceIn(if (start == Long.MIN_VALUE) start else start - 1, end)
        } }
        val timed = wordAnimation && words.isNotEmpty()
        val begin = words.minOfOrNull { it.startTimeMs } ?: line.startTimeMs
        val finish = maxOf(line.endTimeMs, words.mapIndexed { i, word ->
            appleMotionEnd(word.startTimeMs, word.endTimeMs, sustained[i], i == words.lastIndex, customization.animationSpeed)
        }.maxOrNull() ?: line.endTimeMs)
        val phase by remember(position, begin, finish, browsing) { derivedStateOf {
            if (browsing || position.value < begin) 0 else if (position.value >= finish) 2 else 1
        } }
        Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(with(density) { (contentHeight + padding * (if (auxiliaryFollows) 1f else 2f)).toDp() })
            .semantics { text = AnnotatedString(line.text); getTextLayoutResult { it.add(layout); true } }) {
            translate(padding, padding) {
                val opacity = emphasis.value
                val blend = weightBlend.value.coerceIn(0f, 1f)
                fun drawWeight(index: Int, amount: Float) {
                    if (amount <= 0f) return
                    val paragraph = layouts[index]
                    val ink = wordInkCache[index]
                    if (!timed || ink == null || browsing) {
                        drawText(paragraph, color = Color.White.copy(alpha = (.2f + .8f * opacity) * amount))
                    } else {
                        val now = when (phase) { 0 -> if (begin == Long.MIN_VALUE) begin else begin - 1; 1 -> position.value; else -> finish }
                        if (opacity < 1f) drawText(paragraph, color = Color.White.copy(alpha = .2f * (1f - opacity) * amount))
                        if (opacity > 0f) ink.draw(this, now, motionEnabled, wordLift, glow, background, canLift, opacity * amount,
                            customization.animationSpeed, customization.glowIntensity)
                    }
                }
                drawWeight(0, 1f - blend)
                drawWeight(1, blend)
            }
        }
        if (phoneticLayouts.any { it.isNotEmpty() }) Canvas(Modifier.matchParentSize().testTag(phoneticTag).semantics {
            text = AnnotatedString(phoneticAnchors.joinToString(" ") { anchor -> anchor.syllables.joinToString("") { it.text } })
            getTextLayoutResult { result -> result.addAll(phoneticInk.map { it.layout }); true }
        }) {
            translate(padding, padding) {
                val now = phoneticNow.value
                val blend = weightBlend.value.coerceIn(0f, 1f)
                if (blend < 1f) phoneticLayouts[0].forEach { it.draw(this, now, wordAnimation && !browsing, 1f - blend, phoneticInkCache[it]) }
                if (blend > 0f) phoneticLayouts[1].forEach { it.draw(this, now, wordAnimation && !browsing, blend, phoneticInkCache[it]) }
            }
        }
        }
    }
}

/** Real glyph bounds, evaluated only with a newly shaped layout, never from a playback frame. */
private fun appleTextInkEdges(layout: TextLayoutResult, resolvedFace: Any, fontPx: Float): Pair<Float, Float>? {
    val face = resolvedFace as? Typeface ?: return null
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = fontPx }
    val bounds = android.graphics.Rect()
    val text = layout.layoutInput.text.text
    var top = Float.POSITIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    for (line in 0 until layout.lineCount) {
        val start = layout.getLineStart(line)
        val end = layout.getLineEnd(line, visibleEnd = true)
        if (end <= start) continue
        paint.getTextBounds(text, start, end, bounds)
        if (bounds.isEmpty) continue
        val baseline = layout.getLineBaseline(line)
        top = minOf(top, baseline + bounds.top)
        bottom = maxOf(bottom, baseline + bounds.bottom)
    }
    return if (top.isFinite() && bottom.isFinite()) top to bottom else null
}
