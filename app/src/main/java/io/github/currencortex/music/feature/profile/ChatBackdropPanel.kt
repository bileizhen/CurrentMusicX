package io.github.currencortex.music.feature.profile

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Fade the entire backdrop layer from 100% to 0%; there is no opaque material plateau. */
@Composable
internal fun ChatBackdropPanel(backdrop: LayerBackdrop?, atTop: Boolean, modifier: Modifier = Modifier) {
    val surface = MiuixTheme.colorScheme.background
    Box(modifier.clearAndSetSemantics {}
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val mask = Brush.verticalGradient(if (atTop) listOf(Color.White, Color.Transparent)
                else listOf(Color.Transparent, Color.White))
            onDrawWithContent { drawContent(); drawRect(mask, blendMode = BlendMode.DstIn) }
        }.then(if (Build.VERSION.SDK_INT >= 33 && backdrop != null) Modifier.chatBackdrop(backdrop)
            else Modifier.background(surface)))
}

@Composable
@RequiresApi(33)
private fun Modifier.chatBackdrop(backdrop: LayerBackdrop): Modifier {
    val radius = with(LocalDensity.current) { 6.dp.toPx() }
    val gaussian = remember(radius) {
        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()
    }
    return drawBackdrop(backdrop, shape = { RectangleShape }, effects = {
        padding = radius * 3f
        renderEffect = gaussian
    })
}
