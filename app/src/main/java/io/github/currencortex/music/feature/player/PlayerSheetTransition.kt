package io.github.currencortex.music.feature.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.findRootCoordinates
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
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.round
import kotlin.math.roundToInt

internal const val PLAYER_SHEET_DURATION = 420
internal const val PLAYER_SHEET_CLOSE_DURATION = 360
internal enum class PlayerSheetMotion { NONE, OPEN, CLOSE }
private val sheetEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)
private val collapseEasing = CubicBezierEasing(.3f, 0f, .2f, 1f)
internal fun playerSheetSurfaceAlpha(progress: Float) = (progress / .14f).coerceIn(0f, 1f)
internal fun playerSheetCornerScale(progress: Float) = 1 - ((progress - .94f) / .06f).coerceIn(0f, 1f)
internal fun playerSheetContentAlpha(progress: Float) = ((progress - .16f) / .6f).coerceIn(0f, 1f)
internal val LocalPlayerSheetProgress = staticCompositionLocalOf<() -> Float> { { 1f } }
internal val LocalPlayerSheetOffset = staticCompositionLocalOf<() -> Offset> { { Offset.Zero } }

internal data class PlayerArtworkOrigin(val bounds: Rect, val rotation: Float, val borderColor: Color)
internal data class PlayerArtworkFrame(val progress: Float, val bounds: Rect, val rotation: Float)
internal val PlayerArtworkGeometry = SemanticsPropertyKey<PlayerArtworkFrame>("PlayerArtworkGeometry")
internal val PlayerArtworkVisible = SemanticsPropertyKey<Boolean>("PlayerArtworkVisible")

/** A quadratic curve with its control point at (end.x, start.y), shared by all sheet elements. */
internal fun playerSheetElementArc(start: Rect, end: Rect, progress: Float, anchorTopLeft: Boolean = false): Rect {
    val p = progress.coerceIn(0f, 1f)
    val horizontal = 1 - (1 - p) * (1 - p)
    val vertical = p * p
    val startAnchor = if (anchorTopLeft) start.topLeft else start.center
    val endAnchor = if (anchorTopLeft) end.topLeft else end.center
    val position = Offset(startAnchor.x * (1 - horizontal) + endAnchor.x * horizontal,
        startAnchor.y * (1 - vertical) + endAnchor.y * vertical)
    val size = Size(start.width * (1 - vertical) + end.width * vertical,
        start.height * (1 - vertical) + end.height * vertical)
    return Rect(if (anchorTopLeft) position else position - Offset(size.width / 2, size.height / 2), size)
}

/** Destination coordinates remain full-size; the sheet translation is removed for the flight. */
internal class PlayerArtworkTransition(val progress: () -> Float) {
    val text = PlayerSheetTextTransition(progress)
    val controls = PlayerSheetControlsTransition(progress)
    var origin by mutableStateOf<PlayerArtworkOrigin?>(null)
    var target by mutableStateOf<LayoutCoordinates?>(null)
    var destination by mutableStateOf<(() -> Rect?)?>(null)
    var sheet by mutableStateOf<LayoutCoordinates?>(null)
    var immersiveArtwork by mutableStateOf<PlayerPagerArtworkTransition?>(null)
    var squareArtwork by mutableStateOf(false)
    var viewport by mutableStateOf<Rect?>(null)
    val moving: Boolean get() = origin != null && target?.isAttached == true && sheet?.isAttached == true && progress() > 0f && progress() < 1f
}
internal val LocalPlayerArtworkTransition = staticCompositionLocalOf<PlayerArtworkTransition?> { null }

