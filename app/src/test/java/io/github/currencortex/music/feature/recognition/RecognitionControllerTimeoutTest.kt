package io.github.currencortex.music.feature.recognition

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecognitionControllerTimeoutTest {
    @Test fun captureTimeoutReleasesTheRecorderAndDoesNotEnterAnalysisOrMatch() = runTest {
        var released = false
        var encoded = false
        var requested = false
        val controller = RecognitionController(this, RecognitionRecorder { _, _ ->
            try { awaitCancellation() } finally { released = true }
        }, { encoded = true; "fp" }, { _, _ -> requested = true; emptyList() }, RecognitionAudioSource.SYSTEM)
        controller.start(); runCurrent()
        advanceTimeBy(12_999); runCurrent()
        assertTrue(controller.state.value.busy)
        assertEquals(RecognitionStep.CAPTURE, controller.state.value.step)
        advanceTimeBy(1); runCurrent(); advanceUntilIdle()
        assertTrue(released)
        assertFalse(encoded); assertFalse(requested)
        assertEquals(RecognitionPhase.ERROR, controller.state.value.phase)
        assertEquals(RecognitionStep.CAPTURE, controller.state.value.step)
        assertTrue(controller.state.value.message.orEmpty().contains("采集声音超时"))
    }

    @Test fun requestTimeoutIsReportedAsMatchingAndClearsCapturedPcm() = runTest {
        val pcm = ShortArray(24_000) { 91 }
        var requestCancelled = false
        val controller = RecognitionController(this, RecognitionRecorder { _, _ -> pcm }, { "fp" }, { _, _ ->
            try { awaitCancellation() } finally { requestCancelled = true }
        }, RecognitionAudioSource.SYSTEM)
        controller.start(); runCurrent()
        assertEquals(RecognitionStep.MATCH, controller.state.value.step)
        advanceTimeBy(14_999); runCurrent()
        assertTrue(controller.state.value.busy)
        advanceTimeBy(1); runCurrent(); advanceUntilIdle()
        assertTrue(requestCancelled)
        assertEquals(RecognitionPhase.ERROR, controller.state.value.phase)
        assertEquals(RecognitionStep.MATCH, controller.state.value.step)
        assertTrue(controller.state.value.message.orEmpty().contains("网易云匹配超时"))
        assertTrue(pcm.all { it == 0.toShort() })
    }
}
