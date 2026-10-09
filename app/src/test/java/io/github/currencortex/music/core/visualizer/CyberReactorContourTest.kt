package io.github.currencortex.music.core.visualizer

import org.junit.Assert.*
import org.junit.Test

class CyberReactorContourTest {
    private val config = VisualizerEffectConfig(enabled = true, presetId = VisualizerPreset.CYBER_REACTOR)
    @Test fun silenceRemainsCircleWithoutInventedSpikes() {
        val contour = CyberReactorContour(192)
        contour.update(VisualizerInterpolatedFrame(), config, false)
        assertTrue(contour.radii.all { it == 1.025f })
    }
    @Test fun realSignalProducesBoundedSpikesAndReducedMotionAttenuatesThem() {
        val frame = VisualizerInterpolatedFrame().apply { bass = .6f; treble = .4f; pulse = 1f; spectrum.fill(.4f); waveform.fill(.5f) }
        val normal = CyberReactorContour(192); val reduced = CyberReactorContour(192)
        normal.update(frame, config, false); reduced.update(frame, config, true)
        assertTrue(normal.radii.max() > 1.4f)
        assertTrue(normal.radii.all { it in 1.025f..1.8f })
        assertTrue(reduced.radii.max() < normal.radii.max())
        assertTrue(reduced.radii.max() < 1.2f)
        assertEquals(.4f, frame.spectrum[0], 0f); assertEquals(.5f, frame.waveform[0], 0f)
    }
    @Test fun malformedInputAndEveryQualityRemainFiniteAndReuseOutput() {
        for (count in listOf(192,144,96,64)) {
            val contour = CyberReactorContour(count); val output = contour.radii
            val frame = VisualizerInterpolatedFrame(floatArrayOf(Float.NaN,Float.POSITIVE_INFINITY),waveform=floatArrayOf(Float.NaN))
            contour.update(frame,config,false)
            assertSame(output,contour.radii); assertTrue(output.all(Float::isFinite))
            contour.reset(); assertTrue(output.all { it == 1.025f })
        }
    }
    @Test fun presetPersistsByStableNameAndUnknownNamesKeepDefault() {
        assertEquals(VisualizerPreset.CYBER_REACTOR,VisualizerPreset.from("CYBER_REACTOR"))
        assertEquals(VisualizerPreset.ORBIT_SPECTRUM,VisualizerPreset.from("unknown"))
    }
    @Test fun displayGainKeepsPolarityAndSilenceAndCapsLoudInputs() {
        val gain = CyberReactorContour.waveformGain(.01f)
        assertEquals(.36f,CyberReactorContour.displayWaveform(.02f,gain),.001f)
        assertEquals(-.36f,CyberReactorContour.displayWaveform(-.02f,gain),.001f)
        assertEquals(0f,CyberReactorContour.displayWaveform(0f,CyberReactorContour.waveformGain(0f)),0f)
        assertEquals(0f,CyberReactorContour.displayWaveform(Float.NaN,gain),0f)
        assertEquals(1f,CyberReactorContour.displayWaveform(1f,gain),0f)
        assertEquals(48f,CyberReactorContour.waveformGain(Float.NaN),0f)
    }
    @Test fun zeroIntensityStopsWaveDeformationEvenWhenCaptureHasSignal() {
        val frame = VisualizerInterpolatedFrame().apply { rms=.01f; bass=.4f; treble=.4f; pulse=1f; waveform.fill(.2f); spectrum.fill(.4f) }
        val contour = CyberReactorContour(192)
        contour.update(frame,config.copy(globalIntensity=0f),false)
        assertTrue(contour.radii.all { it == 1.025f })
    }
}
