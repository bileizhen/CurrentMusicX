package io.github.currencortex.music

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.visualizer.*
import io.github.currencortex.music.data.settings.MusicSettingsRepository
import io.github.currencortex.music.data.visualizer.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class VisualizerEffectsSettingsTest {
    @Test fun allEffectParametersPersistInActualDataStoreAndShareM0M1Switches() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val file = File(context.cacheDir, "effects-${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var repository = MusicSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }, scope)
            val original = repository.snapshot()
            for (preset in VisualizerPreset.entries) {
                repository.editVisualizerEffects { VisualizerEffectConfig(true, preset, 1.5f, 2f, 2.5f,
                    1.7f, .9f, .8f, .6f, .7f, true, false) }
                repository.editVisualizerRender { it.copy(frameRate = VisualizerFrameRate.FPS_120, preferredQuality = VisualizerQuality.HIGH) }
                scope.coroutineContext[Job]!!.cancelAndJoin()
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                repository = MusicSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }, scope)
                val stored = repository.snapshot()
                assertEquals(VisualizerEffectConfig(true, preset, 1.5f, 2f, 2.5f, 1.7f, .9f, .8f, .6f, .7f, true, false), stored.visualizerEffects)
                assertTrue(stored.visualizerEnabled); assertFalse(stored.visualizerRender.automaticOptimization)
                assertEquals(VisualizerQuality.HIGH, stored.visualizerRender.preferredQuality)
                assertEquals(original.server, stored.server); assertEquals(original.quality, stored.quality)
            }
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
}
