package io.github.currencortex.music.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.currencortex.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

internal const val SIGNATURE_DURATION_MS = 2100L

/** A fixed wall-clock budget, independent of network readiness and system animator multipliers. */
@Composable fun SignatureLaunchOverlay(username: String, windowReady: Boolean = true,
    preview: Boolean = false, onFinished: () -> Unit) {
    // Snapshot the restored account name for this one opening; account updates cannot restart it.
    val name = remember { signatureName(username) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    var prepared by remember { mutableStateOf(false) }
    var reduced by remember { mutableStateOf(false) }
    val currentWindowReady by rememberUpdatedState(windowReady)
    val finish = rememberLaunchCompletion(onFinished)
    LaunchedEffect(finish.finished) {
        if (finish.finished) return@LaunchedEffect
        reduced = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f
        // Covers native splash gating AND asset preparation, including an unexpected resource failure.
        withTimeoutOrNull(2600L) {
            withTimeoutOrNull(450L) { snapshotFlow { prepared && currentWindowReady }.first { it } }
            if (reduced) {
                elapsed = 1600f
                withFrameNanos { }
            } else {
                val start = withFrameNanos { it }
                while (elapsed < SIGNATURE_DURATION_MS) {
                    withFrameNanos { elapsed = ((it - start) / 1_000_000f).coerceAtMost(SIGNATURE_DURATION_MS.toFloat()) }
                }
            }
        }
        finish()
    }
    BackHandler { if (preview) finish() }
    Box(Modifier.fillMaxSize().testTag("launch_animation")
        .graphicsLayer {
            val exit = ((elapsed - 1800f) / 300f).coerceIn(0f, 1f)
            alpha = 1f - exit * exit * (3f - 2f * exit)
        }
        .skipLaunchOnDoubleTap(finish)) {
        SignatureLaunchFrame(name, { if (reduced) 1600f else elapsed }, onPrepared = { prepared = true })
    }
}

/** Used by production, settings thumbnails and deterministic visual tests. */
@Composable internal fun SignatureLaunchFrame(username: String, time: () -> Float,
    modifier: Modifier = Modifier, onPrepared: () -> Unit = {}) {
    val context = LocalContext.current
    val name = remember(username) { signatureName(username) }
    val renderer by produceState<SignatureLaunchRenderer?>(null, context, name) {
        value = null
        value = withContext(Dispatchers.Default) {
            try {
                val (latin, chinese) = SignatureGlyphs.load(context.assets, name)
                SignatureLaunchRenderer(name, latin, chinese, context.resources.getFont(R.font.lxgw_wenkai_regular))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A corrupt/missing optional artwork asset must never prevent opening the app.
                SignatureLaunchRenderer(name, org.json.JSONObject(), null)
            }
        }
    }
    val prepared by rememberUpdatedState(onPrepared)
    LaunchedEffect(renderer) { if (renderer != null) prepared() }
    Canvas(modifier.fillMaxSize().testTag("signature_launch_frame")
        .semantics { contentDescription = "CurrentMusic · $name" }) {
        drawRect(Color(0xFF05070C))
        renderer?.draw(drawContext.canvas.nativeCanvas, size.width, size.height, time())
    }
}