@Composable internal fun PlayerArtwork(url: String, modifier: Modifier, pixels: Int = 800,
    transitionTarget: Boolean = true, pagerRole: PlayerPagerArtworkRole? = null) {
    val transfer = LocalPlayerArtworkTransition.current
    val pager = LocalPlayerPagerArtwork.current
    val coverMotion = LocalPlayerCoverMotion.current.takeIf { pagerRole == null || pagerRole == PlayerPagerArtworkRole.COVER }
    val rotation = LocalPlayerArtworkRotation.current
    val square = transfer?.squareArtwork == true
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(transfer, transitionTarget) { onDispose { if (transfer?.target === coordinates) transfer?.target = null } }
    DisposableEffect(pager, pagerRole) { onDispose {
        if (pagerRole == PlayerPagerArtworkRole.COVER && pager?.cover === coordinates) pager?.cover = null
        if (pagerRole == PlayerPagerArtworkRole.LYRICS && pager?.lyrics === coordinates) pager?.lyrics = null
        if (pagerRole == PlayerPagerArtworkRole.QUEUE && pager?.queue === coordinates) pager?.queue = null
    } }
    SideEffect { if (pagerRole == null && transitionTarget && coordinates?.isAttached == true) transfer?.target = coordinates }
    Box(modifier.onGloballyPositioned {
        coordinates = it
        when (pagerRole) {
            PlayerPagerArtworkRole.COVER -> pager?.cover = it
            PlayerPagerArtworkRole.LYRICS -> pager?.lyrics = it
            PlayerPagerArtworkRole.QUEUE -> pager?.queue = it
            null -> if (transitionTarget) transfer?.target = it
        }
        if (pagerRole != null) pager?.updateBounds()
    }
        .playerMotionDiagnostics {
            this[CoverRotation] = if (square) 0f else rotation?.value ?: 0f
            this[PlayerArtworkVisible] = transfer?.moving != true && !(pagerRole != null && pager?.ready == true)
        }.semantics {
            if (pagerRole != null && pager?.ready == true) hideFromAccessibility()
        }) {
        PlayerSongCover(url, Modifier.matchParentSize().graphicsLayer {
            alpha = if (transfer?.moving == true || (pagerRole != null && pager?.ready == true)) 0f else 1f
        }, artworkModifier = Modifier.graphicsLayer {
            rotationZ = if (square || pagerRole == PlayerPagerArtworkRole.QUEUE) 0f else rotation?.value ?: 0f
        }.then(if (pagerRole == PlayerPagerArtworkRole.QUEUE) Modifier else Modifier.playerCoverShadow(square = square)).clip(when {
            pagerRole == PlayerPagerArtworkRole.QUEUE -> RoundedCornerShape(8.dp)
            square -> RoundedCornerShape(5)
            else -> CircleShape
        }), motion = coverMotion, pixels = pixels,
            cornerRadius = if (pagerRole == PlayerPagerArtworkRole.QUEUE) 8.dp else 0.dp)
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
    miniOpenBounds: Rect? = null, onReopen: () -> Unit = {},
    onBack: () -> Unit, onSettled: () -> Unit, content: @Composable () -> Unit) {
    val settled by rememberUpdatedState(onSettled)
    SideEffect { artwork.viewport = viewport }
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
        PlayerSheetSongTextFlight(artwork.text, viewport)
        PlayerSheetControlFlights(artwork.controls, viewport)
        // The shrinking player still owns its full touch layer. Keep the mini's opening
        // area above it so the next press reverses the close immediately, before release
        // (the old layer may otherwise disappear between that press and release).
        if (!open && motion == PlayerSheetMotion.CLOSE && backEnabled && miniOpenBounds != null) {
            val density = LocalDensity.current
            val bounds = miniOpenBounds.translate(-(viewport?.topLeft ?: Offset.Zero))
            val reopen by rememberUpdatedState(onReopen)
            fun reopenNow() {
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    expansion.animation.stop()
                    reopen()
                }
            }
            Box(Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .requiredSize(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .testTag("player_sheet_reopen_mini")
                .semantics {
                    role = Role.Button; contentDescription = "重新打开播放器"
                    onClick { reopenNow(); true }
                }.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        down.consume()
                        reopenNow()
                    }
                })
        }
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
        .playerMotionDiagnostics { this[PlayerSheetGeometry] = frame }
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
        Box(Modifier.fillMaxSize().onGloballyPositioned { artwork?.sheet = it; artwork?.text?.sheet = it; artwork?.controls?.sheet = it }) {
            CompositionLocalProvider(LocalPlayerSheetOffset provides { frame.bounds.topLeft }) { content() }
        }
    }
}

