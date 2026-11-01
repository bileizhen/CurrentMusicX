package io.github.currencortex.music.feature.recognition

import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecognitionControllerTest {
    @Test fun completeAttemptUsesActualDurationPublishesSongsAndClearsPcm() = runTest {
        val pcm = ShortArray(32_000) { 7 }
        val song = Song(1, "Identified song")
        val controller = RecognitionController(this, RecognitionRecorder { _, progress -> progress(4, .5f); pcm }, {
            assertEquals(7, it.first().toInt()); "fingerprint"
        }, { data, duration -> assertEquals("fingerprint", data); assertEquals(4.0, duration, 0.0); listOf(song) })
        controller.start(); advanceUntilIdle()
        assertEquals(RecognitionPhase.MATCHED, controller.state.value.phase)
        assertEquals(listOf(song), controller.state.value.songs)
        assertTrue(pcm.all { it == 0.toShort() })
    }

    @Test fun tooShortRecordingIsNotUploaded() = runTest {
        val controller = RecognitionController(this, RecognitionRecorder { _, _ -> ShortArray(8000) },
            { error("Short recordings must not be encoded") }, { _, _ -> error("Short recordings must not be sent") })
        controller.start(); advanceUntilIdle()
        assertEquals(RecognitionPhase.ERROR, controller.state.value.phase)
        assertTrue(controller.state.value.message!!.contains("至少"))
    }

    @Test fun permissionLossIsAnActionableErrorRatherThanNoMatch() = runTest {
        val controller = RecognitionController(this, RecognitionRecorder { _, _ -> throw SecurityException() },
            { error("No samples") }, { _, _ -> emptyList() })
        controller.start(); advanceUntilIdle()
        assertEquals(RecognitionPhase.ERROR, controller.state.value.phase)
        assertTrue(controller.state.value.message!!.contains("权限"))
    }

    @Test fun finishingACurrentRecordingDoesNotStartADuplicateAttempt() = runTest {
        var recordings = 0
        val controller = RecognitionController(this, RecognitionRecorder { stop, _ ->
            recordings++; while (!stop.get()) delay(100)
            ShortArray(24_000) { 1 }
        }, { "fp" }, { _, _ -> emptyList() })
        controller.start(); runCurrent(); controller.start()
        controller.finishRecording(); advanceUntilIdle()
        assertEquals(1, recordings)
        assertEquals(RecognitionPhase.NO_MATCH, controller.state.value.phase)
    }

    @Test fun leavingCaptureCancelsAndReleasesRecorderWithoutAnUpload() = runTest {
        var released = false
        val controller = RecognitionController(this, RecognitionRecorder { _, _ ->
            try { awaitCancellation() } finally { released = true }
        }, { error("Capture was cancelled") }, { _, _ -> error("Capture was cancelled") })
        controller.start(); runCurrent(); controller.cancel(); advanceUntilIdle()
        assertTrue(released)
        assertEquals(RecognitionPhase.IDLE, controller.state.value.phase)
    }

    @Test fun staleNetworkResultCannotReplaceANewerAttempt() = runTest {
        var attempts = 0
        val controller = RecognitionController(this, RecognitionRecorder { _, _ -> ShortArray(24_000) { 1 } }, { "fp" }, { _, _ ->
            val attempt = ++attempts
            if (attempt == 1) withContext(NonCancellable) { delay(1000) }
            listOf(Song(attempt.toLong(), "Attempt $attempt"))
        })
        controller.start(); runCurrent(); controller.cancel(); controller.start(); advanceUntilIdle()
        assertEquals(2L, controller.state.value.songs.single().id)
    }

    @Test fun resettingForDesktopHandoffClearsThePreviousLocalResult() = runTest {
        val controller = RecognitionController(this, RecognitionRecorder { _, _ -> ShortArray(24_000) { 1 } },
            { "fp" }, { _, _ -> listOf(Song(1, "Old local result")) })
        controller.start(); advanceUntilIdle()
        assertEquals(RecognitionPhase.MATCHED, controller.state.value.phase)
        controller.reset()
        assertEquals(RecognitionState(), controller.state.value)
    }
    @Test fun systemAudioSourceRemainsExplicitThroughoutCaptureAndMatching() = runTest {
        val controller = RecognitionController(this, RecognitionRecorder { _, progress ->
            progress(3, .4f); ShortArray(24_000) { 12 }
        }, { "fp" }, { _, _ -> listOf(Song(7, "System result")) }, RecognitionAudioSource.SYSTEM)
        controller.start()
        assertEquals(RecognitionAudioSource.SYSTEM, controller.state.value.source)
        advanceUntilIdle()
        assertEquals(RecognitionPhase.MATCHED, controller.state.value.phase)
        assertEquals(RecognitionAudioSource.SYSTEM, controller.state.value.source)
        assertEquals("System result", controller.state.value.songs.single().name)
    }

    @Test fun fingerprintTimeoutReleasesSamplesAndAllowsRetry() = runTest {
        val pcm = ShortArray(24_000) { 8 }
        val controller = RecognitionController(this, RecognitionRecorder { _, _ -> pcm }, { awaitCancellation() }, { _, _ -> emptyList() })
        controller.start(); advanceUntilIdle()
        assertEquals(RecognitionPhase.ERROR, controller.state.value.phase)
        assertTrue(controller.state.value.message!!.contains("超时"))
        assertTrue(pcm.all { it == 0.toShort() })
        assertFalse(controller.state.value.busy)
    }
}
