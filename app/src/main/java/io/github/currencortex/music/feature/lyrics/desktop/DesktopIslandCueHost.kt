package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import kotlinx.coroutines.awaitCancellation

private data class IslandCueSlot(val songIdentity: Any?, val index: Int, val line: LyricLine)

/** Keep the current renderer and one premeasured future cue; promotion retains its layouts and halos. */
@Composable internal fun DesktopIslandCueHost(songId: Any?, index: Int, line: LyricLine,
    nextIndex: Int, nextLine: LyricLine?, style: Any, modifier: Modifier = Modifier,
    content: @Composable (LyricLine) -> Unit) {
    val state = remember(songId) { SubcomposeLayoutState() }
    val current = remember(songId, index, line) { IslandCueSlot(songId, index, line) }
    val next = remember(songId, nextIndex, nextLine) { nextLine?.let { IslandCueSlot(songId, nextIndex, it) } }
    val render = rememberUpdatedState(content)
    val active = rememberUpdatedState(current)
    // A single content factory gives prepared/promoted slots the same composition
    // group keys. A different lambda call site would discard the warmed renderer.
    val renderSlot: (IslandCueSlot) -> (@Composable () -> Unit) = remember(state) {
        { slot -> {
            Box(if (active.value == slot) Modifier else Modifier.clearAndSetSemantics { }) { render.value(slot.line) }
        } }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels
    val prepareWidth = minOf(screenWidth, with(density) { 640.dp.roundToPx() }).coerceAtLeast(1)
    LaunchedEffect(state, current, next, style, prepareWidth, lifecycle) {
        if (next == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Spread composition and shaping over two future frames, away from promotion.
            withFrameNanos { }
            val handle = state.precompose(next, renderSlot(next))
            try {
                withFrameNanos { }
                repeat(handle.placeablesCount) { handle.premeasure(it, Constraints.fixedWidth(prepareWidth)) }
                awaitCancellation()
            } finally { handle.dispose() }
        }
    }
    SubcomposeLayout(state, modifier) { constraints ->
        val children = subcompose(current, renderSlot(current)).map { it.measure(constraints) }
        val width = constraints.constrainWidth(children.maxOfOrNull { it.width } ?: 0)
        val height = constraints.constrainHeight(children.maxOfOrNull { it.height } ?: 0)
        layout(width, height) { children.forEach { it.placeRelative(0, 0) } }
    }
}
