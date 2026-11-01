package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.currencortex.music.core.config.ServerDefaults
import io.github.currencortex.music.core.network.CurrentMusicServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ServerSelectionSettingsTest {
    private class MemoryPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
    @Test fun freshInstallUsesAutomaticButExistingUrlsKeepTheirSelection() = runTest {
        for ((url, mode) in listOf(null to CurrentMusicServer.AUTO, ServerDefaults.URL to CurrentMusicServer.MAIN,
            ServerDefaults.CDN to CurrentMusicServer.CDN, ServerDefaults.CDN_GEEKERTAO to CurrentMusicServer.CDN_GEEKERTAO,
            ServerDefaults.CDN_CTCEHS to CurrentMusicServer.CDN_CTCEHS,
            "https://private.example/api/" to CurrentMusicServer.CUSTOM)) {
            val store = MemoryPreferences(url?.let { preferencesOf(stringPreferencesKey("music.server") to it) } ?: emptyPreferences())
            val settings = MusicSettingsRepository(store, backgroundScope).snapshot()
            assertEquals(mode, settings.serverMode)
            assertEquals(url ?: ServerDefaults.URL, settings.server)
            if (mode == CurrentMusicServer.CUSTOM) assertEquals(url, settings.customServer)
        }
    }
    @Test fun selectionPersistsAcrossRestartAndKeepsCustomAddress() = runTest {
        val store = MemoryPreferences()
        val repository = MusicSettingsRepository(store, backgroundScope)
        repository.setServer("https://custom.example/api")
        assertEquals(CurrentMusicServer.CUSTOM, repository.snapshot().serverMode)
        repository.setServerSelection(CurrentMusicServer.AUTO, ServerDefaults.URL)
        val restart = MusicSettingsRepository(store, backgroundScope)
        assertEquals(CurrentMusicServer.AUTO, restart.snapshot().serverMode)
        assertEquals("https://custom.example/api/", restart.snapshot().customServer)
        restart.setServerSelection(CurrentMusicServer.CDN, ServerDefaults.URL)
        assertEquals(CurrentMusicServer.CDN, restart.snapshot().serverMode)
        assertEquals(ServerDefaults.URL, restart.snapshot().server)
        assertEquals("https://custom.example/api/", restart.snapshot().customServer)
        restart.setServer(ServerDefaults.URL)
        assertEquals(CurrentMusicServer.CDN, restart.snapshot().serverMode)
        restart.setServerSelection(CurrentMusicServer.CDN_GEEKERTAO, ServerDefaults.URL)
        val nextRestart = MusicSettingsRepository(store, backgroundScope)
        nextRestart.setServer(ServerDefaults.URL)
        assertEquals(CurrentMusicServer.CDN_GEEKERTAO, nextRestart.snapshot().serverMode)
        assertEquals(ServerDefaults.URL, nextRestart.snapshot().server)
        assertEquals("https://custom.example/api/", nextRestart.snapshot().customServer)
        nextRestart.setServerSelection(CurrentMusicServer.CDN_CTCEHS,ServerDefaults.URL)
        val cdnRestart=MusicSettingsRepository(store,backgroundScope)
        cdnRestart.setServer(ServerDefaults.URL)
        assertEquals(CurrentMusicServer.CDN_CTCEHS,cdnRestart.snapshot().serverMode)
        assertEquals(ServerDefaults.URL,cdnRestart.snapshot().server)
        assertEquals("https://custom.example/api/",cdnRestart.snapshot().customServer)
    }
}
