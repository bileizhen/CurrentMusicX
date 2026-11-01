package io.github.currencortex.music.feature.recognition

import io.github.currencortex.music.data.song.Song
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecognitionLyricsBranchTest {
    private fun pcm(seconds: Int) = ShortArray(seconds * 8000) { 21 }
    private fun streaming(block: suspend (AtomicBoolean, (ShortArray) -> Unit) -> ShortArray) = object : RecognitionRecorder {
        override suspend fun record(stop: AtomicBoolean, progress: (Int, Float) -> Unit): ShortArray = error("Use streaming")
        override suspend fun recordStreaming(stop: AtomicBoolean, progress: (Int, Float) -> Unit,
            onWindow: (ShortArray) -> Unit): ShortArray = block(stop, onWindow)
    }

    @Test fun tenSecondWindowIsUsedOnceForLyricsWithoutAddingAnAfpRound() = runTest {
        for (enabled in listOf(false, true)) {
            val windows = mutableListOf<ShortArray>()
            val afpDurations = mutableListOf<Double>()
            val rounds = mutableListOf<Int>()
            val sessions = mutableSetOf<String>()
            var lyricsCalls = 0
            val controller = RecognitionController(this, streaming { _, emit ->
                for (seconds in listOf(2, 3, 6, 9, 10, 12, 15)) {
                    emit(pcm(seconds).also(windows::add))
                    delay(100)
                }
                pcm(15).also(windows::add)
            }, { "fp" }, { _, _ -> error("Use shared session") }, recognizeInSession = { _, seconds, session, round ->
                afpDurations += seconds; rounds += round; sessions += session
                emptyList()
            }, recognizeLyrics = if (enabled) { samples, session ->
                lyricsCalls++
                assertEquals(80_000, samples.size)
                assertTrue(samples.all { it == 21.toShort() })
                sessions += session
                emptyList()
            } else null)
            controller.start(); advanceUntilIdle()
            assertEquals(listOf(2.0, 3.0, 6.0, 9.0, 12.0, 15.0), afpDurations)
            assertEquals((1..6).toList(), rounds)
            assertEquals(if (enabled) 1 else 0, lyricsCalls)
            assertEquals(1, sessions.size)
            assertEquals(RecognitionPhase.NO_MATCH, controller.state.value.phase)
            assertTrue(windows.all { window -> window.all { it == 0.toShort() } })
        }
    }

    @Test fun earlyAfpMatchStopsCaptureBeforeAnyLyricQuery() = runTest {
        var released = false
        var stopped = false
        val first = pcm(2)
        var lyricCalls = 0
        val controller = RecognitionController(this, streaming { stop, emit ->
            try {
                emit(first)
                delay(1000)
                emit(pcm(10))
                awaitCancellation()
            } finally { released = true; stopped = stop.get() }
        }, { "fp" }, { _, _ -> listOf(Song(1, "Fingerprint hit")) }, recognizeLyrics = { _, _ ->
            lyricCalls++; error("An early AFP hit must not send audio for lyric recognition")
        })
        controller.start(); advanceUntilIdle()
        assertEquals(RecognitionPhase.MATCHED, controller.state.value.phase)
        assertEquals(1L, controller.state.value.songs.single().id)
        assertEquals(0, lyricCalls)
        assertTrue(released); assertTrue(stopped)
        assertTrue(first.all { it == 0.toShort() })
    }

    @Test fun lyricMatchCancelsTheInFlightAfpRequestAndReleasesCaptureAndQueuedWindows() = runTest {
        val windows = mutableListOf<ShortArray>()
        var afpReleased = false
        var captureReleased = false
        var lyricPcm: ShortArray? = null
        val controller = RecognitionController(this, streaming { _, emit ->
            try {
                for (seconds in listOf(2, 3, 6, 9, 10)) {
                    emit(pcm(seconds).also(windows::add)); delay(100)
                }
                awaitCancellation()
            } finally { captureReleased = true }
        }, { "fp" }, { _, _ ->
            try { awaitCancellation() } finally { afpReleased = true }
        }, recognizeLyrics = { samples, _ ->
            lyricPcm = samples
            listOf(Song(2, "Lyric hit"))
        })
        controller.start(); advanceUntilIdle()
        assertEquals(RecognitionPhase.MATCHED, controller.state.value.phase)
        assertEquals(2L, controller.state.value.songs.single().id)
        assertTrue(afpReleased); assertTrue(captureReleased)
        assertNotNull(lyricPcm)
        assertTrue(lyricPcm!!.all { it == 0.toShort() })
        assertTrue(windows.all { window -> window.all { it == 0.toShort() } })
    }

    @Test fun lyricNetworkFailureDoesNotAbortALaterAfpMatch() = runTest {
        var lyricCalls = 0
        val durations = mutableListOf<Double>()
        val controller = RecognitionController(this, streaming { _, emit ->
            for (seconds in listOf(2, 3, 6, 9, 10, 12)) { emit(pcm(seconds)); delay(100) }
            pcm(15)
        }, { "fp" }, { _, seconds ->
            durations += seconds
            if (seconds == 2.0) throw IOException("first AFP failed")
            if (seconds == 12.0) listOf(Song(12, "Recovered AFP hit")) else emptyList()
        }, recognizeLyrics = { _, _ -> lyricCalls++; throw IOException("lyric request failed") })
        controller.start(); advanceUntilIdle()
        assertEquals(1, lyricCalls)
        assertEquals(listOf(2.0, 3.0, 6.0, 9.0, 12.0), durations)
        assertEquals(RecognitionPhase.MATCHED, controller.state.value.phase)
        assertEquals(12L, controller.state.value.songs.single().id)
    }

    @Test fun exhaustedAfpFailuresWaitForTheStillRunningLyricQuery() = runTest {
        val releaseLyrics = CompletableDeferred<Unit>()
        val controller = RecognitionController(this, RecognitionRecorder { _, _ -> pcm(15) }, { "fp" }, { _, _ ->
            throw IOException("AFP failed")
        }, recognizeLyrics = { samples, _ ->
            assertEquals(80_000, samples.size)
            releaseLyrics.await()
            listOf(Song(10, "Recovered lyric hit"))
        })
        controller.start(); runCurrent()
        assertTrue(controller.state.value.busy)
        releaseLyrics.complete(Unit); advanceUntilIdle()
        assertEquals(RecognitionPhase.MATCHED, controller.state.value.phase)
        assertEquals(10L, controller.state.value.songs.single().id)
    }

    @Test fun cancellingWipesInFlightAndPendingAudioAndCancelsBothRequests() = runTest {
        val windows = mutableListOf<ShortArray>()
        var lyricReleased = false
        var afpReleased = false
        var captureReleased = false
        var lyricPcm: ShortArray? = null
        val controller = RecognitionController(this, streaming { _, emit ->
            try {
                for (seconds in listOf(2, 9, 10, 12)) { emit(pcm(seconds).also(windows::add)); delay(100) }
                awaitCancellation()
            } finally { captureReleased = true }
        }, { "fp" }, { _, _ ->
            try { awaitCancellation() } finally { afpReleased = true }
        }, recognizeLyrics = { samples, _ ->
            lyricPcm = samples
            try { awaitCancellation() } finally { lyricReleased = true }
        })
        controller.start(); advanceTimeBy(400); runCurrent()
        assertNotNull(lyricPcm)
        assertTrue(lyricPcm!!.any { it != 0.toShort() })
        controller.cancel(); advanceUntilIdle()
        assertTrue(lyricReleased); assertTrue(afpReleased); assertTrue(captureReleased)
        assertEquals(RecognitionState(), controller.state.value)
        assertTrue(windows.all { window -> window.all { it == 0.toShort() } })
        assertTrue(lyricPcm!!.all { it == 0.toShort() })
    }

    @Test fun finishingBeforeTenSecondsDoesNotInventALyricFailure() = runTest {
        val controller = RecognitionController(this, RecognitionRecorder { _, _ -> pcm(5) }, { "fp" }, { _, _ -> emptyList() },
            recognizeLyrics = { _, _ -> error("The short attempt must not run lyric recognition") })
        controller.start(); advanceUntilIdle()
        assertEquals(RecognitionPhase.NO_MATCH, controller.state.value.phase)
    }

    @Test fun withoutALyricMatcherAnExactlyTenSecondFinishStillGetsItsAfpQuery() = runTest {
        val emitted = pcm(10)
        val final = pcm(10)
        val durations = mutableListOf<Double>()
        val controller = RecognitionController(this, streaming { _, emit ->
            emit(pcm(9)); delay(100)
            emit(emitted); delay(100)
            final
        }, { "fp" }, { _, seconds -> durations += seconds; emptyList() })
        controller.start(); advanceUntilIdle()
        assertEquals(listOf(9.0, 10.0), durations)
        assertEquals(RecognitionPhase.NO_MATCH, controller.state.value.phase)
        assertTrue(emitted.all { it == 0.toShort() })
        assertTrue(final.all { it == 0.toShort() })
    }

    @Test fun failedAuxiliaryQueryIsReportedAfterAfpAlsoFinishesWithoutAMatch() = runTest {
        val original = pcm(15)
        var lyricPcm: ShortArray? = null
        val controller = RecognitionController(this, RecognitionRecorder { _, _ -> original }, { "fp" }, { _, _ -> emptyList() },
            recognizeLyrics = { samples, _ ->
                lyricPcm = samples
                throw IOException("lyric service unavailable")
            })
        controller.start(); advanceUntilIdle()
        assertEquals(RecognitionPhase.ERROR, controller.state.value.phase)
        assertEquals("lyric service unavailable", controller.state.value.message)
        assertTrue(original.all { it == 0.toShort() })
        assertTrue(lyricPcm!!.all { it == 0.toShort() })
    }
}
