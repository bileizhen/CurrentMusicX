package io.github.currencortex.music.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** An owned cache file only; never opens the application's account or playback settings. */
@RunWith(AndroidJUnit4::class)
class OriginalAiMasterSettingsStorageTest {
    @get:Rule val temporary = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)

    @Test fun realFileRewritesAndReopensRetainPerAccountDiyAfterDisablingTheEffect() = runBlocking {
        val file = File(temporary.root, "master-diy-${UUID.randomUUID()}.preferences_pb")
        suspend fun open(block: suspend (OriginalAiMasterSettingsRepository) -> Unit) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val store = PreferenceDataStoreFactory.create(scope = scope) { file }
                block(OriginalAiMasterSettingsRepository(store, scope))
            } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
        open { settings ->
            settings.setDiy(10, setOf("en_bass", "en_live"))
            settings.setDiy(20, setOf("en_vocal"))
            settings.set(OriginalSmartPreferences(true, 10))
            settings.set(OriginalSmartPreferences())
            assertFalse(settings.snapshot().enabled)
            assertEquals(setOf("en_bass", "en_live"), settings.diyFor(10))
            assertEquals(setOf("en_vocal"), settings.diyFor(20))
            assertTrue(settings.diyFor(30).isEmpty())
        }
        open { settings ->
            assertFalse(settings.snapshot().enabled)
            assertEquals(setOf("en_bass", "en_live"), settings.diyFor(10))
            assertEquals(setOf("en_vocal"), settings.diyFor(20))
            settings.setDiy(10, setOf("en_treble"))
            assertEquals(setOf("en_vocal"), settings.diyFor(20))
        }
        open { settings ->
            assertEquals(setOf("en_treble"), settings.diyFor(10))
            assertEquals(setOf("en_vocal"), settings.diyFor(20))
        }
    }
}
