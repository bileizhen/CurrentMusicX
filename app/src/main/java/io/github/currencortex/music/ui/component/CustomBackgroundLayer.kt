package io.github.currencortex.music.ui.component

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

internal val LocalRootBackgroundActive = compositionLocalOf { false }
internal val LocalRootBackgroundBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Shared custom background renderer for both the player backdrop and root-tab backgrounds.
 * Scales smoothly with blur radius on API 31+ to keep RenderEffect transparent edges off-screen,
 * and falls back to bounded software box blur on API 26-30.
 */
@Composable
internal fun CustomImageBackground(
    file: File,
    blurPercent: Float,
    modifier: Modifier = Modifier,
    defaultBlur: Float = 35f,
    maxDecodeWidth: Int = 1080,
    maxDecodeHeight: Int = 1920,
) {
    val context = LocalContext.current
    val blur = if (blurPercent.isFinite()) blurPercent.coerceIn(0f, 100f) else defaultBlur
    val scale = 1f + .3f * blur / 100f
    val request = remember(file, maxDecodeWidth, maxDecodeHeight) {
        ImageRequest.Builder(context)
            .data(file)
            .size(maxDecodeWidth, maxDecodeHeight)
            .build()
    }
    if (Build.VERSION.SDK_INT >= 31) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(if (blur > 0f) Modifier.blur((blur * .8f).dp) else Modifier),
        )
    } else {
        val bitmap by produceState<ImageBitmap?>(null, file, blur.roundToInt(), maxDecodeWidth, maxDecodeHeight) {
            delay(90)
            val fallbackWidth = maxDecodeWidth.coerceAtMost(720)
            val fallbackHeight = maxDecodeHeight.coerceAtMost(1280)
            val result = SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context)
                    .data(file)
                    .size(fallbackWidth, fallbackHeight)
                    .allowHardware(false)
                    .build(),
            )
            if (result is SuccessResult) {
                value = withContext(Dispatchers.Default) {
                    val input = result.image.toBitmap()
                    val pixels = IntArray(input.width * input.height)
                    input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
                    Bitmap.createBitmap(
                        blurBackgroundPixels(pixels, input.width, input.height, (blur * .48f).roundToInt()),
                        input.width,
                        input.height,
                        Bitmap.Config.ARGB_8888,
                    ).asImageBitmap()
                }
            }
        }
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            )
        }
    }
}

/** Root-tab wallpaper layer with a balanced theme contrast veil so cards and titles remain legible. */
@Composable
internal fun RootPageBackground(
    file: File,
    blurPercent: Float,
    dark: Boolean,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clipToBounds()
            .background(surfaceColor),
    ) {
        CustomImageBackground(
            file = file,
            blurPercent = blurPercent,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(surfaceColor.copy(alpha = if (dark) .46f else .38f)),
        )
    }
}

/** Separable box passes for Android 8-11, where Compose RenderEffect blur is unavailable. */
internal fun blurBackgroundPixels(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    require(width > 0 && height > 0 && pixels.size == width * height)
    if (radius <= 0) return pixels.copyOf()
    val r = radius.coerceAtMost(48)
    val window = 2 * r + 1
    var source = pixels.copyOf()
    var target = IntArray(pixels.size)
    repeat(3) {
        repeat(2) { axis ->
            val length = if (axis == 0) width else height
            val lines = if (axis == 0) height else width
            val stride = if (axis == 0) 1 else width
            for (line in 0 until lines) {
                val start = if (axis == 0) line * width else line
                var a = 0
                var red = 0
                var green = 0
                var blue = 0
                fun add(index: Int, sign: Int) {
                    val color = source[start + index.coerceIn(0, length - 1) * stride]
                    a += (color ushr 24) * sign
                    red += (color ushr 16 and 255) * sign
                    green += (color ushr 8 and 255) * sign
                    blue += (color and 255) * sign
                }
                for (index in -r..r) add(index, 1)
                for (index in 0 until length) {
                    target[start + index * stride] = ((a / window) shl 24) or ((red / window) shl 16) or
                        ((green / window) shl 8) or (blue / window)
                    add(index - r, -1)
                    add(index + r + 1, 1)
                }
            }
            val previous = source
            source = target
            target = previous
        }
    }
    return source
}
