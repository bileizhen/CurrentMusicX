package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class PlayerAiRepositoryTest {
    @Test fun privatePostUsesCumulativeDeltasAndFinishesWithoutPostingPublicMessages() = runBlocking {
        MockWebServer().use { server ->
            val expected = RequestSession(server.url("/cm/").toString(), "fixture")
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "event: start\ndata: {\"ok\":true}\n\nevent: delta\ndata: {\"text\":\"你\"}\n\n" +
                "event: delta\ndata: {\"text\":\"你好\"}\n\nevent: done\ndata: {\"text\":\"你好！\",\"cards\":[{\"type\":\"song\",\"id\":8,\"name\":\"歌\"}]}\n\n"))
            val events = withTimeout(3000) { PlayerAiRepository({ expected }).ask("介绍这首歌", PostAttachment("song", 7, "测试"), true).toList() }
            assertEquals("done", events.last().type)
            assertEquals("你好！", events.last().text)
            assertEquals(8L, events.last().cards.single().id)
            val request = server.takeRequest()
            assertEquals("/cm/ai/live", request.path); assertEquals("POST", request.method)
            assertEquals("Bearer fixture", request.getHeader("Authorization"))
            assertTrue(ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["reset"]!!.jsonPrimitive.boolean)
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun unauthenticatedRequestCannotStartOrLeakToAnotherSession() = runBlocking {
        MockWebServer().use { server ->
            val session = RequestSession(server.url("/cm/").toString(), null)
            val error = runCatching { PlayerAiRepository({ session }).ask("问题", PostAttachment("song", 7, "歌"), true).toList() }.exceptionOrNull()
            assertTrue(error is ApiException); assertEquals(0, server.requestCount)
        }
    }
    @Test fun truncatedAnswerIsReportedAsFailureWithoutAutomaticRetry() = runBlocking {
        MockWebServer().use { server ->
            val session = RequestSession(server.url("/cm/").toString(), "fixture")
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("event: delta\ndata: {\"text\":\"部分回答\"}\n\n"))
            val error = runCatching { withTimeout(3000) { PlayerAiRepository({ session }).ask("问题", PostAttachment("song", 7, "歌"), true).toList() } }.exceptionOrNull()
            assertTrue(error is java.io.IOException); assertEquals(1, server.requestCount)
        }
    }

    @Test fun fastCumulativeStreamKeepsFinalAnswerWhenRenderingIsSlow() = runBlocking {
        MockWebServer().use { server ->
            val session = RequestSession(server.url("/cm/").toString(), "fixture")
            val body = buildString {
                repeat(2000) { append("event: delta\ndata: {\"text\":\"回答 $it\"}\n\n") }
                append("event: done\ndata: {\"text\":\"完整回答\",\"cards\":[{\"type\":\"song\",\"id\":8,\"name\":\"推荐\"}]}\n\n")
            }
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))
            val events = mutableListOf<PlayerAiEvent>()
            withTimeout(5000) {
                PlayerAiRepository({ session }).ask("介绍", PostAttachment("song", 7, "歌"), true).collect {
                    delay(20) // Markdown layout can be slower than the incoming cumulative snapshots.
                    events += it
                }
            }
            assertEquals("done", events.last().type)
            assertEquals("完整回答", events.last().text)
            assertEquals(8L, events.last().cards.single().id)
            assertEquals(1, server.requestCount)
        }
    }
}
