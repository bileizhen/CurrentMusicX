package io.github.currencortex.music.core.netease

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class NeteaseTransportBodyTest {
    private class Vault : TokenStore {
        @Volatile private var value: String? = null
        val writes = AtomicInteger()
        override fun read() = value
        override fun write(token: String) { value = token; writes.incrementAndGet() }
        override fun clear() { value = null }
    }
    private fun transport(server: MockWebServer, sessions: NeteaseSessionStore, bodyStarted: CountDownLatch? = null): NeteaseTransport {
        val url = server.url("/").toString()
        val client = OkHttpClient.Builder().readTimeout(18, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        if (bodyStarted != null) client.addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val delegate = response.body ?: return@addNetworkInterceptor response
            response.newBuilder().body(object : ResponseBody() {
                private val buffered = object : ForwardingSource(delegate.source()) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        // Signal before the socket read blocks on the delayed body.
                        // responseBodyStart occurs after bytes arrive and cannot establish
                        // this cancellation boundary for a deliberately stalled response.
                        bodyStarted.countDown()
                        return super.read(sink, byteCount)
                    }
                }.buffer()
                override fun contentType() = delegate.contentType()
                override fun contentLength() = delegate.contentLength()
                override fun source() = buffered
            }).build()
        }
        return NeteaseTransport(sessions, NeteaseEndpoints(url, url, url), client = client.build())
    }

    @Test fun cancellingAfterHeadersInterruptsDelayedBodyAndNeverSavesSetCookie() = runBlocking {
        val server = MockWebServer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val vault = Vault(); val sessions = NeteaseSessionStore(vault, scope); sessions.ready.await()
            val writes = vault.writes.get()
            val bodyStarted = CountDownLatch(1)
            server.enqueue(MockResponse().setBody("""{"code":200}""").setBodyDelay(3, TimeUnit.SECONDS)
                .addHeader("Set-Cookie", "MUSIC_U=cancelled-cookie; Path=/; Max-Age=3600"))
            server.start()
            val transport = transport(server, sessions, bodyStarted)
            var returned = false
            val job = launch(Dispatchers.Default) { transport.request("/api/song/lyric/v1"); returned = true }
            assertTrue(withContext(Dispatchers.IO) { bodyStarted.await(2, TimeUnit.SECONDS) })
            withTimeout(1000) { job.cancelAndJoin() }
            assertFalse(returned)
            assertTrue(sessions.snapshot().credential.cookies.isEmpty())
            assertEquals(writes, vault.writes.get())
            assertEquals(1, server.requestCount)
        } finally { scope.cancel(); server.shutdown() }
    }

    @Test fun failedHttpStatusDoesNotWaitForAnErrorBodyOrSaveItsCookies() = runBlocking {
        val server = MockWebServer(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sessions = NeteaseSessionStore(Vault(), scope); sessions.ready.await()
            server.enqueue(MockResponse().setResponseCode(403).setBody("body is delayed").setBodyDelay(3, TimeUnit.SECONDS)
                .addHeader("Set-Cookie", "MUSIC_U=denied-cookie; Path=/"))
            server.start()
            val result = withTimeout(1000) { appResult { transport(server, sessions).request("/api/song/lyric/v1") } } as AppResult.Failure
            assertEquals(ErrorKind.Forbidden, result.kind)
            assertEquals(403, result.status)
            assertTrue(sessions.snapshot().credential.cookies.isEmpty())
        } finally { scope.cancel(); server.shutdown() }
    }

    @Test fun responseCapsApplyToKnownAndChunkedBodyLengths() = runBlocking {
        val server = MockWebServer(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sessions = NeteaseSessionStore(Vault(), scope); sessions.ready.await()
            server.enqueue(MockResponse().setBody("{}").setHeader("Content-Length", NeteaseTransport.MAX_JSON_BYTES + 1))
            server.enqueue(MockResponse().setChunkedBody("x".repeat(NeteaseTransport.MAX_JSON_BYTES + 1), 8192))
            server.start()
            val transport = transport(server, sessions)
            repeat(2) {
                val failure = appResult { transport.request("/api/song/lyric/v1") } as AppResult.Failure
                assertEquals(ErrorKind.Parse, failure.kind)
                assertTrue(failure.message.contains("16 MB"))
            }
        } finally { scope.cancel(); server.shutdown() }
    }

    @Test fun decryptedGzipExpansionIsBoundedBeforeJsonParsing() = runBlocking {
        val server = MockWebServer(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sessions = NeteaseSessionStore(Vault(), scope); sessions.ready.await()
            val inflated = "{\"code\":200,\"large\":\"" + "x".repeat(NeteaseTransport.MAX_JSON_BYTES) + "\"}"
            server.enqueue(MockResponse().setBody(Buffer().write(encryptedGzip(inflated)))
                .addHeader("Set-Cookie", "MUSIC_U=oversized-cookie; Path=/"))
            server.start()
            val result = appResult { transport(server, sessions).request("/api/song/lyric/v1") } as AppResult.Failure
            assertEquals(ErrorKind.Parse, result.kind)
            assertTrue(result.message.contains("16 MB"))
            assertTrue(sessions.snapshot().credential.cookies.isEmpty())
        } finally { scope.cancel(); server.shutdown() }
    }

    @Test fun ordinaryEncryptedGzipResponsesStillDecodeAndCommitTheirCookies() = runBlocking {
        val server = MockWebServer(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sessions = NeteaseSessionStore(Vault(), scope); sessions.ready.await()
            server.enqueue(MockResponse().setBody(Buffer().write(encryptedGzip("""{"code":200,"data":{"value":"fixture"}}""")))
                .addHeader("Set-Cookie", "test_refresh=accepted; Path=/; Max-Age=3600"))
            server.start()
            val response = transport(server, sessions).request("/api/song/lyric/v1")
            assertEquals("fixture", response.body.getValue("data").jsonObject.getValue("value").jsonPrimitive.content)
            assertEquals("accepted", response.cookies["test_refresh"])
            assertEquals("accepted", sessions.snapshot().credential.cookies["test_refresh"])
        } finally { scope.cancel(); server.shutdown() }
    }

    private fun encryptedGzip(json: String): ByteArray {
        val zipped = ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(json.toByteArray()) } }.toByteArray()
        return Cipher.getInstance("AES/ECB/PKCS5Padding").run {
            // Public eapi protocol constant, not an account key.
            init(Cipher.ENCRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES"))
            doFinal(zipped)
        }
    }
}
