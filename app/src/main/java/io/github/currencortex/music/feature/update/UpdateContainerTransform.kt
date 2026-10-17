package io.github.currencortex.music.feature.update

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
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
import io.github.currencortex.music.feature.settings.SettingIcon
import io.github.currencortex.music.ui.component.MusicIcons
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference

/** A live source coordinate is converted into the destination's local space, including UI scaling. */
data class UpdatePageOrigin(val coordinates: LayoutCoordinates, val color: Color,
    val title: String, val summary: String)

internal data class UpdateTransformFrame(val progress: Float, val bounds: Rect, val radius: Float)
internal val UpdateTransformGeometry = SemanticsPropertyKey<UpdateTransformFrame>("UpdateTransformGeometry")
private data class CapturedOrigin(val bounds: Rect, val color: Color, val title: String, val summary: String)
private class ContainerClip(private val frame: UpdateTransformFrame) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(frame.bounds, CornerRadius(frame.radius)))
}

@Composable internal fun UpdateContainerTransform(visible: Boolean, origin: UpdatePageOrigin?,
    onPresentedChanged: (Boolean) -> Unit = {}, content: @Composable () -> Unit) {
    val expansion = remember { Animatable(0f) }
    val progress = expansion.asState()
    LaunchedEffect(visible) {
        expansion.animateTo(if (visible) 1f else 0f,
            tween(if (visible) 600 else 460, easing = FastOutSlowInEasing))
    }
    val showSource by remember { derivedStateOf { progress.value < .25f } }
    val presented by remember(visible) { derivedStateOf { visible || expansion.value > .0001f || expansion.isRunning } }
    val report by rememberUpdatedState(onPresentedChanged)
    SideEffect { report(presented) }
    DisposableEffect(Unit) { onDispose { report(false) } }
    var destination by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lastOrigin by remember { mutableStateOf<CapturedOrigin?>(null) }
    var captured by remember(visible) { mutableStateOf<CapturedOrigin?>(null) }
    val source = if (visible) captured ?: run {
        val target = destination
        if (origin?.coordinates?.isAttached == true && target?.isAttached == true)
            runCatching { target.localBoundingBoxOf(origin.coordinates, clipBounds = true) }.getOrNull()
                ?.takeIf { it.width > 1f && it.height > 1f && it.overlaps(Rect(Offset.Zero,
                    Size(target.size.width.toFloat(), target.size.height.toFloat()))) }
                ?.let { CapturedOrigin(it, origin.color, origin.title, origin.summary) }
        else null
    } else lastOrigin
    SideEffect { if (visible && captured == null) { captured = source; lastOrigin = source } }
    val density = LocalDensity.current
    val radius = with(density) { 26.dp.toPx() }
    fun frame(size: Size): UpdateTransformFrame {
        val p = progress.value
        val end = Rect(Offset.Zero, size)
        val start = source?.bounds ?: end
        return UpdateTransformFrame(p, androidx.compose.ui.geometry.lerp(start, end, p), radius * (1f - p))
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { destination = it }) {
        if (presented) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value * .14f }.background(Color.Black))
            Box(Modifier.fillMaxSize().testTag("update_container_transform")
                .semantics { this[UpdateTransformGeometry] = frame(Size(destination?.size?.width?.toFloat() ?: 1f,
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
                source?.takeIf { showSource }?.let { entry ->
                    Card(Modifier.requiredWidth(with(density) { entry.bounds.width.toDp() })
                        .requiredHeight(with(density) { entry.bounds.height.toDp() })
                        .graphicsLayer {
                            val bounds = frame(Size(destination?.size?.width?.toFloat() ?: 1f,
                                destination?.size?.height?.toFloat() ?: 1f)).bounds
                            translationX = bounds.left; translationY = bounds.top
                            alpha = (1f - progress.value / .25f).coerceIn(0f, 1f)
                        }.semantics { hideFromAccessibility() }) {
                        ArrowPreference(entry.title, summary = entry.summary,
                            startAction = { SettingIcon(MusicIcons.Download) }, onClick = {})
                    }
                }
            }
        }
    }
    // Consume repeated back presses until the source card has finished returning.
    BackHandler(enabled = !visible && presented) {}
}
