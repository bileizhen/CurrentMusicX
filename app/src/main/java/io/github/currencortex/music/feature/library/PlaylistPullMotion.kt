package io.github.currencortex.music.feature.library

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.ln

/** Only the song surface moves. The hero and viewport never grow a refresh header. */
internal class PlaylistPullMotion(private val scope: CoroutineScope, private val maximum: Float,
    private val threshold: Float, private val loading: () -> Boolean, private val refresh: () -> Unit) : NestedScrollConnection {
    var offset by mutableFloatStateOf(0f)
        private set
    private var pulled = 0f
    private var dragging = false
    private var rebound: Job? = null

    private fun startDrag() {
        if (dragging) return
        rebound?.cancel()
        pulled = -ln((1 - offset.coerceIn(0f, maximum * .99f) / maximum).coerceAtLeast(.01f)) * maximum / .35f
        dragging = true
    }
    private fun update(delta: Float) {
        pulled = (pulled + delta).coerceIn(0f, threshold * 3)
        offset = maximum * (1 - exp(-pulled * .35f / maximum))
    }
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0 || offset <= 0) return Offset.Zero
        startDrag()
        val consumed = maxOf(available.y, -pulled)
        update(consumed)
        return Offset(0f, consumed)
    }
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0) return Offset.Zero
        startDrag()
        update(available.y)
        return Offset(0f, available.y)
    }
    private fun release(): Boolean {
        if (!dragging) return false
        val wasPulling = pulled > 0
        val shouldRefresh = pulled >= threshold && !loading()
        dragging = false
        pulled = 0f
        rebound?.cancel()
        rebound = scope.launch {
            animate(offset, 0f, animationSpec = spring(dampingRatio = .82f, stiffness = 450f)) { value, _ -> offset = value }
            offset = 0f
        }
        if (shouldRefresh) refresh()
        return wasPulling
    }
    override suspend fun onPreFling(available: Velocity): Velocity =
        if (release() && available.y > 0) Velocity(0f, available.y) else Velocity.Zero
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        release()
        return Velocity.Zero
    }
}

@Composable internal fun rememberPlaylistPullMotion(loading: Boolean, onRefresh: () -> Unit): PlaylistPullMotion {
    val scope = rememberCoroutineScope()
    val currentLoading = rememberUpdatedState(loading)
    val currentRefresh = rememberUpdatedState(onRefresh)
    val maximum = with(LocalDensity.current) { 36.dp.toPx() }
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }
    return remember(scope, maximum, threshold) {
        PlaylistPullMotion(scope, maximum, threshold, { currentLoading.value }, { currentRefresh.value() })
    }
}

internal fun Modifier.playlistPullSurface(motion: PlaylistPullMotion) = graphicsLayer { translationY = motion.offset }
