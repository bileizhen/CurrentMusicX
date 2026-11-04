package io.github.currencortex.music.ui.component

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.collectLatest
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

internal val RootElasticStrainKey = SemanticsPropertyKey<Float>("RootElasticStrain")

/** Follow the existing pager with a spring; preserve velocity when a finger changes direction. */
@Composable
internal fun rememberPageStrain(pager: PagerState, enabled: Boolean): State<Float> {
    val follower = remember(pager, enabled) { AnimationState(pager.currentPage + pager.currentPageOffsetFraction) }
    var animated by remember(enabled) { mutableStateOf(false) }
    LaunchedEffect(pager, enabled) {
        animated = enabled && (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) != 0f
        if (!animated) return@LaunchedEffect
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collectLatest { position ->
            follower.animateTo(position, spring(dampingRatio = .72f, stiffness = 550f, visibilityThreshold = .001f),
                sequentialAnimation = true)
        }
    }
    return remember(pager, follower, animated) {
        derivedStateOf {
            if (!animated) 0f else ((pager.currentPage + pager.currentPageOffsetFraction - follower.value) * .22f)
                .coerceIn(-.075f, .075f)
        }
    }
}

/** One GPU deformation of the viewport, with no page relayout, bitmap capture or per-item animation. */
@Composable
internal fun Modifier.elasticPageSurface(strain: State<Float>): Modifier {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    val surface = if (Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated) {
        shaderPageSurface(strain, direction)
    } else stretchPageSurface(strain)
    return then(surface).semantics { this[RootElasticStrainKey] = strain.value * direction }
}

private fun stretchPageSurface(strain: State<Float>) = Modifier.clipToBounds().graphicsLayer {
    // Older Android versions still get a bounded stretch and spring return.
    scaleX = 1f + abs(strain.value) * .6f
    clip = true
}

@RequiresApi(33)
@Composable
private fun shaderPageSurface(strain: State<Float>, direction: Float): Modifier {
    val shader = remember { runCatching { RuntimeShader(ELASTIC_PAGE_SHADER) }.getOrNull() }
        ?: return stretchPageSurface(strain)
    return Modifier.graphicsLayer {
        clip = true
        val bend = strain.value * direction
        // Stop the offscreen pass once the bend is smaller than half a physical pixel.
        if (abs(bend) * size.width < .5f || size.width <= 0 || size.height <= 0) {
            renderEffect = null
        } else {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("bend", bend * size.width)
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }
    }
}

// The lower content trails the header. A smooth horizontal envelope anchors both screen edges,
// so adjacent pages share a curved seam without exposing blank wedges or moving the fixed dock.
private const val ELASTIC_PAGE_SHADER = """
uniform shader content;
uniform float2 size;
uniform float bend;
half4 main(float2 p) {
    float2 uv = clamp(p / size, 0.0, 1.0);
    float envelope = sin(uv.x * 3.14159265);
    float lag = 0.18 + 0.82 * uv.y * uv.y;
    float2 samplePoint = float2(p.x - bend * envelope * lag, p.y);
    return content.eval(clamp(samplePoint, float2(0.5), size - float2(0.5)));
}
"""
