package io.github.currencortex.music

import io.github.currencortex.music.data.settings.MusicSettingsRepository
import io.github.currencortex.music.data.settings.LyricsWeight
import androidx.test.core.app.ApplicationProvider

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LyricsTypographyTest {
    @Test fun fontSizeSurvivesStoreRestartWithoutChangingMusicPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = java.io.File(context.cacheDir, "lyrics-font-test-${UUID.randomUUID()}").apply { mkdirs() }
        val file = java.io.File(directory, "music.preferences_pb")
        suspend fun store(block: suspend (MusicSettingsRepository) -> Unit) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
                block(MusicSettingsRepository(dataStore, scope))
            } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
        try {
            store { settings ->
                assertEquals(30f, settings.snapshot().lyricsFontSize, 0f)
                assertEquals(LyricsWeight.CURRENT, settings.snapshot().lyricsWeight)
                settings.setPreloadMetered(true)
                settings.setLyricsFontSize(38f)
                settings.setLyricsWeight(LyricsWeight.ALL)
            }
            store { settings ->
                assertEquals(38f, settings.snapshot().lyricsFontSize, 0f)
                assertEquals(LyricsWeight.ALL, settings.snapshot().lyricsWeight)
                assertTrue(settings.snapshot().preloadMetered)
                settings.setLyricsFontSize(Float.NaN)
                assertEquals(30f, settings.snapshot().lyricsFontSize, 0f)
                settings.setLyricsFontSize(100f)
                assertEquals(40f, settings.snapshot().lyricsFontSize, 0f)
                settings.setLyricsWeight(LyricsWeight.CURRENT)
            }
            store { settings -> assertEquals(LyricsWeight.CURRENT, settings.snapshot().lyricsWeight) }
        } finally { file.delete(); directory.delete() }
    }
}
