package io.github.currencortex.music.feature.update

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Independent waves retain their values and velocities when the owner retargets the menu. */
@Stable
internal class UpdateMenuMotion {
    val path = Animatable(0f)
    val scale = Animatable(0f)
    val opacity = Animatable(0f)

    val visible: Boolean get() = listOf(path, scale, opacity).any { it.isRunning || it.value > .001f }

    suspend fun animate(open: Boolean) = coroutineScope {
        val target = if (open) 1f else 0f
        launch { path.animateTo(target, popupSpring(if (open) .13f else 0f, if (open) .29f else .26f)) }
        launch { scale.animateTo(target, popupSpring(if (open) .20f else 0f, if (open) .40f else .21f)) }
        launch { opacity.animateTo(target, popupSpring(0f, if (open) .27f else .23f)) }
    }
}

/** Portable response/bounce approximation; COUI's vendor spring solver is not a Compose API. */
private fun popupSpring(bounce: Float, response: Float): SpringSpec<Float> = spring(
    dampingRatio = 1f - bounce,
    stiffness = (2 * PI / response).let { (it * it).toFloat() },
    visibilityThreshold = .001f,
)

internal data class UpdateMenuGeometry(val origin: TransformOrigin, val start: Offset) {
    /** Cubic gravity path with distance-limited, direction-aware control points. */
    fun translation(progress: Float): Offset {
        val distance = start.getDistance()
        if (distance < 1f) return start * (1f - progress)
        val delta = -start
        val arc = 130f * ((distance - 30f) / 370f).coerceIn(0f, 1f) *
            min(abs(delta.x), abs(delta.y)) / (max(abs(delta.x), abs(delta.y)) + .0001f)
        var normal = Offset(-delta.y, delta.x) / distance
        if (normal.y < 0f) normal = -normal
        if (abs(normal.y) < .001f) normal = Offset(0f, 1f)
        val first = start + delta * .35f + normal * arc
        val second = start + delta * .65f + normal * arc
        val t = progress.coerceIn(0f, 1f)
        val remaining = 1f - t
        return start * (remaining * remaining * remaining) +
            first * (3f * remaining * remaining * t) + second * (3f * remaining * t * t)
    }
}

internal fun updateMenuGeometry(anchor: Rect?, panel: Rect): UpdateMenuGeometry {
    if (anchor == null || panel.width <= 0f || panel.height <= 0f)
        return UpdateMenuGeometry(TransformOrigin(1f, 0f), Offset.Zero)
    val relative = anchor.center - panel.topLeft
    val x = (relative.x / panel.width).coerceIn(0f, 1f)
    val y = (relative.y / panel.height).coerceIn(0f, 1f)
    return UpdateMenuGeometry(TransformOrigin(x, y), relative - Offset(panel.width * x, panel.height * y))
}
