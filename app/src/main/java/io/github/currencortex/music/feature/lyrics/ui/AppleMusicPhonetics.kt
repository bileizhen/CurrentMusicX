package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.feature.lyrics.model.LyricWord

internal data class ApplePhoneticSyllable(val text: String, val start: Long?, val end: Long?)
internal data class ApplePhoneticAnchor(val startOffset: Int, val endOffset: Int, val above: Boolean,
    val syllables: List<ApplePhoneticSyllable>)

/** One shaped annotation texture. Real source syllables share a batch, without per-syllable jobs. */
internal class ApplePhoneticInk private constructor(val layout: TextLayoutResult,
    private var origin: Offset, private val ink: AppleMusicWordInk?,
    val fallback: Boolean, private val displayScale: Float) {
    val bottom: Float get() = origin.y + layout.size.height * displayScale
    fun placeFallback(top: Float) { if (fallback) origin = origin.copy(y = top) }

    fun draw(scope: DrawScope, now: Long, animate: Boolean, opacity: Float = 1f) = with(scope) {
        translate(origin.x, origin.y) {
            scale(displayScale, displayScale, pivot = Offset.Zero) {
            if (!animate || ink == null) {
                drawText(layout, color = Color.White.copy(alpha = .3f * opacity))
            } else {
                ink.draw(this, now, motion = false, liftEnabled = false, glow = false,
                    background = false, canLift = false, opacity = .3f * opacity)
            }
            }
        }
    }

    companion object {
        fun create(anchor: ApplePhoneticAnchor, base: TextLayoutResult, measurer: TextMeasurer,
            fontSize: Float, family: FontFamily, width: Int, density: Density,
            direction: LayoutDirection, lineHeight: Float = 1.2f): ApplePhoneticInk? {
            val firstLine = base.getLineForOffset(anchor.startOffset)
            val boxes = (anchor.startOffset until anchor.endOffset).filter { base.getLineForOffset(it) == firstLine }
                .map { base.getBoundingBox(it) }.filter { it.width > 0f }
            if (boxes.isEmpty()) return null
            val text = anchor.syllables.joinToString("") { it.text }
            if (text.isBlank()) return null
            val layout = measurer.measure(AnnotatedString(text), TextStyle(fontFamily = family,
                fontSize = (fontSize * .5f).sp, lineHeight = (fontSize * .5f * (lineHeight + .1f)).sp),
                constraints = Constraints(maxWidth = width))
            val fontPx = with(density) { fontSize.sp.toPx() }
            val baseline = base.getLineBaseline(firstLine)
            val left = boxes.minOf { it.left }; val right = boxes.maxOf { it.right }
            val fit = ((right - left) / layout.size.width.coerceAtLeast(1)).coerceAtMost(1f)
            // A multi-line base supplies no per-character phonetic correspondence. Keep the
            // complete annotation in a separate readable block instead of guessing that mapping.
            val fallback = base.getLineForOffset(anchor.endOffset - 1) != firstLine || layout.lineCount > 1 || fit < .75f
            val displayScale = if (fallback) 1f else fit
            val x = if (fallback) when (base.layoutInput.style.textAlign) {
                TextAlign.End -> (width - layout.size.width).toFloat()
                TextAlign.Center -> (width - layout.size.width) * .5f
                else -> 0f
            } else ((left + right - layout.size.width * displayScale) * .5f)
                .coerceIn(0f, (width - layout.size.width * displayScale).coerceAtLeast(0f))
            val y = if (anchor.above) baseline - fontPx * .85f - layout.size.height * displayScale else baseline + fontPx * .16f
            var offset = 0
            val cues = anchor.syllables.mapNotNull { syllable ->
                val from = offset; offset += syllable.text.length
                if (applePhoneticProgress(0, syllable.start, syllable.end) == null) null else
                    LyricWord(syllable.text, syllable.start!!, syllable.end!!, from, offset)
            }
            val ink = AppleMusicWordInk.create(layout, cues, List(cues.size) { false }, measurer, density, direction)
            return ApplePhoneticInk(layout, Offset(x, y), ink, fallback, displayScale)
        }
    }
}
