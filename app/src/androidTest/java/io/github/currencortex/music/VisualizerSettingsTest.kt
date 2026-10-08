package io.github.currencortex.music

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.MusicSettingsRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class VisualizerSettingsTest {
    @Test fun captureDefaultsOffAndOptInPersistsWithoutChangingOtherSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val file = File(context.cacheDir, "visualizer-${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var settings = MusicSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }, scope)
            val original = settings.snapshot()
            assertFalse(original.visualizerEnabled)
            settings.setVisualizerEnabled(true)
            assertEquals(original.copy(visualizerEnabled = true), settings.snapshot())
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            settings = MusicSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }, scope)
            assertTrue(settings.snapshot().visualizerEnabled)
            settings.setVisualizerEnabled(false)
            assertEquals(original, settings.snapshot())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
}
