package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import kotlin.math.roundToInt

/** Fade the entire cached paragraph before reclaiming its space, rather than wiping glyphs. */
@Composable internal fun LyricsAuxiliaryText(text: String, position: State<Long>, visible: Boolean,
    size: Float, weight: FontWeight, align: TextAlign, tag: String, opacity: Float,
    reserveSpace: Boolean = false, animateHeight: Boolean = true) {
    if (text.isBlank()) return
    val inkAlpha = animateFloatAsState(if (visible) 1f else 0f,
        tween(if (visible) 180 else 110, delayMillis = if (visible) 60 else 0), label = "$tag opacity")
    val height = if (!animateHeight) rememberUpdatedState(if (visible) 1f else 0f) else if (reserveSpace) remember { mutableFloatStateOf(1f) } else animateFloatAsState(if (visible) 1f else 0f,
        tween(if (visible) 280 else 220, delayMillis = if (visible) 0 else 70, easing = FastOutSlowInEasing), label = "$tag space")
    val auxiliary = remember(text) { LyricLine(0, Long.MAX_VALUE, text) }
    KaraokeText(auxiliary, position, false, Modifier.fillMaxWidth()
        .layout { measurable, constraints ->
            val child = measurable.measure(constraints.copy(minHeight = 0))
            layout(child.width, if (reserveSpace) child.height else (child.height * height.value).roundToInt()) {
                child.placeRelative(0, 0)
            }
        }.graphicsLayer {
            alpha = inkAlpha.value
            translationY = (1f - inkAlpha.value) * 3.dp.toPx()
        }.padding(top = 4.dp).then(if (visible) Modifier.testTag(tag) else Modifier),
        fontSize = size, fontWeight = weight, reservedFontWeight = weight, textAlign = align,
        color = Color.White.copy(alpha = opacity), lineHeightRatio = 1.2f, exposeText = visible)
}
