package io.github.currencortex.music

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Actual account and server materials; temporary preferences, synthetic PCM, no speaker or queue edits. */
class OriginalAdaptiveEffectsLiveTest {
    @Test fun actualMemberSmartAndMoodControllersFetchPrepareProcessAndSwitchOff() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memberSoundEffects") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName == "com.bileizhen.currentmusic")
        val foreground = ActivityScenario.launch(MainActivity::class.java)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val file = File.createTempFile("adaptive-effects-test-", ".preferences_pb", app.cacheDir).also { it.delete() }
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        val evidence = File(app.getExternalFilesDir(null), "adaptive-effects-live.txt")
        val notes = StringBuilder()
        fun note(value: String) { notes.appendLine(value); evidence.writeText(notes.toString()) }
        val songs = MutableStateFlow<Song?>(Song(186016, "Synthetic verification"))
        val engine = MutableStateFlow(true)
        val container = app.container
        var smart: OriginalSmartEffectsController? = null
        var mood: OriginalMoodEffectsController? = null
        try {
            container.ready.await(); container.neteaseSessions.ready.await()
            assertTrue(NeteaseNativeEngine.available())
            withContext(Dispatchers.Main) {
                smart = OriginalSmartEffectsController(container.neteaseSoundEffects, container.neteaseSessions,
                    OriginalSmartSettingsRepository(store, scope), songs, engine, scope,
                    beforeEnable = { mood?.disable() }, definition = NeteaseNativeEngine::smartDefinition)
                mood = OriginalMoodEffectsController(container.originalMoodModels, container.neteaseSessions,
                    OriginalMoodSettingsRepository(store, scope), songs, engine, scope,
                    beforeEnable = { smart?.disable() }, definition = NeteaseNativeEngine::moodDefinition)
                smart!!.setEnabled(true)
            }
            val smartState = withTimeout(90_000) { smart!!.state.first { !it.loading && it.soundKey != null } }
            assertTrue(smartState.message, smartState.enabled)
            val smartPreset = requireNotNull(smart!!.selection.value)
            val smartBytes = process(smartPreset)
            withContext(Dispatchers.Main) { smart!!.reportProcessing(smartPreset.id) }
            assertTrue(smart!!.state.value.processing)
            note("smartRealRightsAndKey=true,pcmBytes=$smartBytes")
            withContext(Dispatchers.Main) { smart!!.disable(); mood!!.setEnabled(true) }
            val moodState = withTimeout(90_000) {
                mood!!.state.first { !it.loading && (it.enabled && it.emotion != null || !it.requestedEnabled) }
            }
            assertTrue(moodState.message, moodState.enabled)
            val moodPreset = requireNotNull(mood!!.selection.value)
            val moodBytes = process(moodPreset)
            withContext(Dispatchers.Main) { mood!!.reportProcessing(moodPreset.id) }
            assertTrue(mood!!.state.value.processing)
            assertNull(smart!!.selection.value)
            note("moodRealSvipModelDownloadAndInference=true,pcmBytes=$moodBytes")
            withContext(Dispatchers.Main) { mood!!.disable() }
            assertNull(mood!!.selection.value)
            assertFalse(mood!!.state.value.requestedEnabled)
            note("disabledAndSelectionCleared=true")
        } finally {
            withContext(Dispatchers.Main) { smart?.close(); mood?.close() }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete(); File(file.path + ".tmp").delete()
            foreground.close()
        }
    }

    private suspend fun process(preset: NeteaseOriginalPreset): Int = withContext(Dispatchers.Default) {
        var produced = 0
        NeteaseNativeEngine(48000, 2, preset).use { native ->
            val input = ByteBuffer.allocateDirect(2048).order(ByteOrder.nativeOrder())
            val output = ByteBuffer.allocateDirect(32768)
            repeat(24) {
                input.clear(); repeat(1024) { sample -> input.putShort(if (sample % 4 < 2) 1000 else -1000) }; input.flip()
                output.clear(); produced += native.process(input, output)
            }
        }
        assertTrue(produced > 0)
        produced
    }
}
