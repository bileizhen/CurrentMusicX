package io.github.currencortex.music.feature.visualizer

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.currencortex.music.core.visualizer.*
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.feature.visualizer.render.VisualizerPresetRenderer
import io.github.currencortex.music.ui.component.coverRequestUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

internal val LocalPlayerVisualizerObserver = staticCompositionLocalOf<(VisualizerRenderState, VisualizerPresetRenderer?) -> Unit> { { _, _ -> } }

/** The original artwork owns its image and transitions; this layer only surrounds it. */
@Composable internal fun PlayerVisualizerArtwork(vm: PlayerViewModel, visible: Boolean,
    modifier: Modifier = Modifier, artwork: @Composable BoxScope.() -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val render = remember(vm) { VisualizerRenderState() }
    val owner = remember(vm) { Any() }
    val renderer = remember(settings.visualizerEffects.presetId) { VisualizerPresetRenderer(settings.visualizerEffects.presetId) }
    var palette by remember(song?.cover) { mutableStateOf<VisualizerArtworkPalette?>(null) }
    val url = song?.cover.orEmpty()
    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        palette = withContext(Dispatchers.IO) {
            val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context)
                .data(coverRequestUrl(url, 144)).size(144).allowHardware(false).build()) as? SuccessResult
            result?.image?.toBitmap()?.let { bitmap ->
                val pixels = IntArray(144)
                for (y in 0 until 12) for (x in 0 until 12)
                    pixels[y * 12 + x] = bitmap.getPixel(x * bitmap.width / 12, y * bitmap.height / 12)
                VisualizerArtworkPalette.fromPixels(pixels)
            }
        }
    }
    SideEffect {
        renderer.config = settings.visualizerEffects
        renderer.updateArtworkPalette(palette)
        renderer.forceCanvas = true
        renderer.embeddedArtwork = true
        renderer.systemReduceMotion = !ValueAnimator.areAnimatorsEnabled()
    }
    LaunchedEffect(lifecycle, visible, settings.visualizerEnabled) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.visualizerVisible(owner, visible && settings.visualizerEnabled,
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            try { awaitCancellation() } finally { vm.visualizerVisible(owner, false, false) }
        }
    }
    val observer = LocalPlayerVisualizerObserver.current
    DisposableEffect(renderer, observer) {
        observer(render, renderer)
        onDispose { renderer.close(); observer(render, null) }
    }
    DisposableEffect(vm, owner) { onDispose { vm.visualizerVisible(owner, false, false) } }
    val preference = remember(view) { view.visualizerWindow()?.let { window ->
        VisualizerRefreshPreference({ window.attributes.preferredRefreshRate }) { value ->
            try { window.attributes = window.attributes.apply { preferredRefreshRate = value } }
            catch (_: RuntimeException) { /* A display hint must not affect playback. */ }
        }
    } }
    SideEffect { preference?.update(if (visible) render.preferredFps else 0f, view.display?.refreshRate ?: 60f) }
    DisposableEffect(preference) { onDispose { preference?.close() } }
    val ring = remember { Stroke(2f) }
    Box(modifier, contentAlignment = Alignment.Center) {
        VisualizerRenderLayer(vm.audioAnalysis, Modifier.matchParentSize().clipToBounds().testTag("player_visualizer")
            .graphicsLayer { alpha = if (visible) 1f else 0f }, settings.visualizerRender,
            visible && settings.visualizerEnabled, render, renderer, drawPreset = { frame ->
                if (renderer.active) {
                    withTransform({ scale(1.1f, 1.1f, center) }) { with(renderer) { render(frame) } }
                    drawCircle(Color(palette?.accent ?: 0xFF9D78ED.toInt()), size.minDimension * .266f,
                        alpha = (.16f + frame.bass * .25f) * settings.visualizerEffects.glowIntensity, style = ring)
                }
            })
        artwork()
    }
}
