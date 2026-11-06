package io.github.currencortex.music.core.room

import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.room.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RoomStreamRecoveryTest {
    @Test fun failureImmediatelyAfterProactiveRenewalRepreparesUnchangedUrl() = runBlocking {
        fixture { session, player, renewals, _, _ ->
            await { renewals.get() == 1 && session.state.value.pendingActions.isEmpty() }
            assertEquals(1, player.preparedTracks.size)
            player.fail()
            await { player.preparedTracks.size == 2 && player.prepared }
            assertEquals(1, renewals.get())
            assertEquals(listOf(42L, 42L), player.preparedTracks.toList())
            assertEquals(listOf("https://example.com/audio", "https://example.com/audio"), player.preparedUrls.toList())
            assertEquals(3000L, player.state.value.positionMs)
            assertFalse(player.state.value.playing)
        }
    }

    @Test fun failureWhileRenewalIsPendingIsRetainedUntilItCompletes() = runBlocking {
        fixture(blockRenewal = true) { session, player, renewals, release, _ ->
            await { renewals.get() == 1 }
            player.fail(); player.fail()
            delay(100)
            assertEquals(1, player.preparedTracks.size)
            assertFalse(player.prepared)
            release.countDown()
            await { player.preparedTracks.size == 2 && player.prepared && session.state.value.pendingActions.isEmpty() }
            assertEquals(1, renewals.get())
        }
    }

    @Test fun switchingTrackCancelsRecoveryWaitingForRenewal() = runBlocking {
        fixture(blockRenewal = true) { session, player, renewals, release, snapshot ->
            await { renewals.get() == 1 }
            player.fail()
            snapshot.set(room(track = 77, revision = 20, seq = 7, expireAt = System.currentTimeMillis() + 300_000))
            session.refresh()
            assertEquals(listOf(42L, 77L), player.preparedTracks.toList())
            release.countDown()
            await { session.state.value.pendingActions.isEmpty() }
            delay(350)
            assertEquals(listOf(42L, 77L), player.preparedTracks.toList())
            assertEquals(77L, player.state.value.song?.id)
        }
    }

    @Test fun leavingCancelsRecoveryWaitingForRenewal() = runBlocking {
        fixture(blockRenewal = true) { session, player, renewals, release, _ ->
            await { renewals.get() == 1 }
            player.fail()
            session.disconnect()
            release.countDown()
            delay(350)
            assertEquals(listOf(42L), player.preparedTracks.toList())
            assertEquals(PlayerMode.LOCAL, player.state.value.mode)
            assertFalse(session.active)
        }
    }

    @Test fun failureDuringReprepareIsRetriedWithSeparateRecoveryCooldown() = runBlocking {
        fixture { session, player, renewals, _, _ ->
            await { renewals.get() == 1 && session.state.value.pendingActions.isEmpty() }
            player.failNextPrepare = true
            player.fail()
            await { player.preparedTracks.size == 3 && player.prepared }
            assertEquals(1, renewals.get())
            assertTrue(player.prepareTimes[2] - player.prepareTimes[1] >= 200)
        }
    }

    private fun room(track: Long = 42, revision: Long = 19, seq: Long = 5, expireAt: Long) =
        """{"room":{"id":"r1"},"role":"member","latestSeq":$seq,"timeline":{"trackNcmId":$track,"trackRevision":$revision,"basePosition":3000,"playing":false,"stream":{"url":"https://example.com/audio","expireAt":$expireAt}}}"""

    private suspend fun await(condition: () -> Boolean) = withTimeout(4000) {
        while (!condition()) delay(10)
    }

    private suspend fun fixture(blockRenewal: Boolean = false,
        block: suspend (RoomSession, RecoveringPlayer, AtomicInteger, CountDownLatch, AtomicReference<String>) -> Unit) {
        MockWebServer().use { server ->
            val renewals = AtomicInteger()
            val release = CountDownLatch(if (blockRenewal) 1 else 0)
            val initial = room(expireAt = System.currentTimeMillis() + 30_000)
            val snapshot = AtomicReference(initial)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/cm/rooms/r1" -> MockResponse().setBody(snapshot.get())
                    "/cm/rooms/r1/sync" -> MockResponse().setBody("""{"serverNow":${System.currentTimeMillis()}}""")
                    "/cm/live/r1/events" -> MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                    "/cm/rooms/r1/stream/renew" -> {
                        renewals.incrementAndGet()
                        check(release.await(5, TimeUnit.SECONDS))
                        snapshot.compareAndSet(initial, room(seq = 6, expireAt = System.currentTimeMillis() + 300_000))
                        MockResponse().setBody("{}")
                    }
                    else -> MockResponse().setBody("{}")
                }
            }
            val expected = RequestSession(server.url("/cm/").toString(), "fixture-token")
            val repo = RoomRepository(ApiClient({ expected.server }, { expected.token }, {})) { expected }
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
                val scope = CoroutineScope(SupervisorJob() + dispatcher)
                val player = RecoveringPlayer()
                val session = RoomSession(repo, RoomSseClient(), player, scope, { 7 }, {},
                    { System.nanoTime() / 1_000_000 }, recoveryIntervalMs = 250)
                try {
                    withContext(dispatcher) {
                        session.join(RoomInfo("r1"))
                        await { player.preparedTracks.isNotEmpty() }
                        block(session, player, renewals, release, snapshot)
                    }
                } finally {
                    release.countDown()
                    withContext(dispatcher) { session.disconnect(); scope.cancel() }
                }
            }
        }
    }

    private class RecoveringPlayer(private val delegate: SilentExternalPlayer = SilentExternalPlayer()) : ExternalPlayer by delegate {
        val preparedTracks = CopyOnWriteArrayList<Long>()
        val preparedUrls = CopyOnWriteArrayList<String>()
        val prepareTimes = CopyOnWriteArrayList<Long>()
        var prepared = false
        var failNextPrepare = false
        override suspend fun roomTrack(song: Song?, url: String?, position: Long, playing: Boolean) {
            delegate.roomTrack(song, url, position, playing)
            prepared = song != null && url != null
            if (song != null && url != null) {
                preparedTracks.add(song.id); preparedUrls.add(url); prepareTimes.add(System.nanoTime() / 1_000_000)
                if (failNextPrepare) { failNextPrepare = false; fail() }
            }
        }
        override suspend fun roomCorrection(position: Long, seek: Boolean, speed: Float, playing: Boolean) {
            // A player in STATE_IDLE after an error cannot be repaired by play()/seek alone.
            if (prepared) delegate.roomCorrection(position, seek, speed, playing)
        }
        fun fail() {
            prepared = false
            state.update { it.copy(playing = false, loading = false) }
            external?.failed()
        }
    }
}
