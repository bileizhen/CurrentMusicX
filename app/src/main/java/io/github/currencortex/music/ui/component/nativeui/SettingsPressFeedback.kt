// SPDX-License-Identifier: GPL-3.0-only
// upstream CommonCard -> FilledButton/InkWell with material_ui 1.5.0 InkSparkle.
package io.github.currencortex.music.ui.component.nativeui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.interaction.*
import androidx.compose.runtime.*
import android.content.res.AssetManager
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

internal data class SettingsInkColors(val splash: Color, val highlight: Color, val hover: Color, val focus: Color)

/** CommonCard's foregroundColor derives FilledButton.styleFrom's .10/.08/.10 overlays. */
@Composable internal fun settingsCardInkColors(): SettingsInkColors {
    val color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    return SettingsInkColors(color.copy(alpha = .1f), color.copy(alpha = .1f), color.copy(alpha = .08f), color.copy(alpha = .1f))
}

/** Palette InkWell inherits these ThemeData colors, rather than FilledButton's. */
@Composable internal fun settingsInkWellColors(): SettingsInkColors {
    val dark = MiuixTheme.colorScheme.onSurface.luminance() > .5f
    val ink = if (dark) Color.White else Color.Black
    return SettingsInkColors(if (dark) Color(0x40CCCCCC) else Color(0x66C8C8C8),
        if (dark) Color(0x40CCCCCC) else Color(0x66BCBCBC), ink.copy(alpha = .04f), ink.copy(alpha = .12f))
}

private class InkWave(val origin: Offset, val color: Color, val seed: Float) { val progress = Animatable(0f) }

internal object SettingsInkSource {
    @Volatile private var cached: String? = null
    fun read(assets: AssetManager): String = cached ?: synchronized(this) {
        cached ?: assets.open("shaders/ink_sparkle.agsl").bufferedReader().use { it.readText() }.also { cached = it }
    }
}

@Composable internal fun Modifier.settingsPressFeedback(interactions: MutableInteractionSource,
    colors: SettingsInkColors = settingsCardInkColors()): Modifier {
    val waves = remember { mutableStateListOf<InkWave>() }
    val pressed = remember { Animatable(0f) }
    val hovered = remember { Animatable(0f) }
    val focused = remember { Animatable(0f) }
    val currentColors by rememberUpdatedState(colors)
    val assets = LocalContext.current.assets
    val painter = remember(assets) { InkSparklePainter(SettingsInkSource.read(assets)) }
    LaunchedEffect(interactions) {
        var pressMotion: Job? = null
        var hoverMotion: Job? = null
        var focusMotion: Job? = null
        val presses = mutableSetOf<PressInteraction.Press>()
        suspend fun highlight(amount: Animatable<Float, AnimationVector1D>, target: Float, duration: Int) {
            amount.animateTo(target, tween((duration * abs(target - amount.value)).roundToInt(), easing = LinearEasing))
        }
        interactions.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    presses += event
                    val wave = InkWave(event.pressPosition, currentColors.splash, Random.nextFloat() * 1000f)
                    waves += wave
                    launch {
                        try { wave.progress.animateTo(1f, tween(617, easing = LinearEasing)) }
                        finally { waves.remove(wave) }
                    }
                    pressMotion?.cancel(); pressMotion = launch { highlight(pressed, 1f, 200) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val press = when (event) {
                        is PressInteraction.Release -> event.press
                        is PressInteraction.Cancel -> event.press
                        else -> error("unreachable")
                    }
                    presses -= press
                    if (presses.isEmpty()) {
                        pressMotion?.cancel(); pressMotion = launch { highlight(pressed, 0f, 200) }
                    }
                    // InkSparkle does not override confirm/cancel: the splash keeps
                    // its 617ms timeline; release reverses only InkHighlight.
                }
                is HoverInteraction.Enter, is HoverInteraction.Exit -> {
                    hoverMotion?.cancel(); hoverMotion = launch { highlight(hovered, if (event is HoverInteraction.Enter) 1f else 0f, 50) }
                }
                is FocusInteraction.Focus, is FocusInteraction.Unfocus -> {
                    focusMotion?.cancel(); focusMotion = launch { highlight(focused, if (event is FocusInteraction.Focus) 1f else 0f, 50) }
                }
            }
        }
    }
    return drawWithContent {
        clipRect {
            if (hovered.value > 0) drawRect(colors.hover.copy(alpha = colors.hover.alpha * hovered.value))
            if (focused.value > 0) drawRect(colors.focus.copy(alpha = colors.focus.alpha * focused.value))
            if (pressed.value > 0) drawRect(colors.highlight.copy(alpha = colors.highlight.alpha * pressed.value))
            waves.forEach { wave -> painter.draw(this, wave.origin, wave.color, wave.seed, wave.progress.value,
                native = Build.VERSION.SDK_INT >= 33 && drawContext.canvas.nativeCanvas.isHardwareAccelerated) }
        }
        // Material ink is behind text, thumbnails and opaque palette dots.
        drawContent()
    }
}
