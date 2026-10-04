package io.github.currencortex.music.ui.component

import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.liquid.lens
import io.github.currencortex.music.ui.component.liquid.vibrancy
import io.github.currencortex.music.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.HighlightStyle
import top.yukonga.miuix.kmp.blur.RuntimeShaderCache
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Match the lit edge to the visible outline, even when the retained layout is taller. */
private class OutlineHighlightStyle(private val delegate: HighlightStyle) : HighlightStyle by delegate {
    override fun DrawScope.createShader(shape: Shape, strokeWidthPx: Float, highlightAlpha: Float,
        runtimeShaderCache: RuntimeShaderCache): Shader? {
        val outline = shape.createOutline(size, layoutDirection, this) as? Outline.Rounded
        val shaderScope = if (outline != null) object : DrawScope by this {
            override val size = androidx.compose.ui.geometry.Size(outline.roundRect.width, outline.roundRect.height)
        } else this
        return with(delegate) { shaderScope.createShader(shape, strokeWidthPx, highlightAlpha, runtimeShaderCache) }
    }
}

/** One material recipe for the navigation capsule and mini player capsule. */
@Composable @RequiresApi(33)
internal fun Modifier.musicGlassMaterial(backdrop: Backdrop, blurEnabled: Boolean, glassEnabled: Boolean,
    highlight: Highlight, shape: () -> Shape = { CircleShape }, layerBlock: GraphicsLayerScope.() -> Unit = {}): Modifier {
    val dark = isInDarkTheme()
    val surface = if (dark) MiuixTheme.colorScheme.surfaceContainer else Color.White
    val tint = surface.copy(alpha = if (blurEnabled) .4f else 1f)
    val outlineHighlight = remember(highlight) { highlight.copy(style = OutlineHighlightStyle(highlight.style)) }
    return if (blurEnabled) drawBackdrop(backdrop = backdrop, shape = shape, effects = {
        vibrancy()
        blur(4.dp.toPx(), 4.dp.toPx())
        if (glassEnabled) lens(refractionHeight = 24.dp.toPx(), refractionAmount = 24.dp.toPx())
    }, highlight = { if (glassEnabled) outlineHighlight.copy(alpha = .75f) else null },
        layerBlock = layerBlock, onDrawSurface = { drawRect(tint) })
    else background(tint, shape())
}

@Composable @RequiresApi(33)
internal fun MusicGlassCapsule(backdrop: Backdrop, modifier: Modifier, blur: Boolean, glass: Boolean,
    content: @Composable () -> Unit) {
    val dark = isInDarkTheme()
    val highlight = if (blur && glass) rememberGravityRotatedHighlight(iosIndicatorSpecular, -45f) else iosIndicatorSpecular
    Box(modifier.dropShadow(CircleShape, Shadow(radius = 10.dp, color = Color.Black, alpha = if (dark) .2f else .1f))
        .musicGlassMaterial(backdrop, blur, glass, highlight)) {
        Box(Modifier.testTag(if (glass && blur) "mini_glass_surface" else "mini_blur_surface")) { content() }
    }
}
