package io.github.currencortex.music.ui.component

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.nativeui.LocalGlassBlurEnabled
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BlurSteps = 20

/** Miuix moves the lower scene by one quarter of the viewport on both push and pop. */
internal class PageSceneBlur {
    private val visibleDepths = mutableStateListOf<Int>()
    private var viewportLeft = 0
    val viewport = Modifier.onLayoutRectChanged(debounceMillis = 0) { viewportLeft = it.positionInRoot.x }

    @Composable
    fun layer(depth: Int): Modifier {
        if (Build.VERSION.SDK_INT < 31 || !LocalGlassBlurEnabled.current) return Modifier
        DisposableEffect(depth) {
            visibleDepths.add(depth)
            onDispose { visibleDepths.remove(depth) }
        }
        if (visibleDepths.none { it > depth } || !LocalView.current.isHardwareAccelerated) return Modifier

        val pxPerDp = with(LocalDensity.current) { 1.dp.toPx() }
        val effects = remember(pxPerDp) {
            Array(BlurSteps + 1) { i ->
                if (i == 0) null else (pxPerDp * i).let { BlurEffect(it, it, TileMode.Clamp) }
            }
        }
        val step = remember { mutableIntStateOf(0) }
        // Read actual placement so the effect also reverses when a back gesture is cancelled.
        return Modifier.onLayoutRectChanged(debounceMillis = 0) { bounds ->
            val displacement = abs(bounds.positionInRoot.x - viewportLeft)
            val fraction = (4f * displacement / bounds.width.coerceAtLeast(1)).coerceIn(0f, 1f)
            step.intValue = (fraction * effects.lastIndex).roundToInt()
        }.graphicsLayer { renderEffect = effects[step.intValue] }
    }
}
