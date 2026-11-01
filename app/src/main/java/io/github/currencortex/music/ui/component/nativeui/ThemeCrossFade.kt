// SPDX-License-Identifier: GPL-3.0-only
// upstream lib/ui/theme_cross_fade.dart @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7.
// Freeze the previously drawn frame, then fade it over the new theme. Content keeps
// its composition and state; translucent pixels use the original dstOut/plus mix.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy as LayerCompositingStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal val ThemeFadeWeight = SemanticsPropertyKey<Float>("ThemeFadeWeight")

@Composable internal fun ThemeCrossFade(themeKey: Any, content: @Composable () -> Unit) {
    val frame = rememberGraphicsLayer()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var drawnKey by remember { mutableStateOf(themeKey) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var snapshotSize by remember { mutableStateOf(IntSize.Zero) }
    val weight = remember { Animatable(0f) }
    LaunchedEffect(themeKey) {
        if (drawnKey == themeKey) return@LaunchedEffect
        if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || frame.size == IntSize.Zero) {
            snapshot = null; drawnKey = themeKey; return@LaunchedEffect
        }
        // Until capture finishes, drawWithContent keeps the last frame without re-recording.
        val image = try { frame.toImageBitmap() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        currentCoroutineContext().ensureActive()
        snapshot = image; snapshotSize = frame.size
        weight.snapTo(if (image == null) 0f else 1f)
        drawnKey = themeKey
        if (image != null) weight.animateTo(0f, tween(200))
        snapshot = null
    }
    Box(Modifier.testTag("theme_cross_fade").semantics { this[ThemeFadeWeight] = weight.value }
        .drawWithContent {
            if (drawnKey == themeKey || frame.size == IntSize.Zero) {
                frame.compositingStrategy = if (snapshot != null) LayerCompositingStrategy.Offscreen else LayerCompositingStrategy.Auto
                frame.record {
                    this@drawWithContent.drawContent()
                    val image = snapshot
                    val alpha = weight.value
                    if (image != null && snapshotSize.width == size.width.toInt() && snapshotSize.height == size.height.toInt() && alpha > 0f) {
                        drawRect(Color.Black.copy(alpha = alpha), blendMode = BlendMode.DstOut)
                        drawImage(image, alpha = alpha, blendMode = BlendMode.Plus, filterQuality = FilterQuality.Low)
                    }
                }
            }
            drawLayer(frame)
        }, propagateMinConstraints = true) { content() }
}
