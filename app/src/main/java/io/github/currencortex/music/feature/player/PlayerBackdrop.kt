package io.github.currencortex.music.feature.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import io.github.currencortex.music.ui.component.coverRequestUrl

/** A low-resolution cover creates the atmosphere; controls stay on a separate sharp layer. */
@Composable fun PlayerBackdrop(cover: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.clipToBounds().background(Color(0xFF262428))) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xFF635047), Color(0xFF222832)))))
        if (cover.isNotBlank() && Build.VERSION.SDK_INT >= 31) AsyncImage(
            model = remember(cover) { ImageRequest.Builder(context).data(coverRequestUrl(cover, 160)).size(160).build() },
            contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }.blur(65.dp))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .33f), Color.Black.copy(alpha = .62f)))))
    }
}
