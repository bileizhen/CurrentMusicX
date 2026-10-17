package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Fade scrolling messages into the page; the header and composer draw above this layer. */
@Composable
internal fun ChatEdgeFade(atTop: Boolean, modifier: Modifier = Modifier) {
    val surface = MiuixTheme.colorScheme.background
    Box(modifier.clearAndSetSemantics {}.drawWithCache {
        val colors = if (atTop) listOf(surface, surface.copy(alpha = 0f))
            else listOf(surface.copy(alpha = 0f), surface)
        val fade = Brush.verticalGradient(colors)
        onDrawBehind { drawRect(fade) }
    })
}
