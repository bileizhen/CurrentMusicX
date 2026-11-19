package io.github.currencortex.music.ui.component

import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.liquid.InnerShadow
import io.github.currencortex.music.ui.component.liquid.innerShadow
import io.github.currencortex.music.ui.component.liquid.lens
import io.github.currencortex.music.ui.component.liquid.rememberCombinedBackdrop
import io.github.currencortex.music.ui.component.miuix.animation.DampedDragAnimation
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.drawBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme

/** A sibling of the clipped base panel, so the expanded lens can extend past its edge. */
@RequiresApi(33)
@Composable internal fun HighApiSideWaterDrop(backdrop: Backdrop, modifier: Modifier,
    motion: DampedDragAnimation, glass: Boolean, mirror: @Composable (Modifier) -> Unit) {
    val tabs = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(backdrop, tabs)
    val shape = remember { RoundedCornerShape(26.dp) }
    val primary = COUITheme.colorScheme.primary
    val highlight = if (glass) rememberGravityRotatedHighlight(iosIndicatorSpecular, 90f) else iosIndicatorSpecular
    mirror(Modifier.clearAndSetSemantics {}.alpha(0f).layerBackdrop(tabs)
        .graphicsLayer(colorFilter = ColorFilter.tint(primary)))
    Box(modifier.testTag("side_water_drop")
        .semantics { this[SideWaterDropVisual] = SideWaterDropFrame(motion.value, motion.pressProgress, motion.scaleX, motion.scaleY) }
        .drawBackdrop(backdrop = combined, shape = { shape }, effects = {
            if (glass) lens(refractionHeight = 10.dp.toPx() * motion.pressProgress,
                refractionAmount = 14.dp.toPx() * motion.pressProgress, depthEffect = true, chromaticAberration = .5f)
        }, highlight = { if (glass) highlight.copy(alpha = motion.pressProgress) else null },
            layerBlock = {
                val velocity = (motion.velocity / 10f).coerceIn(-.2f, .2f)
                scaleX = motion.scaleX * (1f - velocity * .25f)
                scaleY = motion.scaleY / (1f - velocity * .75f)
            }, onDrawSurface = { drawRect(primary.copy(alpha = .12f * (1f - motion.pressProgress))) })
        .innerShadow(shape) { InnerShadow(radius = 8.dp * motion.pressProgress,
            color = primary.copy(alpha = .10f), alpha = motion.pressProgress) })
}
