package io.github.currencortex.music.feature.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.SingletonImageLoader
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt
import io.github.currencortex.music.ui.component.coverRequestUrl

/** A low-resolution cover creates the atmosphere; controls stay on a separate sharp layer. */
@Composable fun PlayerBackdrop(cover: String, modifier: Modifier = Modifier, customImage: File? = null,
    blurPercent: Float = 35f) {
    val context = LocalContext.current
    Box(modifier.clipToBounds().background(Color(0xFF262428))) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xFF635047), Color(0xFF222832)))))
        if (customImage != null) CustomPlayerBackground(customImage, blurPercent, Modifier.matchParentSize())
        else if (cover.isNotBlank() && Build.VERSION.SDK_INT >= 31) AsyncImage(
            model = remember(cover) { ImageRequest.Builder(context).data(coverRequestUrl(cover, 160)).size(160).build() },
            contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }.blur(65.dp))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .33f), Color.Black.copy(alpha = .62f)))))
    }
}

@Composable private fun CustomPlayerBackground(file: File, percent: Float, modifier: Modifier) {
    val context = LocalContext.current
    val blur = if (percent.isFinite()) percent.coerceIn(0f, 100f) else 35f
    val scale = 1f + .3f * blur / 100f
    val request = remember(file) { ImageRequest.Builder(context).data(file).size(1080, 1920).build() }
    if (Build.VERSION.SDK_INT >= 31) {
        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.blur((blur * .8f).dp))
    } else {
        val bitmap by produceState<ImageBitmap?>(null, file, blur.roundToInt()) {
            // Do not decode/blur every slider event on older devices.
            delay(90)
            val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context)
                .data(file).size(720, 1280).allowHardware(false).build())
            if (result is SuccessResult) value = withContext(Dispatchers.Default) {
                val input = result.image.toBitmap()
                val pixels = IntArray(input.width * input.height)
                input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
                android.graphics.Bitmap.createBitmap(blurBackgroundPixels(pixels, input.width, input.height,
                    (blur * .48f).roundToInt()), input.width, input.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
            }
        }
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }) }
    }
}
