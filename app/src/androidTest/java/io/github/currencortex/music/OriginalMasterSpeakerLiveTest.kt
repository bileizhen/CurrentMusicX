package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.netease.NeteaseClientProfile
import io.github.currencortex.music.core.netease.NeteaseTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.effects.*
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.data.song.Song
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import kotlinx.serialization.json.*
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in read-only probe; exports sound materials, never account credentials or player state. */
class OriginalMasterSpeakerLiveTest {
    @Test fun inspectMemberMaterials() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memberSoundEffects") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName == "com.bileizhen.currentmusic")
        val foreground = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
            val container = app.container
            container.ready.await(); container.neteaseSessions.ready.await()
            assertTrue(container.neteaseSessions.state.value.loggedIn)
            val transport = NeteaseTransport(container.neteaseSessions)
            val list = buildJsonArray {
                for (id in listOf(-100,-101,-102,-200,-201,-202,-300,-400)) add(buildJsonObject {
                    put("soundType",9); put("soundId",id)
                })
            }
            val speaker = transport.request("/api/usertool/home/sound/info", buildJsonObject {
                put("reqListStr",list.toString())
            }, saveCookies=false, clientProfile=NeteaseClientProfile.ANDROID).body
            File(app.getExternalFilesDir(null),"speaker-material-probe.json").writeText(speaker.toString())
            val master = transport.request("/api/vip-rights/svip/sound/effect/get",buildJsonObject {
                put("songIds","[186016]")
            },saveCookies=false,clientProfile=NeteaseClientProfile.ANDROID).body
            File(app.getExternalFilesDir(null),"master-material-probe.json").writeText(master.toString())
            assertTrue(speaker["code"]?.jsonPrimitive?.intOrNull == 200)
            assertTrue(master["code"]?.jsonPrimitive?.intOrNull == 200)
        } finally { foreground.close() }
    }
    @Test fun realMemberControllersProduceNativePcmAndFollowSpeakerRoute() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memberSoundEffects") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName == "com.bileizhen.currentmusic")
        val foreground = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val prefs = File.createTempFile("master-speaker-", ".preferences_pb", app.cacheDir).also { it.delete() }
        val store = PreferenceDataStoreFactory.create(scope = scope) { prefs }
        val notes = StringBuilder()
        val evidence = File(app.getExternalFilesDir(null), "master-speaker-native.txt")
        fun note(value: String) { notes.appendLine(value); evidence.writeText(notes.toString()) }
        var master: OriginalAiMasterEffectsController? = null
        var speaker: OriginalSpeakerEffectsController? = null
        try {
            val container = app.container
            container.ready.await(); container.neteaseSessions.ready.await()
            assertTrue(NeteaseNativeEngine.available())
            val environment = MutableStateFlow(OriginalSpeakerEnvironment(true, false, .5f))
            withContext(Dispatchers.Main) {
                master = OriginalAiMasterEffectsController(container.originalAiMasterRepository, container.neteaseSessions,
                    OriginalAiMasterSettingsRepository(store, scope), MutableStateFlow(Song(186016, "Synthetic verification")),
                    MutableStateFlow(true), scope, beforeEnable = { speaker?.disable() },
                    definition = { app.assets.open("netease-ai-master-codec.js").use { it.readBytes() } })
                speaker = OriginalSpeakerEffectsController(container.originalSpeakerRepository, container.neteaseSessions,
                    environment, MutableStateFlow(true), scope, beforeEnable = { master?.disable() })
                master!!.setEnabled(true)
            }
            val masterState = withTimeout(90_000) { master!!.state.first { !it.loading && (master!!.selection.value != null || !it.requestedEnabled) } }
            assertTrue(masterState.message, masterState.enabled)
            val masterPreset = requireNotNull(master!!.selection.value)
            val masterBytes = process(masterPreset)
            withContext(Dispatchers.Main) { master!!.reportProcessing(masterPreset.id) }
            assertTrue(master!!.state.value.processing)
            note("master104RealSvipAndSongComposition=true,pcmBytes=$masterBytes")
            for (scene in OriginalSpeakerScene.entries) {
                withContext(Dispatchers.Main) { speaker!!.setEnabled(true, scene) }
                val ready = withTimeout(90_000) { speaker!!.state.first { !it.loading && it.enabled && it.scene == scene } }
                assertTrue(ready.message, ready.enabled)
                val preset = requireNotNull(speaker!!.selection.value)
                val bytes = process(preset)
                withContext(Dispatchers.Main) { speaker!!.reportProcessing(preset.id) }
                assertTrue(speaker!!.state.value.processing)
                assertNull(master!!.selection.value)
                note("speakerScene=${scene.wireName},modelId=${preset.id},pcmBytes=$bytes")
            }
            withContext(Dispatchers.Main) { environment.value = OriginalSpeakerEnvironment(false, false, .5f) }
            withTimeout(5000) { speaker!!.state.first { !it.enabled && it.requestedEnabled } }
            assertNull(speaker!!.selection.value)
            note("headphoneRouteWithdrawsOriginalSpeaker=true")
            withContext(Dispatchers.Main) { environment.value = OriginalSpeakerEnvironment(true, false, .5f) }
            withTimeout(90_000) { speaker!!.state.first { it.enabled && !it.loading } }
            assertNotNull(speaker!!.selection.value)
            withContext(Dispatchers.Main) { speaker!!.disable() }
            assertNull(speaker!!.selection.value)
            note("speakerRouteResumesAndDisableClears=true")

            // The normal scene has six distinct orientation / volume materials;
            // changing just the scene at mid-volume covers only one of them.
            val verified = mutableSetOf<Long>()
            for (landscape in listOf(false, true)) for (volume in listOf(.1f, .5f, .9f)) {
                val expected = originalSpeakerId(OriginalSpeakerScene.NORMAL, landscape, volume)
                withContext(Dispatchers.Main) {
                    environment.value = OriginalSpeakerEnvironment(true, landscape, volume)
                    speaker!!.setEnabled(true, OriginalSpeakerScene.NORMAL)
                }
                val ready = withTimeout(90_000) { speaker!!.state.first { !it.loading && it.enabled && it.modelId == expected } }
                assertTrue(ready.message, ready.enabled)
                val preset = requireNotNull(speaker!!.selection.value)
                note("speakerMaterial=${preset.id},landscape=$landscape,volume=$volume,pcmBytes=${process(preset)}")
                verified += preset.id
            }
            assertEquals(6, verified.size)
        } finally {
            withContext(Dispatchers.Main) { master?.close(); speaker?.close() }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            prefs.delete(); File(prefs.path + ".tmp").delete()
            foreground.close()
        }
    }
    @Test fun originalAuditionExcerptsAndMasterAbUseNativeDspWithoutChangingQueue() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("memberSoundEffects") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName == "com.bileizhen.currentmusic")
        val foreground = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val container = app.container
        container.ready.await(); container.neteaseSessions.ready.await()
        val before = container.playbackQueue.state.value
        val savedMaster = container.originalAiMasterSettings.snapshot()
        val savedSingle = container.originalEffectsSettings.snapshot()
        val audition = withContext(Dispatchers.Main) { container.createEffectsAudition(scope) }
        val report = File(app.getExternalFilesDir(null), "effects-audition-live.txt")
        val notes = StringBuilder()
        try {
            for (scene in OriginalSpeakerScene.entries) {
                withContext(Dispatchers.Main) { audition.speaker(scene) }
                val ready = withTimeout(45_000) { audition.state.first { it.error != null || it.playing && it.processing } }
                assertNull(ready.error, ready.error)
                assertTrue(ready.playing && ready.processing)
                notes.appendLine("speaker_${scene.wireName}=realExcerptPlayingWithNativeDsp")
                withContext(Dispatchers.Main) { audition.stop() }
            }
            val song = requireNotNull(before.current) { "Select an available real song before the opt-in audition test" }
            withContext(Dispatchers.Main) { audition.master(song, true, setOf("en_vibe")) }
            val processed = withTimeout(60_000) { audition.state.first { it.error != null || it.playing && it.processing } }
            assertNull(processed.error, processed.error)
            assertTrue(processed.processed && processed.processing)
            notes.appendLine("master_processed=actualSongAndOriginalDiyDsp")
            withContext(Dispatchers.Main) { audition.master(song, false, emptySet()) }
            val original = withTimeout(45_000) { audition.state.first { it.error != null || it.playing } }
            assertNull(original.error, original.error)
            assertFalse(original.processed)
            assertFalse(original.processing)
            withContext(Dispatchers.Main) { audition.stop() }
            assertEquals(before.current, container.playbackQueue.state.value.current)
            assertEquals(before.songs, container.playbackQueue.state.value.songs)
            assertEquals(savedMaster, container.originalAiMasterSettings.snapshot())
            assertEquals(savedSingle, container.originalEffectsSettings.snapshot())
            notes.appendLine("original_abAndQueueAndSavedSelectionUnchanged=true")
        } finally {
            report.writeText(notes.toString())
            withContext(Dispatchers.Main) { audition.close() }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            foreground.close()
        }
    }
    private suspend fun process(preset: NeteaseOriginalPreset): Int = withContext(Dispatchers.Default) {
        var produced = 0
        NeteaseNativeEngine(48000, 2, preset).use { engine ->
            val input = ByteBuffer.allocateDirect(2048).order(ByteOrder.nativeOrder())
            val output = ByteBuffer.allocateDirect(32768)
            repeat(24) {
                input.clear(); repeat(1024) { sample -> input.putShort(if (sample % 4 < 2) 1000 else -1000) }; input.flip()
                output.clear(); produced += engine.process(input, output)
            }
        }
        assertTrue("Native engine must output PCM", produced > 0)
        produced
    }

}
