package io.github.currencortex.music.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.currencortex.music.data.auth.Account
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** Account storage is restored before the name reveal; never consult a separate online service. */
internal fun launchUsername(account: Account?): String =
    account?.username?.trim()?.takeIf(String::isNotEmpty)
        ?: account?.nickname?.trim()?.takeIf(String::isNotEmpty) ?: "欢迎聆听"

/** Consume input while disk settings select the actual animation, before either timeline mounts. */
@Composable internal fun LaunchPreparationOverlay() {
    val background = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background
    BackHandler { }
    Canvas(Modifier.fillMaxSize().testTag("launch_preparing").pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
    }) { drawRect(background) }
}

internal const val HALO_INTRO_MS = 8500

/** One display-synchronised clock; artwork is prepared off the UI thread before it starts. */
@Composable fun HaloLaunchOverlay(username: String, ready: Boolean, windowReady: Boolean = true,
    preview: Boolean = false, onFinished: () -> Unit) {
    val time = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val currentReady by rememberUpdatedState(ready)
    val currentWindowReady by rememberUpdatedState(windowReady)
    val finish by rememberUpdatedState(onFinished)
    var prepared by remember { mutableStateOf(false) }
    var reduced by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { prepared }.first { it }
        withTimeoutOrNull(600) { snapshotFlow { currentWindowReady }.first { it } }
        reduced = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f
        if (reduced) time.snapTo(7000f)
        else time.animateTo(HALO_INTRO_MS.toFloat(), tween(HALO_INTRO_MS, easing = LinearEasing))
        withTimeoutOrNull(2000) { snapshotFlow { currentReady }.first { it } }
        if (!reduced) exit.animateTo(1f, tween(240))
        finish()
    }
    BackHandler { if (preview) finish() }
    Box(Modifier.fillMaxSize().testTag("launch_animation").graphicsLayer { alpha = 1f - exit.value }
        .pointerInput(Unit) { awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        } }) {
        HaloLaunchFrame(username, { time.value }, reduced, onPrepared = { prepared = true })
    }
}

/** Deterministic frames share the production renderer, including dynamic Unicode usernames. */
@Composable internal fun HaloLaunchFrame(username: String, time: () -> Float,
    reducedMotion: Boolean = false, onPrepared: () -> Unit = {}) {
    val resources = LocalResources.current
    val customTypeface = io.github.currencortex.music.ui.theme.LocalCustomTypeface.current
    val name = remember(username) {
        username.replace(Regex("[\\r\\n\\t]+"), " ").trim().ifEmpty { "欢迎聆听" }
    }
    val renderer by produceState<HaloLaunchRenderer?>(null, resources, name, customTypeface) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            HaloLaunchRenderer(resources, name, customTypeface)
        }
    }
    val prepared by rememberUpdatedState(onPrepared)
    LaunchedEffect(renderer) { if (renderer != null) prepared() }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().testTag("halo_launch_frame")
            .semantics { contentDescription = "CurrentMusic · $name" }) {
            drawRect(Color.Black)
            renderer?.draw(drawContext.canvas.nativeCanvas, size.width, size.height,
                if (reducedMotion) 7000f else time())
        }
        if (renderer != null) Box(Modifier.testTag("halo_artwork_ready"))
    }
}
