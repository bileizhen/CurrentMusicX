package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.unit.Density
import io.github.currencortex.music.ui.component.liquid.CombinedBackdrop
import top.yukonga.miuix.kmp.blur.Backdrop

internal val LocalPageGlassScene = compositionLocalOf<PageGlassScene?> { null }

internal class PageGlassScene(private val transition: Transition<EnterExitState>) {
    fun moving() = transition.currentState != transition.targetState
    fun entering() = transition.currentState == EnterExitState.PreEnter && transition.targetState == EnterExitState.Visible

    val sources = mutableStateListOf<Backdrop>()
    var origin: LayoutCoordinates? = null

    fun owns(source: Backdrop): Boolean = if (source is CombinedBackdrop) owns(source.first) && owns(source.second)
        else source in sources
}

/** During a page slide a button and its backdrop travel together. Refresh for motion
 * within the page, without invalidating the shader for every change in window position. */
internal class PageGlassSample(private val source: Backdrop, private val scene: PageGlassScene) : Backdrop by source {
    private data class Placement(val coordinates: LayoutCoordinates, val offset: Offset?)
    private var placement by mutableStateOf<Placement?>(null)

    override val isCoordinatesDependent: Boolean
        get() = !scene.moving() || placement?.offset == null

    fun place(coordinates: LayoutCoordinates) {
        val origin = scene.origin
        val offset = if (origin != null && origin.isAttached && coordinates.isAttached &&
            origin.findRootCoordinates() == coordinates.findRootCoordinates())
            origin.localPositionOf(coordinates, Offset.Zero) else null
        placement = Placement(coordinates, offset)
    }

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?, downscaleFactor: Int) {
        val sample = coordinates?.takeIf { it.isAttached } ?: placement?.coordinates?.takeIf { it.isAttached }
        with(source) { drawBackdrop(density, sample, layerBlock, downscaleFactor) }
    }
}
