package io.github.currencortex.music.data.lx

import io.github.currencortex.music.core.network.SourceAvailabilityStatus.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class LxSourceHealthTest {
    @Test fun successfulResultsExpireAndFailuresRetrySoonerWithoutCachingChecking() {
        assertTrue(LxSourceHealth(AVAILABLE, 100).fresh(599_999))
        assertFalse(LxSourceHealth(AVAILABLE, 100).fresh(600_100))
        assertFalse(LxSourceHealth(AVAILABLE, 100).fresh(99))
        assertTrue(LxSourceHealth(UNAVAILABLE, 100).fresh(60_099))
        assertFalse(LxSourceHealth(UNAVAILABLE, 100).fresh(60_100))
        assertFalse(LxSourceHealth(CHECKING, 100).fresh(101))
        assertFalse(LxSourceHealth().fresh(0))
    }
    @Test fun urlOrHttpSuccessWithHtmlAndJsonIsNotAvailableAudio() = runBlocking {
        MockWebServer().use { server ->
            val http = LxHttpClient(true)
            server.enqueue(MockResponse().setBody("<html><body>source unavailable</body></html>"))
            server.enqueue(MockResponse().setBody("""{"code":404,"message":"missing"}"""))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(429))
            server.enqueue(MockResponse().setResponseCode(504))
            for (expected in listOf(UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, LIMITED, TIMEOUT)) {
                assertEquals(expected, http.probeAudio(server.url("/audio").toString()))
                val request = server.takeRequest()
                assertEquals("bytes=0-4095", request.getHeader("Range"))
                assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
            }
        }
    }
    @Test fun probeAcceptsAudioRedirectAndStopsAtPrefixWhenServerIgnoresRange() = runBlocking {
        MockWebServer().use { server ->
            val bytes = ByteArray(16_384).apply {
                "RIFF".toByteArray().copyInto(this); "WAVE".toByteArray().copyInto(this, 8)
            }
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/file.wav")))
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            assertEquals(AVAILABLE, LxHttpClient(true).probeAudio(server.url("/redirect").toString()))
            assertEquals("/redirect", server.takeRequest().path)
            assertEquals("/file.wav", server.takeRequest().path)
        }
    }
}
