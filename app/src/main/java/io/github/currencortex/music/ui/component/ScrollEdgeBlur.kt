package io.github.currencortex.music.ui.component

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal data class ScrollEdgeBlurState(val top: Boolean, val bottom: Boolean, val blurred: Boolean)
internal val ScrollEdgeBlurKey = SemanticsPropertyKey<ScrollEdgeBlurState>("ScrollEdgeBlur")

/** Apply to the viewport before verticalScroll/LazyColumn; leave fixed controls outside. */
@Composable
internal fun Modifier.scrollEdgeBlur(topHidden: Boolean, bottomHidden: Boolean, radial: Boolean = false): Modifier = then(
    if (Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated) blurEdges(topHidden, bottomHidden, radial)
    else fadeEdges(topHidden, bottomHidden)
)

private fun Modifier.edgeSemantics(topHidden: Boolean, bottomHidden: Boolean, blurred: Boolean) = semantics {
    this[ScrollEdgeBlurKey] = ScrollEdgeBlurState(topHidden, bottomHidden, blurred)
}

@RequiresApi(33)
@Composable
private fun blurEdges(topHidden: Boolean, bottomHidden: Boolean, radial: Boolean): Modifier {
    val masks = remember { runCatching { List(2) { RuntimeShader(SCROLL_EDGE_SHADER) } }.getOrNull() }
        ?: return fadeEdges(topHidden, bottomHidden)
    return Modifier.graphicsLayer {
        clip = true
        if (!topHidden && !bottomHidden) {
            renderEffect = null
            return@graphicsLayer
        }
        masks.forEachIndexed { index, mask ->
            mask.setFloatUniform("size", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
            mask.setFloatUniform("edge", 24.dp.toPx().coerceAtMost(size.height / 4).coerceAtLeast(1f))
            mask.setFloatUniform("top", if (topHidden) 1f else 0f)
            mask.setFloatUniform("bottom", if (bottomHidden) 1f else 0f)
            mask.setFloatUniform("blurred", index.toFloat())
            mask.setFloatUniform("radial", if (radial) 1f else 0f)
        }
        val sharp = RenderEffect.createRuntimeShaderEffect(masks[0], "content")
        val radius = 2.5.dp.toPx()
        val gaussian = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
        val soft = RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(masks[1], "content"), gaussian)
        renderEffect = RenderEffect.createBlendModeEffect(sharp, soft, android.graphics.BlendMode.PLUS)
            .asComposeRenderEffect()
    }.edgeSemantics(topHidden, bottomHidden, true)
}

private fun fadeEdges(topHidden: Boolean, bottomHidden: Boolean): Modifier = Modifier
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; clip = true }
    .drawWithCache {
        val edge = (24.dp.toPx() / size.height.coerceAtLeast(1f)).coerceIn(0f, .25f)
        val mask = Brush.verticalGradient(0f to if (topHidden) Color.Transparent else Color.White,
            edge to Color.White, (1f - edge) to Color.White,
            1f to if (bottomHidden) Color.Transparent else Color.White)
        onDrawWithContent { drawContent(); drawRect(mask, blendMode = BlendMode.DstIn) }
    }.edgeSemantics(topHidden, bottomHidden, false)

// Crossfade sharp content into the platform Gaussian blur at hidden-content edges.
// Masks sample once: displaced texture taps create visible duplicate glyph strokes.
private const val SCROLL_EDGE_SHADER = """
uniform shader content;
uniform float2 size;
uniform float edge;
uniform float top;
uniform float bottom;
uniform float blurred;
uniform float radial;
half4 main(float2 p) {
    float d = edge + 1.0;
    if (top > 0.5) d = min(d, p.y);
    if (bottom > 0.5) d = min(d, size.y - p.y);
    float y = d / edge;
    float x = (p.x - size.x * 0.5) / (size.x * 0.9);
    float distance = mix(y, length(float2(x, y)), radial);
    float amount = 1.0 - smoothstep(0.0, 1.0, distance);
    float blend = amount * amount;
    float mask = mix(1.0 - blend, blend, blurred) * smoothstep(0.0, edge * 0.4, d);
    return content.eval(p) * mask;
}
"""
