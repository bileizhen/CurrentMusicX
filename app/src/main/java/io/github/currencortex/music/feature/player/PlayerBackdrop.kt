package io.github.currencortex.music.feature.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
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
import io.github.currencortex.music.ui.component.rememberArtworkGlowPalette
import io.github.currencortex.music.ui.component.coverRequestUrl
import io.github.currencortex.music.ui.component.miuix.effect.BgEffectBackground
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/** Artwork-derived flowing or frosted backgrounds; controls stay on a separate sharp layer. */
@Composable fun PlayerBackdrop(cover: String, modifier: Modifier = Modifier, customImage: File? = null,
    blurPercent: Float = 35f, active: Boolean = true, dynamicBackground: Boolean = true,
    motionSpeed: (() -> Float)? = null) {
    Box(modifier.clipToBounds().background(Color(0xFF262428))) {
        if (customImage != null) CustomPlayerBackground(customImage, blurPercent, Modifier.matchParentSize())
        else if (dynamicBackground) ArtworkPlayerBackground(cover, active, motionSpeed, Modifier.matchParentSize())
        else FrostedArtworkBackground(cover, blurPercent, Modifier.matchParentSize())
        val frosted = customImage == null && !dynamicBackground
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            if (frosted) listOf(Color.Black.copy(alpha = .56f), Color.Black.copy(alpha = .62f), Color.Black.copy(alpha = .58f))
            else listOf(Color.Black.copy(alpha = .33f), Color.Black.copy(alpha = .62f)))))
    }
}

@Composable private fun FrostedArtworkBackground(cover: String, percent: Float, modifier: Modifier) {
    val context = LocalContext.current
    val blur = if (percent.isFinite()) percent.coerceIn(0f, 100f).roundToInt() else 35
    // Blur a small software cover once per change, on every Android version. This avoids
    // a full-screen RenderEffect and its transparent edges, including in software rendering.
    val bitmap by key(cover) {
        produceState<ImageBitmap?>(null, context, cover, blur) {
            if (cover.isBlank()) return@produceState
            delay(90)
            val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context)
                .data(coverRequestUrl(cover, 192)).size(192).allowHardware(false).build())
            value = if (result is SuccessResult) withContext(Dispatchers.Default) {
                val input = result.image.toBitmap()
                // Flatten transparent artwork before filtering to keep its edge colours neutral.
                val opaque = android.graphics.Bitmap.createBitmap(input.width, input.height, android.graphics.Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(opaque).apply {
                    drawColor(android.graphics.Color.rgb(66, 64, 63))
                    drawBitmap(input, 0f, 0f, null)
                }
                val pixels = IntArray(input.width * input.height)
                opaque.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
                opaque.recycle()
                android.graphics.Bitmap.createBitmap(blurBackgroundPixels(pixels, input.width, input.height,
                    (blur * .24f).roundToInt()), input.width, input.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
            } else null
        }
    }
    Box(modifier.testTag("player_static_background")
        .background(Color(0xFF42403F))) {
        Crossfade(bitmap, animationSpec = tween(600), label = "frosted artwork", modifier = Modifier.matchParentSize()) { image ->
            if (image != null) Image(image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.matchParentSize().background(Color(0xFFB5B1A8).copy(alpha = .08f)))
    }
}

@Composable private fun ArtworkPlayerBackground(cover: String, active: Boolean, motionSpeed: (() -> Float)?, modifier: Modifier) {
    // Reuse the dock/lyrics palette cache and Coil's small software cover request.
    val palette by rememberArtworkGlowPalette(cover)
    val primary by animateColorAsState(Color(palette.primary), tween(900), label = "player background primary")
    val secondary by animateColorAsState(Color(palette.secondary), tween(900), label = "player background secondary")
    val pools = remember(primary, secondary) {
        listOf(primary.copy(alpha = .85f), secondary.copy(alpha = .8f),
            lerp(primary, secondary, .35f).copy(alpha = .65f),
            lerp(secondary, Color.Black, .25f).copy(alpha = .9f))
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val motionEnabled = (rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    val accelerated = LocalView.current.isHardwareAccelerated
    Box(modifier.testTag("player_artwork_background")
        .background(Brush.verticalGradient(listOf(lerp(Color(0xFF171719), primary, .6f),
            lerp(Color(0xFF171719), secondary, .35f))))) {
        BgEffectBackground(dynamicBackground = active && motionEnabled && lifecycle.isAtLeast(Lifecycle.State.RESUMED),
            modifier = Modifier.matchParentSize(), isFullSize = true, effectBackground = accelerated,
            palette = pools, darkTheme = true, surfaceColor = Color(0xFF171719), motionSpeed = motionSpeed) { }
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
