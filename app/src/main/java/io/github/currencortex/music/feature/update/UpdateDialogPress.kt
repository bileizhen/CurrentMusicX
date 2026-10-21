package io.github.currencortex.music.feature.update

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class UpdateDialogPress(val source: MutableInteractionSource,
    val amount: Animatable<Float, AnimationVector1D>)

/** Observe without consuming: a scroll cancels the highlight, while a tap still reaches selectable. */
internal fun Modifier.updateDialogRowPress(feedback: UpdateDialogPress): Modifier = pointerInput(feedback) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val press = PressInteraction.Press(down.position)
        feedback.source.tryEmit(press)
        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
        feedback.source.tryEmit(if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press))
    }
}

/** A quick tap still gets a short pulse; cancelled drags return without firing an action. */
@Composable internal fun rememberUpdateDialogPress(): UpdateDialogPress {
    val feedback = remember { UpdateDialogPress(MutableInteractionSource(), Animatable(0f)) }
    LaunchedEffect(feedback) {
        var pulse: Job? = null
        feedback.source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pulse?.cancel()
                    pulse = launch { feedback.amount.animateTo(1f, tween(65)) }
                }
                is PressInteraction.Release -> {
                    pulse?.cancel()
                    pulse = launch {
                        feedback.amount.animateTo(1f, tween(35))
                        delay(35)
                        feedback.amount.animateTo(0f, tween(180))
                    }
                }
                is PressInteraction.Cancel -> {
                    pulse?.cancel()
                    pulse = launch { feedback.amount.animateTo(0f, tween(100)) }
                }
            }
        }
    }
    return feedback
}
