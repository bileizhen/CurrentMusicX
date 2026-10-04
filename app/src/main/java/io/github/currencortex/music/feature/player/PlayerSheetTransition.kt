package io.github.currencortex.music.feature.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.currencortex.music.ui.component.MusicCover
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal const val PLAYER_SHEET_DURATION = 420
internal const val PLAYER_SHEET_CLOSE_DURATION = 360
internal enum class PlayerSheetMotion { NONE, OPEN, CLOSE }
private val sheetEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)
private val collapseEasing = CubicBezierEasing(.3f, 0f, .2f, 1f)
internal fun playerSheetSurfaceAlpha(progress: Float) = (progress / .14f).coerceIn(0f, 1f)
internal fun playerSheetCornerScale(progress: Float) = 1 - ((progress - .94f) / .06f).coerceIn(0f, 1f)
internal fun playerSheetContentAlpha(progress: Float) = ((progress - .16f) / .6f).coerceIn(0f, 1f)
internal val LocalPlayerSheetProgress = staticCompositionLocalOf<() -> Float> { { 1f } }

internal data class PlayerArtworkOrigin(val bounds: Rect, val rotation: Float, val borderColor: Color)
internal data class PlayerArtworkFrame(val progress: Float, val bounds: Rect, val rotation: Float)
internal val PlayerArtworkGeometry = SemanticsPropertyKey<PlayerArtworkFrame>("PlayerArtworkGeometry")

/** Destination coordinates remain full-size; the sheet translation is removed for the flight. */
internal class PlayerArtworkTransition(val progress: () -> Float) {
    var origin by mutableStateOf<PlayerArtworkOrigin?>(null)
    var target by mutableStateOf<LayoutCoordinates?>(null)
    var sheet by mutableStateOf<LayoutCoordinates?>(null)
    val moving: Boolean get() = origin != null && target?.isAttached == true && sheet?.isAttached == true && progress() > 0f && progress() < 1f
}
internal val LocalPlayerArtworkTransition = staticCompositionLocalOf<PlayerArtworkTransition?> { null }

@Composable internal fun PlayerArtwork(url: String, modifier: Modifier, pixels: Int = 800,
    transitionTarget: Boolean = true) {
    val transfer = LocalPlayerArtworkTransition.current
    val rotation = LocalPlayerArtworkRotation.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(transfer, transitionTarget) { onDispose { if (transfer?.target === coordinates) transfer?.target = null } }
    SideEffect { if (transitionTarget && coordinates?.isAttached == true) transfer?.target = coordinates }
    Box(modifier.onGloballyPositioned { coordinates = it; if (transitionTarget) transfer?.target = it }
        .semantics { this[CoverRotation] = rotation?.value ?: 0f }) {
        MusicCover(url, Modifier.matchParentSize().graphicsLayer {
            alpha = if (transfer?.moving == true) 0f else 1f
            rotationZ = rotation?.value ?: 0f
        }.clip(CircleShape), pixels)
    }
}

internal data class PlayerSheetOrigin(val bounds: Rect, val radius: Float)
internal data class PlayerSheetFrame(val progress: Float, val bounds: Rect, val radius: Float)
internal val PlayerSheetGeometry = SemanticsPropertyKey<PlayerSheetFrame>("PlayerSheetGeometry")

private data class SheetClip(val width: Float, val height: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(Rect(0f, 0f, width, height), CornerRadius(radius)))
}

/** Kept outside navigation so the source page and dock never leave composition. */
@Composable internal fun PlayerSheetHost(open: Boolean, motion: PlayerSheetMotion,
    expansion: PlayerSheetState,
    origin: PlayerSheetOrigin?, viewport: Rect?, artwork: PlayerArtworkTransition, artworkUrl: String,
    predictiveBack: Boolean, backEnabled: Boolean,
    onBack: () -> Unit, onSettled: () -> Unit, content: @Composable () -> Unit) {
    val settled by rememberUpdatedState(onSettled)
    val scope = rememberCoroutineScope()
    val isOpen by rememberUpdatedState(open)
    LaunchedEffect(open, motion) {
        if (expansion.dragging) return@LaunchedEffect
        when (motion) {
            PlayerSheetMotion.OPEN -> expansion.animation.animateTo(1f, tween(PLAYER_SHEET_DURATION, easing = sheetEasing))
            PlayerSheetMotion.CLOSE -> expansion.animation.animateTo(0f, tween(PLAYER_SHEET_CLOSE_DURATION, easing = collapseEasing))
            PlayerSheetMotion.NONE -> expansion.animation.snapTo(if (open) 1f else 0f)
        }
        settled()
    }
    // These handlers precede PlayerScreen's dialogs. A cancelled gesture restores the same
    // layer instead of navigating or composing another copy of the source page.
    PredictiveBackHandler(enabled = open && backEnabled && predictiveBack) { events ->
        try {
            expansion.animation.stop()
            val start = expansion.value
            expansion.draggedProgress = null
            expansion.animation.snapTo(start)
            events.collect { expansion.animation.snapTo(start * (1f - it.progress.coerceIn(0f, 1f))) }
            onBack()
        } catch (cancelled: CancellationException) {
            if (isOpen) scope.launch { expansion.animation.animateTo(1f, tween(PLAYER_SHEET_DURATION, easing = sheetEasing)) }
            throw cancelled
        }
    }
    BackHandler(enabled = open && backEnabled && !predictiveBack, onBack = onBack)
    val present by remember(open) { derivedStateOf { open || expansion.value > .0001f } }
    // A second back during docking must not pop the still-visible source page.
    BackHandler(enabled = !open && present && motion == PlayerSheetMotion.CLOSE) {}
    if (present) Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPlayerArtworkTransition provides artwork, LocalPlayerSheetProgress provides expansion::value) {
            PlayerSheetExpansion(origin, viewport, expansion::value) { content() }
        }
        PlayerArtworkFlight(artwork, artworkUrl, viewport)
    }
}

