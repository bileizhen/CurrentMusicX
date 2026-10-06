package io.github.currencortex.music.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

data class AvatarFlightOrigin(val url: String?, val bounds: Rect, val decorationUrl: String?, val decorationScale: Double)

@Stable class AvatarFlightState {
    var active by mutableStateOf(false)
    var request by mutableIntStateOf(0)
    var source by mutableStateOf<Rect?>(null)
    var destination by mutableStateOf<Rect?>(null)
    // Stationary home slot, retained across pager disposal only while its list header is visible.
    var homeLanding by mutableStateOf<Rect?>(null)
    var url by mutableStateOf<String?>(null)
    var decorationUrl by mutableStateOf<String?>(null)
    var decorationScale by mutableStateOf(1.0)
    var returning by mutableStateOf(false)
    var homeOrigin: (() -> AvatarFlightOrigin?)? = null
    var profileOrigin: (() -> AvatarFlightOrigin?)? = null
    fun beginFromHome() {
        if (homeLanding == null) return
        homeOrigin?.invoke()?.let { begin(it, returning = false) }
    }
    fun beginFromProfile() {
        val landing = homeLanding ?: return
        profileOrigin?.invoke()?.let { begin(it, returning = true, landing = landing) }
    }
    private fun begin(origin: AvatarFlightOrigin, returning: Boolean, landing: Rect? = null) {
        if (active) return
        this.returning = returning
        url = origin.url; source = origin.bounds
        decorationUrl = origin.decorationUrl; decorationScale = origin.decorationScale
        destination = landing; request++; active = true
    }
}
val LocalAvatarFlight = staticCompositionLocalOf<AvatarFlightState?> { null }

/** One retained image; only GPU position/scale changes while the destination layout stays fixed. */
@Composable fun AvatarFlightOverlay(state: AvatarFlightState, onCancel: () -> Unit) {
    if (!state.active) return
    val progress = remember(state.request) { Animatable(0f) }
    var target by remember(state.request) { mutableStateOf<Rect?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(state.request) {
        target = withTimeoutOrNull(700) { snapshotFlow { state.destination }.first { it != null } }
        if (target != null) progress.animateTo(1f, tween(540, easing = CubicBezierEasing(.25f, .1f, .25f, 1f)))
        state.active = false
    }
    BackHandler(onBack = onCancel)
    Box(Modifier.fillMaxSize().testTag("home_avatar_transition")
        .onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }
        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } } }) {
        AvatarFlightPicture(state.url, Modifier.size(64.dp).graphicsLayer {
            val from = state.source ?: return@graphicsLayer
            val to = target ?: from
            val t = progress.value; val q = 1f - t
            val start = from.center; val end = to.center
            val control = Offset((start.x + end.x) / 2, (start.y + end.y) / 2 - minOf(96.dp.toPx(), abs(start.x - end.x) * .28f))
            val point = start * (q * q) + control * (2 * q * t) + end * (t * t)
            translationX = point.x - rootOrigin.x - size.width / 2
            translationY = point.y - rootOrigin.y - size.height / 2
            val diameter = from.width + (to.width - from.width) * t
            scaleX = diameter / size.width; scaleY = scaleX
        }, state.decorationUrl, state.decorationScale)
    }
}
