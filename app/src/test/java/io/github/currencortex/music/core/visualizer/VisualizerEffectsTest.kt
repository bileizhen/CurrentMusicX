package io.github.currencortex.music.core.visualizer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.currencortex.music.data.settings.MusicSettingsRepository
import io.github.currencortex.music.data.visualizer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class VisualizerEffectsTest {
    private val c = VisualizerEffectConfig(enabled = true)
    private fun frame() = VisualizerInterpolatedFrame().apply { generation = 1; bass = .6f; treble = .4f; rms = .1f }
    @Test fun defaultsAreOptInAndOrbit() { assertFalse(VisualizerEffectConfig().enabled); assertEquals(VisualizerPreset.ORBIT_SPECTRUM, VisualizerPreset.from(null)) }
    @Test fun corruptedEnumFallsBack() { assertEquals(VisualizerPreset.ORBIT_SPECTRUM, VisualizerPreset.from("future")) }
    @Test fun parametersHaveFiniteSafeRanges() {
        val n = c.copy(globalIntensity = Float.NaN, bassSensitivity = 999f, spectrumSensitivity = -1f,
            glowIntensity = Float.POSITIVE_INFINITY, particleDensity = 999f, motionIntensity = -1f).normalized()
        assertEquals(1f, n.globalIntensity, 0f); assertEquals(3f, n.bassSensitivity, 0f)
        assertEquals(0f, n.spectrumSensitivity, 0f); assertEquals(1f, n.glowIntensity, 0f)
        assertEquals(1f, n.particleDensity, 0f); assertEquals(0f, n.motionIntensity, 0f)
    }
    @Test fun scaleNeverExceedsSafetyLimit() {
        for (p in VisualizerPreset.entries) {
            val state = VisualizerEffectState(); val f = frame().apply { bass = 1f; pulse = 1f }
            state.step(f, c.copy(presetId = p, motionIntensity = 1f), VisualizerQuality.ULTRA, 1, .01f)
            assertTrue(state.coverScale in 1f..1.12f)
        }
    }
    @Test fun kickOnlyTriggersOnce() {
        val s = VisualizerEffectState(); val f = frame().apply { kickCount = 1; pulse = 1f }
        repeat(20) { s.step(f, c, VisualizerQuality.ULTRA, it + 1L, .01f) }
        assertEquals(1L, s.events); assertEquals(1, s.waveAge.count { it >= 0 })
    }
    @Test fun shockwavesAreBoundedUnderManyKicks() {
        val s = VisualizerEffectState(); val f = frame()
        repeat(100) { f.kickCount++; s.step(f, c, VisualizerQuality.ULTRA, it + 1L, .001f) }
        assertEquals(4, s.waveAge.size); assertEquals(4, s.waveAge.count { it >= 0 })
    }
    @Test fun shockwavesExpire() {
        val s = VisualizerEffectState(); val f = frame().apply { kickCount = 1 }
        repeat(40) { s.step(f, c, VisualizerQuality.ULTRA, it + 1L, .01f) }
        assertEquals(0, s.waveAge.count { it >= 0 })
    }
    @Test fun glitchExpiresAndHasCooldown() {
        val s = VisualizerEffectState(); val f = frame().apply { transientCount = 1 }
        s.step(f, c, VisualizerQuality.ULTRA, 1_000_000_000, .01f); assertTrue(s.glitch > 0)
        repeat(10) { s.step(f, c, VisualizerQuality.ULTRA, 1_010_000_000 + it * 10_000_000L, .01f) }
        assertEquals(0f, s.glitch, 0f)
        f.transientCount++; s.step(f, c, VisualizerQuality.ULTRA, 1_200_000_000, .01f); assertEquals(0f, s.glitch, 0f)
        f.transientCount++; s.step(f, c, VisualizerQuality.ULTRA, 1_500_000_000, .01f); assertTrue(s.glitch > 0)
    }
    @Test fun malformedSpectrumIsSafe() {
        assertEquals(0f, VisualizerEffectState.spectrum(floatArrayOf(), 1f, 1f), 0f)
        assertEquals(0f, VisualizerEffectState.spectrum(floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY), Float.NaN, 1f), 0f)
        assertTrue(VisualizerEffectState.spectrum(floatArrayOf(.1f), 9f, 9f) <= 1f)
    }
    @Test fun poolSizesFollowQuality() {
        val s = VisualizerEffectState(); val f = frame()
        for (q in VisualizerQuality.entries) {
            s.step(f, c.copy(particleDensity = 1f), q, 1, .01f)
            assertEquals(listOf(96,48,24,8)[q.ordinal], s.particleCount)
        }
    }
    @Test fun reducedMotionRemovesShakeAndAttenuatesGlitch() {
        val s = VisualizerEffectState(); val f = frame().apply { transientCount = 1; pulse = 1f }
        s.step(f, c.copy(presetId = VisualizerPreset.BASS_IMPACT, reduceMotion = true), VisualizerQuality.ULTRA, 1, .01f)
        assertEquals(0f, s.shakeX, 0f); assertTrue(s.glitch < .1f); assertTrue(s.coverScale < 1.04f)
    }
    @Test fun systemReducedMotionAlsoApplies() {
        val s = VisualizerEffectState()
        s.step(frame().apply { pulse = 1f }, c.copy(presetId = VisualizerPreset.BASS_IMPACT), VisualizerQuality.ULTRA, 1, .01f, true)
        assertEquals(0f, s.shakeX, 0f)
    }
    @Test fun presetActivationDoesNotReplayOldEvents() {
        val s = VisualizerEffectState(); val f = frame().apply { kickCount = 30; transientCount = 12 }
        s.activate(f); s.step(f, c, VisualizerQuality.ULTRA, 1, .01f)
        assertEquals(0L, s.events); assertEquals(0f, s.glitch, 0f)
        f.kickCount++; s.step(f, c, VisualizerQuality.ULTRA, 2, .01f); assertEquals(1L, s.events)
    }
    @Test fun gpuMetricsRemainUnknownWhenNotAvailableAndAreMeasuredWhenPresent() {
        val m = VisualizerPerformanceMonitor()
        val config = VisualizerRenderSettings(); val display = VisualizerDisplayState()
        val decision = VisualizerFrameRateDecision(60,60,null)
        m.window(1_000_000_000, 3_000_000, 16_000_000)
        assertNull(m.snapshot(1_000_000_000, config, display, decision, VisualizerQuality.ULTRA, true).gpuAverageMs)
        m.window(1_020_000_000, 3_000_000, 16_000_000, gpuNanos = 2_000_000)
        assertEquals(2f, m.snapshot(1_020_000_000, config, display, decision, VisualizerQuality.ULTRA, true).gpuAverageMs!!, 0f)
    }
    @Test fun trackGenerationClearsOldEvents() {
        val s = VisualizerEffectState(); val f = frame().apply { kickCount = 1 }
        s.step(f, c, VisualizerQuality.ULTRA, 1, .01f)
        f.generation++; f.kickCount = 0; s.step(f, c, VisualizerQuality.ULTRA, 2, .01f)
        assertEquals(0L, s.events); assertEquals(0, s.waveAge.count { it >= 0 })
    }
    @Test fun pauseResetAndNewPresetDoNotKeepEnergy() {
        val s = VisualizerEffectState(); s.step(frame(), c, VisualizerQuality.ULTRA, 1, .05f); s.reset()
        assertEquals(1f, s.coverScale, 0f); assertEquals(0f, s.phase, 0f); assertEquals(0, s.particleCount)
        assertEquals(0f, VisualizerEffectState().transition, 0f)
    }
    @Test fun deltaTimeIsIndependentOfFrameRate() {
        fun run(hz: Int): VisualizerEffectState = VisualizerEffectState().apply {
            repeat(hz) { step(frame(), c, VisualizerQuality.ULTRA, it + 1L, 1f / hz) }
        }
        assertEquals(run(30).phase, run(120).phase, .0001f)
        assertEquals(1f, run(60).transition, 0f)
    }
    @Test fun windowFallbackWaitsForComposeAndRestoresPreviousPreference() {
        var window = 90f
        val lease = VisualizerRefreshPreference({ window }) { window = it }
        lease.update(120f,120f); assertEquals(90f,window,0f)
        lease.update(120f,60f); assertEquals(120f,window,0f)
        lease.update(120f,120f); assertEquals(120f,window,0f)
        lease.update(60f,60f); assertEquals(60f,window,0f)
        lease.update(0f,60f); assertEquals(90f,window,0f)
    }
    @Test fun windowFallbackPreservesConcurrentOwner() {
        var window = 0f
        val lease = VisualizerRefreshPreference({ window }) { window = it }
        lease.update(120f,60f); window = 72f
        lease.update(120f,60f); lease.close(); assertEquals(72f,window,0f)
    }
    @Test fun zeroGlobalIntensityRemovesAudioMotion() {
        val s = VisualizerEffectState(); val f = frame().apply { kickCount = 1; transientCount = 1; pulse = 1f }
        s.step(f, c.copy(globalIntensity = 0f), VisualizerQuality.ULTRA, 1, .01f)
        assertEquals(1f, s.coverScale, 0f); assertEquals(0f, s.phase, 0f); assertEquals(0f, s.glitch, 0f)
        assertEquals(0f, s.waveStrength.maxOrNull()!!, 0f)
    }
    @Test fun silenceDoesNotMoveParticlesOrInventEvents() {
        val s = VisualizerEffectState(); val f = VisualizerInterpolatedFrame()
        val angles = s.particleAngle.copyOf()
        repeat(100) { s.step(f, c, VisualizerQuality.ULTRA, it + 1L, .01f) }
        assertArrayEquals(angles, s.particleAngle, 0f); assertEquals(0L, s.events); assertEquals(0f, s.phase, 0f)
    }
    @Test fun shaderPolicyAlwaysRetainsCanvasOnOldApiFailureOrLowQuality() {
        assertFalse(VisualizerShaderPolicy.enabled(32, true, VisualizerQuality.ULTRA, false))
        assertFalse(VisualizerShaderPolicy.enabled(33, false, VisualizerQuality.ULTRA, false))
        assertFalse(VisualizerShaderPolicy.enabled(36, true, VisualizerQuality.ULTRA, true))
        assertFalse(VisualizerShaderPolicy.enabled(36, true, VisualizerQuality.ECO, false))
        assertTrue(VisualizerShaderPolicy.enabled(33, true, VisualizerQuality.HIGH, false))
    }
    @Test fun persistenceUsesExistingEnableAndPerformanceKeys() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val data = MutableStateFlow<Preferences>(emptyPreferences())
        val store = object : DataStore<Preferences> {
            override val data = data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(data.value).also { data.value = it }
        }
        try {
            val repo = MusicSettingsRepository(store, scope)
            repo.setVisualizerFrameRate(VisualizerFrameRate.FPS_120)
            repo.editVisualizerRender { it.copy(preferredQuality = VisualizerQuality.HIGH) }
            repo.editVisualizerEffects { it.copy(enabled = true, presetId = VisualizerPreset.NEON_PULSE,
                bassSensitivity = 9f, reduceMotion = true, adaptiveQuality = false) }
            val read = MusicSettingsRepository(store, scope).snapshot()
            assertTrue(read.visualizerEnabled); assertFalse(read.visualizerRender.automaticOptimization)
            assertEquals(VisualizerFrameRate.FPS_120, read.visualizerRender.frameRate)
            assertEquals(VisualizerQuality.HIGH, read.visualizerRender.preferredQuality)
            assertEquals(VisualizerPreset.NEON_PULSE, read.visualizerEffects.presetId)
            assertEquals(3f, read.visualizerEffects.bassSensitivity, 0f); assertTrue(read.visualizerEffects.reduceMotion)
            repo.setVisualizerEnabled(false); repo.editVisualizerRender { it.copy(automaticOptimization = true) }
            assertFalse(repo.snapshot().visualizerEffects.enabled); assertTrue(repo.snapshot().visualizerEffects.adaptiveQuality)
        } finally { scope.cancel() }
    }
}
