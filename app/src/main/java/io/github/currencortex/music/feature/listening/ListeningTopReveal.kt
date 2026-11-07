package io.github.currencortex.music.feature.listening

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.currencortex.music.ui.component.LocalMusicPageActive

internal data class ListeningTopReveal(val progress: Float, val trigger: Modifier)

/** The original observes a one-pixel anchor once, then starts the reveal 200ms later. */
@Composable internal fun rememberListeningTopReveal(vararg keys: Any?): ListeningTopReveal {
    var completed by rememberSaveable(*keys) { mutableStateOf(false) }
    val progress = remember(*keys) { Animatable(if (completed) 1f else 0f) }
    var entered by remember(*keys) { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = LocalMusicPageActive.current && LocalListeningMotionEnabled.current &&
        lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val trigger = Modifier.onGloballyPositioned { coordinates ->
        val visible = coordinates.boundsInWindow()
        val origin = coordinates.positionInWindow()
        if (origin.x >= visible.left && origin.x+1f <= visible.right &&
            origin.y >= visible.top && origin.y+1f <= visible.bottom) entered = true
    }
    // Latch entry: scrolling the anchor out again must not cancel or replay the animation.
    LaunchedEffect(progress, active, entered) {
        if (active && entered && !completed) {
            progress.animateTo(1f, tween(((1f-progress.value)*600).toInt(),
                delayMillis=if(progress.value==0f) 200 else 0, easing=LinearEasing))
            completed = true
        }
    }
    return ListeningTopReveal(progress.value, trigger)
}
