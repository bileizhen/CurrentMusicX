package io.github.currencortex.music.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import coil3.Image as CoilImage
import coil3.BitmapImage
import coil3.DrawableImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest

/**
 * Draws an avatar decoration, restoring the alpha that some server builds lose when they
 * re-encode a GIF onto an opaque black canvas. A pixel painted on black carries its own
 * coverage in its brightness, so the brightest channel becomes the alpha matte.
 *
 * The matte is only enabled for images whose corners are opaque black. Correctly
 * transparent decorations keep their authored alpha, including dark outlines and detail;
 * applying the matte to them would fade those pixels because their coverage is not stored
 * in brightness.
 */
@Composable fun DecorationImage(request: ImageRequest, contentDescription: String?, modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit, onSuccess: () -> Unit = {}, onError: (Throwable) -> Unit = {}) {
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsState()
    val image = (state as? AsyncImagePainter.State.Success)?.result?.image
    val blackBacked = remember(request, image) { image?.isOpaqueBlackCanvas() == true }
    LaunchedEffect(state) {
        when (val current = state) {
            is AsyncImagePainter.State.Success -> onSuccess()
            is AsyncImagePainter.State.Error -> onError(current.result.throwable)
            else -> Unit
        }
    }
    Image(painter, contentDescription, modifier = modifier.then(if (blackBacked) Modifier.decorationMatte() else Modifier),
        contentScale = contentScale)
}

/** Runtime shaders need Android 13; older releases draw the source image unchanged. */
@Composable private fun Modifier.decorationMatte(): Modifier =
    if (Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated) matteLayer() else this

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

/** A flattened decoration is fully opaque with a black canvas around the artwork. */
private fun CoilImage.isOpaqueBlackCanvas(): Boolean = runCatching {
    val corners = when (this) {
        is BitmapImage -> {
            val source = bitmap
            val readable = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
            listOf(readable.getPixel(0, 0), readable.getPixel(readable.width - 1, 0),
                readable.getPixel(0, readable.height - 1), readable.getPixel(readable.width - 1, readable.height - 1))
        }
        is DrawableImage -> {
            // Animated GIFs arrive as a drawable; sample the current frame at 16×16.
            val sample = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sample)
            val old = Rect(drawable.bounds)
            drawable.setBounds(0, 0, sample.width, sample.height)
            drawable.draw(canvas)
            drawable.bounds = old
            listOf(sample.getPixel(0, 0), sample.getPixel(15, 0), sample.getPixel(0, 15), sample.getPixel(15, 15))
        }
        else -> emptyList()
    }
    corners.isNotEmpty() && corners.all { pixel ->
        Color.alpha(pixel) == 255 && Color.red(pixel) <= 8 && Color.green(pixel) <= 8 && Color.blue(pixel) <= 8
    }
}.getOrDefault(false)

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