package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsActivation
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopLyricsActivationTest {
    @Test fun missingPermissionNeverStartsAConnectionAndManualRetryCanEnable() = runTest {
        var permission = false
        var connections = 0
        val saved = mutableListOf<Boolean>()
        val control = DesktopLyricsActivation(backgroundScope, { permission }, { connections++ }, { saved += it })
        control.request(true); runCurrent()
        assertEquals(0, connections); assertNotNull(control.state.value.error)
        permission = true; control.request(true); runCurrent()
        assertEquals(1, connections); assertEquals(listOf(false, true), saved)
        assertNull(control.state.value.error)
    }
    @Test fun closeWinsOverNonCancellableLateConnection() = runTest {
        val release = CompletableDeferred<Unit>()
        val saved = mutableListOf<Boolean>()
        val control = DesktopLyricsActivation(backgroundScope, { true },
            { withContext(NonCancellable) { release.await() } }, { saved += it })
        control.request(true); runCurrent()
        assertTrue(control.state.value.connecting)
        control.request(false); runCurrent()
        release.complete(Unit); runCurrent()
        assertEquals(listOf(false), saved)
        assertFalse(control.state.value.connecting)
    }
    @Test fun revokedPermissionDuringConnectNeverEnablesWindow() = runTest {
        val release = CompletableDeferred<Unit>()
        var permission = true
        val saved = mutableListOf<Boolean>()
        val control = DesktopLyricsActivation(backgroundScope, { permission }, { release.await() }, { saved += it })
        control.request(true); runCurrent(); permission = false; release.complete(Unit); runCurrent()
        assertEquals(listOf(false), saved)
        assertNotNull(control.state.value.error)
        assertFalse(control.state.value.connecting)
    }
    @Test fun failedConnectionCanBeRetriedAndDisposeCancelsPendingEnable() = runTest {
        var fail = true
        val saved = mutableListOf<Boolean>()
        val release = CompletableDeferred<Unit>()
        val control = DesktopLyricsActivation(backgroundScope, { true }, {
            if (fail) error("connection failed") else release.await()
        }, { saved += it })
        control.request(true); runCurrent(); assertNotNull(control.state.value.error)
        fail = false; control.request(true); runCurrent(); assertNull(control.state.value.error)
        control.close(); release.complete(Unit); runCurrent(); assertEquals(listOf(false), saved)
    }
}