/** Normalize once per flight, then unwrap each playback step across full turns. */
internal class PlayerArtworkFlightAngle(initialAngle: Float) {
    private var previousAngle = initialAngle
    private var continuousAngle = initialAngle - 360f * round(initialAngle / 360f)

    fun rotation(angle: Float, progress: Float): Float {
        val delta = angle - previousAngle
        continuousAngle += delta - 360f * round(delta / 360f)
        previousAngle = angle
        return continuousAngle * (1 - progress)
    }
}

/** Shared artwork follows the same progress as the sheet, including direct dragging. */
@Composable private fun PlayerArtworkFlight(transfer: PlayerArtworkTransition, url: String,
    viewport: Rect?) {
    // The immersive cover flies below its controls, inside the sheet, using the same arc.
    if (transfer.immersiveArtwork != null) return
    val sheet = transfer.sheet?.takeIf { it.isAttached } ?: return
    val target = transfer.target?.takeIf { it.isAttached } ?: return
    val source = transfer.origin ?: return
    val window = viewport ?: return
    val targetSize = target.size
    if (targetSize.width <= 0 || targetSize.height <= 0) return
    val density = LocalDensity.current
    val rotation = LocalPlayerArtworkRotation.current
    val lastDestination = remember(transfer, target, sheet) { arrayOfNulls<Rect>(1) }
    val moving by remember(transfer) { derivedStateOf { transfer.moving } }
    // Reversing a drag retains the same angle history until the flight reaches an endpoint.
    val flightAngle = remember(transfer, source, rotation, moving) {
        PlayerArtworkFlightAngle(rotation?.value ?: source.rotation)
    }
    val frame by remember(transfer, target, source, sheet, window, rotation, flightAngle) { derivedStateOf {
        val p = transfer.progress().coerceIn(0f, 1f)
        // Relative coordinates cancel the sheet's moving layer transform without relying
        // on which graphics layer happened to update first in this frame.
        val start = source.bounds.translate(-window.topLeft)
        // A draw observer may run once more while the sheet's subtree is being removed.
        // Keep the last measured endpoint rather than querying detached/different roots.
        val destination = if (sheet.isAttached && target.isAttached && sheet.findRootCoordinates() === target.findRootCoordinates()) {
            (transfer.destination?.invoke() ?: Rect(sheet.localPositionOf(target, Offset.Zero),
                Size(targetSize.width.toFloat(), targetSize.height.toFloat()))).also { lastDestination[0] = it }
        } else lastDestination[0] ?: start
        // Measured from the supplied recording: horizontal travel eases out, while the
        // vertical travel and diameter ease in. This rolls inward before lifting up.
        val angle = rotation?.value ?: source.rotation
        val flightRotation = if (transfer.squareArtwork) flightAngle.rotation(angle, p) else angle
        PlayerArtworkFrame(p, playerSheetElementArc(start, destination, p), flightRotation)
    } }
    if (moving) MusicCover(url, Modifier.requiredSize(with(density) { targetSize.width.toDp() })
        .testTag("player_artwork_flight").playerMotionDiagnostics { this[PlayerArtworkGeometry] = frame }
        .graphicsLayer {
            val f = frame
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = f.bounds.left; translationY = f.bounds.top
            scaleX = f.bounds.width / targetSize.width; scaleY = scaleX
        }.graphicsLayer { rotationZ = frame.rotation }
        .playerCoverShadow(square = transfer.squareArtwork) { frame.progress }
        .graphicsLayer {
            val fraction = .5f * (1 - frame.progress) + (if (transfer.squareArtwork) .05f else .5f) * frame.progress
            shape = RoundedCornerShape(with(density) { (size.minDimension * fraction).toDp() })
            clip = true
        }
        .drawWithContent {
            drawContent()
            val stroke = 2.dp.toPx() * (1 - frame.progress) / (frame.bounds.width / targetSize.width)
            if (stroke > 0f) {
                val fraction = .5f * (1 - frame.progress) + (if (transfer.squareArtwork) .05f else .5f) * frame.progress
                drawRoundRect(source.borderColor, topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius((size.minDimension * fraction - stroke / 2).coerceAtLeast(0f)),
                    style = Stroke(stroke))
            }
        }, pixels = 800, cornerRadius = 0.dp)
}
