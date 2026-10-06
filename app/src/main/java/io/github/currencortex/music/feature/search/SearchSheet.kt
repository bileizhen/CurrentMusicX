package io.github.currencortex.music.feature.search

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal class SearchSheetState(initiallyOpen: Boolean = false) {
    val animation = Animatable(if (initiallyOpen) 1f else 0f)
    var source by mutableStateOf<Rect?>(null)
    val progress get() = animation.value
}
internal val SearchSheetProgress = SemanticsPropertyKey<Float>("SearchSheetProgress")
private val easing = CubicBezierEasing(.2f, 0f, 0f, 1f)

/** The home page remains mounted; keyboard insets resize only the search content. */
@Composable internal fun SearchSheetHost(open: Boolean, state: SearchSheetState, predictiveBack: Boolean,
    backEnabled: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val currentOpen by rememberUpdatedState(open)
    LaunchedEffect(open) { state.animation.animateTo(if (open) 1f else 0f, tween(if (open) 420 else 340, easing = easing)) }
    val density = LocalDensity.current
    val imeOpen = WindowInsets.ime.getBottom(density) > 0
    PredictiveBackHandler(enabled = open && backEnabled && !imeOpen && predictiveBack) { events ->
        try {
            state.animation.stop()
            events.collect { state.animation.snapTo(1f - it.progress.coerceIn(0f, 1f)) }
            onBack()
        } catch (cancelled: CancellationException) {
            if (currentOpen) scope.launch { state.animation.animateTo(1f, tween(340, easing = easing)) }
            throw cancelled
        }
    }
    BackHandler(enabled = open && backEnabled && !imeOpen && !predictiveBack, onBack = onBack)
    if (!open && state.progress <= .0001f) return
    BoxWithConstraints(Modifier.fillMaxSize().testTag("search_sheet")
        .semantics { this[SearchSheetProgress] = state.progress; if (!open) hideFromAccessibility() else dismiss { onBack(); true } }
        .pointerInput(open) { awaitPointerEventScope { while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (!open) event.changes.forEach { it.consume() }
        } } }) {
        val height = with(density) { maxHeight.toPx() }
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationY = height * (1f - state.progress)
            shape = RoundedCornerShape(topStart = 30.dp * (1f - state.progress), topEnd = 30.dp * (1f - state.progress))
            clip = true
        }.background(MiuixTheme.colorScheme.background))
        content()
    }
}
