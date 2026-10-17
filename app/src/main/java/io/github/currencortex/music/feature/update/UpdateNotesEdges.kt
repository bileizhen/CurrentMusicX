package io.github.currencortex.music.feature.update

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal data class UpdateNotesEdgeState(val top: Boolean, val bottom: Boolean, val blurred: Boolean)
internal val UpdateNotesEdgesKey = SemanticsPropertyKey<UpdateNotesEdgeState>("UpdateNotesEdges")

/** Wrap the scroll viewport, not its full content or the fixed installation controls. */
@Composable
internal fun Modifier.updateNotesEdges(scroll: ScrollState): Modifier = then(
    if (Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated) notesBlurEdges(scroll)
    else notesFadeEdges(scroll)
)

private fun Modifier.edgeSemantics(scroll: ScrollState, blurred: Boolean) = semantics {
    this[UpdateNotesEdgesKey] = UpdateNotesEdgeState(scroll.canScrollBackward, scroll.canScrollForward, blurred)
}

@RequiresApi(33)
@Composable
private fun notesBlurEdges(scroll: ScrollState): Modifier {
    val masks = remember { runCatching { List(2) { RuntimeShader(NOTES_EDGE_SHADER) } }.getOrNull() }
        ?: return notesFadeEdges(scroll)
    return Modifier.graphicsLayer {
        masks.forEachIndexed { index, mask ->
            mask.setFloatUniform("size", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
            mask.setFloatUniform("edge", 24.dp.toPx().coerceAtMost(size.height / 4).coerceAtLeast(1f))
            mask.setFloatUniform("top", if (scroll.canScrollBackward) 1f else 0f)
            mask.setFloatUniform("bottom", if (scroll.canScrollForward) 1f else 0f)
            mask.setFloatUniform("blurred", index.toFloat())
        }
        val sharp = RenderEffect.createRuntimeShaderEffect(masks[0], "content")
        val radius = 2.5.dp.toPx()
        val gaussian = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
        val soft = RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(masks[1], "content"), gaussian)
        renderEffect = RenderEffect.createBlendModeEffect(sharp, soft, android.graphics.BlendMode.PLUS)
            .asComposeRenderEffect()
        clip = true
    }.edgeSemantics(scroll, true)
}

private fun notesFadeEdges(scroll: ScrollState): Modifier = Modifier
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; clip = true }
    .drawWithCache {
        val edge = (24.dp.toPx() / size.height.coerceAtLeast(1f)).coerceIn(0f, .25f)
        val mask = Brush.verticalGradient(0f to if (scroll.canScrollBackward) Color.Transparent else Color.White,
            edge to Color.White, (1f - edge) to Color.White,
            1f to if (scroll.canScrollForward) Color.Transparent else Color.White)
        onDrawWithContent { drawContent(); drawRect(mask, blendMode = BlendMode.DstIn) }
    }.edgeSemantics(scroll, false)

// Crossfade sharp content into the platform Gaussian blur at hidden-content edges.
// Masks sample once: displaced texture taps create visible duplicate glyph strokes.
private const val NOTES_EDGE_SHADER = """
uniform shader content;
uniform float2 size;
uniform float edge;
uniform float top;
uniform float bottom;
uniform float blurred;
half4 main(float2 p) {
    float d = edge + 1.0;
    if (top > 0.5) d = min(d, p.y);
    if (bottom > 0.5) d = min(d, size.y - p.y);
    float amount = 1.0 - smoothstep(0.0, edge, d);
    float blend = amount * amount;
    float mask = mix(1.0 - blend, blend, blurred) * smoothstep(0.0, edge * 0.4, d);
    return content.eval(p) * mask;
}
"""
