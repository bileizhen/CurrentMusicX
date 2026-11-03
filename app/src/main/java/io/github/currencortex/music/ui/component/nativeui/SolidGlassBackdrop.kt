package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop

/** An opaque, uniform source has no position to track. Fill the entire shader input,
 * including the padding used for refraction at the edge of the control. */
private class SolidGlassBackdrop(val color: Color) : Backdrop {
    private val argb = color.toArgb()
    override val isCoordinatesDependent = false
    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?, downscaleFactor: Int) {
        drawContext.canvas.nativeCanvas.drawColor(argb)
    }
}

@Composable internal fun rememberSolidGlassBackdrop(base: Backdrop?, color: Color): Backdrop? {
    if (base == null || color.alpha != 1f || (base is SolidGlassBackdrop && base.color == color)) return base
    return remember(color) { SolidGlassBackdrop(color) }
}
