package io.github.currencortex.music.core.room

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.room.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class RoomManagementContractTest {
    @Test fun ownedRoomsKeepArchivedStateAndLeadingZeroInviteCode() = runBlocking {
        MockWebServer().use { server ->
            val session = RequestSession(server.url("/cm/").toString(), "fixture")
            val repo = RoomRepository(ApiClient({ session.server }, { session.token }, {})) { session }
            server.enqueue(MockResponse().setBody("""{"total":1,"rooms":[{"id":12,"code":"001234","closed_at":1794000000,"archive_reason":"idle"}]}"""))
            val result = repo.list("0012", 20, RoomScope.MINE)
            val request = server.takeRequest()
            assertEquals("mine", request.requestUrl!!.queryParameter("scope"))
            assertEquals("20", request.requestUrl!!.queryParameter("offset"))
            assertEquals("0012", request.requestUrl!!.queryParameter("query"))
            assertTrue(result.rooms.single().archived)
            assertEquals("001234", result.rooms.single().code)
            assertEquals("idle", result.rooms.single().archiveReason)
            assertFalse(ApiJson.decodeFromJsonElement<RoomCard>(ApiJson.parseToJsonElement("""{"id":12,"closed_at":null}""")).archived)
            val room = ApiJson.decodeFromJsonElement<RoomInfo>(ApiJson.parseToJsonElement("""{"id":12,"archivedAt":1794000000,"archiveReason":"manual"}"""))
            assertTrue(room.archived)
        }
    }

    @Test fun restorationDeletionAndUnbanUseInternalIdAndExpectedSession() = runBlocking {
        MockWebServer().use { server ->
            var session = RequestSession(server.url("/cm/").toString(), "fixture")
            val expected = session
            val repo = RoomRepository(ApiClient({ session.server }, { session.token }, {})) { session }
            server.enqueue(MockResponse().setBody("""{"ok":true,"room":{"id":12,"code":"001234","archivedAt":0}}"""))
            assertFalse(repo.reopen("12", expected).archived)
            server.takeRequest().let { assertEquals("POST", it.method); assertEquals("/cm/rooms/12/reopen", it.path) }
            server.enqueue(MockResponse().setBody("""{"bans":[{"userId":9,"nickname":"User","reason":"spam","createdAt":1}]}"""))
            assertEquals(9L, repo.bans("12", expected).single().userId)
            assertEquals("/cm/rooms/12/bans", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("{}")); repo.unban("12", 9, expected)
            server.takeRequest().let { assertEquals("DELETE", it.method); assertEquals("/cm/rooms/12/bans/9", it.path) }
            server.enqueue(MockResponse().setBody("{}")); repo.delete("12", expected)
            server.takeRequest().let { assertEquals("DELETE", it.method); assertEquals("/cm/rooms/12", it.path) }
            session = RequestSession(session.server, "replacement", session.neteaseRevision)
            assertTrue(appResult { repo.delete("12", expected) } is AppResult.Failure)
            assertNull(server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS))
        }
    }

    @Test fun createKeepsApprovalIndependentOfFreeModeAndValidatesLimits() = runBlocking {
        MockWebServer().use { server ->
            val session = RequestSession(server.url("/cm/").toString(), "fixture")
            val repo = RoomRepository(ApiClient({ session.server }, { session.token }, {})) { session }
            server.enqueue(MockResponse().setBody("""{"room":{"id":12}}"""))
            repo.create(" Room ", "", false, false, session, needApproval = false, joinLocked = true)
            val body = ApiJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertFalse(body.getValue("freeMode").jsonPrimitive.boolean)
            assertFalse(body.getValue("needApproval").jsonPrimitive.boolean)
            assertTrue(body.getValue("joinLocked").jsonPrimitive.boolean)
            assertEquals("Room", body.getValue("name").jsonPrimitive.content)
            assertTrue(appResult { repo.create("x".repeat(61), "", true, false, session) } is AppResult.Failure)
            assertTrue(appResult { repo.create("x", "x".repeat(129), true, false, session) } is AppResult.Failure)
            assertNull(server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS))
        }
    }

    @Test fun reorderSendsWaitingQueueIndex() = runBlocking {
        MockWebServer().use { server ->
            val session = RequestSession(server.url("/cm/").toString(), "fixture")
            val repo = RoomRepository(ApiClient({ session.server }, { session.token }, {})) { session }
            server.enqueue(MockResponse().setBody("{}")); repo.reorder("12", "91", 0, session)
            val request = server.takeRequest()
            assertEquals("/cm/rooms/12/queue/reorder", request.path)
            val body = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("91", body.getValue("qid").jsonPrimitive.content)
            assertEquals(0, body.getValue("index").jsonPrimitive.int)
            assertTrue(appResult { repo.reorder("12", "91", -1, session) } is AppResult.Failure)
        }
    }
}
