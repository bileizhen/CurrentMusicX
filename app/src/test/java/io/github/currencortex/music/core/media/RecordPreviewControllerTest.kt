package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.data.settings.RecordShelfPreviewMode
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordPreviewControllerTest {
    private class Audio : RecordPreviewAudio {
        override var gain = 1f
        override var positionMs = 0L
        override var playing = false
        override var ended = false
        override var failed = false
        override var durationMs = 0L
        override var buffering = false
        val starts = mutableListOf<Pair<String, Float>>()
        var source: RecordPreviewSource? = null
        var released = false
        override suspend fun prepare(source: RecordPreviewSource) { this.source = source; positionMs = 0; ended = false }
        override fun play() { starts += requireNotNull(source).uri to gain; playing = true }
        override fun stop() { playing = false }
        override fun release() { released = true }
    }
    private fun source(song: Song) = RecordPreviewSource("song-${song.id}", "key-${song.id}", SongPreviewClip(47500, 67500, true))

    @Test fun audiblePlaybackFadesInAndRetractionFadesOutBeforeStopping() = runTest {
        val audio = Audio()
        val preview = RecordPreviewController(backgroundScope, audio, { source(it) }, nowMs = { testScheduler.currentTime })
        preview.select(Song(1, "One")); advanceTimeBy(100); runCurrent()
        assertTrue(audio.playing); assertEquals(0f, audio.starts.single().second)
        advanceTimeBy(250); runCurrent(); assertTrue(audio.gain in .2f.. .8f)
        advanceTimeBy(350); runCurrent(); assertEquals(1f, audio.gain)
        preview.select(null); runCurrent()
        assertTrue(audio.playing)
        advanceTimeBy(110); runCurrent(); assertTrue(audio.gain in .2f.. .8f)
        advanceTimeBy(150); runCurrent(); assertFalse(audio.playing); assertEquals(0f, audio.gain)
    }

    @Test fun rapidSelectionCancelsAStaleSourceAndEveryNewSourceStartsMuted() = runTest {
        val audio = Audio()
        val preview = RecordPreviewController(backgroundScope, audio, {
            if (it.id == 1L) withContext(NonCancellable) { delay(1000) }
            source(it)
        }, nowMs = { testScheduler.currentTime })
        preview.select(Song(1, "One")); advanceTimeBy(100)
        preview.select(Song(2, "Two")); advanceTimeBy(1500); runCurrent()
        assertEquals(listOf("song-2" to 0f), audio.starts)
        assertEquals(2L, preview.state.value.song!!.id)
        preview.select(Song(3, "Three")); advanceTimeBy(1000); runCurrent()
        assertEquals(listOf("song-2" to 0f, "song-3" to 0f), audio.starts)
    }

    @Test fun endingAndExplicitPlaybackBothDrainGainAndReleaseOnlyOnClose() = runTest {
        val audio = Audio()
        val preview = RecordPreviewController(backgroundScope, audio, { source(it) }, nowMs = { testScheduler.currentTime })
        preview.select(Song(1, "One")); advanceTimeBy(800)
        audio.positionMs = 19600; advanceTimeBy(150); runCurrent()
        assertTrue(audio.gain in 0f.. .99f)
        advanceTimeBy(500); runCurrent(); assertFalse(audio.playing)
        preview.select(Song(2, "Two")); advanceTimeBy(800)
        var committed = false
        preview.finish { assertEquals(0f, audio.gain); assertFalse(audio.playing); committed = true }
        advanceTimeBy(100); assertFalse(committed)
        advanceTimeBy(200); runCurrent(); assertTrue(committed); assertFalse(audio.released)
        preview.finish(release = true); runCurrent(); assertTrue(audio.released)
    }

    @Test fun loadingTimeoutStopsTheDeckAndDoesNotRemainPreparingForever() = runTest {
        val audio = Audio()
        val preview = RecordPreviewController(backgroundScope, audio, { delay(20000); source(it) }, nowMs = { testScheduler.currentTime })
        preview.select(Song(1, "One")); advanceTimeBy(13000); runCurrent()
        assertFalse(audio.playing); assertEquals(0f, audio.gain)
        assertEquals("试听加载超时", preview.state.value.label)
    }

    @Test fun loopRestartsMutedAtTheSameClipAndTurningItOffFinishesTheCurrentPass() = runTest {
        val audio = Audio()
        val preview = RecordPreviewController(backgroundScope, audio, { source(it) }, nowMs = { testScheduler.currentTime })
        preview.setMode(RecordShelfPreviewMode.LOOP)
        preview.select(Song(1, "One")); advanceTimeBy(800)
        audio.positionMs = 19600; advanceTimeBy(1000); runCurrent()
        assertEquals(listOf("song-1" to 0f, "song-1" to 0f), audio.starts)
        assertEquals(47500L, audio.source!!.clip.startMs)
        preview.setMode(RecordShelfPreviewMode.ONCE)
        audio.positionMs = 19600; advanceTimeBy(1000); runCurrent()
        assertEquals(2, audio.starts.size); assertFalse(audio.playing)
        preview.setMode(RecordShelfPreviewMode.LOOP); advanceTimeBy(800); runCurrent()
        assertEquals(3, audio.starts.size); assertTrue(audio.playing)
    }

    @Test fun nextOnlyEmitsOnceAfterNaturalCompletionIncludingEnablingAfterTheClipEnds() = runTest {
        val audio = Audio()
        val preview = RecordPreviewController(backgroundScope, audio, { source(it) }, nowMs = { testScheduler.currentTime })
        val requests = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { preview.nextRequests.collect { requests += it.id } }
        preview.select(Song(1, "One")); advanceTimeBy(800)
        audio.positionMs = 19600; advanceTimeBy(1000)
        assertTrue(requests.isEmpty())
        preview.setMode(RecordShelfPreviewMode.NEXT); runCurrent()
        assertEquals(listOf(1L), requests)
        preview.setMode(RecordShelfPreviewMode.ONCE); preview.setMode(RecordShelfPreviewMode.NEXT); runCurrent()
        assertEquals(listOf(1L), requests)
        preview.setMode(RecordShelfPreviewMode.RANDOM)
        preview.select(Song(2, "Two")); advanceTimeBy(800)
        audio.positionMs = 19600; advanceTimeBy(1000)
        assertEquals(listOf(1L, 2L), requests); assertFalse(audio.playing)
    }

    @Test fun pauseErrorsAndLostOwnershipNeverAdvanceOrRestart() = runTest {
        for (mode in listOf(RecordShelfPreviewMode.LOOP, RecordShelfPreviewMode.NEXT, RecordShelfPreviewMode.RANDOM)) {
            for (interruption in 0..2) {
                val audio = Audio()
                var allowed = true
                val preview = RecordPreviewController(backgroundScope, audio, { source(it) },
                    stillAllowed = { allowed }, nowMs = { testScheduler.currentTime })
                val requests = mutableListOf<Song>()
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { preview.nextRequests.collect { requests += it } }
                preview.setMode(mode); preview.select(Song(1, "One")); advanceTimeBy(800)
                when (interruption) {
                    0 -> audio.playing = false
                    1 -> { audio.failed = true; audio.ended = true }
                    2 -> { audio.positionMs = 19600; allowed = false }
                }
                advanceTimeBy(1200); runCurrent()
                assertEquals(1, audio.starts.size); assertTrue(requests.isEmpty()); assertFalse(audio.playing)
                preview.finish(release = true); runCurrent()
            }
        }
    }

    @Test fun fullLengthUsesTheActualTrackDurationAndSwitchingBackRestoresTheClip() = runTest {
        val audio = Audio().apply { durationMs = 180000 }
        val preview = RecordPreviewController(backgroundScope, audio, { source(it) }, nowMs = { testScheduler.currentTime })
        val requests = mutableListOf<Song>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { preview.nextRequests.collect { requests += it } }
        preview.setMode(RecordShelfPreviewMode.RANDOM)
        preview.select(Song(1, "One", durationMs = 200000)); advanceTimeBy(800)
        preview.setFullLength(true); advanceTimeBy(1000); runCurrent()
        assertTrue(audio.source!!.fullLength)
        assertEquals(0L, audio.source!!.clip.startMs)
        assertEquals(0f, audio.starts.last().second)
        assertEquals("整首试听", preview.state.value.label)
        audio.positionMs = 19600; advanceTimeBy(1000); runCurrent()
        assertTrue(audio.playing); assertTrue(requests.isEmpty())
        audio.playing = false; audio.buffering = true
        advanceTimeBy(1000); runCurrent()
        assertTrue(requests.isEmpty()); assertEquals("整首试听", preview.state.value.label)
        audio.buffering = false; audio.playing = true; audio.positionMs = 179600
        advanceTimeBy(1000); runCurrent()
        assertEquals(1L, requests.single().id); assertFalse(audio.playing)
        preview.setFullLength(false); advanceTimeBy(1000); runCurrent()
        assertFalse(audio.source!!.fullLength)
        assertEquals(47500L, audio.source!!.clip.startMs)
        assertEquals("高潮试听", preview.state.value.label)
    }
}
