package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.effects.OriginalMoodEntitlement
import io.github.currencortex.music.data.settings.OriginalSmartPreferences
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OriginalMoodEffectsControllerTest {
    private class Fixture(val test: TestScope, stored: OriginalSmartPreferences = OriginalSmartPreferences()) {
        var saved = stored
        var rightsCalls = 0
        var definitionCalls = 0
        var enabledCalls = 0
        var modelCalls = 0
        var descriptorCalls = 0
        var right = OriginalMoodEntitlement(10, false, 0)
        var query: suspend () -> OriginalMoodEntitlement = { right }
        val account = MutableStateFlow(OriginalSmartAccount(10, 0, true))
        val song = MutableStateFlow<Song?>(Song(1, "synthetic"))
        val engine = MutableStateFlow(true)
        val controller = OriginalMoodEffectsController(accounts = account, ready = {}, readSaved = { saved },
            writeSaved = { saved = it }, entitlement = { rightsCalls++; query() },
            prepareModel = { modelCalls++; error("Unauthorized path must not load the model") },
            descriptor = { descriptorCalls++; error("Unauthorized path must not fetch song descriptors") },
            songs = song, engineStatus = engine, scope = test.backgroundScope,
            beforeEnable = { enabledCalls++ }, definition = { definitionCalls++; null },
            now = { test.testScheduler.currentTime }, workDispatcher = StandardTestDispatcher(test.testScheduler))

        fun assertWithdrawn() {
            assertNull(controller.selection.value)
            assertFalse(controller.state.value.enabled)
            assertFalse(controller.state.value.processing)
            assertEquals(0, modelCalls); assertEquals(0, descriptorCalls)
            assertEquals(0, enabledCalls); assertEquals(0, definitionCalls)
        }
    }

    @Test fun defaultDisabledDoesNotReadAnyMembershipResourceOrModel() = runTest {
        val f = Fixture(this)
        runCurrent(); f.song.value = Song(2, "next"); runCurrent()
        assertEquals(0, f.rightsCalls); f.assertWithdrawn(); f.controller.close()
    }

    @Test fun blackVipWithoutTheSpecific106BenefitCannotActivateAi() = runTest {
        val f = Fixture(this)
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        assertEquals(1, f.rightsCalls)
        assertFalse(f.controller.state.value.requestedEnabled)
        assertTrue(f.controller.state.value.message.contains("专属权益"))
        assertEquals(OriginalSmartPreferences(), f.saved)
        f.assertWithdrawn(); f.controller.close()
    }

    @Test fun alreadyExpiredSpecificRightIsRejectedBeforeLoadingAlgorithms() = runTest {
        val f = Fixture(this)
        f.right = OriginalMoodEntitlement(10, true, 100)
        advanceTimeBy(101); runCurrent(); f.controller.setEnabled(true); runCurrent()
        assertFalse(f.controller.state.value.requestedEnabled)
        assertEquals(OriginalSmartPreferences(), f.saved)
        f.assertWithdrawn(); f.controller.close()
    }

    @Test fun savedIntentForAnotherAccountIsRevokedWithoutQuery() = runTest {
        val f = Fixture(this, OriginalSmartPreferences(true, 20))
        runCurrent()
        assertEquals(0, f.rightsCalls)
        assertEquals(OriginalSmartPreferences(), f.saved)
        f.assertWithdrawn(); f.controller.close()
    }

    @Test fun responseForAnotherAccountCannotBecomeAuthorization() = runTest {
        val f = Fixture(this)
        f.right = OriginalMoodEntitlement(20, true, 100_000)
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        assertEquals(OriginalSmartPreferences(), f.saved)
        f.assertWithdrawn(); f.controller.close()
    }

    @Test fun lateAllowedResponseAfterAccountChangeCannotRestoreOldIntent() = runTest {
        val f = Fixture(this)
        val delayed = CompletableDeferred<OriginalMoodEntitlement>()
        f.query = { withContext(NonCancellable) { delayed.await() } }
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        assertTrue(f.controller.state.value.loading)
        f.account.value = OriginalSmartAccount(20, 1, true)
        runCurrent()
        delayed.complete(OriginalMoodEntitlement(10, true, 1_000_000)); runCurrent()
        assertEquals(OriginalSmartPreferences(), f.saved)
        assertFalse(f.controller.state.value.requestedEnabled)
        f.assertWithdrawn(); f.controller.close()
    }

    @Test fun explicitDisableRejectsLateSuccessfulBenefitResponse() = runTest {
        val f = Fixture(this)
        val delayed = CompletableDeferred<OriginalMoodEntitlement>()
        f.query = { withContext(NonCancellable) { delayed.await() } }
        runCurrent(); f.controller.setEnabled(true); runCurrent()
        f.controller.disable(); runCurrent()
        delayed.complete(OriginalMoodEntitlement(10, true, 1_000_000)); runCurrent()
        assertEquals(OriginalSmartPreferences(), f.saved)
        assertFalse(f.controller.state.value.requestedEnabled)
        f.assertWithdrawn(); f.controller.close()
    }

    @Test fun logoutOrStaleCredentialNeverQueriesTheBenefit() = runTest {
        for (account in listOf(OriginalSmartAccount(0, 2, false), OriginalSmartAccount(10, 2, true, true))) {
            val f = Fixture(this, OriginalSmartPreferences(true, 10))
            f.account.value = account; runCurrent()
            assertEquals(0, f.rightsCalls)
            assertEquals(OriginalSmartPreferences(), f.saved)
            f.assertWithdrawn(); f.controller.close()
        }
    }
}
