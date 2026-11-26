package io.github.currencortex.music.ui.component

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

/**
 * Decoration assets are authored on a transparent canvas, but some server builds re-encode
 * them onto an opaque black canvas. Both variants must look identical: a pixel painted on
 * black carries its own coverage in its brightness, so use the brightest channel as the
 * alpha matte and restore the premultiplied color for the alpha that remains. Transparent
 * assets keep their authored alpha (their matte never exceeds it), so this is safe for both.
 * Runtime shaders need Android 13; older releases draw the source image unchanged.
 */
@Composable fun Modifier.decorationMatte(): Modifier = then(
    if (Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated) matteLayer() else this)

@RequiresApi(33)
@Composable private fun Modifier.matteLayer(): Modifier {
    // A bad shader must never take the screen down with it; fall back to the raw image.
    val effect = remember {
        runCatching { RenderEffect.createRuntimeShaderEffect(RuntimeShader(DECORATION_MATTE_SHADER), "content") }.getOrNull()
    } ?: return this
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        renderEffect = effect.asComposeRenderEffect()
    }
}

// AGSL colors are premultiplied. matte = max(rgb) = alpha * max(color), so repremultiplying
// the sampled color by matte / alpha keeps the authored color while dropping the black canvas.
private const val DECORATION_MATTE_SHADER = """
uniform shader content;
half4 main(float2 coord) {
    half4 color = content.eval(coord);
    half matte = max(color.r, max(color.g, color.b));
    half3 rgb = color.a > 0.001 ? color.rgb * (matte / color.a) : half3(0.0);
    return half4(rgb, matte);
}
"""