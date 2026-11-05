package io.github.currencortex.music.data.auth

import io.github.currencortex.music.core.config.ServerDefaults
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class NativePasskeyLoginTest {
    private val origin = "android:apk-key-hash:" + "a".repeat(43)
    private val assertion = """{"id":"credential-id","type":"public-key","response":{"clientDataJSON":"signed-client-data","signature":"signature"}}"""
    private class Fixture : AutoCloseable {
        private val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        private val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        private val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var saved: String? = null
        val accounts = AccountRepository(object : TokenStore {
            override fun read() = saved
            override fun write(token: String) { saved = token }
            override fun clear() { saved = null }
        }, scope).apply { this.server = this@Fixture.server.url("/cm/").newBuilder().host("localhost").build().toString() }
        var now = 1000L
        private val client = OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY)
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        // Any accidental use of the changing media route fails instead of contacting another host.
        val repo = AuthRepository(ApiClient({ accounts.server }, { accounts.token }, accounts::expired,
            client = client, routeServer = { "https://unrelated.invalid/cm/" }), accounts, "test-device", clock = { now }) { _, _ -> }
        fun options(rpId: String = "localhost", verification: String = "required") {
            server.enqueue(MockResponse().setBody("""{"challengeToken":"proof","publicKey":{"challenge":"random-challenge","rpId":"$rpId","userVerification":"$verification"}}"""))
        }
        override fun close() { scope.cancel(); server.shutdown() }
    }
    @Test fun nativeAssertionUsesPinnedRouteAndOriginAndSavesOnlyVerifiedSession(): Unit = runBlocking {
        Fixture().use { f ->
            f.accounts.save("old-session", UserDto(1, "old"))
            f.options()
            f.server.enqueue(MockResponse().setBody("""{"token":"verified-session","user":{"id":2,"username":"new"}}"""))
            val result = f.repo.loginWithPasskey(origin) { options ->
                assertEquals("old-session", f.saved)
                assertEquals("localhost", ApiJson.parseToJsonElement(options).jsonObject["rpId"]!!.jsonPrimitive.content)
                assertion
            }
            assertTrue(result.toString(), result is AppResult.Success)
            assertEquals("verified-session", f.saved)
            val begin = requireNotNull(f.server.takeRequest(3, TimeUnit.SECONDS)); val finish = requireNotNull(f.server.takeRequest(3, TimeUnit.SECONDS))
            assertEquals("/cm/auth/passkeys/login/options", begin.path)
            assertEquals("/cm/auth/passkeys/login/verify", finish.path)
            for (request in listOf(begin, finish)) { assertEquals(origin, request.getHeader("Origin")); assertNull(request.getHeader("Authorization")) }
            val body = ApiJson.parseToJsonElement(finish.body.readUtf8()).jsonObject
            assertEquals(setOf("challengeToken", "credential", "device", "platform"), body.keys)
            assertEquals(ApiJson.parseToJsonElement(assertion), body["credential"])
            assertEquals("app", body["platform"]!!.jsonPrimitive.content)
        }
    }
    @Test fun serverMustRequireUserVerificationAndExpectedRpBeforeOpeningProvider(): Unit = runBlocking {
        for ((rp, uv) in listOf("attacker.example" to "required", "localhost" to "preferred")) Fixture().use { f ->
            f.options(rp, uv)
            var opened = false
            assertTrue(f.repo.loginWithPasskey(origin) { opened = true; assertion } is AppResult.Failure)
            assertFalse(opened); assertNull(f.saved)
            requireNotNull(f.server.takeRequest(3, TimeUnit.SECONDS)); assertNull(f.server.takeRequest(100, TimeUnit.MILLISECONDS))
        }
    }
    @Test fun expiredOrDifferentAccountCannotSubmitProviderResult(): Unit = runBlocking {
        for (changed in listOf(false, true)) Fixture().use { f ->
            f.options()
            assertTrue(f.repo.loginWithPasskey(origin) {
                if (changed) f.accounts.save("other", UserDto(3, "other")) else f.now += 300_000
                assertion
            } is AppResult.Failure)
            requireNotNull(f.server.takeRequest(3, TimeUnit.SECONDS)); assertNull(f.server.takeRequest(100, TimeUnit.MILLISECONDS))
            assertEquals(if (changed) "other" else null, f.saved)
        }
    }
    @Test fun cancellingSystemPromptNeverCallsVerifyOrChangesSession(): Unit = runBlocking {
        Fixture().use { f ->
            f.accounts.save("existing", UserDto(1, "listener")); f.options()
            try {
                f.repo.loginWithPasskey(origin) { throw CancellationException("cancelled") }
                fail("Cancellation must propagate")
            } catch (_: CancellationException) { }
            requireNotNull(f.server.takeRequest(3, TimeUnit.SECONDS)); assertNull(f.server.takeRequest(100, TimeUnit.MILLISECONDS))
            assertEquals("existing", f.saved)
        }
    }
    @Test fun webOnlyServerExplainsMissingNativeSupportWithoutOpeningSystemPrompt(): Unit = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"请在受支持的 HTTPS 网页入口使用通行密钥"}"""))
            val result = f.repo.loginWithPasskey(origin) { error("Must not open provider") } as AppResult.Failure
            assertTrue(result.toString(), result.message.contains("尚未支持 Android")); assertNull(f.saved)
        }
    }
    @Test fun builtInLinesShareOneRpAndCustomServersKeepTheirOwnRp() {
        for (server in listOf(ServerDefaults.URL, ServerDefaults.CDN, ServerDefaults.CDN_GEEKERTAO, ServerDefaults.CDN_CTCEHS)) {
            assertEquals(ServerDefaults.URL, NativePasskeyRoute.server(server))
            assertEquals("music.20110208.xyz", NativePasskeyRoute.rpId(server))
        }
        assertEquals("private.example", NativePasskeyRoute.rpId("https://private.example/custom/"))
        assertThrows(IllegalArgumentException::class.java) { NativePasskeyRoute.server("http://private.example/cm/") }
    }
}
