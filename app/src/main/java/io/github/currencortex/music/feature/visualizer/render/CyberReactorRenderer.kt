package io.github.currencortex.music.feature.visualizer.render

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import io.github.currencortex.music.core.visualizer.*
import io.github.currencortex.music.data.visualizer.VisualizerQuality
import kotlin.math.*

/** Canvas HUD inspired by the supplied video; driven only by the shared render frame. */
internal class CyberReactorRenderer {
    private val contours = arrayOf(192, 144, 96, 64).map(::CyberReactorContour).toTypedArray()
    private val ringPath = Path()
    private val signalPath = Path()
    private val edge = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val halo = Stroke(11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val broadHalo = Stroke(24f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val panelStroke = Stroke(1f)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    fun reset() { contours.forEach { it.reset() }; ringPath.reset(); signalPath.reset() }

    fun draw(scope: DrawScope, frame: VisualizerInterpolatedFrame, state: VisualizerEffectState,
        config: VisualizerEffectConfig, quality: VisualizerQuality, primary: Color, accent: Color,
        embedded: Boolean, reduced: Boolean) = with(scope) {
        val origin = if (embedded) center else Offset(size.width * .32f, size.height * .48f)
        val radius = size.minDimension * if (embedded) .235f else .17f
        val color = lerp(primary, Color.White, .32f)
        val contour = contours[quality.ordinal]
        contour.update(frame, config, reduced)
        ringPath.reset()
        for (i in 0 until contour.count) {
            val reach = radius * contour.radii[i]
            val px = origin.x + contour.x[i] * reach
            val py = origin.y + contour.y[i] * reach
            if (i == 0) ringPath.moveTo(px, py) else ringPath.lineTo(px, py)
        }
        ringPath.close()
        if (quality.ordinal < 2) drawPath(ringPath, primary,
            alpha = (.06f + state.bass * .08f) * config.glowIntensity, style = broadHalo)
        drawPath(ringPath, accent, alpha = .16f * config.glowIntensity, style = halo)
        drawPath(ringPath, color, alpha = .85f, style = edge)
        drawCircle(accent, radius * .95f, origin, alpha = .22f, style = panelStroke)
        // Pooled kick waves and sparks retain their original lifetime and event identity.
        for (i in state.waveAge.indices) if (state.waveAge[i] >= 0f) {
            val age = state.waveAge[i] / .35f
            drawCircle(color, radius * (1.08f + age * .7f), origin,
                alpha = (1f - age) * state.waveStrength[i] * .22f, style = edge)
        }
        for (i in 0 until state.particleCount step if (quality.ordinal >= 2) 2 else 1) {
            val p = origin + Offset(cos(state.particleAngle[i]), sin(state.particleAngle[i])) *
                radius * (1.3f + state.particleRadius[i] * .5f)
            drawCircle(accent, 1.1f, p, alpha = .08f + state.treble * .3f)
        }
        // Fixed circuit routes brighten with energy; no random frame-to-frame positions.
        val circuits = if (quality.ordinal >= 2) 5 else 10
        for (i in 0 until circuits) {
            val x0 = size.width * (.045f + i * .092f)
            val y0 = size.height * (.08f + (i % 3) * .07f)
            signalPath.reset(); signalPath.moveTo(x0, y0)
            signalPath.lineTo(x0 + size.width * .025f, y0)
            signalPath.lineTo(x0 + size.width * .025f, y0 + size.height * .09f)
            signalPath.lineTo(x0 + size.width * .06f, y0 + size.height * .12f)
            drawPath(signalPath, primary, alpha = .06f + state.treble * .06f, style = edge)
            drawCircle(accent, 2f, Offset(x0, y0), alpha = .14f)
        }
        val panelLeft = size.width * if (embedded) .16f else .64f
        val panelTop = size.height * if (embedded) .82f else .20f
        val panelWidth = size.width * if (embedded) .68f else .25f
        val panelHeight = size.height * if (embedded) .13f else .55f
        drawRect(Color(0xFF050E16), Offset(panelLeft,panelTop), Size(panelWidth,panelHeight), alpha = .62f)
        drawRect(accent, Offset(panelLeft,panelTop), Size(panelWidth,panelHeight), alpha = .4f, style = panelStroke)
        if (!embedded) {
            label("AUDIO REACTOR", panelLeft, panelTop - size.height * .035f, color)
            label("LIVE SIGNAL", panelLeft + 6f, panelTop + size.height * .055f, accent)
            for (i in 0..2) drawLine(accent, Offset(panelLeft+6f,panelTop+panelHeight*(.16f+i*.045f)),
                Offset(panelLeft+panelWidth-6f,panelTop+panelHeight*(.16f+i*.045f)), 1f, alpha = .2f)
        }
        val waveY = panelTop + panelHeight * if (embedded) .32f else .47f
        val waveGain = CyberReactorContour.waveformGain(frame.rms) * config.globalIntensity
        if (!embedded) for (i in 0..1) {
            val fraction = if (i == 0) .32f else .63f
            drawLine(accent,Offset(panelLeft,panelTop+panelHeight*fraction),
                Offset(panelLeft+panelWidth,panelTop+panelHeight*fraction),1f,alpha=.28f)
        }
        signalPath.reset()
        val step = if (quality.ordinal < 2) 1 else 2
        if (frame.waveform.size > 1) for (i in frame.waveform.indices step step) {
            val value = CyberReactorContour.displayWaveform(frame.waveform[i],waveGain)
            val x = panelLeft + panelWidth * (.05f + i.toFloat() / (frame.waveform.size - 1) * .9f)
            val y = waveY - value * panelHeight * .16f
            if (i == 0) signalPath.moveTo(x,y) else signalPath.lineTo(x,y)
        }
        drawPath(signalPath, color, style = edge)
        val count = if (quality.ordinal < 2) 40 else 24
        val base = panelTop + panelHeight * .92f
        for (i in 0 until count) {
            val level = VisualizerEffectState.spectrum(frame.spectrum, i.toFloat()/(count-1),
                config.spectrumSensitivity * config.globalIntensity)
            val x = panelLeft + panelWidth * (.05f + (i+.5f)/count*.9f)
            drawLine(lerp(primary,color,i.toFloat()/count), Offset(x,base),
                Offset(x,base-level*panelHeight*.31f), panelWidth/count*.55f, alpha = .85f)
        }
        // Tall electric rail from the video, using the actual waveform rather than a sine oscillator.
        val railX = size.width * if (embedded) .94f else .95f
        val top = size.height * .18f; val bottom = size.height * .77f
        drawLine(accent, Offset(railX,top),Offset(railX,bottom),1f,alpha=.15f)
        signalPath.reset()
        if (frame.waveform.size > 1) for (i in frame.waveform.indices step step) {
            val value = CyberReactorContour.displayWaveform(frame.waveform[i],waveGain)
            val x = railX + value * size.width * .018f
            val y = top + (bottom-top)*i/(frame.waveform.size-1)
            if (i==0) signalPath.moveTo(x,y) else signalPath.lineTo(x,y)
        }
        drawPath(signalPath,accent,alpha=.18f*config.glowIntensity,style=halo)
        drawPath(signalPath,color,alpha=.7f,style=edge)
        drawCircle(color,size.minDimension*.009f,Offset(railX,top),alpha=.7f)
        drawCircle(color,size.minDimension*.009f,Offset(railX,bottom),alpha=.7f)
    }
    private fun DrawScope.label(text: String,x: Float,y: Float,color: Color) {
        ink.color=color.toArgb(); ink.textSize=size.minDimension*.016f
        drawIntoCanvas { it.nativeCanvas.drawText(text,x,y,ink) }
    }
}
