package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/** One finite, window-owned intro per track. Hidden windows suspend it; no lyric timing is invented. */
@Composable internal fun rememberIslandPresentation(songId: Long?, ready: Boolean, motion: Boolean): State<Float> {
    val reveal = remember(songId) { Animatable(if (songId == null || !motion) 1f else 0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(songId, ready, motion, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!motion || songId == null) reveal.snapTo(1f)
            else if (ready && reveal.value < 1f) {
                if (reveal.value == 0f) delay(1200)
                reveal.animateTo(1f, tween(900))
            }
        }
    }
    return reveal.asState()
}

/** Preserve velocity when the next cue changes width before the previous resize finishes. */
@Composable internal fun rememberIslandWidth(target: Float, motion: Boolean): State<Float> =
    animateFloatAsState(target, if (motion) spring(dampingRatio = .95f, stiffness = 260f, visibilityThreshold = .25f)
        else tween(0), label = "island adaptive width")

/** Read animated geometry during layout, without recomposing the lyric renderer each frame. */
internal fun Modifier.islandAnimatedWidth(width: State<Float>): Modifier = layout { measurable, constraints ->
    val pixels = width.value.dp.roundToPx().coerceIn(1, constraints.maxWidth.coerceAtLeast(1))
    val child = measurable.measure(constraints.copy(minWidth = minOf(constraints.minWidth, pixels), maxWidth = pixels))
    layout(child.width, child.height) { child.placeRelative(0, 0) }
}

/** A finite appearance on each visible lifecycle, including returning from settings. */
@Composable internal fun rememberIslandEntrance(motion: Boolean): State<Float> {
    val progress = remember { mutableFloatStateOf(if (motion) 0f else 1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            // Reset synchronously while hidden, before the first frame on return.
            if (event == Lifecycle.Event.ON_STOP) progress.floatValue = 0f
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycle, motion) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            progress.floatValue = if (motion) 0f else 1f
            if (motion) animate(0f, 1f, animationSpec = tween(420, easing = CubicBezierEasing(.22f, 0f, .18f, 1f))) { value, _ ->
                progress.floatValue = value
            }
            awaitCancellation()
        }
    }
    return progress
}
