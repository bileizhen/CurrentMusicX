package io.github.currencortex.music.core.room

import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.room.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RoomRefreshContractTest {
    @Test fun explicitRoleOverridesOnlineMembersAndNullRevokesMembership() {
        fun detail(role: String) = ApiJson.decodeFromJsonElement<RoomDetail>(ApiJson.parseToJsonElement(
            """{"room":{"id":1},"members":[{"userId":7,"role":"owner"}]$role}"""))
        assertEquals(RoomRole.OWNER, detail("").role(7))
        assertEquals(RoomRole.ADMIN, detail(",\"role\":\"admin\"").role(999))
        assertEquals(RoomRole.MEMBER, detail(",\"role\":\"member\"").role(7))
        assertNull(detail(",\"role\":null").role(7))
    }

    @Test fun futureAnchorPreloadsPausedAndUsesServerClockForStart() {
        val clock = RoomSynchronizer().apply { sample(10_000, 1000, 1000) }
        val timeline = RoomTimeline(basePosition = 1234, baseAt = 11_200, playing = true)
        assertFalse(clock.shouldPlay(timeline, 2199))
        assertEquals(1234L, clock.position(timeline, 2199))
        assertEquals(1L, clock.startsIn(timeline, 2199))
        assertTrue(clock.shouldPlay(timeline, 2200))
        assertFalse(clock.shouldPlay(timeline.copy(playing = false), 3000))
    }

    @Test fun resetWithOlderSequenceReplacesSnapshotAndReconnectCursor() = runBlocking {
        val count = AtomicInteger()
        val snapshot = AtomicReference("""{"room":{"id":"r1"},"role":"admin","latestSeq":50}""")
        fixture(snapshot, events = { request ->
            if (count.incrementAndGet() == 1) {
                snapshot.set("""{"room":{"id":"r1"},"role":"admin","latestSeq":7,"timeline":{"basePosition":700}}""")
                MockResponse().setHeader("Content-Type", "text/event-stream")
                    .setBody("id: 3\nevent: reset\ndata: {\"seq\":3}\n\n")
            } else {
                assertEquals("7", request.getHeader("Last-Event-ID"))
                assertEquals("7", request.requestUrl!!.queryParameter("since"))
                MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
            }
        }) { _, session, _ ->
            withTimeout(5000) { while (count.get() < 2) delay(20) }
            assertEquals(7L, session.state.value.detail!!.latestSeq)
            assertEquals(700L, session.state.value.detail!!.timeline!!.basePosition)
        }
    }

    @Test fun administratorRetriesFailedAutomaticAdvanceWithOriginalRevision() = runBlocking {
        val writes = CopyOnWriteArrayList<String>()
        val snapshot = AtomicReference("""{"room":{"id":"r1"},"role":"admin","latestSeq":5,"timeline":{"playing":true,"trackRevision":19}}""")
        fixture(snapshot, action = { request ->
            if (request.requestUrl!!.encodedPath.endsWith("/next")) {
                writes.add(request.body.readUtf8())
                if (writes.size == 1) MockResponse().setResponseCode(502) else {
                    snapshot.set("""{"room":{"id":"r1"},"role":"admin","latestSeq":6,"timeline":{"playing":false,"trackRevision":20}}""")
                    MockResponse().setBody("{}")
                }
            } else MockResponse().setBody("{}")
        }) { _, session, _ ->
            session.ended(); session.ended()
            withTimeout(8500) { while (writes.size < 2) delay(20) }
            assertEquals(listOf("{\"trackRevision\":19}", "{\"trackRevision\":19}"), writes.toList())
            withTimeout(2000) { while (session.state.value.detail?.timeline?.trackRevision != 20L) delay(20) }
            session.ended(); delay(100); assertEquals(2, writes.size)
        }
    }

    @Test fun expiringStreamRenewsBeforeFailureWithoutStartingPausedPlayback() = runBlocking {
        val writes = AtomicInteger()
        val now = System.currentTimeMillis()
        val snapshot = AtomicReference("""{"room":{"id":"r1"},"role":"member","latestSeq":5,"timeline":{"trackNcmId":42,"basePosition":3000,"playing":false,"stream":{"url":"https://example.com/old","expireAt":${now + 30_000}}}}""")
        fixture(snapshot, action = { request ->
            if (request.requestUrl!!.encodedPath.endsWith("/stream/renew")) {
                writes.incrementAndGet()
                snapshot.set("""{"room":{"id":"r1"},"role":"member","latestSeq":6,"timeline":{"trackNcmId":42,"basePosition":3000,"playing":false,"stream":{"url":"https://example.com/new","expireAt":${now + 300_000}}}}""")
            }
            MockResponse().setBody("{}")
        }) { _, session, player ->
            withTimeout(4000) { while (session.state.value.detail?.latestSeq != 6L) delay(20) }
            assertEquals(1, writes.get()); assertFalse(player.state.value.playing)
            assertEquals(3000L, player.state.value.positionMs)
        }
    }

    @Test fun seekingBackWithinSameTrackCancelsFailedAutomaticAdvance() = runBlocking {
        val writes = AtomicInteger()
        val snapshot = AtomicReference("""{"room":{"id":"r1"},"role":"admin","latestSeq":5,"timeline":{"playing":true,"trackRevision":19,"baseAt":1000}}""")
        fixture(snapshot, action = { request ->
            if (request.requestUrl!!.encodedPath.endsWith("/next")) {
                writes.incrementAndGet(); MockResponse().setResponseCode(502)
            } else MockResponse().setBody("{}")
        }) { _, session, _ ->
            session.ended()
            withTimeout(2000) { while (writes.get() != 1 || session.state.value.pendingActions.isNotEmpty()) delay(10) }
            // Another controller seeks within the same recording: the revision is unchanged.
            snapshot.set("""{"room":{"id":"r1"},"role":"admin","latestSeq":6,"timeline":{"playing":true,"trackRevision":19,"basePosition":30000,"baseAt":2000}}""")
            session.refresh()
            delay(5500)
            assertEquals(1, writes.get())
            // Reaching the end of this new playback timeline is still allowed to advance.
            session.ended()
            withTimeout(2000) { while (writes.get() < 2) delay(10) }
            assertEquals(2, writes.get())
        }
    }

    @Test fun localPlaybackIntentCancelsRetryBeforeServerTimelineChanges() = runBlocking {
        val writes = AtomicInteger()
        val snapshot = AtomicReference("""{"room":{"id":"r1"},"role":"admin","latestSeq":5,"timeline":{"playing":true,"trackRevision":19}}""")
        fixture(snapshot, action = { request ->
            val path = request.requestUrl!!.encodedPath
            if (path.endsWith("/next")) writes.incrementAndGet()
            if (path.endsWith("/next") || path.endsWith("/seek")) MockResponse().setResponseCode(502)
            else MockResponse().setBody("{}")
        }) { _, session, _ ->
            session.ended()
            withTimeout(2000) { while (writes.get() != 1 || session.state.value.pendingActions.isNotEmpty()) delay(10) }
            session.seek(30_000)
            delay(5500)
            assertEquals(1, writes.get())
        }
    }

    @Test fun automaticAdvanceSurvivesTransientSnapshotFailure() = runBlocking {
        val writes = CopyOnWriteArrayList<String>()
        val failedSnapshots = AtomicInteger()
        val snapshot = AtomicReference("""{"room":{"id":"r1"},"role":"admin","latestSeq":5,"timeline":{"playing":true,"trackRevision":19}}""")
        fixture(snapshot, detail = {
            if (writes.size == 1 && failedSnapshots.getAndIncrement() == 0) MockResponse().setResponseCode(503)
            else MockResponse().setBody(snapshot.get())
        }, action = { request ->
            if (request.requestUrl!!.encodedPath.endsWith("/next")) {
                writes.add(request.body.readUtf8())
                if (writes.size == 1) MockResponse().setResponseCode(502) else {
                    snapshot.set("""{"room":{"id":"r1"},"role":"admin","latestSeq":6,"timeline":{"playing":false,"trackRevision":20}}""")
                    MockResponse().setBody("{}")
                }
            } else MockResponse().setBody("{}")
        }) { _, session, _ ->
            session.ended()
            withTimeout(18_000) { while (writes.size < 2) delay(20) }
            assertTrue(failedSnapshots.get() >= 2)
            assertEquals(listOf("{\"trackRevision\":19}", "{\"trackRevision\":19}"), writes.toList())
        }
    }

    private suspend fun fixture(snapshot: AtomicReference<String>,
        events: (RecordedRequest) -> MockResponse = { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) },
        detail: () -> MockResponse = { MockResponse().setBody(snapshot.get()) },
        action: (RecordedRequest) -> MockResponse = { MockResponse().setBody("{}") },
        block: suspend (MockWebServer, RoomSession, SilentExternalPlayer) -> Unit) {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/cm/rooms/r1" -> detail()
                    "/cm/rooms/r1/sync" -> MockResponse().setBody("""{"serverNow":${System.currentTimeMillis()}}""")
                    "/cm/live/r1/events" -> events(request)
                    else -> action(request)
                }
            }
            val expected = RequestSession(server.url("/cm/").toString(), "fixture-token")
            val repo = RoomRepository(ApiClient({ expected.server }, { expected.token }, {})) { expected }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val player = SilentExternalPlayer()
            val session = RoomSession(repo, RoomSseClient(), player, scope, { 7 }, {}, { System.nanoTime() / 1_000_000 })
            try { session.join(RoomInfo("r1")); block(server, session, player) }
            finally { session.disconnect(); scope.cancel() }
        }
    }
}
