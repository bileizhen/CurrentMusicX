package io.github.currencortex.music.core.update

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class UpdateSourceProbeTest {
    private val release = AppRelease("1.2.0", "", "https://github.com/bileizhen/CurrentMusicX/releases/tag/v1.2.0",
        "https://github.com/bileizhen/CurrentMusicX/releases/download/v1.2.0/app.apk", "app.apk", false, 1024)
    private val signature = byteArrayOf(0x50, 0x4b, 0x03, 0x04)

    private fun secure(test: suspend (MockWebServer, UpdateSourceProbe) -> Unit) = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.start()
            val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
            test(server, UpdateSourceProbe(client, urlFor = { _, _ -> server.url("/asset").toString() }))
        }
    }

    @Test fun requestsOnlyTheApkHeaderAndReportsElapsedTime() = secure { server, probe ->
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-3/1024")
            .setBody(Buffer().write(signature)))
        val result = probe.check(release, UpdateSource.TBAP)
        assertEquals(result.toString(), UpdateSourceStatus.AVAILABLE, result.status)
        assertTrue(result.latencyMs!! >= 1)
        val request = server.takeRequest()
        assertEquals("bytes=0-3", request.getHeader("Range"))
        assertEquals("identity", request.getHeader("Accept-Encoding"))
    }

    @Test fun rateLimitIsDistinctFromOtherHttpFailures() = secure { server, probe ->
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(UpdateSourceStatus.LIMITED, probe.check(release, UpdateSource.GH_PROXY).status)
        val denied = probe.check(release, UpdateSource.GH_PROXY)
        assertEquals(UpdateSourceStatus.UNAVAILABLE, denied.status)
        assertEquals("HTTP 403", denied.detail)
    }

    @Test fun successfulHtmlAndWrongAssetSizeAreUnavailable() = secure { server, probe ->
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>challenge</html>"))
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-3/2048")
            .setBody(Buffer().write(signature)))
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 4-7/1024")
            .setBody(Buffer().write(signature)))
        repeat(3) { assertEquals(UpdateSourceStatus.UNAVAILABLE, probe.check(release, UpdateSource.TBAP).status) }
    }

    @Test fun acceptingRangeIsOptionalButSignatureIsRequired() = secure { server, probe ->
        server.enqueue(MockResponse().setBody(Buffer().write(signature + ByteArray(1020))))
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-3/1024").setBody("oops"))
        assertEquals(UpdateSourceStatus.AVAILABLE, probe.check(release, UpdateSource.TBAP).status)
        assertEquals(UpdateSourceStatus.UNAVAILABLE, probe.check(release, UpdateSource.TBAP).status)
    }

    @Test fun followsHttpsRedirectAndRejectsDowngrade() = secure { server, probe ->
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/download"))
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-3/1024")
            .setBody(Buffer().write(signature)))
        assertEquals(UpdateSourceStatus.AVAILABLE, probe.check(release, UpdateSource.TBAP).status)
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://localhost:1/asset"))
        assertEquals(UpdateSourceStatus.UNAVAILABLE, probe.check(release, UpdateSource.TBAP).status)
        assertEquals(3, server.requestCount)
    }

    @Test fun leavingTheDialogCancelsAnInFlightRequest() = secure { server, probe ->
        coroutineScope {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val task = launch { probe.check(release, UpdateSource.TBAP); fail("Cancelled check must not return a status") }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
            withTimeout(2000) { task.cancelAndJoin() }
            assertTrue(task.isCancelled)
        }
    }

    @Test fun stalledApkResponseIsReportedAsTimeout() = secure { server, probe ->
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-3/1024")
            .setBody(Buffer().write(signature)).setBodyDelay(6, TimeUnit.SECONDS))
        assertEquals(UpdateSourceStatus.TIMEOUT, probe.check(release, UpdateSource.TBAP).status)
    }
}