/** Full-size content is laid out once. Only the clip, position and opacity animate. */
@Composable private fun PlayerSheetExpansion(origin: PlayerSheetOrigin?, viewport: Rect?,
    progress: () -> Float, content: @Composable () -> Unit) {
    var layerSize by remember { mutableStateOf(Size.Zero) }
    val artwork = LocalPlayerArtworkTransition.current
    val drag = LocalPlayerSheetDrag.current
    val density = LocalDensity.current
    val frame by remember(progress, origin, viewport, density) { derivedStateOf {
        val size = layerSize
        val p = progress().coerceIn(0f, 1f)
        if (size.width <= 0 || size.height <= 0) PlayerSheetFrame(p, Rect.Zero, 0f) else {
            val fallbackHeight = with(density) { 64.dp.toPx() }.coerceAtMost(size.height)
            val local = if (origin != null && viewport != null) origin.bounds.translate(-viewport.topLeft)
                else Rect(with(density) { 26.dp.toPx() }, size.height - fallbackHeight,
                    size.width - with(density) { 26.dp.toPx() }, size.height)
            val left = local.left.coerceIn(0f, size.width) * (1f - p)
            val top = local.top.coerceIn(0f, size.height - fallbackHeight) * (1f - p)
            val width = local.width.coerceIn(1f, size.width) * (1f - p) + size.width * p
            val height = local.height.coerceIn(fallbackHeight, size.height) * (1f - p) + size.height * p
            val radius = (origin?.radius ?: with(density) { 32.dp.toPx() }) * playerSheetCornerScale(p)
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
                    if (progress() < .995f && drag?.state?.dragging != true) event.changes.forEach { it.consume() }
                }
            }
        }
        .graphicsLayer {
            alpha = playerSheetSurfaceAlpha(frame.progress)
            translationX = frame.bounds.left; translationY = frame.bounds.top
            shape = SheetClip(frame.bounds.width, frame.bounds.height, frame.radius); clip = true
        }) {
        // Measure in the content coordinate space, inside the moving graphics layer.
        Box(Modifier.fillMaxSize().onGloballyPositioned { artwork?.sheet = it }) { content() }
    }
}

/** Shared circular artwork follows the same progress as the sheet, including direct dragging. */
@Composable private fun PlayerArtworkFlight(transfer: PlayerArtworkTransition, url: String,
    viewport: Rect?) {
    val sheet = transfer.sheet?.takeIf { it.isAttached } ?: return
    val target = transfer.target?.takeIf { it.isAttached } ?: return
    val source = transfer.origin ?: return
    val window = viewport ?: return
    val targetSize = target.size
    if (targetSize.width <= 0 || targetSize.height <= 0) return
    val density = LocalDensity.current
    val rotation = LocalPlayerArtworkRotation.current
    val frame by remember(transfer, target, source, sheet, window, rotation) { derivedStateOf {
        val p = transfer.progress().coerceIn(0f, 1f)
        // Relative coordinates cancel the sheet's moving layer transform without relying
        // on which graphics layer happened to update first in this frame.
        val destinationTop = sheet.localPositionOf(target, Offset.Zero)
        val destination = Rect(destinationTop, Size(targetSize.width.toFloat(), targetSize.height.toFloat()))
        val start = source.bounds.translate(-window.topLeft)
        // Measured from the supplied recording: horizontal travel eases out, while the
        // vertical travel and diameter ease in. This rolls inward before lifting up.
        val horizontal = 1 - (1 - p) * (1 - p)
        val vertical = p * p
        val center = Offset(start.center.x * (1 - horizontal) + destination.center.x * horizontal,
            start.center.y * (1 - vertical) + destination.center.y * vertical)
        val diameter = start.width * (1 - vertical) + destination.width * vertical
        PlayerArtworkFrame(p, Rect(center - Offset(diameter / 2, diameter / 2), Size(diameter, diameter)), rotation?.value ?: source.rotation)
    } }
    val moving by remember(transfer) { derivedStateOf { transfer.moving } }
    if (moving) MusicCover(url, Modifier.requiredSize(with(density) { targetSize.width.toDp() })
        .testTag("player_artwork_flight").semantics { this[PlayerArtworkGeometry] = frame }
        .graphicsLayer {
            val f = frame
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = f.bounds.left; translationY = f.bounds.top
            scaleX = f.bounds.width / targetSize.width; scaleY = scaleX
        }.graphicsLayer { rotationZ = frame.rotation }.clip(CircleShape)
        .drawWithContent {
            drawContent()
            val stroke = 2.dp.toPx() * (1 - frame.progress) / (frame.bounds.width / targetSize.width)
            if (stroke > 0f) drawCircle(source.borderColor, radius = size.width / 2 - stroke / 2, style = Stroke(stroke))
        }, pixels = 800)
}
