package io.github.currencortex.music.feature.recognition

import io.github.currencortex.music.data.song.Song
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecognitionStreamingTest {
    private fun pcm(seconds: Int) = ShortArray(seconds * 8000) { 13 }
    private fun streaming(block: suspend (AtomicBoolean, (Int, Float) -> Unit, (ShortArray) -> Unit) -> ShortArray) =
        object : RecognitionRecorder {
            override suspend fun record(stop: AtomicBoolean, progress: (Int, Float) -> Unit): ShortArray =
                error("Controller must use the streaming entry point")
            override suspend fun recordStreaming(stop: AtomicBoolean, progress: (Int, Float) -> Unit,
                onWindow: (ShortArray) -> Unit): ShortArray = block(stop, progress, onWindow)
        }

    @Test fun cumulativeWindowsShareASessionAndAnEarlyMatchStopsCaptureAndClearsEveryWindow() = runTest {
        val windows = mutableListOf<ShortArray>()
        val requests = mutableListOf<Triple<Double, String, Int>>()
        var captureReleased = false
        var stopRequested = false
        val recorder = streaming { stop, progress, onWindow ->
            try {
                for (seconds in listOf(2, 3, 6, 9, 12, 15)) {
                    delay(1000)
                    progress(seconds, .5f)
                    onWindow(pcm(seconds).also(windows::add))
                }
                pcm(15).also(windows::add)
            } finally { captureReleased = true; stopRequested = stop.get() }
        }
        val controller = RecognitionController(this, recorder, { "fp" }, { _, _ -> error("Use session matcher") },
            recognizeInSession = { _, seconds, session, round ->
                requests += Triple(seconds, session, round)
                if (seconds >= 6) listOf(Song(7, "Early match")) else emptyList()
            })
        controller.start(); advanceUntilIdle()
        assertEquals(listOf(2.0, 3.0, 6.0), requests.map { it.first })
        assertEquals(listOf(1, 2, 3), requests.map { it.third })
        assertEquals(1, requests.map { it.second }.distinct().size)
        assertTrue(requests.first().second.isNotBlank())
        assertTrue(captureReleased); assertTrue(stopRequested)
        assertEquals(3, windows.size)
        assertTrue(windows.all { window -> window.all { it == 0.toShort() } })
        assertEquals(RecognitionPhase.MATCHED, controller.state.value.phase)
        assertEquals(7L, controller.state.value.songs.single().id)
    }

    @Test fun slowRequestDoesNotBlockCaptureAndOnlyTheLatestPendingWindowIsRetained() = runTest {
        val releaseRequest = CompletableDeferred<Unit>()
        val windows = mutableListOf<ShortArray>()
        val requested = mutableListOf<Double>()
        var captureFinished = false
        val recorder = streaming { _, progress, onWindow ->
            for (seconds in listOf(2, 3, 6, 9, 12)) {
                onWindow(pcm(seconds).also(windows::add))
                progress(seconds, .5f)
                delay(100)
            }
            progress(15, .5f)
            captureFinished = true
            pcm(15).also(windows::add)
        }
        val controller = RecognitionController(this, recorder, { "fp" }, { _, seconds ->
            requested += seconds
            if (requested.size == 1) releaseRequest.await()
            emptyList()
        })
        controller.start(); runCurrent()
        assertEquals(RecognitionPhase.RECORDING, controller.state.value.phase)
        assertEquals(RecognitionStep.MATCH, controller.state.value.step)
        advanceTimeBy(1000); runCurrent()
        assertTrue(captureFinished)
        assertEquals(15, controller.state.value.seconds)
        assertEquals(RecognitionPhase.RECOGNIZING, controller.state.value.phase)
        assertEquals(listOf(2.0), requested)
        assertTrue(windows.subList(1, 5).all { window -> window.all { it == 0.toShort() } })
        assertTrue(windows.last().any { it != 0.toShort() })
        releaseRequest.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(2.0, 15.0), requested)
        assertEquals(RecognitionPhase.NO_MATCH, controller.state.value.phase)
        assertTrue(windows.all { window -> window.all { it == 0.toShort() } })
    }

    @Test fun encodingAndNetworkFailuresContinueToLaterWindowsWithSerialRequestRounds() = runTest {
        val requests = mutableListOf<Pair<Double, Int>>()
        var encoded = 0
        val controller = RecognitionController(this, streaming { _, _, onWindow ->
            for (seconds in listOf(2, 3, 6)) { onWindow(pcm(seconds)); delay(100) }
            pcm(6)
        }, {
            if (++encoded == 1) throw IllegalStateException("Temporary encoding failure")
            "fp"
        }, { _, _ -> error("Use session matcher") }, recognizeInSession = { _, seconds, _, round ->
            requests += seconds to round
            if (round == 1) throw IOException("Temporary request failure")
            listOf(Song(9, "Recovered match"))
        })
        controller.start(); advanceUntilIdle()
        assertEquals(listOf(3.0 to 1, 6.0 to 2), requests)
        assertEquals(RecognitionPhase.MATCHED, controller.state.value.phase)
        assertEquals(9L, controller.state.value.songs.single().id)
    }

    @Test fun finishFlushesAnIntermediateLengthIntoTheExistingSessionWithoutDuplicateRequests() = runTest {
        val requests = mutableListOf<Triple<Double, String, Int>>()
        val windows = mutableListOf<ShortArray>()
        val controller = RecognitionController(this, streaming { stop, progress, onWindow ->
            onWindow(pcm(2).also(windows::add)); progress(2, .5f)
            while (!stop.get()) delay(100)
            pcm(5).also(windows::add)
        }, { "fp" }, { _, _ -> error("Use session matcher") }, recognizeInSession = { _, seconds, session, round ->
            requests += Triple(seconds, session, round)
            emptyList()
        })
        controller.start(); runCurrent()
        assertEquals(listOf(2.0), requests.map { it.first })
        controller.finishRecording(); advanceUntilIdle()
        assertEquals(listOf(2.0, 5.0), requests.map { it.first })
        assertEquals(listOf(1, 2), requests.map { it.third })
        assertEquals(1, requests.map { it.second }.distinct().size)
        assertEquals(RecognitionPhase.NO_MATCH, controller.state.value.phase)
        assertTrue(windows.all { window -> window.all { it == 0.toShort() } })
    }

    @Test fun cancelReleasesTheActiveRequestCaptureAndPendingPcm() = runTest {
        val windows = mutableListOf<ShortArray>()
        var captureReleased = false
        var requestReleased = false
        val controller = RecognitionController(this, streaming { _, _, onWindow ->
            try {
                for (seconds in listOf(2, 3, 6)) { onWindow(pcm(seconds).also(windows::add)); delay(100) }
                awaitCancellation()
            } finally { captureReleased = true }
        }, { "fp" }, { _, _ ->
            try { awaitCancellation() } finally { requestReleased = true }
        })
        controller.start(); runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals(3, windows.size)
        controller.cancel(); advanceUntilIdle()
        assertTrue(captureReleased); assertTrue(requestReleased)
        assertTrue(windows.all { window -> window.all { it == 0.toShort() } })
        assertEquals(RecognitionState(), controller.state.value)
    }

    @Test fun finalWindowAlreadyEmittedIsNotMatchedTwiceAndBothCopiesAreCleared() = runTest {
        val emitted = pcm(2)
        val final = pcm(2)
        var requests = 0
        val controller = RecognitionController(this, streaming { _, _, onWindow ->
            onWindow(emitted); delay(100)
            final
        }, { "fp" }, { _, _ -> requests++; emptyList() })
        controller.start(); advanceUntilIdle()
        assertEquals(1, requests)
        assertEquals(RecognitionPhase.NO_MATCH, controller.state.value.phase)
        assertTrue(emitted.all { it == 0.toShort() })
        assertTrue(final.all { it == 0.toShort() })
    }

    @Test fun staleWindowProgressAndLateResultCannotOverwriteANewerSession() = runTest {
        val sessions = mutableListOf<String>()
        var calls = 0
        var oldProgress: ((Int, Float) -> Unit)? = null
        val controller = RecognitionController(this, streaming { _, progress, onWindow ->
            if (oldProgress == null) oldProgress = progress
            onWindow(pcm(2))
            delay(100)
            pcm(3)
        }, { "fp" }, { _, _ -> error("Use session matcher") }, recognizeInSession = { _, _, session, _ ->
            sessions += session
            val call = ++calls
            if (call == 1) withContext(NonCancellable) { delay(500) }
            listOf(Song(call.toLong(), "Attempt $call"))
        })
        controller.start(); runCurrent()
        controller.cancel(); controller.start(); runCurrent()
        oldProgress!!(99, 1f)
        advanceUntilIdle()
        assertEquals(2, sessions.distinct().size)
        assertEquals(RecognitionPhase.MATCHED, controller.state.value.phase)
        assertEquals(2L, controller.state.value.songs.single().id)
        assertNotEquals(99, controller.state.value.seconds)
    }

    @Test fun requestTimeoutCanRecoverUsingTheLatestCapturedWindow() = runTest {
        val requests = mutableListOf<Double>()
        val controller = RecognitionController(this, streaming { _, _, onWindow ->
            onWindow(pcm(2)); delay(100)
            pcm(15)
        }, { "fp" }, { _, seconds ->
            requests += seconds
            if (requests.size == 1) awaitCancellation()
            listOf(Song(15, "Timeout recovery"))
        })
        controller.start(); advanceUntilIdle()
        assertEquals(listOf(2.0, 15.0), requests)
        assertEquals(RecognitionPhase.MATCHED, controller.state.value.phase)
    }

    @Test fun emptyOrSilentCaptureNeverReachesFingerprintingAndReportsCaptureFailure() = runTest {
        for (samples in listOf(ShortArray(0), ShortArray(16_000))) {
            val controller = RecognitionController(this, RecognitionRecorder { _, _ -> samples },
                { error("Invalid capture must not be encoded") }, { _, _ -> error("Invalid capture must not be uploaded") })
            controller.start(); advanceUntilIdle()
            assertEquals(RecognitionPhase.ERROR, controller.state.value.phase)
            assertTrue(controller.state.value.message.orEmpty().contains(if (samples.isEmpty()) "片段太短" else "麦克风声音"))
        }
    }

    @Test fun twoAndFifteenSecondsAreAcceptedButLongerAudioIsRejected() = runTest {
        val durations = mutableListOf<Double>()
        for (size in listOf(16_000, 120_000, 120_001)) {
            val samples = ShortArray(size) { 2 }
            val controller = RecognitionController(this, RecognitionRecorder { _, _ -> samples }, { "fp" }, { _, seconds ->
                durations += seconds; emptyList()
            })
            controller.start(); advanceUntilIdle()
            assertEquals(if (size <= 120_000) RecognitionPhase.NO_MATCH else RecognitionPhase.ERROR, controller.state.value.phase)
            assertTrue(samples.all { it == 0.toShort() })
        }
        assertEquals(listOf(2.0, 15.0), durations)
    }
}
