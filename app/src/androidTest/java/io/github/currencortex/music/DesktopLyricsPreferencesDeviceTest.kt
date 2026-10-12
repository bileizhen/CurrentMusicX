package io.github.currencortex.music

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.MusicSettingsRepository
import io.github.currencortex.music.feature.lyrics.desktop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DesktopLyricsPreferencesDeviceTest {
    @Test fun savedDesktopSettingsSurviveDataStoreRestartWithoutChangingAppLyrics(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(context.packageName.endsWith(".verification"))
        context.startActivity(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val file = context.preferencesDataStoreFile("desktop-persistence-${UUID.randomUUID()}")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val music = MusicSettingsRepository(store, scope)
            music.setLyricsFontSize(32f); music.setLyricsOffset(250)
            val before = music.snapshot()
            val desktop = DesktopLyricsPreferences(store, scope)
            desktop.ready.await()
            desktop.edit { it.copy(effect = DesktopEffect.STAGE, locked = true, fontSize = 24f, offsetMs = -250, x = .15f, y = .7f) }
            val expected = desktop.state.first { it.effect == DesktopEffect.STAGE }
            assertEquals(before, music.snapshot())
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val restoredStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val restored = DesktopLyricsPreferences(restoredStore, scope)
            restored.ready.await()
            assertEquals(expected, restored.state.value)
            assertEquals(before, MusicSettingsRepository(restoredStore, scope).snapshot())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
}
