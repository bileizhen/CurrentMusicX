// SPDX-License-Identifier: GPL-3.0-only
// upstream goo.dart gooOutline: pills plus a smooth-min neck between neighbors.
// blob 162b057ed768582ed649421833df562fc95866af. Reuses the native gooPairOutline port.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Both neighbors share one outline, capture and optical pass with the editor. */
internal class GlassGooRowShape(private val field: Rect, private val left: Rect, private val right: Rect,
    private val leftBlend: Float, private val rightBlend: Float) : GlassOpticalShape {
    override fun opticalGeometry(size: Size, density: Density, direction: LayoutDirection) =
        GlassOpticalGeometry(field, field.height / 2, right, rightBlend, left, leftBlend)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        fun pair(neighbor: Rect, blend: Float): Path {
            val shape = glassGooShape { GlassPopupFrame(field, field.height / 2, neighbor, 1f, 1f, 1f, blend) }
            return when (val outline = shape.createOutline(size, layoutDirection, density)) {
                is Outline.Generic -> outline.path
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            }
        }
        return Outline.Generic(Path().apply { op(pair(left, leftBlend), pair(right, rightBlend), PathOperation.Union) })
    }
}
