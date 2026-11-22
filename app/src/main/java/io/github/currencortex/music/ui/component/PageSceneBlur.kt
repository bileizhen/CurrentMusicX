package io.github.currencortex.music.ui.component

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
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
import kotlin.math.roundToInt

private const val BlurSteps = 20

@Composable
internal fun pageSceneBlur(covered: Boolean): Modifier {
    if (!covered || Build.VERSION.SDK_INT < 31 || !LocalGlassBlurEnabled.current || !LocalView.current.isHardwareAccelerated) {
        return Modifier
    }
    val pxPerDp = with(LocalDensity.current) { 1.dp.toPx() }
    val effects = remember(pxPerDp) {
        Array(BlurSteps + 1) { step ->
            if (step == 0) null else (pxPerDp * step).let { BlurEffect(it, it, TileMode.Clamp) }
        }
    }
    val step = remember { mutableIntStateOf(0) }
    return Modifier.onLayoutRectChanged(debounceMillis = 0) { bounds ->
        val fraction = (kotlin.math.abs(bounds.positionInRoot.x) / bounds.width.toFloat().coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        step.intValue = (fraction * effects.lastIndex).roundToInt()
    }.graphicsLayer {
        renderEffect = effects[step.intValue]
    }
}
