package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.effects.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OriginalSpeakerEffectsControllerTest {
    @Test fun routeVolumeSceneAndAccountChangesInvalidateTheActualDspSelection() = runTest {
        val accounts = MutableStateFlow(OriginalSmartAccount(42, 1, true))
        val environment = MutableStateFlow(OriginalSpeakerEnvironment(true, false, .5f))
        val prepared = mutableListOf<Long>()
        val controller = OriginalSpeakerEffectsController(accounts, {}, environment, MutableStateFlow(true),
            rights = { OriginalSpeakerEntitlement(accounts.value.userId, true, 5000) },
            catalog = { scene -> originalSpeakerIds(scene).map { OriginalSpeakerMaterial(it, "original", "", "", 1) } },
            prepare = { material -> prepared += material.id; NeteaseOriginalPreset(material.id, material.name, 1, byteArrayOf()) },
            scope = backgroundScope, beforeEnable = {}, now = { testScheduler.currentTime })
        runCurrent()
        controller.setEnabled(true)
        runCurrent()
        assertTrue(controller.state.value.enabled)
        assertEquals(-201L, controller.selection.value?.id)
        environment.value = environment.value.copy(speaker = false)
        runCurrent()
        assertFalse(controller.state.value.enabled)
        assertTrue(controller.state.value.requestedEnabled)
        assertNull(controller.selection.value)
        environment.value = OriginalSpeakerEnvironment(true, true, .8f)
        runCurrent()
        assertEquals(-102L, controller.selection.value?.id)
        controller.setScene(OriginalSpeakerScene.SHOWER)
        runCurrent()
        assertEquals(-300L, controller.selection.value?.id)
        accounts.value = OriginalSmartAccount(43, 2, true)
        runCurrent()
        assertFalse(controller.state.value.requestedEnabled)
        assertNull(controller.selection.value)
        controller.close()
    }
    @Test fun revokedRightsAndCancellationCannotApplyLateMaterial() = runTest {
        val environment = MutableStateFlow(OriginalSpeakerEnvironment(true, false, .5f))
        var allow = false
        val readyMaterial = CompletableDeferred<Unit>()
        val controller = OriginalSpeakerEffectsController(MutableStateFlow(OriginalSmartAccount(42, 1, true)), {},
            environment, MutableStateFlow(true), rights = { OriginalSpeakerEntitlement(42, allow, 5000) },
            catalog = { listOf(OriginalSpeakerMaterial(-201, "original", "", "", 1)) },
            prepare = { readyMaterial.await(); NeteaseOriginalPreset(it.id, it.name, 1, byteArrayOf()) },
            scope = backgroundScope, beforeEnable = {}, now = { testScheduler.currentTime })
        runCurrent()
        controller.setEnabled(true)
        runCurrent()
        assertFalse(controller.state.value.enabled)
        assertNull(controller.selection.value)
        allow = true
        controller.refresh()
        runCurrent()
        assertTrue(controller.state.value.loading)
        controller.disable()
        readyMaterial.complete(Unit)
        runCurrent()
        assertFalse(controller.state.value.enabled)
        assertFalse(controller.state.value.loading)
        assertNull(controller.selection.value)
        controller.close()
    }
}
