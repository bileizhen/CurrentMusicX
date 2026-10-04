package io.github.currencortex.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The overlay has its own bounds so scaled server decorations never crop the avatar. */
@Composable fun DecoratedAvatar(avatarUrl: String?, decorationUrl: String?, modifier: Modifier = Modifier,
    scale: Double = 1.0, size: Dp = 64.dp, onImageError: (Throwable) -> Unit = {},
    reserveOverlay: Boolean = true) {
    val bounded = scale.takeIf { it.isFinite() }?.coerceIn(1.0, 2.5)?.toFloat() ?: 1f
    val overlaySize = size * bounded
    // Row layouts need a constant footprint; the ring then overflows unclipped via requiredSize.
    Box(modifier.size(if (reserveOverlay && decorationUrl != null) overlaySize else size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(CircleShape).background(MiuixTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Text("♪")
            if (avatarUrl != null) AsyncImage(ImageRequest.Builder(LocalContext.current).data(avatarUrl).size(256).build(),
                contentDescription = "用户头像", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onError = { onImageError(it.result.throwable) })
        }
        if (decorationUrl != null) AsyncImage(ImageRequest.Builder(LocalContext.current).data(decorationUrl).size(512).build(),
            contentDescription = "头像挂件", contentScale = ContentScale.Fit, modifier = Modifier.requiredSize(overlaySize),
            onError = { onImageError(it.result.throwable) })
    }
}
