package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.effects.OriginalAiMasterMaterial
import io.github.currencortex.music.data.effects.OriginalMoodEntitlement
import io.github.currencortex.music.data.settings.OriginalSmartPreferences
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OriginalAiMasterEffectsControllerTest {
    private class Fixture(val test: TestScope) {
        var saved = OriginalSmartPreferences()
        var rights = OriginalMoodEntitlement(10, true, 120_000)
        var query: suspend () -> OriginalMoodEntitlement = { rights }
        var materialCalls = 0
        var exclusiveCalls = 0
        val diy = mutableMapOf<Long, Set<String>>()
        var saveDiy: suspend (Long, Set<String>) -> Unit = { uid, selected -> diy[uid] = selected }
        val account = MutableStateFlow(OriginalSmartAccount(10, 0, true))
        val songs = MutableStateFlow<Song?>(Song(186016, "fixture"))
        private fun resource(name: String) = Json.parseToJsonElement(javaClass.classLoader!!
            .getResource("original-ai-master/$name.json")!!.readText()).jsonObject
        val controller = OriginalAiMasterEffectsController(accounts = account, ready = {},
            readSaved = { saved }, writeSaved = { saved = it }, entitlement = { query() },
            prepare = { materialCalls++; OriginalAiMasterMaterial(resource("base"), resource("diy"),
                "key_warm", resource("song"), "原版歌曲说明", "温润", "网易云真实长文案") }, songs = songs,
            engineStatus = MutableStateFlow(true), scope = test.backgroundScope,
            beforeEnable = { exclusiveCalls++ }, definition = {
                sequenceOf(File("src/main/assets/netease-ai-master-codec.js"),
                    File("app/src/main/assets/netease-ai-master-codec.js")).first { it.isFile }.readBytes()
            }, now = { test.testScheduler.currentTime }, workDispatcher = StandardTestDispatcher(test.testScheduler),
            readDiy = { diy[it].orEmpty() }, writeDiy = { uid, selected -> saveDiy(uid, selected) })
    }

    @Test fun realSelectionNeedsPcmAcknowledgementAndExpires() = runTest {
        val f = Fixture(this); runCurrent()
        f.controller.setEnabled(true); runCurrent()
        assertTrue(f.controller.state.value.enabled)
        assertFalse(f.controller.state.value.processing)
        assertEquals(ORIGINAL_AI_MASTER_PRESET_ID, f.controller.selection.value!!.id)
        assertEquals(1, f.materialCalls); assertEquals(1, f.exclusiveCalls)
        f.controller.reportProcessing(ORIGINAL_MOOD_PRESET_ID)
        assertFalse(f.controller.state.value.processing)
        f.controller.reportProcessing(ORIGINAL_AI_MASTER_PRESET_ID)
        assertTrue(f.controller.state.value.processing)
        f.rights = OriginalMoodEntitlement(10, false, 120_000)
        advanceTimeBy(120_001); runCurrent()
        assertFalse(f.controller.state.value.enabled); assertNull(f.controller.selection.value)
        assertEquals(OriginalSmartPreferences(), f.saved)
        f.controller.close()
    }

    @Test fun noSvipNeverLoads104MaterialsOrDisablesOtherEffects() = runTest {
        val f = Fixture(this); f.rights = OriginalMoodEntitlement(10, false, 0); runCurrent()
        f.controller.setEnabled(true); runCurrent()
        assertFalse(f.controller.state.value.enabled); assertNull(f.controller.selection.value)
        assertEquals(0, f.materialCalls); assertEquals(0, f.exclusiveCalls)
        f.controller.close()
    }

    @Test fun disableAndAccountChangeDiscardLateAuthorization() = runTest {
        val f = Fixture(this); val pending = CompletableDeferred<OriginalMoodEntitlement>()
        f.query = { withContext(NonCancellable) { pending.await() } }; runCurrent()
        f.controller.setEnabled(true); runCurrent()
        f.controller.disable(); f.account.value = OriginalSmartAccount(20, 1, true); runCurrent()
        pending.complete(OriginalMoodEntitlement(10, true, 120_000)); runCurrent()
        assertFalse(f.controller.state.value.enabled); assertNull(f.controller.selection.value)
        assertEquals(0, f.materialCalls); assertEquals(0, f.exclusiveCalls)
        assertEquals(OriginalSmartPreferences(), f.saved)
        f.controller.close()
    }

    @Test fun diySavesRecomposesAndSurvivesSongChangeAndOffWithoutInventedMetadata() = runTest {
        val f = Fixture(this); runCurrent(); f.controller.setEnabled(true); runCurrent()
        val initial = f.controller.selection.value!!.bytes
        val chosen = setOf("en_bass", "en_vibe", "en_live")
        f.controller.setDiy(chosen); runCurrent()
        assertEquals(chosen, f.diy[10])
        assertEquals(chosen, f.controller.state.value.selectedDiy)
        assertEquals(setOf("en_bass", "en_vibe"), f.controller.state.value.usedDiy)
        assertFalse(initial.contentEquals(f.controller.selection.value!!.bytes))
        assertEquals(1, f.materialCalls) // Reuse current verified song material for preference changes.
        assertEquals("温润", f.controller.state.value.baseName)
        assertEquals("网易云真实长文案", f.controller.state.value.longText)
        f.songs.value = Song(2, "next"); runCurrent()
        assertEquals(2, f.materialCalls)
        assertEquals(chosen, f.controller.state.value.selectedDiy)
        f.controller.disable(); runCurrent()
        assertEquals(chosen, f.controller.state.value.selectedDiy)
        assertEquals(chosen, f.diy[10])
        assertTrue(f.controller.state.value.usedDiy.isEmpty())
        f.controller.setEnabled(true); runCurrent()
        assertEquals(chosen, f.controller.state.value.selectedDiy)
        f.controller.close()
    }

    @Test fun accountChangesLoadOnlyThatAccountsDiyAndNeverAutoEnable() = runTest {
        val f = Fixture(this)
        f.diy[10] = setOf("en_bass"); f.diy[20] = setOf("en_vocal")
        runCurrent(); assertEquals(setOf("en_bass"), f.controller.state.value.selectedDiy)
        f.controller.setEnabled(true); runCurrent()
        f.account.value = OriginalSmartAccount(20, 1, true); runCurrent()
        assertFalse(f.controller.state.value.enabled)
        assertEquals(setOf("en_vocal"), f.controller.state.value.selectedDiy)
        f.account.value = OriginalSmartAccount(10, 2, true); runCurrent()
        assertEquals(setOf("en_bass"), f.controller.state.value.selectedDiy)
        assertFalse(f.controller.state.value.enabled)
        f.controller.close()
    }

    @Test fun lateSaveCannotOverwriteNewerDiyOrCurrentAccountState() = runTest {
        val f = Fixture(this); val release = CompletableDeferred<Unit>(); var first = true
        f.saveDiy = { uid, chosen ->
            if (first) { first = false; withContext(NonCancellable) { release.await() } }
            f.diy[uid] = chosen
        }
        runCurrent()
        f.controller.setDiy(setOf("en_bass")); runCurrent()
        assertTrue(f.controller.state.value.diySaving)
        f.controller.setDiy(setOf("en_live")); runCurrent()
        release.complete(Unit); runCurrent()
        assertEquals(setOf("en_live"), f.diy[10])
        assertEquals(setOf("en_live"), f.controller.state.value.selectedDiy)
        assertFalse(f.controller.state.value.diySaving)
        f.account.value = OriginalSmartAccount(20, 1, true); runCurrent()
        assertTrue(f.controller.state.value.selectedDiy.isEmpty())
        f.controller.close()
    }
}
