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

    @Test fun onlyAudioUsesLeizAndProjectCredentialsNeverReachIt() = runBlocking {
        MockWebServer().use { cm -> MockWebServer().use { leiz ->
            var access = AudioSourceAccess(AudioProvider.LEIZ, "fixture-secret-key")
            val repo = MusicRepository(ApiClient({ cm.url("/cm/").toString() }, { "project-token" }, {}),
                { access }, LeizAudioClient(leiz.url("/api/netease").toString()))
            leiz.enqueue(response("jyeffect"))
            val source = repo.source(347230, AudioQuality.AUTO)
            assertEquals("jyeffect", source.level); assertTrue(source.highSpec)
            val request = leiz.takeRequest()
            assertEquals("jymaster", request.requestUrl!!.queryParameter("level"))
            assertEquals("347230", request.requestUrl!!.queryParameter("id"))
            assertEquals("fixture-secret-key", request.getHeader("x-api-key"))
            assertNull(request.getHeader("Token")); assertNull(request.getHeader("Authorization"))
            assertNull(request.requestUrl!!.queryParameter("key")); assertEquals(0, cm.requestCount)
            cm.enqueue(MockResponse().setBody("""{"songs":[],"totals":{"song":0}}"""))
            cm.enqueue(MockResponse().setBody("""{"lines":[]}"""))
            cm.enqueue(MockResponse().setBody("""{"songs":[{"ncm_id":347230,"name":"CurrentMusic metadata"}]}"""))
            repo.search("song"); repo.lyrics(347230); assertEquals("CurrentMusic metadata", repo.detail(347230).name)
            repeat(3) { assertNull(cm.takeRequest().getHeader("x-api-key")) }
            assertEquals(1, leiz.requestCount)
            access = AudioSourceAccess(AudioProvider.CURRENT_MUSIC, "fixture-secret-key")
            cm.enqueue(MockResponse().setBody("""{"url":"https://cdn.example/cm.mp3","level":"standard"}"""))
            repo.source(347230, AudioQuality.STANDARD, RequestSession(cm.url("/cm/").toString(), "project-token"))
            val original = cm.takeRequest()
            assertEquals("/cm/ncm/song/url", original.requestUrl!!.encodedPath)
            assertNull(original.getHeader("x-api-key")); assertEquals(1, leiz.requestCount)
        } }
    }

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

    @Test fun changingProviderDuringResolutionRejectsOldSource() = runBlocking {
        MockWebServer().use { server ->
            var access = AudioSourceAccess(AudioProvider.LEIZ, "fixture-old")
            val repo = MusicRepository(ApiClient({ server.url("/cm/").toString() }, { null }, {}), { access },
                LeizAudioClient(server.url("/api/netease").toString()))
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    access = AudioSourceAccess(AudioProvider.LEIZ, "fixture-new")
                    return response()
                }
            }
            try { repo.source(1, AudioQuality.STANDARD); fail("Old key response must be rejected") }
            catch (e: ApiException) { assertEquals(ErrorKind.AudioSourceChanged, e.kind) }
            assertFalse(access.toString().contains("fixture-new"))
        }
    }
}
