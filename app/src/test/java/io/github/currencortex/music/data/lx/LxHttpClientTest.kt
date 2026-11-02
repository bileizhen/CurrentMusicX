package io.github.currencortex.music.data.lx

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class LxHttpClientTest {
    @Test fun cancellingAStreamingBodyDoesNotWaitForTheWholeResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(10000)).throttleBody(10, 1, java.util.concurrent.TimeUnit.SECONDS))
            val start = System.nanoTime()
            assertNull(withTimeoutOrNull(250) {
                LxHttpClient(true).execute(buildJsonObject { put("url", server.url("/slow").toString()) })
            })
            assertTrue((System.nanoTime() - start) / 1_000_000 < 3000)
        }
    }
    @Test fun publicTransportRejectsPrivateAndCredentialUrls() {
        val http = LxHttpClient()
        listOf("file:///etc/passwd", "http://localhost/a", "http://127.0.0.1/a", "http://192.168.1.1/a",
            "http://[::1]/a", "https://token:secret@example.com/a").forEach { url ->
            assertThrows(LxSourceException::class.java) { http.url(url) }
        }
        assertEquals("https://cdn.example.com/a", http.url("https://cdn.example.com/a").toString())
        assertThrows(LxSourceException::class.java) { lxReadBounded(ByteArray(11).inputStream(), 10) }
    }
    @Test fun callbackResponseParsesJsonAndDoesNotShareCookies() = runBlocking {
        MockWebServer().use { server ->
            val http = LxHttpClient(allowPrivateForTests = true)
            server.enqueue(MockResponse().setHeader("Set-Cookie", "private=session").setBody("""{"url":"https://cdn.example.com/song.mp3"}"""))
            server.enqueue(MockResponse().setBody("second"))
            fun request() = buildJsonObject { put("url", server.url("/resolve").toString()); put("options", buildJsonObject {
                put("method", "POST"); put("form", buildJsonObject { put("id", "42") })
            }) }
            val response = http.execute(request())
            assertEquals(200, response["statusCode"]!!.jsonPrimitive.int)
            assertEquals("https://cdn.example.com/song.mp3", response["body"]!!.jsonObject["url"]!!.jsonPrimitive.content)
            http.execute(request())
            repeat(2) {
                val recorded = server.takeRequest()
                assertEquals("id=42", recorded.body.readUtf8()); assertNull(recorded.getHeader("Cookie"))
                assertNull(recorded.getHeader("Authorization"))
            }
        }
    }
    @Test fun redirectsStripSourceCredentialsWhenOriginChanges() = runBlocking {
        MockWebServer().use { first -> MockWebServer().use { second ->
            first.enqueue(MockResponse().setResponseCode(302).setHeader("Location", second.url("/script.js")))
            second.enqueue(MockResponse().setBody("ok"))
            LxHttpClient(true).execute(buildJsonObject {
                put("url", first.url("/redirect").toString()); put("options", buildJsonObject {
                    put("headers", buildJsonObject { put("Authorization", "source-token"); put("Cookie", "source-cookie") })
                })
            })
            assertEquals("source-token", first.takeRequest().getHeader("Authorization"))
            val final = second.takeRequest(); assertNull(final.getHeader("Authorization")); assertNull(final.getHeader("Cookie"))
        } }
    }
}
