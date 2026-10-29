package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsAutomaticSourceSettingsTest {
    private val source = stringPreferencesKey("lyrics.source")
    private val migration = intPreferencesKey("lyrics.source.automaticMigrationVersion")

    private class MemoryPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        private val mutex = Mutex()
        var failWrites = false
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            if (failWrites) throw IOException("disk temporarily unavailable")
            transform(data.value).also { data.value = it }
        }
    }

    @Test fun defaultAndUnknownSourcesUseAutomaticSelection() {
        assertEquals(LyricsSource.AUTO, MusicSettings().lyricsSource)
        assertEquals(LyricsSource.AUTO, LyricsSource.from(null))
        assertEquals(LyricsSource.AUTO, LyricsSource.from("unknown"))
        assertEquals(LyricsSource.AUTO, LyricsSource.from("AUTO"))
        assertEquals(LyricsSource.NETEASE, LyricsSource.from("NETEASE"))
        assertEquals(LyricsSource.CURRENT_MUSIC, LyricsSource.from("CURRENT_MUSIC"))
        assertEquals(LyricsSource.LOCAL, LyricsSource.from("LOCAL"))
    }

    @Test fun allUnmigratedSourcesReadAsAutomaticBeforeTheStartupWriteRuns() = runTest {
        for (legacy in listOf(null, "CURRENT_MUSIC", "NETEASE", "unknown", "AUTO")) {
            val original = if (legacy == null) emptyPreferences() else preferencesOf(source to legacy)
            val store = MemoryPreferences(original)
            val repository = MusicSettingsRepository(store, backgroundScope)
            // StandardTestDispatcher has not executed the eager collector or migration yet.
            assertEquals(legacy, store.data.value[source])
            assertNull(store.data.value[migration])
            assertEquals(LyricsSource.AUTO, repository.snapshot().lyricsSource)
            assertEquals(LyricsSource.AUTO, repository.state.value.lyricsSource)
            runCurrent()
            assertEquals("AUTO", store.data.value[source])
            assertEquals(1, store.data.value[migration])
            assertEquals(LyricsSource.AUTO, repository.state.value.lyricsSource)
        }
    }

    @Test fun migrationPreservesOtherPreferencesAndDoesNotRepeatAfterManualSelection() = runTest {
        val font = floatPreferencesKey("lyrics.fontSize")
        val offset = longPreferencesKey("lyrics.offsetMs")
        val store = MemoryPreferences(preferencesOf(source to "CURRENT_MUSIC", font to 39f, offset to 350L))
        val repository = MusicSettingsRepository(store, backgroundScope)
        runCurrent()
        assertEquals(LyricsSource.AUTO, repository.snapshot().lyricsSource)
        assertEquals(39f, repository.snapshot().lyricsFontSize, .001f)
        assertEquals(350L, repository.snapshot().lyricsOffsetMs)
        for (chosen in LyricsSource.entries) {
            repository.setLyricsSource(chosen)
            val recreated = MusicSettingsRepository(store, backgroundScope)
            assertEquals(chosen, recreated.snapshot().lyricsSource)
            runCurrent()
            assertEquals(chosen, recreated.state.value.lyricsSource)
            assertEquals(chosen.name, store.data.value[source])
            assertEquals(1, store.data.value[migration])
        }
    }

    @Test fun explicitSelectionBeforeStartupMigrationWinsTheRace() = runTest {
        val store = MemoryPreferences(preferencesOf(source to "CURRENT_MUSIC"))
        val repository = MusicSettingsRepository(store, backgroundScope)
        repository.setLyricsSource(LyricsSource.NETEASE)
        assertEquals("NETEASE", store.data.value[source])
        assertEquals(1, store.data.value[migration])
        runCurrent()
        assertEquals(LyricsSource.NETEASE, repository.state.value.lyricsSource)
        assertEquals(LyricsSource.NETEASE, repository.snapshot().lyricsSource)
    }

    @Test fun aFailedMigrationWriteStillReadsAutomaticAndRetriesOnRecreation() = runTest {
        val store = MemoryPreferences(preferencesOf(source to "NETEASE")).apply { failWrites = true }
        val repository = MusicSettingsRepository(store, backgroundScope)
        runCurrent()
        assertEquals(LyricsSource.AUTO, repository.snapshot().lyricsSource)
        assertEquals("NETEASE", store.data.value[source])
        assertNull(store.data.value[migration])
        store.failWrites = false
        val recreated = MusicSettingsRepository(store, backgroundScope)
        runCurrent()
        assertEquals(LyricsSource.AUTO, recreated.snapshot().lyricsSource)
        assertEquals("AUTO", store.data.value[source])
        assertEquals(1, store.data.value[migration])
    }

    @Test fun newerMigrationMarkersAreNotDowngradedByAnExplicitChoice() = runTest {
        val store = MemoryPreferences(preferencesOf(source to "CURRENT_MUSIC", migration to 3))
        val repository = MusicSettingsRepository(store, backgroundScope)
        assertEquals(LyricsSource.CURRENT_MUSIC, repository.snapshot().lyricsSource)
        runCurrent()
        repository.setLyricsSource(LyricsSource.NETEASE)
        runCurrent()
        assertEquals(LyricsSource.NETEASE, repository.snapshot().lyricsSource)
        assertEquals(3, store.data.value[migration])
    }
}
