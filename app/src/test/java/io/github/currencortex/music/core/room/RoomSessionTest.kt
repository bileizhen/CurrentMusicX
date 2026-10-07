package io.github.currencortex.music.core.room

import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.room.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class RoomSessionTest {
    @Test fun duplicateCommandsSubmitOnceAndRefreshFailureDoesNotUndoAcceptedWrite() = runBlocking {
        withOwnerRoom { server, session ->
            val writes = AtomicInteger()
            val release = java.util.concurrent.CountDownLatch(1)
            val failedRefresh = java.util.concurrent.atomic.AtomicBoolean()
            val original = server.dispatcher
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.requestUrl!!.encodedPath.endsWith("/next")) {
                        writes.incrementAndGet(); release.await(3, java.util.concurrent.TimeUnit.SECONDS)
                        failedRefresh.set(true); return MockResponse().setBody("{}")
                    }
                    if (request.requestUrl!!.encodedPath == "/cm/rooms/r1" && failedRefresh.get()) return MockResponse().setResponseCode(500)
                    return original.dispatch(request)
                }
            }
            try {
                session.next(); session.next()
                withTimeout(2000) { while (writes.get() == 0) delay(10) }
                assertEquals(setOf("next"), session.state.value.pendingActions)
                release.countDown()
                withTimeout(3000) { while (session.state.value.pendingActions.isNotEmpty()) delay(10) }
                assertEquals(1, writes.get())
                assertTrue(session.state.value.error?.startsWith("操作已提交") == true)
                assertTrue(session.active)
            } finally { release.countDown() }
        }
    }
    @Test fun commandFinishingAfterDisconnectCannotRestoreErrorOrPendingState() = runBlocking {
        withOwnerRoom { server, session ->
            val started = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            val original = server.dispatcher
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.requestUrl!!.encodedPath.endsWith("/next")) {
                        started.countDown(); release.await(3, java.util.concurrent.TimeUnit.SECONDS)
                        return MockResponse().setResponseCode(500)
                    }
                    return original.dispatch(request)
                }
            }
            try {
                session.next()
                withContext(Dispatchers.IO) { assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS)) }
                session.disconnect(); release.countDown(); delay(300)
                assertFalse(session.active); assertNull(session.state.value.error)
                assertTrue(session.state.value.pendingActions.isEmpty())
            } finally { release.countDown() }
        }
    }
    @Test fun staleSnapshotCannotReplaceNewerTimeline() = runBlocking {
        withOwnerRoom { server, session ->
            val original = server.dispatcher
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = if (request.requestUrl!!.encodedPath == "/cm/rooms/r1")
                    MockResponse().setBody("""{"room":{"id":"r1"},"members":[{"userId":7,"role":"owner"}],"latestSeq":4,"timeline":{"basePosition":100}}""")
                else original.dispatch(request)
            }
            session.refresh()
            assertEquals(5L, session.state.value.detail!!.latestSeq)
            assertNull(session.state.value.detail!!.timeline)
        }
    }
    private suspend fun withOwnerRoom(block: suspend (MockWebServer, RoomSession) -> Unit) {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/cm/rooms/r1" -> MockResponse().setBody("""{"room":{"id":"r1"},"members":[{"userId":7,"role":"owner"}],"latestSeq":5}""")
                    "/cm/rooms/r1/sync" -> MockResponse().setBody("""{"serverNow":${System.currentTimeMillis()}}""")
                    "/cm/live/r1/events" -> MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                    else -> MockResponse().setBody("{}")
                }
            }
            val expected = RequestSession(server.url("/cm/").toString(), "owner-fixture-token")
            val repo = RoomRepository(ApiClient({ expected.server }, { expected.token }, {})) { expected }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val session = RoomSession(repo, RoomSseClient(), SilentExternalPlayer(), scope, { 7 }, {}, { System.nanoTime() / 1_000_000 })
            try { session.join(RoomInfo("r1")); block(server, session) }
            finally { session.disconnect(); scope.cancel() }
        }
    }
    @Test fun acceptedSongRequestIsNotRetriedWhenQueueRefreshFails() = runBlocking {
        MockWebServer().use { server ->
            val refreshFail = java.util.concurrent.atomic.AtomicBoolean()
            val writes = AtomicInteger()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/cm/rooms/r1" -> if (refreshFail.get()) MockResponse().setResponseCode(500) else
                        MockResponse().setBody("""{"room":{"id":"r1"},"members":[{"userId":7,"role":"member"}]}""")
                    "/cm/rooms/r1/queue" -> { writes.incrementAndGet(); refreshFail.set(true); MockResponse().setBody("{}") }
                    "/cm/rooms/r1/sync" -> MockResponse().setBody("""{"serverNow":${System.currentTimeMillis()}}""")
                    "/cm/live/r1/events" -> MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                    else -> MockResponse().setBody("{}")
                }
            }
            val expected = RequestSession(server.url("/cm/").toString(), "request-fixture-token")
            val repo = RoomRepository(ApiClient({ expected.server }, { expected.token }, {})) { expected }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val session = RoomSession(repo, RoomSseClient(), SilentExternalPlayer(), scope, { 7 }, {}, { System.nanoTime() / 1_000_000 })
            try {
                session.join(RoomInfo("r1"))
                assertTrue(session.submit(Song(95, "Requested")) is AppResult.Success)
                withTimeout(2000) { while (session.state.value.error?.startsWith("点歌已提交") != true) delay(10) }
                assertEquals(1, writes.get()); assertTrue(session.active)
            } finally { session.disconnect(); scope.cancel() }
        }
    }
    @Test fun disconnectReconnectReplayAndCloseRestoreSilentLocalQueue() = runBlocking {
        MockWebServer().use { server ->
            val connections = AtomicInteger(); val observed = CopyOnWriteArrayList<RecordedRequest>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    observed.add(request)
                    return when(request.requestUrl!!.encodedPath) {
                        "/cm/rooms/r1" -> MockResponse().setBody("""{"room":{"id":"r1","code":"123456"},"members":[{"userId":7,"role":"owner"}],"latestSeq":5}""")
                        "/cm/rooms/r1/sync" -> MockResponse().setBody("""{"serverNow":${System.currentTimeMillis()}}""")
                        "/cm/live/r1/events" -> MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                            if (connections.incrementAndGet() == 1) "id: 6\nevent: seek\ndata: {\"seq\":6,\"payload\":{}}\n\n"
                            else "id: 7\nevent: close\ndata: {\"seq\":7,\"payload\":{}}\n\n")
                        else -> MockResponse().setBody("{}")
                    }
                }
            }
            val expected = RequestSession(server.url("/cm/").toString(), "isolated-room-token")
            val repo = RoomRepository(ApiClient({ expected.server }, { expected.token }, {})) { expected }
            val player = SilentExternalPlayer().apply { queue.replace(listOf(Song(55, "Local")), 0) }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val session = RoomSession(repo, RoomSseClient(), player, scope, { 7 }, {}, { System.nanoTime() / 1_000_000 })
            try {
                session.join(RoomInfo("r1"))
                withTimeout(6000) { while (connections.get() < 2 || session.active) delay(20) }
                assertEquals(PlayerMode.LOCAL, player.state.value.mode); assertEquals(55L, player.queue.state.value.current?.id)
                assertFalse(player.state.value.playing)
                val streams = observed.filter { it.requestUrl!!.encodedPath.contains("/live/") }
                assertEquals("6", streams[1].requestUrl!!.queryParameter("since")); assertEquals("6", streams[1].getHeader("Last-Event-ID"))
                val count = connections.get(); delay(1200); assertEquals(count, connections.get())
            } finally { session.disconnect(); scope.cancel() }
        }
    }
    @Test fun memberCannotSendPlaybackOrApprovalCommands() = runBlocking {
        MockWebServer().use { server ->
            val observed = CopyOnWriteArrayList<String>()
            val memberRole = java.util.concurrent.atomic.AtomicReference("member")
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath; observed.add(path)
                    return when(path) {
                        "/cm/rooms/r1" -> MockResponse().setBody("""{"room":{"id":"r1"},"members":[{"userId":7,"role":"${memberRole.get()}"}]}""")
                        "/cm/rooms/r1/sync" -> MockResponse().setBody("""{"serverNow":${System.currentTimeMillis()}}""")
                        "/cm/live/r1/events" -> MockResponse().setHeader("Content-Type", "text/event-stream").setBody(": ping\n\n")
                        else -> MockResponse().setBody("{}")
                    }
                }
            }
            val expected = RequestSession(server.url("/cm/").toString(), "member-token")
            val repo = RoomRepository(ApiClient({ expected.server }, { expected.token }, {})) { expected }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val player = SilentExternalPlayer()
            val session = RoomSession(repo, RoomSseClient(), player, scope, { 7 }, {}, { System.nanoTime() / 1_000_000 })
            try {
                session.join(RoomInfo("r1")); session.play(true); session.next(); session.queueAction(RoomQueueItem("q1"), "approve")
                delay(150)
                assertFalse(observed.any { it.endsWith("/play") || it.endsWith("/next") || it.endsWith("/approve") })
                assertFalse(player.state.value.canControlPlayback)
                memberRole.set("admin"); session.refresh(); assertTrue(player.state.value.canControlPlayback)
                memberRole.set("member"); session.refresh(); assertFalse(player.state.value.canControlPlayback)
                session.leave(); assertFalse(session.active); assertTrue(player.state.value.canControlPlayback)
            } finally { session.disconnect(); scope.cancel() }
        }
    }
}
