package io.github.currencortex.music

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.LyricsSource
import io.github.currencortex.music.data.settings.MusicSettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real preference-file upgrade and restart, without launching an Activity or touching user settings. */
class LyricsAutomaticSourceStorageTest {
    @Test fun anExistingProjectSourceMigratesOnceAndLaterManualChoicesSurviveReopening() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "lyrics-auto-${UUID.randomUUID()}.preferences_pb")
        val source = stringPreferencesKey("lyrics.source")
        val migration = intPreferencesKey("lyrics.source.automaticMigrationVersion")
        var owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var store = PreferenceDataStoreFactory.create(scope = owner) { file }
            store.edit { it[source] = "CURRENT_MUSIC" }
            var repository = MusicSettingsRepository(store, owner)
            assertEquals(LyricsSource.AUTO, repository.snapshot().lyricsSource)
            withTimeout(5000) { store.data.first { it[migration] == 1 } }
            repository.setLyricsSource(LyricsSource.NETEASE)
            owner.coroutineContext[Job]!!.cancelAndJoin()

            owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            store = PreferenceDataStoreFactory.create(scope = owner) { file }
            repository = MusicSettingsRepository(store, owner)
            assertEquals(LyricsSource.NETEASE, repository.snapshot().lyricsSource)
            repository.setLyricsSource(LyricsSource.CURRENT_MUSIC)
            owner.coroutineContext[Job]!!.cancelAndJoin()

            owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            store = PreferenceDataStoreFactory.create(scope = owner) { file }
            repository = MusicSettingsRepository(store, owner)
            assertEquals(LyricsSource.CURRENT_MUSIC, repository.snapshot().lyricsSource)
            assertEquals(1, store.data.first()[migration])
        } finally {
            owner.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
