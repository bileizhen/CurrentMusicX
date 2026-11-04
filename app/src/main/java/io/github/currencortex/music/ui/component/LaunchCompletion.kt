package io.github.currencortex.music.ui.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

@Stable
internal class LaunchCompletion(private val onFinished: () -> Unit) {
    var finished by mutableStateOf(false)
        private set

    operator fun invoke() {
        if (finished) return
        finished = true
        onFinished()
    }
}

@Composable
internal fun rememberLaunchCompletion(onFinished: () -> Unit): LaunchCompletion {
    val latest by rememberUpdatedState(onFinished)
    return remember { LaunchCompletion { latest() } }
}

/** Consume the gesture before dismissing, so the second tap never reaches the page underneath. */
internal fun Modifier.skipLaunchOnDoubleTap(completion: LaunchCompletion): Modifier =
    pointerInput(Unit) {
        // Main traverses from child to parent: let the inner tap detector read each event,
        // then shield ancestors even while that detector waits between taps or cancels a drag.
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
        }
    }.pointerInput(completion) { detectTapGestures(onDoubleTap = { completion() }) }
