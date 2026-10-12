package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.layout.layout

/** Replace hard breaks one-for-one so source UTF-16 timing offsets stay intact. */
internal fun islandSingleLine(text: String): String = text.map {
    if (it == '\n' || it == '\r' || it == '\u2028' || it == '\u2029') ' ' else it
}.joinToString("")

/** A display pan, not synthesized word timing. Player time freezes and seeks it directly. */
internal fun islandPan(elapsedMs: Long, overflow: Float, speed: Float): Float {
    if (overflow <= 0 || speed <= 0) return 0f
    val travel = overflow / speed * 1000.0
    val cycle = travel * 2 + 2600.0
    val phase = elapsedMs.coerceAtLeast(0).toDouble() % cycle
    return when {
        phase < 900 -> 0f
        phase < 900 + travel -> ((phase - 900) / travel * overflow).toFloat()
        phase < 2200 + travel -> overflow
        phase < 2200 + travel * 2 -> ((2200 + travel * 2 - phase) / travel * overflow).toFloat()
        else -> 0f
    }
}

/** Cache full shaped width per cue; read the shared clock only in the layer phase. */
@Composable internal fun DesktopIslandMarquee(text: String, style: TextStyle, position: State<Long>,
    startMs: Long, motion: Boolean, padding: Dp = 0.dp, modifier: Modifier = Modifier,
    content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer()
    val resolver = androidx.compose.ui.platform.LocalFontFamilyResolver.current
    val face by resolver.resolve(style.fontFamily, fontWeight = style.fontWeight ?: androidx.compose.ui.text.font.FontWeight.Normal,
        fontSynthesis = style.fontSynthesis ?: androidx.compose.ui.text.font.FontSynthesis.All)
    val natural = remember(text, style, density, direction, face) {
        measurer.measure(AnnotatedString(text), style, softWrap = false, maxLines = 1).size.width
    }
    // Observe available width during measure, not subcomposition. A spring resize
    // no longer recomposes the paragraph or removes/readds its mask/layer nodes.
    val width = natural + with(density) { padding.toPx() * 2 }
    val speed = with(density) { 24.dp.toPx() }
    val viewport = remember { mutableIntStateOf(natural) }
    Box(modifier.fillMaxWidth().layout { measurable, constraints ->
        val child = measurable.measure(constraints)
        viewport.intValue = child.width
        layout(child.width, child.height) { child.placeRelative(0, 0) }
    }.graphicsLayer {
        clip = true
        compositingStrategy = if (width > size.width) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithCache {
        val overflowing = width > size.width
        val edge = minOf(14.dp.toPx(), size.width * .2f) / size.width.coerceAtLeast(1f)
        val fade = Brush.horizontalGradient(0f to Color.Transparent, edge to Color.White,
            (1f - edge) to Color.White, 1f to Color.Transparent)
        onDrawWithContent {
            drawContent()
            if (overflowing) drawRect(fade, blendMode = BlendMode.DstIn)
        }
    }) {
        Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
            .width(with(density) { width.toDp() })
            .graphicsLayer {
                val available = viewport.intValue.toFloat()
                val offset = if (width <= available) (width - available) / 2
                    else if (motion) islandPan(position.value - startMs, width - available, speed) else 0f
                translationX = if (direction == LayoutDirection.Rtl) offset else -offset
            }) { content() }
    }
}
