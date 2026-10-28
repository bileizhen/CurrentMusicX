package io.github.currencortex.music.core.media

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListeningReportQueueTest {
    private val owner = RequestSession("https://example.test/cm/", "owner", 1)
    private val song = Song(11, "Track")

    @Test fun nativeFailureDoesNotBlockCurrentMusicStartOrListeningDeltas() = runTest {
        val current = mutableListOf<Long?>()
        val failures = mutableListOf<String>()
        val queue = ListeningReportQueue(backgroundScope, { owner },
            currentMusic = { _, ms, _ -> current += ms },
            netease = { _, _, _ -> throw ApiException(ErrorKind.Server) },
            navidrome = { _, _, _ -> fail("Private library must not receive a public track") },
            onFailure = { platform, _ -> failures += platform })
        queue.report(song, owner)
        queue.report(song, owner, 15_000)
        runCurrent()
        assertEquals(listOf(null, 15_000L), current)
        assertEquals(listOf("Netease", "Netease"), failures)
    }

    @Test fun slowNativeRequestDoesNotDelayCurrentMusicAndItsFinalDelta() = runTest {
        val current = mutableListOf<Long?>()
        val blocked = CompletableDeferred<Unit>()
        val queue = ListeningReportQueue(backgroundScope, { owner },
            currentMusic = { _, ms, _ -> current += ms },
            netease = { _, _, _ -> blocked.await() }, navidrome = { _, _, _ -> })
        val service = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        service.launch { queue.report(song, owner); queue.report(song, owner, 1200) }
        runCurrent()
        service.cancel()
        assertEquals(listOf(null, 1200L), current)
        blocked.complete(Unit)
    }

    @Test fun accountAndNativeSessionChangesAreCheckedIndependentlyBeforeSending() = runTest {
        var active = owner
        val current = mutableListOf<Long?>()
        val native = mutableListOf<Long?>()
        val queue = ListeningReportQueue(backgroundScope, { active },
            currentMusic = { _, ms, _ -> current += ms },
            netease = { _, ms, _ -> native += ms }, navidrome = { _, _, _ -> })
        queue.report(song, owner, 15_000)
        active = RequestSession(owner.server, owner.token, 2)
        runCurrent()
        assertEquals(listOf(15_000L), current)
        assertTrue(native.isEmpty())
        queue.report(song, active, 1200)
        active = RequestSession(owner.server, "another-account", 3)
        runCurrent()
        assertEquals(listOf(15_000L), current)
        assertTrue(native.isEmpty())
    }

    @Test fun videosAndPrivateTracksNeverReachPublicStatistics() = runTest {
        val privateReports = mutableListOf<Long?>()
        val queue = ListeningReportQueue(backgroundScope, { owner },
            currentMusic = { _, _, _ -> fail("Private track leaked to CurrentMusic") },
            netease = { _, _, _ -> fail("Private track leaked to Netease") },
            navidrome = { _, ms, _ -> privateReports += ms })
        queue.report(song.copy(video = true), owner)
        queue.report(song.copy(video = true), owner, 15_000)
        queue.report(song.copy(musicSource = MusicSource.NAVIDROME), owner)
        queue.report(song.copy(musicSource = MusicSource.NAVIDROME), owner, 15_000)
        queue.report(song.copy(musicSource = MusicSource.LOCAL, localUri = "content://media/external/audio/media/1"), owner)
        queue.report(song.copy(musicSource = MusicSource.LOCAL, localUri = "content://media/external/audio/media/1"), owner, 15_000)
        queue.report(song, owner, 0)
        runCurrent()
        assertEquals(listOf(null, 15_000L), privateReports)
    }
}
