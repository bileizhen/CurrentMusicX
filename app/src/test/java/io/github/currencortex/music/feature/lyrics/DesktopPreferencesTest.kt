package io.github.currencortex.music.feature.lyrics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
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
    @Test fun previousPayloadRetainsWindowSettingsAndDefaultsNewEffects(): Unit = runBlocking {
        val store = object : DataStore<Preferences> {
            override val data = MutableStateFlow(preferencesOf(stringPreferencesKey("desktopLyrics.preferences.v1") to
                "true|true|STAGE|CAPTION|false|30.0|0.6|-250|0.1|0.8|true"))
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val preferences = DesktopLyricsPreferences(store, scope); preferences.ready.await()
            val old = preferences.state.value
            assertTrue(old.enabled); assertTrue(old.locked); assertEquals(30f, old.fontSize, .001f)
            assertEquals(-250L, old.offsetMs); assertEquals(.8f, old.y, .001f)
            assertTrue(old.glowEnabled); assertEquals(1f, old.glowStrength, .001f)
            assertEquals(1f, old.glowSpread, .001f); assertEquals(0xFFFFFFFFL, old.textColor); assertEquals(0xFFE0E0E0L, old.glowColor)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
    @Test fun customColorsRejectInvalidInputAndNormalizeIndependentOfWindowOpacity() {
        assertEquals(0xFF80DEFFL, desktopParseColor(" #80deff "))
        assertNull(desktopParseColor("123")); assertNull(desktopParseColor("GG00AA")); assertNull(desktopParseColor("0080DEFF"))
        assertEquals("80DEFF", desktopColorHex(0xFF80DEFFL))
        val s = DesktopLyricsSettings(glowStrength = Float.NaN, glowSpread = 99f,
            textColor = 0x0080DEFF, glowColor = 0x00112233, opacity = .6f).normalized()
        assertEquals(1f, s.glowStrength, .001f); assertEquals(2f, s.glowSpread, .001f)
        assertEquals(0xFF80DEFFL, s.textColor); assertEquals(0xFF112233L, s.glowColor); assertEquals(.6f, s.opacity, .001f)
    }
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
            desktop.edit { it.copy(appearance = DesktopAppearance.ISLAND, islandFontSize = 16f, islandY = .15f, fontSize = 22f, offsetMs = -350, effect = DesktopEffect.STAGE, translation = false, x = .8f,
                textColor = 0xFF80DEFFL, glowColor = 0xFFFFAACCL, glowEnabled = false, glowStrength = 1.7f, glowSpread = 1.4f) }
            val expected = desktop.state.first { it.fontSize == 22f }
            assertEquals(16f, expected.displayFontSize, .001f)
            assertEquals(22f, expected.copy(appearance = DesktopAppearance.CAPTION).displayFontSize, .001f)
            assertEquals(original, music.snapshot())
            val secondConsumer = DesktopLyricsPreferences(store, scope)
            secondConsumer.ready.await()
            assertEquals(expected, secondConsumer.state.value)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}
