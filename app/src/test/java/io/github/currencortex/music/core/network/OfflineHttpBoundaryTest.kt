package io.github.currencortex.music.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class OfflineHttpBoundaryTest {
    @Test fun injectedOfflineClientFailsBeforeAnySocketOrHttpRequest() = runBlocking {
        MockWebServer().use { server ->
            val blocked = AtomicInteger()
            val offline = ApiClient.defaultHttpClient().newBuilder().retryOnConnectionFailure(false)
                .addInterceptor {
                    blocked.incrementAndGet()
                    throw IOException("Offline performance fixture")
                }.build()
            val base = server.url("/cm/").toString()
            val api = ApiClient({ base }, { null }, {}, client = offline)
            try {
                api.request("GET", "announcements", expectedSession = RequestSession(base, null))
                fail("Offline transport must fail")
            } catch (error: IOException) {
                assertEquals("Offline performance fixture", error.message)
            }
            assertEquals(1, blocked.get())
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun defaultClientRetainsTheProductionTransportConfiguration() {
        val client = ApiClient.defaultHttpClient()
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(75_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
        assertEquals(90_000, client.callTimeoutMillis)
        assertFalse(client.followRedirects)
        assertTrue(client.interceptors.isEmpty())
    }
}
