package io.github.currencortex.music.feature.visualizer

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.view.Window
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.preferredFrameRate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.core.visualizer.*
import io.github.currencortex.music.data.visualizer.*
import kotlinx.coroutines.flow.collectLatest

internal fun Context.visualizerWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.visualizerWindow()
    else -> null
}

internal fun android.view.View.visualizerWindow(): Window? {
    var node: Any? = this
    while (node is android.view.View) {
        if (node is androidx.compose.ui.window.DialogWindowProvider) return node.window
        node = node.parent
    }
    return context.visualizerWindow()
}

/** One VSync loop per active layer; only the draw lambda reads its high-frequency revision. */
@Composable fun VisualizerRenderLayer(engine: AudioAnalysisEngine, modifier: Modifier = Modifier,
    settings: VisualizerRenderSettings = VisualizerRenderSettings(), visible: Boolean = true,
    render: VisualizerRenderState = remember(engine) { VisualizerRenderState() },
    presetRenderer: io.github.currencortex.music.feature.visualizer.render.VisualizerPresetRenderer? = null) {
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val display = rememberVisualizerDisplay(view)
    val currentSettings = rememberUpdatedState(settings)
    val currentRenderer = rememberUpdatedState(presetRenderer)
    val path = remember { Path() }
    val waveformStroke = remember { Stroke(2f) }
    SideEffect { render.monitor.composition() }
    LaunchedEffect(engine, lifecycle, visible, render) {
        val quality = VisualizerQualityController()
        val clock = VisualizerFrameClock { withFrameNanos { it } }
        try {
            if (!visible) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                quality.reset()
                try {
                    engine.status.collectLatest { status ->
                        val capture = status == CaptureStatus.CAPTURING
                        val fade = status == CaptureStatus.PAUSED && render.frame.audioTimestampNanos > 0
                        if (!capture && !fade) { render.clear(); currentRenderer.value?.reset(); return@collectLatest }
                        if (fade) render.interpolator.pause()
                        render.monitor.resetTiming()
                        render.running = true
                        var previousSettings = currentSettings.value
                        var previousDisplay = display.value
                        var previousQuality = VisualizerQuality.entries[maxOf(quality.quality.ordinal, previousSettings.preferredQuality.ordinal)]
                        var decision = VisualizerFrameRatePolicy.decide(previousSettings, previousDisplay, previousQuality)
                        render.preferredFps = decision.preferenceFps.toFloat()
                        var lastStatistics = 0L
                        var fadeStart = 0L
                        val windowMetrics = view.visualizerWindow()?.let {
                            try { VisualizerWindowMetrics(it, render.monitor) { display.value.refreshRate } }
                            catch (_: RuntimeException) { null } // Vendor metrics failures must not stop drawing.
                        }
                        try {
                            VisualizerRenderLoop(clock).run({ decision.effectiveFps }, render.monitor::tick) { time, dt ->
                                val config = currentSettings.value
                                val device = display.value
                                if (config != previousSettings || device != previousDisplay || maxOf(quality.quality.ordinal, config.preferredQuality.ordinal) != previousQuality.ordinal) {
                                    if (config.frameRate != previousSettings.frameRate || config.automaticOptimization != previousSettings.automaticOptimization) quality.reset()
                                    decision = VisualizerFrameRatePolicy.decide(config, device, VisualizerQuality.entries[maxOf(quality.quality.ordinal, config.preferredQuality.ordinal)])
                                    render.monitor.resetTiming()
                                    previousSettings = config; previousDisplay = device; previousQuality = VisualizerQuality.entries[maxOf(quality.quality.ordinal, config.preferredQuality.ordinal)]
                                    render.preferredFps = decision.preferenceFps.toFloat()
                                }
                                render.quality = previousQuality
                                if (capture) {
                                    val audio = engine.frames.value
                                    render.interpolator.accept(audio)
                                    render.monitor.audio(audio.timestampNanos)
                                }
                                render.interpolator.step(time, dt)
                                currentRenderer.value?.step(render.frame, time, dt, render.quality)
                                render.revision++
                                if (time - lastStatistics >= 500_000_000L) {
                                    val stats = render.monitor.snapshot(time, config, device, decision, render.quality, true)
                                    render.statistics.value = stats
                                    render.audioReadout.value = engine.frames.value.copy(beatPulse = render.frame.pulse)
                                    quality.observe(time, stats, config.automaticOptimization)
                                    lastStatistics = time
                                }
                                if (fadeStart == 0L) fadeStart = time
                                !fade || time - fadeStart < 600_000_000L
                            }
                        } finally { windowMetrics?.close() }
                        render.clear(); currentRenderer.value?.reset()
                    }
                } finally { render.clear(); currentRenderer.value?.reset() }
            }
        } finally { render.clear(); currentRenderer.value?.reset() }
    }
    DisposableEffect(render) { onDispose { render.clear(); currentRenderer.value?.reset() } }
    Canvas(modifier.preferredFrameRate(render.preferredFps)) {
        render.revision // The only 30–120 Hz snapshot read, in the draw phase.
        val started = System.nanoTime()
        val frame = render.frame
        if (presetRenderer != null) {
            with(presetRenderer) { render(frame) }
        } else {
        drawRect(Color(0xFF10121C))
        val values = if (settings.rawSpectrum) frame.rawSpectrum else frame.spectrum
        val stride = render.quality.spectrumStride
        val count = values.size / stride
        val step = size.width / count
        for (i in 0 until count) {
            var level = 0f
            for (j in 0 until stride) level = maxOf(level, values[i * stride + j])
            val height = level * size.height * .65f
            drawRect(Color(0xFF8A7CFF), Offset(i * step, size.height * .65f - height), Size(step * .75f, height))
        }
        path.reset()
        for (i in frame.waveform.indices step render.quality.waveformStride) {
            val x = i * size.width / (frame.waveform.size - 1)
            val y = size.height * (.83f - frame.waveform[i] * .14f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF53D8E8), style = waveformStroke)
        // An immediately triggered, time-decayed pulse; no invented beats between audio packets.
        drawCircle(Color(0xFFFFBE62), size.minDimension * (.025f + .035f * frame.pulse),
            Offset(size.width - 18f, 18f), alpha = .2f + .8f * frame.pulse)
        drawLine(Color(0xFF53D8E8), Offset.Zero, Offset(size.width * frame.rms, 0f), 4f)
        }
        if (render.running) render.monitor.draw(System.nanoTime(), System.nanoTime() - started)
    }
}
