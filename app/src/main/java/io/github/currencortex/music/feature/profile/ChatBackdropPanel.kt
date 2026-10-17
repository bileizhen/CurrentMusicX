package io.github.currencortex.music.feature.profile

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Blur the messages behind fixed controls, then feather only the material's outer edge. */
@Composable
internal fun ChatBackdropPanel(backdrop: LayerBackdrop?, atTop: Boolean, modifier: Modifier = Modifier) {
    val surface = MiuixTheme.colorScheme.background
    Box(modifier.clearAndSetSemantics {}
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val feather = (24.dp.toPx() / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val mask = if (atTop) Brush.verticalGradient(0f to Color.White, (1f - feather) to Color.White, 1f to Color.Transparent)
                else Brush.verticalGradient(0f to Color.Transparent, feather to Color.White, 1f to Color.White)
            onDrawWithContent { drawContent(); drawRect(mask, blendMode = BlendMode.DstIn) }
        }.then(if (Build.VERSION.SDK_INT >= 33 && backdrop != null) Modifier.chatBackdrop(backdrop, surface)
            else Modifier.background(surface)))
}

@RequiresApi(33)
private fun Modifier.chatBackdrop(backdrop: LayerBackdrop, surface: Color): Modifier =
    drawBackdrop(backdrop, shape = { RectangleShape }, effects = { blur(12.dp.toPx(), 12.dp.toPx()) },
        onDrawSurface = { drawRect(surface.copy(alpha = .66f)) })
