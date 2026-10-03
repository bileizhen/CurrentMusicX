package io.github.currencortex.music.core.room

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.room.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Assert.*

class RoomProtocolTest {
    @Test fun clockIgnoresSlowSamplesAndPausedPositionDoesNotAdvance() {
        val sync = RoomSynchronizer()
        sync.sample(10_000, 1000, 1100); sync.sample(80_000, 1200, 1600)
        val tl = RoomTimeline(playing = true, basePosition = 2000, baseAt = 10_000)
        assertEquals(3050L, sync.position(tl, 2100))
        assertEquals(2000L, sync.position(tl.copy(playing = false), 100_000))
        assertEquals(1.04f, sync.correction(tl, 2100, 2700).speed)
        assertEquals(.96f, sync.correction(tl, 2100, 3450).speed)
        assertEquals(1f, sync.correction(tl, 2100, 3000).speed)
        assertTrue(sync.correction(tl, 2100, 0).seek)
        assertTrue(sync.correction(tl.copy(playing = false), 2100, 2500).seek)
        assertEquals(1f, sync.correction(tl.copy(playing = false), 2100, 2500).speed)
    }
    @Test fun roleMatrixProtectsOwnersAndOtherAdministrators() {
        assertTrue(RoomPermissions.kick(RoomRole.OWNER, RoomRole.ADMIN))
        assertTrue(RoomPermissions.kick(RoomRole.ADMIN, RoomRole.MEMBER))
        assertFalse(RoomPermissions.kick(RoomRole.ADMIN, RoomRole.ADMIN))
        assertFalse(RoomPermissions.kick(RoomRole.ADMIN, RoomRole.OWNER))
        assertFalse(RoomPermissions.kick(RoomRole.MEMBER, RoomRole.MEMBER))
        assertTrue(RoomPermissions.remove(RoomRole.MEMBER, RoomQueueItem("q", mine = true)))
        assertFalse(RoomPermissions.remove(RoomRole.MEMBER, RoomQueueItem("q")))
        assertFalse(RoomPermissions.remove(RoomRole.OWNER, RoomQueueItem("q", status = "playing")))
    }
    @Test fun roomAndRequestContractsUseCamelCaseRoomAndSnakeCaseMetadata() = runBlocking {
        MockWebServer().use { server ->
            val expected = RequestSession(server.url("/cm/").toString(), "room-fixture-token")
            var current = expected
            val repo = RoomRepository(ApiClient({ current.server }, { current.token }, {})) { current }
            server.enqueue(MockResponse().setBody("""{"room":{"id":"uuid-room","code":"012345","name":"Private","hasPassword":true}}"""))
            assertTrue(repo.find("012345").hasPassword)
            assertEquals("012345", server.takeRequest().requestUrl!!.queryParameter("code"))
            server.enqueue(MockResponse().setBody("{}"))
            repo.add("uuid-room", Song(11, "A&B", "artist", durationMs = 1234), expected)
            val request = server.takeRequest()
            assertEquals("Bearer room-fixture-token", request.getHeader("Authorization"))
            assertEquals("/cm/rooms/uuid-room/queue", request.path)
            val meta = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject.getValue("meta").jsonObject
            assertEquals(11L, meta.getValue("ncm_id").jsonPrimitive.long)
            assertEquals(1234L, meta.getValue("duration").jsonPrimitive.long)
            server.enqueue(MockResponse().setBody("{}")); repo.queueAction("uuid-room", "uuid-queue", "reject", expected)
            assertEquals("/cm/rooms/uuid-room/queue/uuid-queue/reject", server.takeRequest().path)
            current = RequestSession(current.server, "replacement-token")
            val rejected = appResult { repo.action("uuid-room", "pause", expected = expected) }
            assertEquals(ErrorKind.Unauthorized, (rejected as AppResult.Failure).kind)
            assertNull(server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS))
        }
    }
    @Test fun numericAndOpaqueRoomIdsDecodeWithoutLosingLeadingZeroCode() {
        val room = ApiJson.decodeFromJsonElement<RoomInfo>(ApiJson.parseToJsonElement("""{"id":12,"code":"001234"}"""))
        assertEquals("12", room.id); assertEquals("001234", room.code)
        val rooms = ApiJson.decodeFromJsonElement<RoomsDto>(ApiJson.parseToJsonElement("""{"rooms":[{"id":12,"free_mode":1}]}"""))
        assertTrue(rooms.rooms.first().freeMode)
        val roomFlags = ApiJson.decodeFromJsonElement<RoomInfo>(ApiJson.parseToJsonElement("""{"id":12,"hasPassword":0,"joinLocked":1}"""))
        assertFalse(roomFlags.hasPassword); assertTrue(roomFlags.joinLocked)
        val timeline = ApiJson.decodeFromJsonElement<RoomTimeline>(ApiJson.parseToJsonElement("""{"basePosition":1234.5,"playing":0}"""))
        assertEquals(1235L, timeline.basePosition); assertFalse(timeline.playing)
    }
    @Test fun sseResumesSequenceUsesBearerHeaderAndCancelsAfterCollectorLeaves() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody("id: 43\nevent: seek\ndata: {\"seq\":43,\"payload\":{}}\n\n"))
            val session = RequestSession(server.url("/cm/").toString(), "sse-fixture-token")
            val signals = withTimeout(3000) { RoomSseClient().events("opaque-room", 42, "42", session).take(2).toList() }
            assertEquals(RoomSignal.Open, signals[0]); assertEquals("43", (signals[1] as RoomSignal.Event).id)
            val request = server.takeRequest()
            assertEquals("Bearer sse-fixture-token", request.getHeader("Authorization"))
            assertEquals("42", request.getHeader("Last-Event-ID"))
            assertEquals("42", request.requestUrl!!.queryParameter("since"))
            assertNull(request.requestUrl!!.queryParameter("token"))
        }
    }
}
