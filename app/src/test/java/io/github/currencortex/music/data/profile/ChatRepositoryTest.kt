package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ChatRepositoryTest {
    private fun repository(server: MockWebServer, token: () -> String? = { null }): ChatRepository {
        val base = server.url("/cm/").toString()
        return ChatRepository(ApiClient({ base }, token, {})) { RequestSession(base, token()) }
    }

    @Test fun anonymousHistoryKeepsChronologyAndUserMetadata(): Unit = runBlocking {
        MockWebServer().use { server ->
            val repo = repository(server)
            server.enqueue(MockResponse().setBody("""{"messages":[{"id":3,"userId":7,"content":"hi","createdAt":1720000000,"user":{"id":7,"title":"工程师","ip_region":"上海"},"mentions":[7]},{"id":4,"image":"one.jpg"}]}"""))
            val messages = repo.messages(9)
            assertEquals(listOf(3L, 4L), messages.map { it.id })
            assertEquals(1720000000L, messages.first().createdAt)
            assertEquals("工程师", messages.first().user!!.title)
            assertEquals("上海", messages.first().user!!.ipRegion)
            val request = server.takeRequest()
            assertEquals("/cm/chat/messages?limit=30&before=9", request.path)
            assertNull(request.getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("""{"members":[{"id":7,"nickname":"朋友"}]}"""))
            assertEquals("朋友", repo.members().single().nickname)
            assertEquals("/cm/chat/members", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"events":[{"seq":11,"type":"delete","payload":{"id":3}}],"latestSeq":11}"""))
            assertEquals(11L, repo.live(10).latestSeq)
            assertEquals("/cm/chat/live?since=10", server.takeRequest().path)
        }
    }

    @Test fun sendsAndDeletesUseScopedAuthorizationAndNumericMentions(): Unit = runBlocking {
        MockWebServer().use { server ->
            val repo = repository(server) { "secret" }
            server.enqueue(MockResponse().setBody("""{"id":10,"content":"hi"}"""))
            assertEquals(10L, repo.send(" hi ", "one.jpg", listOf(7, 7)).id)
            val request = server.takeRequest()
            assertEquals("POST", request.method); assertEquals("/cm/chat/messages", request.path)
            assertEquals("Bearer secret", request.getHeader("Authorization"))
            val body = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("hi", body["content"]!!.jsonPrimitive.content)
            assertEquals(7L, body["mentions"]!!.jsonArray.single().jsonPrimitive.long)
            server.enqueue(MockResponse().setBody("{}")); repo.delete(10)
            val delete = server.takeRequest()
            assertEquals("DELETE", delete.method); assertEquals("/cm/chat/messages/10", delete.path)
        }
    }

    @Test fun imageUploadMatchesPublicClientAndUrlsStayUnderConfiguredServer(): Unit = runBlocking {
        MockWebServer().use { server ->
            val repo = repository(server) { "secret" }
            server.enqueue(MockResponse().setBody("""{"image":"chat-1.jpg"}"""))
            assertEquals("chat-1.jpg", repo.upload(byteArrayOf(1, 2, 3)))
            val request = server.takeRequest()
            assertEquals("/cm/chat/image", request.path)
            val body = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("AQID", body["file"]!!.jsonPrimitive.content)
            assertEquals("chat.jpg", body["filename"]!!.jsonPrimitive.content)
            assertEquals("image/jpeg", body["contentType"]!!.jsonPrimitive.content)
            assertEquals("https://example.com/cm/chat/image/%E5%9B%BE%20%3F.jpg", repo.imageUrl("https://example.com/cm/", "图 ?.jpg"))
            for (bad in listOf("../image.jpg", "https://other/image.jpg", "x/y.jpg", "x\\y.jpg", "\n.jpg", ""))
                assertNull(repo.imageUrl("https://example.com/cm/", bad))
        }
    }

    @Test fun anonymousWritesAndOldAccountRequestsNeverLeaveDevice(): Unit = runBlocking {
        MockWebServer().use { server ->
            var token: String? = null
            val repo = repository(server) { token }
            assertTrue(appResult { repo.send("hi") } is AppResult.Failure)
            assertTrue(appResult { repo.delete(1) } is AppResult.Failure)
            assertTrue(appResult { repo.upload(byteArrayOf(1)) } is AppResult.Failure)
            val old = RequestSession(server.url("/cm/").toString(), "old")
            token = "new"
            assertTrue(appResult { repo.messages(expected = old) } is AppResult.Failure)
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        }
    }

    @Test fun lateHistoryIsRejectedAfterAccountSwitch(): Unit = runBlocking {
        MockWebServer().use { server ->
            var token: String? = "old"
            val repo = repository(server) { token }
            server.enqueue(MockResponse().setBody("""{"messages":[]}""").setBodyDelay(250, TimeUnit.MILLISECONDS))
            val pending = async { appResult { repo.messages() } }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            token = "new"
            assertTrue(pending.await() is AppResult.Failure)
        }
    }

    @Test fun uncertainSendIsNotRetried(): Unit = runBlocking {
        MockWebServer().use { server ->
            val repo = repository(server) { "secret" }
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setBody("""{"id":11}"""))
            assertTrue(appResult { repo.send("one message only") } is AppResult.Failure)
            assertEquals(1, server.requestCount)
        }
    }
}
