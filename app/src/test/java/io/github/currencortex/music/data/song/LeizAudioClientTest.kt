package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.settings.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class LeizAudioClientTest {
    private fun response(level: String = "lossless") = MockResponse().setBody(
        """{"success":true,"data":{"url":"https://cdn.example/song.flac","level":"$level","type":"flac","name":"ignored metadata"}}""")

    @Test fun explicitQualityErrorsAndMissingKeyHaveSafeMessagesWithoutExpiringProjectSession() = runBlocking {
        MockWebServer().use { server ->
            val client = LeizAudioClient(server.url("/api/netease").toString())
            suspend fun failure(kind: ErrorKind, key: String = "fixture-key") {
                try { client.source(1, AudioQuality.LOSSLESS, key); fail("Expected failure") }
                catch (e: ApiException) { assertEquals(kind, e.kind); if (key.isNotEmpty()) assertFalse(e.message.orEmpty().contains(key)) }
            }
            failure(ErrorKind.AudioKeyRequired, ""); assertEquals(0, server.requestCount)
            for ((status, kind) in listOf(401 to ErrorKind.AudioKeyRejected, 403 to ErrorKind.AudioKeyRejected,
                404 to ErrorKind.AudioUnavailable, 429 to ErrorKind.RateLimited, 503 to ErrorKind.Server)) {
                server.enqueue(MockResponse().setResponseCode(status).setBody("fixture-key private response"))
                failure(kind)
                assertEquals("lossless", server.takeRequest().requestUrl!!.queryParameter("level"))
            }
            server.enqueue(MockResponse().setBody("""{"success":false,"message":"fixture-key"}""")); failure(ErrorKind.AudioUnavailable)
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"success":true,"data":{"url":"file:///private/fixture-key"}}""")); failure(ErrorKind.AudioUnavailable)
        }
    }

    @Test fun redirectsCannotForwardTheApiKeyAndCancellationStopsRequest() = runBlocking {
        MockWebServer().use { server -> MockWebServer().use { destination ->
            val client = LeizAudioClient(server.url("/api/netease").toString())
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/steal")))
            try { client.source(1, AudioQuality.STANDARD, "fixture-key"); fail("Redirect is not JSON") }
            catch (e: ApiException) { assertEquals(ErrorKind.Parse, e.kind) }
            assertEquals(0, destination.requestCount); server.takeRequest()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = launch { client.source(1, AudioQuality.STANDARD, "fixture-key") }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
            withTimeout(3000) { job.cancelAndJoin() }
        } }
    }

}
