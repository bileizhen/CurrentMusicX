package io.github.currencortex.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
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
    var profileLanding by mutableStateOf<Rect?>(null)
    var url by mutableStateOf<String?>(null)
    var decorationUrl by mutableStateOf<String?>(null)
    var decorationScale by mutableStateOf(1.0)
    var returning by mutableStateOf(false)
    var homeOrigin: (() -> AvatarFlightOrigin?)? = null
    var profileOrigin: (() -> AvatarFlightOrigin?)? = null
    // Updated by the render layer without invalidating composition on every frame.
    internal var renderedBounds: Rect? = null
    private fun flyingOrigin(): AvatarFlightOrigin? = if (active)
        (renderedBounds ?: source)?.let { AvatarFlightOrigin(url, it, decorationUrl, decorationScale) } else null
    fun beginFromHome() {
        val origin = flyingOrigin() ?: homeOrigin?.invoke()?.takeIf { homeLanding != null }
        if (origin == null) cancel() else begin(origin, returning = false, landing = profileLanding)
    }
    fun beginFromProfile() {
        val landing = homeLanding
        val origin = flyingOrigin() ?: profileOrigin?.invoke()
        if (landing == null || origin == null) cancel() else begin(origin, returning = true, landing = landing)
    }
    fun cancel() {
        if (active) { request++; active = false }
        renderedBounds = null
    }
    internal fun finish(completedRequest: Int) {
        if (request == completedRequest) { active = false; renderedBounds = null }
    }
    private fun begin(origin: AvatarFlightOrigin, returning: Boolean, landing: Rect? = null) {
        this.returning = returning
        url = origin.url; source = origin.bounds
        renderedBounds = origin.bounds
        decorationUrl = origin.decorationUrl; decorationScale = origin.decorationScale
        destination = landing; request++; active = true
    }
}
val LocalAvatarFlight = staticCompositionLocalOf<AvatarFlightState?> { null }

/** One retained image; only GPU position/scale changes while the destination layout stays fixed. */
@Composable fun AvatarFlightOverlay(state: AvatarFlightState) {
    if (!state.active) return
    val request = state.request
    val source = remember(request) { state.source }
    val progress = remember(request) { Animatable(0f) }
    var target by remember(request) { mutableStateOf<Rect?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(request) {
        target = withTimeoutOrNull(700) { snapshotFlow { state.destination }.first { it != null } }
        if (target != null) progress.animateTo(1f, tween(540, easing = CubicBezierEasing(.25f, .1f, .25f, 1f)))
        state.finish(request)
    }
    // Visual only: navigation and page gestures must stay interactive during a flight.
    Box(Modifier.fillMaxSize().testTag("home_avatar_transition")
        .onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }) {
        AvatarFlightPicture(state.url, Modifier.size(64.dp).graphicsLayer {
            val from = source ?: return@graphicsLayer
            val to = target ?: from
            val t = progress.value; val q = 1f - t
            val start = from.center; val end = to.center
            val control = Offset((start.x + end.x) / 2, (start.y + end.y) / 2 - minOf(96.dp.toPx(), abs(start.x - end.x) * .28f))
            val point = start * (q * q) + control * (2 * q * t) + end * (t * t)
            translationX = point.x - rootOrigin.x - size.width / 2
            translationY = point.y - rootOrigin.y - size.height / 2
            val diameter = from.width + (to.width - from.width) * t
            if (state.request == request) state.renderedBounds =
                Rect(point - Offset(diameter / 2, diameter / 2), point + Offset(diameter / 2, diameter / 2))
            scaleX = diameter / size.width; scaleY = scaleX
        }, state.decorationUrl, state.decorationScale)
    }
}
