package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackLyricsTest {
    @Test fun lateCancelledProviderCannotReplaceNextTrackEvenWithoutUiSubscribers() = runTest {
        val first = CompletableDeferred<Unit>()
        val requests = MutableStateFlow(LyricsRequest(Song(1, "First")))
        val store = PlaybackLyrics(requests, { song ->
            if (song.id == 1L) withContext(NonCancellable) { first.await() }
            LyricsResult(LyricsDocument(listOf(LyricLine(0, 2000, song.name))))
        }, backgroundScope)
        runCurrent()
        requests.value = LyricsRequest(Song(2, "Next")); runCurrent()
        assertEquals(2L, store.state.value.songId)
        assertEquals("Next", store.state.value.document.lines.single().text)
        first.complete(Unit); runCurrent()
        assertEquals("Next", store.state.value.document.lines.single().text)
    }
    @Test fun oneLoadSharedByConsumersSessionChangeAndVideoClear() = runTest {
        val song = Song(1, "Song")
        val requests = MutableStateFlow(LyricsRequest(song, listOf("session")))
        var count = 0
        val store = PlaybackLyrics(requests, { count++; LyricsResult(LyricsDocument(listOf(LyricLine(0, 2000, "Lyrics")))) }, backgroundScope)
        runCurrent()
        val consumer1 = store.state.value; val consumer2 = store.state.value
        assertSame(consumer1, consumer2); assertEquals(1, count)
        requests.value = LyricsRequest(song, listOf("new-session")); runCurrent()
        assertEquals(2, count)
        requests.value = LyricsRequest(song.copy(video = true)); runCurrent()
        assertNull(store.state.value.songId); assertTrue(store.state.value.document.lines.isEmpty())
    }
}
