package io.github.currencortex.music.feature.player

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import kotlin.math.pow

internal const val PLAYER_SHEET_DURATION = 450
internal enum class PlayerSheetMotion { NONE, OPEN, CLOSE }
private val sheetEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)

/** Keep both navigation scenes still; the player layer supplies the actual container motion. */
internal val playerSheetTransitions =
    NavDisplay.transitionSpec { fadeIn(tween(PLAYER_SHEET_DURATION), initialAlpha = .999f) togetherWith fadeOut(tween(PLAYER_SHEET_DURATION), targetAlpha = .999f) } +
    NavDisplay.popTransitionSpec { fadeIn(tween(PLAYER_SHEET_DURATION), initialAlpha = .999f) togetherWith fadeOut(tween(PLAYER_SHEET_DURATION), targetAlpha = .999f) } +
    NavDisplay.predictivePopTransitionSpec { fadeIn(tween(PLAYER_SHEET_DURATION), initialAlpha = .999f) togetherWith fadeOut(tween(PLAYER_SHEET_DURATION), targetAlpha = .999f) }

internal data class PlayerSheetFrame(val progress: Float, val bounds: Rect, val radius: Float)
internal val PlayerSheetGeometry = SemanticsPropertyKey<PlayerSheetFrame>("PlayerSheetGeometry")
internal val LocalPlayerRevealProgress = staticCompositionLocalOf<State<Float>?> { null }

private data class SheetClip(val width: Float, val height: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(Rect(0f, 0f, width, height), CornerRadius(radius)))
}

/** Full-size content is laid out once. Only the layer's clip, position and content alpha animate. */
@OptIn(ExperimentalAnimationApi::class)
@Composable internal fun PlayerSheetExpansion(origin: Rect?, viewport: Rect?, motion: PlayerSheetMotion,
    onTop: Boolean, onProgress: (State<Float>) -> Unit, content: @Composable (State<Float>) -> Unit) {
    val transition = LocalNavAnimatedContentScope.current.transition
    val animated = transition.animateFloat(transitionSpec = { tween(PLAYER_SHEET_DURATION, easing = sheetEasing) }, label = "player sheet expansion") {
        if (it == EnterExitState.Visible) 1f else 0f
    }
    // A predictive gesture starts before onBack is committed. Let Nav3 seek this same animation.
    val predictiveClosing = onTop && transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.PostExit
    val progress = remember(animated, motion, predictiveClosing) {
        derivedStateOf { if (motion != PlayerSheetMotion.NONE || predictiveClosing) animated.value else 1f }
    }
    SideEffect { onProgress(progress) }
    var layerSize by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current
    val frame by remember(progress, origin, viewport, density) { derivedStateOf {
        val size = layerSize
        val p = progress.value.coerceIn(0f, 1f)
        if (size.width <= 0 || size.height <= 0) PlayerSheetFrame(p, Rect.Zero, 0f) else {
            val fallbackHeight = with(density) { 52.dp.toPx() }.coerceAtMost(size.height)
            val local = if (origin != null && viewport != null) origin.translate(-viewport.topLeft)
                else Rect(with(density) { 12.dp.toPx() }, size.height - fallbackHeight, size.width - with(density) { 12.dp.toPx() }, size.height)
            val left = local.left.coerceIn(0f, size.width) * (1f - p)
            val top = local.top.coerceIn(0f, size.height - fallbackHeight) * (1f - p)
            val width = local.width.coerceIn(1f, size.width) * (1f - p) + size.width * p
            val height = local.height.coerceIn(fallbackHeight, size.height) * (1f - p) + size.height * p
            val radius = minOf(local.height / 2, with(density) { 36.dp.toPx() }) * (1f - p.pow(4))
            PlayerSheetFrame(p, Rect(left, top, left + width, top + height), radius)
        }
    } }
    Box(Modifier.fillMaxSize().testTag("player_sheet")
        .onSizeChanged { layerSize = Size(it.width.toFloat(), it.height.toFloat()) }
        .semantics { this[PlayerSheetGeometry] = frame }
        .pointerInput(progress) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (progress.value < .995f) event.changes.forEach { it.consume() }
                }
            }
        }
        .graphicsLayer {
            translationX = frame.bounds.left; translationY = frame.bounds.top
            shape = SheetClip(frame.bounds.width, frame.bounds.height, frame.radius); clip = true
        }) { content(progress) }
}
