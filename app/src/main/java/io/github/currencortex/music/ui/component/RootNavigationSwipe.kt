package io.github.currencortex.music.ui.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Tab-row drags never wrap around; nested glass-indicator drags keep their own gesture. */
@Composable internal fun rootNavigationSwipe(selected: Int, onSelect: (Int) -> Unit): Modifier {
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    return Modifier.pointerInput(threshold, direction) {
        var dragged = 0f
        detectHorizontalDragGestures(onDragStart = { dragged = 0f }, onDragCancel = { dragged = 0f },
            onDragEnd = {
                if (abs(dragged) >= threshold) {
                    val next = (current + (if (dragged < 0) 1 else -1) * direction).coerceIn(0, 3)
                    if (next != current) select(next)
                }
                dragged = 0f
            }) { change, delta -> change.consume(); dragged += delta }
    }
}
