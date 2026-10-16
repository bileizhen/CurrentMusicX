package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.effects.NeteaseSoundEffect
import io.github.currencortex.music.data.effects.NeteaseEffectEntitlement
import io.github.currencortex.music.data.settings.SavedOriginalSoundEffect
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OriginalSoundEffectsControllerTest {
    private fun effect(id: Long = 1001, name: String = "current name") = NeteaseSoundEffect(id, name, "",
        memberType = 1, presetLink = "https://d1.music.126.net/a.ncae", presetMd5 = "a".repeat(32))
    private fun preset(effect: NeteaseSoundEffect) = NeteaseOriginalPreset(effect.id, effect.name, 1, byteArrayOf(1))
    private inner class Fixture(scope: CoroutineScope, savedValue: SavedOriginalSoundEffect? = null,
        engine: Boolean = true, private val startup: CompletableDeferred<Unit>? = null) {
        var saved = savedValue
        var available = engine
        var catalogCalls = 0
        val resolved = mutableListOf<Long>()
        var resolve: suspend (Long) -> NeteaseSoundEffect? = { null }
        var prepared = mutableListOf<Long>()
        var activated = 0
        var rightsCalls = 0
        var clock = 100L
        var rights = NeteaseEffectEntitlement(10)
        var checkRights: suspend () -> NeteaseEffectEntitlement = { rights }
        var rows = listOf(effect())
        var load: suspend (NeteaseSoundEffect) -> NeteaseOriginalPreset = { preset(it) }
        val account = MutableStateFlow(OriginalEffectsAccount("netease:10", 0))
        val controller = OriginalSoundEffectsController(account,
            ready = { startup?.await(); Unit }, readSaved = { saved }, writeSaved = { saved = it },
            catalog = { catalogCalls++; rows }, prepare = { prepared += it.id; load(it) },
            scope = scope, engineAvailable = { available }, entitlement = { rightsCalls++; checkRights() },
            now = { clock }, resolveFromServer = { resolved += it; resolve(it) }, beforeSelection = { activated++ })
    }

    @Test fun restartRevalidatesCurrentCatalogAndKeepsSelectionOnContainerClose() = runTest {
        val stored = SavedOriginalSoundEffect(1001, "old label", "netease:10")
        val first = Fixture(backgroundScope, stored)
        runCurrent()
        assertEquals(listOf(1001L), first.prepared)
        assertEquals(1, first.catalogCalls)
        assertEquals("current name", first.saved!!.name)
        assertEquals(1001L, first.controller.selection.value!!.id)
        assertFalse(first.controller.state.value.processing)
        first.controller.close()
        assertEquals(1001L, first.saved!!.effectId)
        val restarted = Fixture(backgroundScope, first.saved)
        runCurrent()
        assertEquals(listOf(1001L), restarted.prepared)
        assertEquals(1, restarted.catalogCalls)
        restarted.controller.close()
    }

    @Test fun differentAccountCannotRestoreSavedChoiceOrDownloadAnyPreset() = runTest {
        val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(1001, "old", "netease:20"))
        runCurrent()
        assertNull(fixture.saved)
        assertNull(fixture.controller.selection.value)
        assertTrue(fixture.prepared.isEmpty())
        assertEquals(0, fixture.catalogCalls)
        fixture.controller.close()
    }

    @Test fun paidOrRemovedEffectIsNotRestoredFromOldSavedMetadata() = runTest {
        for (rows in listOf(emptyList(), listOf(effect().copy(memberType = 3)))) {
            val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(1001, "old", "netease:10"))
            fixture.rows = rows
            runCurrent()
            assertNull(fixture.saved)
            assertTrue(fixture.prepared.isEmpty())
            assertNull(fixture.controller.selection.value)
            assertNotNull(fixture.controller.state.value.error)
            fixture.controller.close()
        }
    }

    @Test fun clearBeforeStartupFinishesCannotAccidentallyRestoreOldChoice() = runTest {
        val startup = CompletableDeferred<Unit>()
        val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(1001, "old", "netease:10"), startup = startup)
        fixture.controller.clear()
        runCurrent()
        startup.complete(Unit)
        runCurrent()
        assertNull(fixture.saved)
        assertNull(fixture.controller.selection.value)
        assertTrue(fixture.prepared.isEmpty())
        fixture.controller.close()
    }

    @Test fun accountSwitchOrExplicitOffRejectsLateUncooperativePreparation() = runTest {
        val fixture = Fixture(backgroundScope)
        runCurrent()
        val late = CompletableDeferred<NeteaseOriginalPreset>()
        fixture.load = { withContext(NonCancellable) { late.await() } }
        fixture.controller.select(effect())
        runCurrent()
        assertTrue(fixture.controller.state.value.loading)
        fixture.account.value = OriginalEffectsAccount("netease:20", 1)
        runCurrent()
        late.complete(preset(effect()))
        runCurrent()
        assertNull(fixture.controller.selection.value)
        assertNull(fixture.saved)
        assertEquals(0, fixture.activated)
        fixture.controller.close()
    }

    @Test fun temporarilyMissingEngineKeepsSavedIntentAndImportCanResumeRestore() = runTest {
        val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(1001, "old", "netease:10"), engine = false)
        runCurrent()
        assertNotNull(fixture.saved)
        assertTrue(fixture.prepared.isEmpty())
        assertFalse(fixture.controller.state.value.available)
        fixture.available = true
        fixture.controller.refreshAvailability()
        runCurrent()
        assertEquals(1001L, fixture.controller.selection.value!!.id)
        assertTrue(fixture.controller.state.value.available)
        fixture.controller.close()
    }

    @Test fun transientNetworkFailureCanRetryButDoesNotClaimAudioIsProcessing() = runTest {
        val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(1001, "old", "netease:10"))
        fixture.load = { throw java.io.IOException("offline") }
        runCurrent()
        assertNotNull(fixture.saved)
        assertNull(fixture.controller.selection.value)
        assertFalse(fixture.controller.state.value.processing)
        assertNotNull(fixture.controller.state.value.error)
        fixture.load = { preset(it) }
        fixture.controller.retry()
        runCurrent()
        assertEquals(1001L, fixture.controller.selection.value!!.id)
        assertFalse(fixture.controller.state.value.processing)
        fixture.controller.reportProcessing(999)
        assertFalse(fixture.controller.state.value.processing)
        fixture.controller.reportProcessing(1001)
        assertTrue(fixture.controller.state.value.processing)
        fixture.controller.clear()
        runCurrent()
        assertNull(fixture.saved)
        assertFalse(fixture.controller.state.value.processing)
        fixture.controller.close()
    }

    @Test fun closingThenFailedNewSelectionCannotRevivePreviouslySavedEffect() = runTest {
        val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(1001, "old", "netease:10"))
        runCurrent()
        fixture.controller.clear()
        fixture.load = { throw java.io.IOException("offline") }
        fixture.controller.select(effect(1008, "new"))
        runCurrent()
        assertNull(fixture.saved)
        assertNull(fixture.controller.selection.value)
        fixture.controller.close()
    }

    @Test fun knownUnsupportedParametersExposeAReasonAndKeepPreviousSound() = runTest {
        val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(1001, "old", "netease:10"))
        runCurrent()
        val previous = fixture.controller.selection.value
        fixture.load = { throw UnsupportedOperationException("当前引擎不支持模块 example") }
        fixture.controller.select(effect(1008))
        runCurrent()
        assertSame(previous, fixture.controller.selection.value)
        assertEquals(1001L, fixture.saved!!.effectId)
        assertEquals("当前引擎不支持模块 example", fixture.controller.state.value.unsupported[1008])
        assertEquals("当前引擎不支持模块 example", originalEffectUnavailableReason(effect(1008), fixture.controller.state.value))
        fixture.controller.close()
    }

    @Test fun restartPrefersFreshAndroidMaterialOverAnIncompleteLegacyCatalogEntry() = runTest {
        val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(3595400, "old name", "netease:10"))
        fixture.rows = listOf(effect(3595400).copy(presetMd5 = ""))
        fixture.resolve = { effect(it, "fresh vinyl") }
        runCurrent()
        assertEquals(listOf(3595400L), fixture.prepared)
        assertEquals("fresh vinyl", fixture.controller.selection.value!!.name)
        fixture.controller.close()
    }

    @Test fun explicitRetryCanRevalidateAnIdAfterUnsupportedMaterialWasReplaced() = runTest {
        val fixture = Fixture(backgroundScope)
        runCurrent()
        fixture.load = { throw UnsupportedOperationException("old material") }
        fixture.controller.select(effect()); runCurrent()
        assertTrue(fixture.controller.state.value.unsupported.containsKey(1001))
        fixture.resolve = { effect(it).copy(presetMd5 = "b".repeat(32)) }
        fixture.load = { preset(it) }
        fixture.controller.retry(); runCurrent()
        assertEquals(1001L, fixture.controller.selection.value!!.id)
        assertTrue(fixture.controller.state.value.unsupported.isEmpty())
        fixture.controller.close()
    }

    @Test fun cancellingSupersededPreparationPreservesActivePresetAndRejectsLateResult() = runTest {
        val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(1001, "active", "netease:10"))
        runCurrent()
        val active = fixture.controller.selection.value
        val late = CompletableDeferred<NeteaseOriginalPreset>()
        fixture.load = { withContext(NonCancellable) { late.await() } }
        fixture.controller.select(effect(1008)); runCurrent()
        fixture.controller.cancelPendingSelection(); runCurrent()
        assertSame(active, fixture.controller.selection.value)
        assertFalse(fixture.controller.state.value.loading)
        late.complete(preset(effect(1008))); runCurrent()
        assertSame(active, fixture.controller.selection.value)
        assertEquals(1001L, fixture.saved!!.effectId)
        fixture.controller.close()
    }

    @Test fun legacyMemberPresetRequiresLoginAndRealMatchingBlackVipRights() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.account.value = OriginalEffectsAccount("anonymous", 0)
        runCurrent()
        fixture.controller.select(effect(1005).copy(memberType = 3))
        runCurrent()
        assertTrue(fixture.controller.state.value.error!!.contains("登录"))
        assertEquals(0, fixture.rightsCalls)
        assertTrue(fixture.prepared.isEmpty())
        fixture.account.value = OriginalEffectsAccount("netease:10", 1)
        runCurrent()
        for (rights in listOf(NeteaseEffectEntitlement(10, false, 1000),
            NeteaseEffectEntitlement(20, true, 1000), NeteaseEffectEntitlement(10, true, 99))) {
            fixture.rights = rights
            fixture.controller.select(effect(1005).copy(memberType = 3))
            runCurrent()
            assertNull(fixture.controller.selection.value)
            assertFalse(fixture.controller.state.value.permissionPending)
            assertTrue(fixture.prepared.isEmpty())
        }
        assertEquals(3, fixture.rightsCalls)
        fixture.controller.close()
    }

    @Test fun svipSelectionUsesItsOwnProductExpiryAndRevokesItEvenWhileBlackVipRemainsActive() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.rights = NeteaseEffectEntitlement(10, true, 9000, false, 0)
        runCurrent()
        val vinyl = effect(3595400, "原声黑胶").copy(memberType = 4)
        assertNull(originalEffectUnavailableReason(vinyl, fixture.controller.state.value))
        fixture.controller.select(vinyl)
        runCurrent()
        assertTrue(fixture.controller.state.value.error!!.contains("SVIP"))
        assertTrue(fixture.prepared.isEmpty())
        fixture.rights = NeteaseEffectEntitlement(10, true, 9000, true, 1100)
        fixture.controller.select(vinyl)
        runCurrent()
        assertEquals(3595400L, fixture.controller.selection.value!!.id)
        assertEquals(1100L, fixture.controller.state.value.permissionExpiresAt)
        fixture.clock = 1100
        advanceTimeBy(1000); runCurrent()
        assertNull(fixture.controller.selection.value)
        assertNull(fixture.saved)
        fixture.controller.close()
    }

    @Test fun otherAccountSvipAndUnknownAuthorizationStrategiesCannotPreparePresets() = runTest {
        val fixture = Fixture(backgroundScope)
        runCurrent()
        val vinyl = effect(3595400).copy(memberType = 4)
        fixture.rights = NeteaseEffectEntitlement(20, false, 0, true, 9000)
        fixture.controller.select(vinyl); runCurrent()
        assertTrue(fixture.prepared.isEmpty())
        assertNull(fixture.controller.selection.value)
        val strategy = vinyl.copy(strategyCode = "not-integrated")
        assertNotNull(originalEffectUnavailableReason(strategy, fixture.controller.state.value))
        fixture.controller.select(strategy); runCurrent()
        assertTrue(fixture.prepared.isEmpty())
        fixture.controller.close()
    }

    @Test fun activeBlackVipPresetExpiresAndRevokesPersistedChoiceWithoutAnotherPlaybackEvent() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.rights = NeteaseEffectEntitlement(10, true, 1100)
        runCurrent()
        fixture.controller.select(effect(1005).copy(memberType = 3))
        runCurrent()
        assertEquals(1005L, fixture.controller.selection.value!!.id)
        assertEquals(1100L, fixture.controller.state.value.permissionExpiresAt)
        assertEquals(1005L, fixture.saved!!.effectId)
        fixture.clock = 1099
        advanceTimeBy(999)
        runCurrent()
        assertNotNull(fixture.controller.selection.value)
        fixture.clock = 1100
        advanceTimeBy(1)
        runCurrent()
        assertNull(fixture.controller.selection.value)
        assertNull(fixture.saved)
        assertTrue(fixture.controller.state.value.error!!.contains("到期"))
        fixture.controller.close()
    }

    @Test fun accountChangeRejectsLateMembershipResponseBeforeAnyMaterialIsPrepared() = runTest {
        val fixture = Fixture(backgroundScope)
        val late = CompletableDeferred<NeteaseEffectEntitlement>()
        fixture.checkRights = { withContext(NonCancellable) { late.await() } }
        runCurrent()
        fixture.controller.select(effect(1005).copy(memberType = 3))
        runCurrent()
        assertTrue(fixture.controller.state.value.permissionPending)
        fixture.account.value = OriginalEffectsAccount("netease:20", 1)
        runCurrent()
        late.complete(NeteaseEffectEntitlement(10, true, 1000))
        runCurrent()
        assertTrue(fixture.prepared.isEmpty())
        assertNull(fixture.saved)
        assertNull(fixture.controller.selection.value)
        fixture.controller.close()
    }

    @Test fun restoredMemberPresetMustRecheckRightsAndCannotReuseOldSavedChoiceAfterRevocation() = runTest {
        val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(1005, "member", "netease:10"))
        fixture.rows = listOf(effect(1005).copy(memberType = 3))
        runCurrent()
        assertEquals(1, fixture.rightsCalls)
        assertNull(fixture.saved)
        assertTrue(fixture.prepared.isEmpty())
        assertNull(fixture.controller.selection.value)
        fixture.controller.close()
    }

    @Test fun unknownNewAuthorizationStrategyNeverFallsBackToLegacyVipOrPublicPermission() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.rights = NeteaseEffectEntitlement(10, true, 1000)
        runCurrent()
        for (candidate in listOf(effect().copy(strategyCode = "new-tier"),
            effect().copy(memberType = 3, hasVipAuthStrategy = true))) {
            fixture.controller.select(candidate)
            runCurrent()
            assertTrue(fixture.controller.state.value.error!!.contains("授权策略"))
        }
        assertEquals(0, fixture.rightsCalls)
        assertTrue(fixture.prepared.isEmpty())
        fixture.controller.close()
    }

    @Test fun membershipExpiringDuringPreparationCannotReplaceAlreadySelectedPublicPreset() = runTest {
        val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(1001, "public", "netease:10"))
        runCurrent()
        val original = fixture.controller.selection.value
        val prepared = CompletableDeferred<NeteaseOriginalPreset>()
        fixture.rights = NeteaseEffectEntitlement(10, true, 1000)
        fixture.load = { prepared.await() }
        fixture.controller.select(effect(1005).copy(memberType = 3))
        runCurrent()
        fixture.clock = 1000
        prepared.complete(preset(effect(1005)))
        runCurrent()
        assertSame(original, fixture.controller.selection.value)
        assertEquals(1001L, fixture.saved!!.effectId)
        assertTrue(fixture.controller.state.value.error!!.contains("到期"))
        fixture.controller.close()
    }

    @Test fun newOfficialIdRestoresFromFreshServerRecordRatherThanSavedLabelOrMaterial() = runTest {
        val id = 3601400L
        val first = Fixture(backgroundScope, SavedOriginalSoundEffect(id, "stale saved name", "netease:10"))
        first.resolve = { effect(it, "fresh server name").copy(presetMd5 = "b".repeat(32)) }
        var preparedHash = ""
        first.load = { preparedHash = it.presetMd5; preset(it) }
        runCurrent()
        assertEquals(listOf(id), first.resolved)
        assertEquals(listOf(id), first.prepared)
        assertEquals("b".repeat(32), preparedHash)
        assertEquals("fresh server name", first.saved!!.name)
        assertEquals(id, first.controller.selection.value!!.id)
        first.controller.close()
        val restart = Fixture(backgroundScope, first.saved)
        restart.resolve = { effect(it, "server changed after restart").copy(presetMd5 = "c".repeat(32)) }
        restart.load = { preparedHash = it.presetMd5; preset(it) }
        runCurrent()
        assertEquals(listOf(id), restart.resolved)
        assertEquals("c".repeat(32), preparedHash)
        assertEquals("server changed after restart", restart.saved!!.name)
        restart.controller.close()
    }

    @Test fun missingOrMismatchedServerIdCannotRestoreOrPrepareSavedNewChoice() = runTest {
        for (returned in listOf(null, effect(3600400))) {
            val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(3601400, "old", "netease:10"))
            fixture.resolve = { returned }
            runCurrent()
            assertEquals(listOf(3601400L), fixture.resolved)
            assertNull(fixture.saved)
            assertNull(fixture.controller.selection.value)
            assertTrue(fixture.prepared.isEmpty())
            assertEquals(0, fixture.activated)
            fixture.controller.close()
        }
    }

    @Test fun accountSwitchRejectsLateUncooperativeOfficialIdResolutionBeforeMaterialPreparation() = runTest {
        val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(3601400, "old", "netease:10"))
        val late = CompletableDeferred<NeteaseSoundEffect>()
        fixture.resolve = { withContext(NonCancellable) { late.await() } }
        runCurrent()
        assertEquals(listOf(3601400L), fixture.resolved)
        fixture.account.value = OriginalEffectsAccount("netease:20", 1)
        runCurrent()
        late.complete(effect(3601400))
        runCurrent()
        assertNull(fixture.saved)
        assertNull(fixture.controller.selection.value)
        assertTrue(fixture.prepared.isEmpty())
        assertEquals(0, fixture.activated)
        fixture.controller.close()
    }

    @Test fun newOfficialMemberIdStillRechecksMatchingUnexpiredBlackRights() = runTest {
        for (allowed in listOf(false, true)) {
            val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(3601400, "old", "netease:10"))
            fixture.resolve = { effect(it).copy(memberType = 3) }
            fixture.rights = NeteaseEffectEntitlement(10, allowed, 1000)
            runCurrent()
            assertEquals(1, fixture.rightsCalls)
            if (allowed) assertEquals(3601400L, fixture.controller.selection.value!!.id)
            else {
                assertNull(fixture.saved)
                assertTrue(fixture.prepared.isEmpty())
                assertNull(fixture.controller.selection.value)
            }
            fixture.controller.close()
        }
    }

    @Test fun transientNewIdResolutionFailurePreservesIntentAndRetriesAuthoritativeLookup() = runTest {
        val fixture = Fixture(backgroundScope, SavedOriginalSoundEffect(3601400, "old", "netease:10"))
        fixture.resolve = { throw java.io.IOException("offline") }
        runCurrent()
        assertNotNull(fixture.saved)
        assertTrue(fixture.prepared.isEmpty())
        fixture.resolve = { effect(it) }
        fixture.controller.retry()
        runCurrent()
        assertEquals(listOf(3601400L, 3601400L), fixture.resolved)
        assertEquals(3601400L, fixture.controller.selection.value!!.id)
        fixture.controller.close()
    }
}
