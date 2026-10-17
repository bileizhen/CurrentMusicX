package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ChatEventClientTest {
    @Test fun anonymousPublicStreamUsesSequenceWithoutLeakingTokenInUrl(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "event: message\ndata: {\"seq\":4,\"type\":\"message\",\"payload\":{\"id\":9,\"content\":\"hi\"}}\n\n"))
            val opened = CompletableDeferred<Unit>()
            val event = withTimeout(3_000) { ChatEventClient().events(RequestSession(server.url("/cm/").toString(), null), 3) {
                opened.complete(Unit)
            }.first() }
            assertEquals(4L, event.seq)
            assertTrue(opened.isCompleted)
            val request = server.takeRequest()
            assertEquals("/cm/live/chat?sse=1&since=3", request.path)
            assertNull(request.getHeader("Authorization"))
            assertEquals("text/event-stream", request.getHeader("Accept"))
        }
    }

    @Test fun streamAuthorizesWithHeaderAndParsesDeletes(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "event: delete\ndata: {\"seq\":5,\"type\":\"delete\",\"payload\":{\"id\":9}}\n\n"))
            val event = withTimeout(3_000) { ChatEventClient().events(RequestSession(server.url("/cm/").toString(), "secret"), 4).first() }
            assertEquals("delete", event.type)
            val request = server.takeRequest()
            assertEquals("Bearer secret", request.getHeader("Authorization"))
            assertFalse(request.path!!.contains("secret"))
        }
    }

    @Test fun malformedEventsTerminateForSnapshotAndSequenceReplay(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "event: delete\ndata: {\"seq\":5,\"type\":\"delete\",\"payload\":{}}\n\n"))
            val error = try {
                withTimeout(3_000) { ChatEventClient().events(RequestSession(server.url("/cm/").toString(), null), 0).collect() }
                null
            } catch (error: IOException) { error }
            assertEquals("Invalid chat event", error?.message)
        }
    }

    @Test fun forbiddenStreamStopsWithTypedError(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))
            val error = try {
                withTimeout(3_000) { ChatEventClient().events(RequestSession(server.url("/cm/").toString(), null), 0).collect() }
                null
            } catch (error: ApiException) { error }
            assertEquals(ErrorKind.Forbidden, error?.kind)
            assertEquals(403, error?.status)
            assertEquals(1, server.requestCount)
        }
    }
}
