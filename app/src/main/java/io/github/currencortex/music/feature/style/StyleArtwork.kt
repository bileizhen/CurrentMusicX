package io.github.currencortex.music.feature.style

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.currencortex.music.ui.component.MusicPlaceholder
import io.github.currencortex.music.ui.component.coverRequestUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Artwork and ambient surface share the first song's image, including Coil's existing cache. */
@Composable internal fun rememberStyleTint(cover: String): MutableState<Color?> =
    remember(cover) { mutableStateOf(null) }

@Composable internal fun styleSurface(tint: Color?): Color {
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val dark = surface.luminance() < .3f
    return animateColorAsState(lerp(surface, tint ?: surface, if (dark) .28f else .20f), tween(220), label = "style tint").value
}

@Composable internal fun StyleArtwork(cover: String, modifier: Modifier, tint: MutableState<Color?>, pixels: Int = 360,
    pending: Boolean = false, contentDescription: String = "第一首歌曲封面") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember(cover) { mutableStateOf(cover.isNotBlank()) }
    var failed by remember(cover) { mutableStateOf(cover.isBlank()) }
    Box(modifier.background(MiuixTheme.colorScheme.onSurface.copy(alpha = .06f)), contentAlignment = Alignment.Center) {
        if (loading || pending) MusicPlaceholder(Modifier.matchParentSize())
        if (failed && !pending) Text("♪", fontSize = 32.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .22f))
        if (cover.isNotBlank()) AsyncImage(
            model = remember(context, cover, pixels) { ImageRequest.Builder(context)
                .data(coverRequestUrl(cover, pixels)).size(pixels).allowHardware(false).build() },
            contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(),
            onSuccess = { result ->
                loading = false; failed = false
                scope.launch {
                    // Sample a tiny software bitmap off the UI thread; no palette dependency or extra request.
                    tint.value = withContext(Dispatchers.Default) {
                        val bitmap = result.result.image.toBitmap(12, 12)
                        var red = 0; var green = 0; var blue = 0
                        for (y in 0 until 12) for (x in 0 until 12) {
                            val pixel = bitmap.getPixel(x, y)
                            red += android.graphics.Color.red(pixel)
                            green += android.graphics.Color.green(pixel)
                            blue += android.graphics.Color.blue(pixel)
                        }
                        Color(red / 144, green / 144, blue / 144)
                    }
                }
            },
            onError = { loading = false; failed = true })
    }
}
