package io.github.currencortex.music.feature.lyrics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.currencortex.music.data.settings.MusicSettingsRepository
import io.github.currencortex.music.feature.lyrics.desktop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test


class DesktopPreferencesTest {
    @Test fun desktopOptionsPersistAndDoNotChangeApplicationLyrics(): Unit = runBlocking {
        // Windows File.renameTo lacks Android's POSIX replacement semantics.
        // Real restart/disk persistence is covered by DesktopLyricsPreferencesDeviceTest.
        val store = object : DataStore<Preferences> {
            override val data = MutableStateFlow<Preferences>(emptyPreferences())
            private val mutex = Mutex()
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = mutex.withLock {
                transform(data.value).also { data.value = it }
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val music = MusicSettingsRepository(store, scope)
            music.setLyricsFontSize(32f); music.setLyricsOffset(250)
            val original = music.snapshot()
            val desktop = DesktopLyricsPreferences(store, scope)
            desktop.ready.await()
            desktop.edit { it.copy(fontSize = 22f, offsetMs = -350, effect = DesktopEffect.STAGE, translation = false, x = .8f) }
            val expected = desktop.state.first { it.fontSize == 22f }
            assertEquals(original, music.snapshot())
            val secondConsumer = DesktopLyricsPreferences(store, scope)
            secondConsumer.ready.await()
            assertEquals(expected, secondConsumer.state.value)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}
