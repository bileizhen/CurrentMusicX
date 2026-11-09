package io.github.currencortex.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/** Owned by the clicked card, never shared by cards with the same playlist ID. */
class PlaylistContainerMotion internal constructor(val cover: String, val title: String) {
    internal val source = List(2) { PlaylistMotionElement() }
    internal val destination = List(2) { PlaylistMotionElement() }
    internal var progress: State<Float>? by mutableStateOf(null)
    internal var viewport by mutableStateOf<LayoutCoordinates?>(null)
    internal var artworkReady by mutableStateOf(false)
}

internal class PlaylistMotionElement {
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    var layer: GraphicsLayer? = null
    fun bounds(viewport: LayoutCoordinates?): Rect? {
        val node = coordinates ?: return null
        if (!node.isAttached || viewport?.isAttached != true) return null
        return runCatching { viewport.localBoundingBoxOf(node, clipBounds = false) }.getOrNull()
            ?.takeIf { it.width > 0f && it.height > 0f && it.overlaps(Rect(0f, 0f,
                viewport.size.width.toFloat(), viewport.size.height.toFloat())) }
    }
}

internal val LocalPlaylistContainerMotion = compositionLocalOf<PlaylistContainerMotion?> { null }

/** Record real glyphs/artwork, including font fallback, and suppress only matched elements. */
@Composable internal fun playlistMotionElement(motion: PlaylistContainerMotion?, index: Int,
    destination: Boolean = false): Modifier {
    if (motion == null) return Modifier
    val element = (if (destination) motion.destination else motion.source)[index]
    val layer = rememberGraphicsLayer()
    DisposableEffect(element, layer) {
        element.layer = layer
        onDispose { element.coordinates = null; element.layer = null }
    }
    return Modifier.onGloballyPositioned { element.coordinates = it }.drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        val p = motion.progress?.value
        val matched = motion.source[index].bounds(motion.viewport) != null &&
            motion.destination[index].bounds(motion.viewport) != null
        // Transfer ownership at the animation endpoint, not when the destination is
        // disposed on the next composition. Otherwise the overlay can disappear for
        // a frame while this retained card is still recording invisible children.
        if (p == null || !matched || (!destination && p <= 0f) ||
            (destination && p >= 1f && (index != 0 || motion.artworkReady))) drawLayer(layer)
    }
}

/** Quadratic Bezier offset: zero at both ends, with a bounded upward bow midway. */
internal fun playlistArcBounds(start: Rect, end: Rect, progress: Float, maxLift: Float): Rect {
    val p = progress.coerceIn(0f, 1f)
    val lift = min(maxLift, (abs(end.center.x - start.center.x) + abs(end.center.y - start.center.y)) * .18f)
    return androidx.compose.ui.geometry.lerp(start, end, p).translate(Offset(0f, -4f * lift * p * (1f - p)))
}

@Composable internal fun PlaylistMotionOverlay(motion: PlaylistContainerMotion) {
    Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        val p = motion.progress?.value ?: return@Canvas
        if (p <= 0f) return@Canvas
        for (index in 0..1) {
            if (p >= 1f && (index != 0 || motion.artworkReady)) continue
            val source = motion.source[index]
            val target = motion.destination[index]
            val from = source.bounds(motion.viewport) ?: continue
            val to = target.bounds(motion.viewport) ?: continue
            val bounds = playlistArcBounds(from, to, p, 72.dp.toPx())
            val mix = if (index == 0 && !motion.artworkReady) 0f else ((p - .35f) / .55f).coerceIn(0f, 1f)
            // Cover scales with its bounds. Text scales uniformly, so different wrapping
            // and ellipsis crossfade without stretching the glyphs horizontally.
            fun draw(element: PlaylistMotionElement, alpha: Float, textScale: Float) {
                val layer = element.layer ?: return
                if (alpha <= 0f || layer.size.width == 0 || layer.size.height == 0) return
                val sx = if (index == 0) bounds.width / layer.size.width else textScale
                val sy = if (index == 0) bounds.height / layer.size.height else textScale
                drawMotionLayer(layer, bounds.topLeft, sx, sy, alpha)
            }
            draw(source, 1f - mix, 1f + (22f / 15f - 1f) * p)
            draw(target, mix, 15f / 22f + (1f - 15f / 22f) * p)
        }
    }
}

private fun DrawScope.drawMotionLayer(layer: GraphicsLayer, position: Offset,
    sx: Float, sy: Float, alpha: Float) {
    withTransform({ translate(position.x, position.y); scale(sx, sy, Offset.Zero) }) {
        drawContext.canvas.saveLayer(Rect(0f, 0f, layer.size.width.toFloat(), layer.size.height.toFloat()),
            Paint().apply { this.alpha = alpha })
        drawLayer(layer)
        drawContext.canvas.restore()
    }
}
