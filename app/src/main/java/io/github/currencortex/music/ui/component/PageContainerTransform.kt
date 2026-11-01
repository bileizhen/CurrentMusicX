package io.github.currencortex.music.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*

/** A live source coordinate is converted into the destination's local space, including UI scaling. */
data class PageContainerOrigin(val coordinates: LayoutCoordinates, val color: Color,
    val title: String, val summary: String, val icon: androidx.compose.ui.graphics.vector.ImageVector? = MusicIcons.Download,
    val currentColor: State<Color>? = null, val sourceLayer: GraphicsLayer? = null)

internal data class PageContainerFrame(val progress: Float, val bounds: Rect, val radius: Float)
internal val PageContainerGeometry = SemanticsPropertyKey<PageContainerFrame>("PageContainerGeometry")
private data class CapturedOrigin(val bounds: Rect, val color: Color, val layer: GraphicsLayer?)
private class ContainerClip(private val frame: PageContainerFrame) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(frame.bounds, CornerRadius(frame.radius)))
}

@Composable internal fun PageContainerTransform(visible: Boolean, origin: PageContainerOrigin?,
    onPresentedChanged: (Boolean) -> Unit = {}, tag: String = "page_container_transform", content: @Composable () -> Unit) {
    val expansion = remember { Animatable(0f) }
    val progress = expansion.asState()
    LaunchedEffect(visible) {
        expansion.animateTo(if (visible) 1f else 0f,
            tween(if (visible) 600 else 460, easing = FastOutSlowInEasing))
    }
    val presented by remember(visible) { derivedStateOf { visible || expansion.value > .0001f || expansion.isRunning } }
    val report by rememberUpdatedState(onPresentedChanged)
    SideEffect { report(presented) }
    DisposableEffect(Unit) { onDispose { report(false) } }
    var destination by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lastOrigin by remember { mutableStateOf<CapturedOrigin?>(null) }
    var captured by remember(visible) { mutableStateOf<CapturedOrigin?>(null) }
    // Freeze each direction independently. Appearance can resize/recolour the retained source
    // while this page is open; returning must target its current geometry, not the old row.
    val source = captured ?: run {
        val target = destination
        if (origin?.coordinates?.isAttached == true && target?.isAttached == true)
            // The recorded row retains its full local origin even when partly clipped by a
            // scrolling ancestor. Cropping these bounds would shift its glyphs on frame zero.
            runCatching { target.localBoundingBoxOf(origin.coordinates, clipBounds = false) }.getOrNull()
                ?.takeIf { it.width > 1f && it.height > 1f && it.overlaps(Rect(Offset.Zero,
                    Size(target.size.width.toFloat(), target.size.height.toFloat()))) }
                ?.let { CapturedOrigin(it, origin.currentColor?.value ?: origin.color, origin.sourceLayer) }
        else null
    } ?: if (!visible) lastOrigin else null
    SideEffect { if (captured == null) { captured = source; if(visible)lastOrigin = source } }
    val density = LocalDensity.current
    val radius = with(density) { 26.dp.toPx() }
    fun frame(size: Size): PageContainerFrame {
        val p = progress.value
        val end = Rect(Offset.Zero, size)
        val start = source?.bounds ?: end
        return PageContainerFrame(p, androidx.compose.ui.geometry.lerp(start, end, p), radius * (1f - p))
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { destination = it }) {
        if (presented) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value * .14f }.background(Color.Black).pointerInput(Unit) {
                awaitEachGesture { do { val event=awaitPointerEvent(); event.changes.forEach { it.consume() } }
                    while(event.changes.any { it.pressed }) }
            })
            Box(Modifier.fillMaxSize().testTag(tag)
                .semantics { this[PageContainerGeometry] = frame(Size(destination?.size?.width?.toFloat() ?: 1f,
                    destination?.size?.height?.toFloat() ?: 1f)) }
                .graphicsLayer {
                    clip = true; shape = ContainerClip(frame(size))
                    alpha = if (source == null) progress.value else 1f
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (progress.value < .999f) event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }) {
                Box(Modifier.fillMaxSize().background(source?.color ?: Color(0xFF0A0A0A)))
                Box(Modifier.fillMaxSize().graphicsLayer {
                    alpha = if (source == null) 1f else ((progress.value - .12f) / .5f).coerceIn(0f, 1f)
                }) { content() }
                source?.let { entry -> entry.layer?.let { layer ->
                    // Reuse the real row's glyphs and layout. A reconstructed ArrowPreference
                    // used different metrics and was composed/disposed at the .25 boundary.
                    // Keep this drawing node alive; only its translation and opacity change.
                    // The fading source needs only one row-sized surface, not a fullscreen
                    // transparent texture. Its dimensions stay fixed throughout the motion.
                    Canvas(Modifier.align(AbsoluteAlignment.TopLeft).requiredSize(
                        with(density){entry.bounds.width.toDp()},with(density){entry.bounds.height.toDp()}
                    ).graphicsLayer {
                        val bounds=frame(Size(destination?.size?.width?.toFloat()?:1f,
                            destination?.size?.height?.toFloat()?:1f)).bounds
                        translationX=bounds.left;translationY=bounds.top
                        alpha=(1f-progress.value/.25f).coerceIn(0f,1f)
                    }.semantics {hideFromAccessibility()}) {
                        drawLayer(layer)
                    }
                } }
            }
        }
    }
    // Consume repeated back presses until the source card has finished returning.
    BackHandler(enabled = !visible && presented) {}
}
