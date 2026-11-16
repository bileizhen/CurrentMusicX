package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricWord
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Wrapped rows align the main-text baselines and reserve space for their annotations. */
@Composable internal fun ClassicPhoneticText(
    line: LyricLine,
    phonetics: ClassicPhoneticLine,
    fontSize: Float,
    reservedWeight: FontWeight,
    auxiliaryWeight: FontWeight,
    align: TextAlign,
    focused: Boolean,
    forceLineAnimation: Boolean,
    modifier: Modifier = Modifier,
    mainText: @Composable (LyricLine, Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val family = currentLyricsFontFamily()
    val normalFace by LocalFontFamilyResolver.current.resolve(family, fontWeight = FontWeight.Normal,
        fontSynthesis = lyricsFontSynthesis(FontWeight.Normal))
    val boldFace by LocalFontFamilyResolver.current.resolve(family, fontWeight = reservedWeight,
        fontSynthesis = lyricsFontSynthesis(reservedWeight))
    val auxiliaryFace by LocalFontFamilyResolver.current.resolve(family, fontWeight = auxiliaryWeight,
        fontSynthesis = lyricsFontSynthesis(auxiliaryWeight))
    val measurer = rememberTextMeasurer()
    val opacity = animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "classic phonetic opacity")
    val annotationSpace = animateFloatAsState(if (focused) 1f else 0f,
        if (focused) snap() else tween(220, delayMillis = 180, easing = FastOutSlowInEasing),
        label = "classic phonetic space")
    val mainStyle = TextStyle(fontFamily = family, fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.2f).sp, textAlign = TextAlign.Center)
    val annotationStyle = mainStyle.copy(fontSize = (fontSize * .44f).sp,
        lineHeight = (fontSize * .44f * 1.2f).sp, fontWeight = auxiliaryWeight,
        fontSynthesis = lyricsFontSynthesis(auxiliaryWeight), color = Color.White.copy(alpha = .5f))
    val cellLines = remember(line, phonetics) { phonetics.cells.map { cell ->
        line.copy(text = line.text.substring(cell.startOffset, cell.endOffset),
            words = line.words.filter { it.startOffset >= cell.startOffset && it.endOffset <= cell.endOffset }.map {
                it.copy(startOffset = it.startOffset - cell.startOffset, endOffset = it.endOffset - cell.startOffset)
            }, ruby = emptyList(), translation = "", romanization = "", backgroundVocals = emptyList())
    } }
    BoxWithConstraints(modifier.fillMaxWidth().clearAndSetSemantics {
        text = AnnotatedString(buildString {
            append(line.text)
            if (focused) {
                phonetics.cells.map { it.ruby }.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
                    ?.let { append('\n'); append(it.joinToString(" ")) }
                phonetics.cells.map { it.romanization }.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
                    ?.let { append('\n'); append(it.joinToString(" ")) }
            }
        })
    }) {
        val availableWidth = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val widths = remember(phonetics, cellLines, availableWidth, fontSize, reservedWeight, auxiliaryWeight,
            normalFace, boldFace, auxiliaryFace, density.density, density.fontScale, direction) {
            phonetics.cells.mapIndexed { index, cell ->
                fun width(text: String, style: TextStyle): Int = if (text.isEmpty()) 0 else
                    measurer.measure(AnnotatedString(text), style,
                        constraints = Constraints(maxWidth = availableWidth)).size.width
                maxOf(width(cellLines[index].text, mainStyle.copy(fontWeight = FontWeight.Normal,
                    fontSynthesis = lyricsFontSynthesis(FontWeight.Normal))),
                    width(cellLines[index].text, mainStyle.copy(fontWeight = reservedWeight,
                        fontSynthesis = lyricsFontSynthesis(reservedWeight))),
                    width(cell.ruby, annotationStyle), width(cell.romanization, annotationStyle))
                    .coerceIn(1, availableWidth)
            }
        }
        val gap = with(density) { (fontSize * .12f).sp.toPx() }.roundToInt()
        val rowGap = with(density) { (fontSize * .18f).sp.toPx() }.roundToInt()
        val displayLines = remember(cellLines, widths, forceLineAnimation) {
            if (!forceLineAnimation || (!line.wordsEstimated && line.words.isNotEmpty()) || line.endTimeMs <= line.startTimeMs) cellLines else {
                val totalWidth = widths.sumOf { it.toLong() }.coerceAtLeast(1)
                var offset = 0L
                cellLines.mapIndexed { index, cell ->
                    val start = line.startTimeMs + ((line.endTimeMs - line.startTimeMs).toDouble() * offset / totalWidth).toLong()
                    offset += widths[index]
                    val end = line.startTimeMs + ((line.endTimeMs - line.startTimeMs).toDouble() * offset / totalWidth).toLong()
                    cell.copy(words = listOf(LyricWord(cell.text, start, end, 0, cell.text.length)), wordsEstimated = false)
                }
            }
        }
        Layout(content = {
            phonetics.cells.forEachIndexed { index, cell ->
                val width = Modifier.width(with(density) { widths[index].toDp() })
                if (cell.ruby.isBlank()) Spacer(width) else BasicText(cell.ruby,
                    width.graphicsLayer { alpha = opacity.value }.testTag("lyric_ruby"), style = annotationStyle)
                mainText(displayLines[index], width)
                if (cell.romanization.isBlank()) Spacer(width) else BasicText(cell.romanization,
                    width.graphicsLayer { alpha = opacity.value }.testTag("lyric_romanization"), style = annotationStyle)
            }
        }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
            val measured = measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints.fixedWidth(widths[index / 3]))
            }
            val rows = mutableListOf<List<Int>>()
            var row = mutableListOf<Int>()
            val pendingSpaces = mutableListOf<Int>()
            var usedWidth = 0
            widths.forEachIndexed { index, width ->
                val cell = phonetics.cells[index]
                if (cellLines[index].text.isBlank() && cell.ruby.isBlank() && cell.romanization.isBlank()) {
                    pendingSpaces += index
                    return@forEachIndexed
                }
                val additions = pendingSpaces + index
                val nextWidth = usedWidth + additions.sumOf { widths[it] } +
                    gap * (additions.size - if (row.isEmpty()) 1 else 0)
                if (nextWidth > constraints.maxWidth) {
                    if (row.isNotEmpty()) rows += row
                    row = mutableListOf(index)
                    usedWidth = width
                } else {
                    row.addAll(additions)
                    usedWidth = nextWidth
                }
                pendingSpaces.clear()
            }
            if (row.isNotEmpty()) rows += row
            val baselines = widths.indices.map { index ->
                measured[index * 3 + 1][FirstBaseline].takeUnless { it == AlignmentLine.Unspecified } ?: 0
            }
            val rowAscents = rows.map { row -> row.maxOf { baselines[it] } }
            val space = annotationSpace.value
            val rowHeights = rows.mapIndexed { index, row ->
                intArrayOf((row.maxOf { measured[it * 3].height } * space).roundToInt(),
                    rowAscents[index] + row.maxOf { measured[it * 3 + 1].height - baselines[it] },
                    (row.maxOf { measured[it * 3 + 2].height } * space).roundToInt())
            }
            val annotationGap = ceil(with(density) { (fontSize * .05f).sp.toPx() } * space).toInt()
            val interRowGap = (rowGap * space).roundToInt()
            val height = rowHeights.sumOf { layers -> layers.sum() +
                (if (layers[0] > 0) annotationGap else 0) + (if (layers[2] > 0) annotationGap else 0) } +
                interRowGap * (rows.size - 1).coerceAtLeast(0)
            layout(constraints.maxWidth, constraints.constrainHeight(height)) {
                var y = 0
                rows.forEachIndexed { rowIndex, row ->
                    val layers = rowHeights[rowIndex]
                    val width = row.sumOf { widths[it] } + gap * (row.count() - 1)
                    var x = when (align) {
                        TextAlign.Center -> (constraints.maxWidth - width) / 2
                        TextAlign.End -> constraints.maxWidth - width
                        else -> 0
                    }
                    val mainY = y + layers[0] + if (layers[0] > 0) annotationGap else 0
                    val readingY = mainY + layers[1] + if (layers[2] > 0) annotationGap else 0
                    row.forEach { index ->
                        measured[index * 3].placeRelative(x, y + layers[0] - measured[index * 3].height)
                        measured[index * 3 + 1].placeRelative(x, mainY + rowAscents[rowIndex] - baselines[index])
                        measured[index * 3 + 2].placeRelative(x, readingY)
                        x += widths[index] + gap
                    }
                    y = readingY + layers[2] + interRowGap
                }
            }
        }
    }
}
