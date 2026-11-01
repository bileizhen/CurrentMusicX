package io.github.currencortex.music.feature.recognition

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import java.util.concurrent.atomic.AtomicBoolean
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
        advanceTimeBy(17_999); runCurrent()
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

    @Test fun overallDeadlineEndsMultipleSlowRoundsBeforeTheServiceDeadlineAndClearsPcm() = runTest {
        val first = ShortArray(16_000) { 4 }
        val final = ShortArray(120_000) { 4 }
        var captureCompleted = false
        var requests = 0
        var requestsReleased = 0
        val recorder = object : RecognitionRecorder {
            override suspend fun record(stop: AtomicBoolean, progress: (Int, Float) -> Unit): ShortArray =
                error("Expected streaming capture")
            override suspend fun recordStreaming(stop: AtomicBoolean, progress: (Int, Float) -> Unit,
                onWindow: (ShortArray) -> Unit): ShortArray {
                onWindow(first)
                delay(15_000)
                captureCompleted = true
                return final
            }
        }
        val controller = RecognitionController(this, recorder, { delay(9000); "fp" }, { _, _ ->
            requests++
            try { delay(14_000); emptyList() } finally { requestsReleased++ }
        })
        controller.start(); runCurrent()
        advanceTimeBy(41_999); runCurrent()
        assertTrue(captureCompleted)
        assertEquals(2, requests)
        assertTrue(controller.state.value.busy)
        advanceTimeBy(1); runCurrent(); advanceUntilIdle()
        assertEquals(42_000L, currentTime)
        assertEquals(2, requestsReleased)
        assertEquals(RecognitionPhase.ERROR, controller.state.value.phase)
        assertTrue(controller.state.value.message.orEmpty().startsWith("识曲超时"))
        assertTrue(first.all { it == 0.toShort() })
        assertTrue(final.all { it == 0.toShort() })
    }
}
