package io.github.currencortex.music.ui.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
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
        // Main traverses from child to parent. Consuming a small MOVE here would cancel
        // the inner detector in Final, so only cancel motion beyond the platform tap slop.
        awaitPointerEventScope {
            var pointer: PointerId? = null
            var origin = Offset.Zero
            var dragged = false
            while (true) {
                val changes = awaitPointerEvent(PointerEventPass.Main).changes
                if (pointer == null) changes.firstOrNull { it.pressed && !it.previousPressed }?.let {
                    pointer = it.id
                    origin = it.position
                    dragged = false
                }
                val tracked = changes.firstOrNull { it.id == pointer }
                if (changes.count { it.pressed } > 1 ||
                    (tracked != null && (tracked.position - origin).getDistance() > viewConfiguration.touchSlop)) {
                    dragged = true
                }
                changes.forEach {
                    // Down/up remain shielded even between taps; canceled drags stay consumed.
                    if (!it.previousPressed || !it.pressed || dragged) it.consume()
                }
                if (changes.none { it.pressed }) pointer = null
            }
        }
    }.pointerInput(completion) { detectTapGestures(onDoubleTap = { completion() }) }
