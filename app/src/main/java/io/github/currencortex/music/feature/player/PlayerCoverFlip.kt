package io.github.currencortex.music.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.coverRequestUrl
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Cover URLs and other refreshed metadata must not trigger another turn.
internal fun Song.coverFlipKey() = listOf(musicSource, id, privateIdentity, emby?.id,
    navidrome?.id, localUri)

internal class PlayerCoverFlipState(song: Song?) {
    var latest by mutableStateOf(song)
    var shown by mutableStateOf(song)
        private set
    val cover: String get() =
        (if (shown?.coverFlipKey() == latest?.coverFlipKey()) latest else shown)?.cover.orEmpty()
    val angle = Animatable(0f)
    val lift = Animatable(0f)

    /** Cancellation retains the visible face and pose so the next song can take over. */
    suspend fun show(song: Song?, animate: Boolean) {
        latest = song
        if (!animate || shown == null || song == null) {
            shown = song
            angle.snapTo(0f)
            lift.snapTo(0f)
            return
        }
        if (shown?.coverFlipKey() != song.coverFlipKey()) {
            lift.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
            angle.animateTo(90f, tween(180, easing = FastOutSlowInEasing))
            // Both faces are edge-on here. Never display a mirrored cover.
            shown = song
            angle.snapTo(-90f)
        } else shown = song
        coroutineScope {
            launch { angle.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
            launch { lift.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
        }
    }
}

internal val LocalPlayerCoverFlip = staticCompositionLocalOf<PlayerCoverFlipState?> { null }

@Composable internal fun rememberPlayerCoverFlip(song: Song?, enabled: Boolean): PlayerCoverFlipState {
    val state = remember { PlayerCoverFlipState(song) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = enabled && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val latestSong by rememberUpdatedState(song)
    SideEffect { state.latest = song }
    LaunchedEffect(song?.coverFlipKey(), active) { state.show(latestSong, active) }

    // Start the incoming request during the lift, using the same cache entry as MusicCover.
    val context = LocalContext.current
    DisposableEffect(context, song?.cover, active) {
        val request = if (active && !song?.cover.isNullOrBlank()) SingletonImageLoader.get(context)
            .enqueue(ImageRequest.Builder(context).data(coverRequestUrl(song.cover, 800)).size(800).build()) else null
        onDispose { request?.dispose() }
    }
    return state
}

/** Applied inside the shared positioning layer, outside the existing record rotation. */
internal fun Modifier.playerCoverFlip(state: PlayerCoverFlipState?): Modifier =
    if (state == null) this else graphicsLayer {
        val raised = state.lift.value
        translationY = -minOf(24.dp.toPx(), size.height * .075f) * raised
        scaleX = 1f + .035f * raised
        scaleY = scaleX
        rotationX = -7f * raised
        rotationY = state.angle.value
        cameraDistance = 12f * size.maxDimension
    }
