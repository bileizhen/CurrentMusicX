package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.effects.NeteaseEffectEntitlement
import io.github.currencortex.music.data.settings.OriginalSmartPreferences
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OriginalSmartEffectsControllerTest {
    private val definition = """{"0":{"eq":{"on":false}},"1":{"bt":{"on":true,"bass":2,"treble":1}}}""".toByteArray()

    private inner class Fixture(val test: TestScope, stored: OriginalSmartPreferences = OriginalSmartPreferences(),
        initialEngine: Boolean = true, val startup: CompletableDeferred<Unit>? = null) {
        var saved = stored
        val writes = mutableListOf<OriginalSmartPreferences>()
        var entitlementCalls = 0
        var keyCalls = mutableListOf<Long>()
        var enabledCallbacks = 0
        var definitionReads = 0
        var wallClockOffset = 0L
        var resource: ByteArray? = definition
        var right = NeteaseEffectEntitlement(10, true, 1_000_000)
        var checkRight: suspend () -> NeteaseEffectEntitlement = { right }
        var lookup: suspend (Long) -> String = { "1" }
        val account = MutableStateFlow(OriginalSmartAccount(10, 0, true))
        val song = MutableStateFlow<Song?>(Song(1, "one"))
        val engine = MutableStateFlow(initialEngine)
        val controller = OriginalSmartEffectsController(account,
            ready = { startup?.await(); Unit }, readSaved = { saved },
            writeSaved = { saved = it; writes += it },
            entitlement = { entitlementCalls++; checkRight() },
            smartKey = { keyCalls += it; lookup(it) }, songs = song, engineStatus = engine,
            scope = test.backgroundScope, beforeEnable = { enabledCallbacks++ },
            definition = { definitionReads++; resource }, now = { test.testScheduler.currentTime + wallClockOffset },
            workDispatcher = StandardTestDispatcher(test.testScheduler))
    }

    @Test fun disabledNeverQueriesMembershipSongClassificationOrResources() = runTest {
        val f = Fixture(this)
        runCurrent()
        f.song.value = Song(2, "two"); runCurrent()
        assertEquals(0, f.entitlementCalls)
        assertTrue(f.keyCalls.isEmpty())
        assertEquals(0, f.definitionReads)
        assertNull(f.controller.selection.value)
        f.controller.close()
    }

    @Test fun publicSongClassificationCannotSubstituteForActualBlackVipRights() = runTest {
        val f = Fixture(this)
        f.right = NeteaseEffectEntitlement(10, false, 0)
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        assertFalse(f.controller.state.value.enabled)
        assertFalse(f.controller.state.value.requestedEnabled)
        assertTrue(f.controller.state.value.message.contains("黑胶会员"))
        assertTrue(f.keyCalls.isEmpty())
        assertEquals(0, f.enabledCallbacks)
        assertEquals(OriginalSmartPreferences(), f.saved)
        f.controller.close()
    }

    @Test fun mismatchedSavedOwnerNeverRestoresForAnotherLoggedInAccount() = runTest {
        val f = Fixture(this, OriginalSmartPreferences(true, 20))
        runCurrent()
        assertEquals(OriginalSmartPreferences(), f.saved)
        assertEquals(0, f.entitlementCalls)
        assertNull(f.controller.selection.value)
        f.controller.close()
    }

    @Test fun verifiedActivationUsesExactOriginalSubPresetAndCloseKeepsSavedIntent() = runTest {
        val f = Fixture(this)
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        val selected = requireNotNull(f.controller.selection.value)
        assertEquals(-110L, selected.id)
        assertTrue(selected.bytes.toString(Charsets.UTF_8).contains("bass"))
        assertEquals("1", f.controller.state.value.soundKey)
        assertEquals(OriginalSmartPreferences(true, 10), f.saved)
        assertEquals(1, f.enabledCallbacks)
        assertTrue(f.controller.state.value.enabled)
        assertFalse(f.controller.state.value.processing)
        f.controller.reportProcessing(1001); assertFalse(f.controller.state.value.processing)
        f.controller.reportProcessing(-110); assertTrue(f.controller.state.value.processing)
        f.controller.close()
        assertNull(f.controller.selection.value)
        assertEquals(OriginalSmartPreferences(true, 10), f.saved)
    }

    @Test fun missingSoundKeyUsesTheOriginalDefaultZeroPackage() = runTest {
        val f = Fixture(this)
        f.lookup = { "999" }
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        assertEquals("0", f.controller.state.value.soundKey)
        assertFalse(f.controller.selection.value!!.bytes.toString(Charsets.UTF_8).contains("bass"))
        f.controller.close()
    }

    @Test fun uncooperativeOldSongResponseCannotBlockOrReplaceNewSong() = runTest {
        val f = Fixture(this)
        val late = CompletableDeferred<String>()
        f.lookup = { id -> if (id == 1L) withContext(NonCancellable) { late.await() } else "1" }
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        assertTrue(f.controller.state.value.loading)
        f.song.value = Song(2, "two"); runCurrent()
        assertEquals(2L, f.controller.state.value.songId)
        assertEquals("1", f.controller.state.value.soundKey)
        val current = f.controller.selection.value
        late.complete("0"); runCurrent()
        assertSame(current, f.controller.selection.value)
        assertEquals(1, f.enabledCallbacks)
        f.controller.close()
    }

    @Test fun explicitDisableImmediatelyRevokesAndRejectsLateLookup() = runTest {
        val f = Fixture(this)
        val late = CompletableDeferred<String>()
        f.lookup = { withContext(NonCancellable) { late.await() } }
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        f.controller.disable()
        assertFalse(f.controller.state.value.enabled)
        assertNull(f.controller.selection.value)
        runCurrent(); late.complete("1"); runCurrent()
        assertNull(f.controller.selection.value)
        assertEquals(OriginalSmartPreferences(), f.saved)
        assertEquals(0, f.enabledCallbacks)
        f.controller.close()
    }

    @Test fun absentEngineOrDefinitionKeepsAccountBoundIntentButDoesNotClaimActivation() = runTest {
        val f = Fixture(this, OriginalSmartPreferences(true, 10), initialEngine = false)
        runCurrent()
        assertEquals(OriginalSmartPreferences(true, 10), f.saved)
        assertFalse(f.controller.state.value.enabled)
        assertFalse(f.controller.state.value.processing)
        assertNull(f.controller.selection.value)
        f.resource = null; f.engine.value = true; runCurrent()
        assertFalse(f.controller.state.value.enabled)
        assertTrue(f.controller.state.value.message.contains("资源"))
        assertEquals(0, f.enabledCallbacks)
        f.resource = definition; f.controller.refresh(); runCurrent()
        assertNotNull(f.controller.selection.value)
        assertTrue(f.controller.state.value.enabled)
        f.controller.close()
    }

    @Test fun membershipExpiresDuringPlaybackAndRestoresManualWithoutWaitingForNextSong() = runTest {
        val f = Fixture(this)
        f.right = NeteaseEffectEntitlement(10, true, 1_000)
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        assertNotNull(f.controller.selection.value)
        advanceTimeBy(1_001); runCurrent()
        assertNull(f.controller.selection.value)
        assertFalse(f.controller.state.value.enabled)
        assertEquals(OriginalSmartPreferences(), f.saved)
        f.controller.close()
    }

    @Test fun accountRevisionRevalidatesAndLogoutRevokesSavedIntent() = runTest {
        val f = Fixture(this)
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        assertNotNull(f.controller.selection.value)
        f.right = NeteaseEffectEntitlement(10, false, 0)
        f.account.value = f.account.value.copy(revision = 1)
        runCurrent()
        assertNull(f.controller.selection.value)
        assertFalse(f.controller.state.value.enabled)
        assertEquals(OriginalSmartPreferences(), f.saved)
        f.controller.close()
        val loggedOut = Fixture(this, OriginalSmartPreferences(true, 10))
        loggedOut.account.value = OriginalSmartAccount(0, 2, false)
        runCurrent()
        assertEquals(0, loggedOut.entitlementCalls)
        assertNull(loggedOut.controller.selection.value)
        assertEquals(OriginalSmartPreferences(), loggedOut.saved)
        loggedOut.controller.close()
    }

    @Test fun nonNeteaseOrVideoNeverRequestsSongKeyAndStillEnforcesMutualExclusion() = runTest {
        for (song in listOf<Song?>(null, Song(3, "video", video = true), Song(4, "qq", musicSource = MusicSource.QQ_MUSIC))) {
            val f = Fixture(this)
            f.song.value = song
            runCurrent(); f.controller.setEnabled(true); runCurrent()
            assertTrue(f.keyCalls.isEmpty())
            assertTrue(f.controller.state.value.enabled)
            assertNull(f.controller.selection.value)
            assertEquals(1, f.enabledCallbacks)
            f.controller.close()
        }
    }

    @Test fun disableBeforeStartupPreventsRestoringPreviouslySavedEnabledFlag() = runTest {
        val startup = CompletableDeferred<Unit>()
        val f = Fixture(this, OriginalSmartPreferences(true, 10), startup = startup)
        f.controller.disable(); runCurrent(); startup.complete(Unit); runCurrent()
        assertEquals(0, f.entitlementCalls)
        assertNull(f.controller.selection.value)
        assertEquals(OriginalSmartPreferences(), f.saved)
        f.controller.close()
    }

    @Test fun temporarilyFailedRightsLookupDoesNotClaimActivationAndCanRetry() = runTest {
        val f = Fixture(this, OriginalSmartPreferences(true, 10))
        f.checkRight = { throw java.io.IOException("offline") }
        runCurrent()
        assertNull(f.controller.selection.value)
        assertFalse(f.controller.state.value.enabled)
        assertEquals(OriginalSmartPreferences(true, 10), f.saved)
        f.checkRight = { f.right }; f.controller.refresh(); runCurrent()
        assertNotNull(f.controller.selection.value)
        f.controller.close()
    }

    @Test fun nativeFailureWithdrawsAudioAndKeepsAccountBoundRetryIntent() = runTest {
        val f = Fixture(this)
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        f.controller.reportProcessing(-110)
        assertTrue(f.controller.state.value.processing)
        f.controller.reportFailure("原版引擎处理失败")
        assertNull(f.controller.selection.value)
        assertFalse(f.controller.state.value.enabled)
        assertFalse(f.controller.state.value.processing)
        assertEquals("原版引擎处理失败", f.controller.state.value.message)
        assertEquals(OriginalSmartPreferences(true, 10), f.saved)
        f.controller.refresh(); runCurrent()
        assertNotNull(f.controller.selection.value)
        f.controller.close()
    }

    @Test fun pcmAcknowledgementImmediatelyWithdrawsRightsAfterForwardWallClockCorrection() = runTest {
        val f = Fixture(this)
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        assertNotNull(f.controller.selection.value)
        f.wallClockOffset = 2_000_000
        f.controller.reportProcessing(-110)
        assertNull(f.controller.selection.value)
        assertFalse(f.controller.state.value.processing)
        runCurrent()
        assertFalse(f.controller.state.value.enabled)
        assertEquals(OriginalSmartPreferences(), f.saved)
        f.controller.close()
    }

    @Test fun idleMembershipWatchRechecksWallClockWithinSixtySeconds() = runTest {
        val f = Fixture(this)
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        assertNotNull(f.controller.selection.value)
        f.wallClockOffset = 2_000_000
        advanceTimeBy(60_001); runCurrent()
        assertNull(f.controller.selection.value)
        assertFalse(f.controller.state.value.enabled)
        assertEquals(OriginalSmartPreferences(), f.saved)
        f.controller.close()
    }
}
