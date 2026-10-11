package io.github.currencortex.music.core.media

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.AudioSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NextAudioPreloaderTest {
    private val audio = ResolvedAudio(AudioSource("fixture", "standard", 44100, 2), "key", 20_000)

    @Test fun renewsBeforeExpiryAndCancellationStopsAllFutureWork() = runTest {
        val refreshes = mutableListOf<Boolean>()
        var downloads = 0
        val task = launch {
            NextAudioPreloader({ refreshes += it; audio }, { downloads++; delay(4_000); true },
                nowMs = { testScheduler.currentTime }).run()
        }
        runCurrent()
        advanceTimeBy(19_999); runCurrent()
        assertEquals(listOf(false), refreshes)
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(false, true), refreshes)
        task.cancelAndJoin()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(2, downloads)
    }

    @Test fun failedResolveAndFailedDownloadBothRetryWithFreshUrls() = runTest {
        val refreshes = mutableListOf<Boolean>()
        var downloads = 0
        val task = launch {
            NextAudioPreloader({
                refreshes += it
                if (refreshes.size == 1) throw IOException("temporary source failure")
                audio
            }, {
                downloads++
                if (downloads == 1) throw IOException("expired CDN URL")
                true
            }, nowMs = { testScheduler.currentTime }).run()
        }
        runCurrent(); advanceTimeBy(2_000); runCurrent()
        assertEquals(1, downloads)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf(false, true, true), refreshes)
        assertEquals(2, downloads)
        task.cancelAndJoin()
    }

    @Test fun rejectedAccountAndHighSpecificationWarningStopPrefetch() = runTest {
        var attempts = 0
        NextAudioPreloader({ attempts++; throw ApiException(ErrorKind.Forbidden) }, { fail("Denied account must not preload"); true }).run()
        assertEquals(1, attempts)
        var calls = 0
        NextAudioPreloader({ calls++; audio }, { false }).run()
        advanceTimeBy(60_000)
        assertEquals(1, calls)
    }
}
