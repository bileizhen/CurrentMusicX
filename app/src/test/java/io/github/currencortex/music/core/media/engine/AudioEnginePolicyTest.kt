package io.github.currencortex.music.core.media.engine

import io.github.currencortex.music.data.settings.AudioBufferPolicy
import io.github.currencortex.music.data.settings.AudioOutputMode
import io.github.currencortex.music.data.settings.AudioOutputSettings
import org.junit.Assert.*
import org.junit.Test

class AudioEnginePolicyTest {
    @Test fun repeatedRequestPreservesConfirmedRuntimeEvidence() {
        val diagnostics = AudioDiagnostics(); val router = AudioOutputRouter(diagnostics)
        val requested = AudioOutputSettings(mode = AudioOutputMode.BIT_PERFECT)
        router.request(requested)
        diagnostics.update { it.copy(bitPerfect = BitPerfectEvidence.ANDROID_CONFIGURATION_VERIFIED,
            backend = "AudioTrack / Android Bit-perfect", output = AudioPcmFormat(96000, 21, 2)) }
        router.request(requested); router.effective(requested)
        assertEquals(BitPerfectEvidence.ANDROID_CONFIGURATION_VERIFIED, diagnostics.state.value.bitPerfect)
        assertEquals(AudioPcmFormat(96000, 21, 2), diagnostics.state.value.output)
    }
    @Test fun fallbackIsOncePerAttemptRetainsPreferenceAndRetryRearms() {
        val diagnostics = AudioDiagnostics(); val router = AudioOutputRouter(diagnostics)
        val requested = AudioOutputSettings(mode = AudioOutputMode.NETEASE_USB, sampleRate = 192000, usbDeviceKey = "dac")
        router.request(requested)
        assertTrue(router.fallback("permission revoked")); assertFalse(router.fallback("second failure"))
        assertEquals(AudioOutputMode.NETEASE_USB, diagnostics.state.value.requestedMode)
        assertEquals("permission revoked", diagnostics.state.value.fallbackReason)
        assertEquals(requested.copy(mode = AudioOutputMode.AUDIO_TRACK, sampleRate = 0), router.effective(requested))
        router.retry()
        assertEquals(requested, router.effective(requested)); assertNull(router.fallbackReason())
        assertEquals(BitPerfectEvidence.UNVERIFIED, diagnostics.state.value.bitPerfect)
        assertTrue(router.fallback("new attempt"))
    }
    @Test fun failedRawPathStillBypassesRequestedPcmProcessingAfterFallback() {
        for (mode in listOf(AudioOutputMode.BIT_PERFECT, AudioOutputMode.NETEASE_USB, AudioOutputMode.DIRECT)) {
            val diagnostics = AudioDiagnostics(); val router = AudioOutputRouter(diagnostics)
            val requested = AudioOutputSettings(mode = mode)
            router.request(requested); assertTrue(router.fallback("not available"))
            assertEquals(AudioOutputMode.AUDIO_TRACK, router.effective(requested).mode)
            // Service passes the requested processing policy even after backend fallback.
            val plan = AudioEffectPipeline.plan(requested.mode, listOf("EQ", "网易云", "MNN"))
            assertTrue(plan.bypass); assertEquals(listOf("EQ", "网易云", "MNN"), plan.requested)
            assertNotNull(plan.reason)
        }
        assertFalse(AudioEffectPipeline.plan(AudioOutputMode.AUDIO_TRACK, listOf("EQ")).bypass)
    }
    @Test fun changedPreferenceClearsPriorFailureAndNormalPathCannotFallbackToItself() {
        val diagnostics = AudioDiagnostics(); val router = AudioOutputRouter(diagnostics)
        router.request(AudioOutputSettings(mode = AudioOutputMode.AAUDIO)); router.fallback("unsupported")
        val next = AudioOutputSettings(mode = AudioOutputMode.OBOE)
        router.request(next); assertEquals(next, router.effective(next)); assertNull(router.fallbackReason())
        router.request(AudioOutputSettings()); assertFalse(router.fallback("already standard"))
    }
    @Test fun declared192kDoesNotTurnAcceptedRequestIntoVerifiedOutput() {
        val decision = AudioFormatNegotiator.negotiate(AudioPcmFormat(192000, 21, 2), AudioOutputMode.BIT_PERFECT,
            AudioDeviceCapabilities(setOf(192000), setOf(21), setOf(2), usb = true))
        assertTrue(decision.accepted); assertTrue(decision.requiresRuntimeVerification)
        val diagnostics = AudioDiagnostics()
        assertEquals(AudioPcmFormat(), diagnostics.state.value.output)
    }
    @Test fun missingPcmUsbOrInteger16DirectIsRejectedWithoutConversions() {
        val unknown = AudioDeviceCapabilities()
        assertFalse(AudioFormatNegotiator.negotiate(AudioPcmFormat(null, 2, 2), AudioOutputMode.AUDIO_TRACK, unknown).accepted)
        assertFalse(AudioFormatNegotiator.negotiate(AudioPcmFormat(48000, 2, 2), AudioOutputMode.BIT_PERFECT, unknown).accepted)
        assertFalse(AudioFormatNegotiator.negotiate(AudioPcmFormat(48000, 4, 2), AudioOutputMode.DIRECT, unknown).accepted)
        assertFalse(AudioFormatNegotiator.negotiate(AudioPcmFormat(96000, 21, 2), AudioOutputMode.AAUDIO,
            AudioDeviceCapabilities(rates = setOf(48000))).accepted)
    }
    @Test fun emptyCapabilitiesRemainUnknownAndDoNotCertifyHardware() {
        val decision = AudioFormatNegotiator.negotiate(AudioPcmFormat(48000, 2, 2), AudioOutputMode.AUDIO_TRACK,
            AudioDeviceCapabilities())
        assertTrue(decision.accepted); assertTrue(decision.requiresRuntimeVerification)
    }
    @Test fun bufferBudgetsKeepNetworkStartupAndResumeWithinCapacity() {
        for (policy in AudioBufferPolicy.entries) {
            val budget = AudioBufferController.budget(policy)
            assertTrue(budget.startMs > 0); assertTrue(budget.resumeMs >= budget.startMs)
            assertTrue(budget.minMs >= budget.resumeMs); assertTrue(budget.maxMs >= budget.minMs)
            assertTrue(budget.maxBytes > 0)
        }
        val fast = AudioBufferController.budget(AudioBufferPolicy.RESPONSIVE)
        val safe = AudioBufferController.budget(AudioBufferPolicy.RESILIENT)
        assertTrue(fast.startMs < safe.startMs); assertTrue(fast.maxMs < safe.maxMs)
    }
    @Test fun timeFirstLoadingStopsAtMemoryPressureOrEmergencyCapacity() {
        val budget = AudioBufferController.budget(AudioBufferPolicy.BALANCED)
        assertTrue(AudioBufferController.shouldLoad(budget, 1_000_000, 1f, budget.maxBytes, true, 128L * 1024 * 1024))
        assertFalse(AudioBufferController.shouldLoad(budget, 1_000_000, 1f, budget.maxBytes, true, 8L * 1024 * 1024))
        assertFalse(AudioBufferController.shouldLoad(budget, 1_000_000, 1f, budget.maxBytes * 2, true, 128L * 1024 * 1024))
        assertFalse(AudioBufferController.shouldLoad(budget, budget.maxMs * 1000L, 1f, 1, true, Long.MAX_VALUE))
    }
    @Test fun changingBufferBudgetDoesNotSilentlyRetryAFailedOutput() {
        val diagnostics = AudioDiagnostics(); val router = AudioOutputRouter(diagnostics)
        val requested = AudioOutputSettings(mode = AudioOutputMode.AAUDIO)
        router.request(requested); router.fallback("unsupported")
        router.request(requested.copy(bufferPolicy = AudioBufferPolicy.RESILIENT))
        assertEquals("unsupported", diagnostics.state.value.fallbackReason)
        assertEquals(AudioOutputMode.AUDIO_TRACK, router.effective(requested.copy(bufferPolicy = AudioBufferPolicy.RESILIENT)).mode)
        assertNull(diagnostics.state.value.backend)
    }
    @Test fun usbSnapshotCannotReusePreviousTrackHardwareEvidence() {
        val old = AudioDiagnosticsSnapshot(output = AudioPcmFormat(192000, 22, 2), deviceId = 42,
            deviceBufferFrames = 512, capacityFrames = 1024, softwareBufferFrames = 24000,
            underruns = 0, latencyMs = 2.1, bitPerfect = BitPerfectEvidence.ANDROID_CONFIGURATION_VERIFIED)
        val updated = usbDiagnostics(old, UsbOutputSnapshot("Test USB", AudioPcmFormat(96000, 22, 2), "UAC2 声明；时钟未知"))
        assertEquals(AudioPcmFormat(), updated.output); assertNull(updated.deviceId)
        assertNull(updated.deviceBufferFrames); assertNull(updated.capacityFrames); assertNull(updated.softwareBufferFrames)
        assertNull(updated.underruns); assertNull(updated.latencyMs)
        assertEquals(BitPerfectEvidence.UNVERIFIED, updated.bitPerfect)
        assertTrue(updated.formatEvidence.contains("96000")); assertTrue(updated.formatEvidence.contains("未验证"))
        assertEquals("UAC2 声明；时钟未知", updated.usbCapabilities)
    }
}
