package io.github.currencortex.music.data.binding

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

/** Real HTTPS request serialization and routing, using only synthetic credentials. */
class BindingRepositoryProjectSyncTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var server: MockWebServer
    private lateinit var sessions: NeteaseSessionStore
    private lateinit var repository: BindingRepository
    private lateinit var api: ApiClient
    private var unauthorized = 0
    private fun owner() = RequestSession(server.url("/cm/").toString(), "project-fixture", sessions.state.value.revision)
    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Before fun setup() = runBlocking {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1").addSubjectAlternativeName("::1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val vault = object : TokenStore {
            var value: String? = null
            override fun read() = value
            override fun write(token: String) { value = token }
            override fun clear() { value = null }
        }
        sessions = NeteaseSessionStore(vault, scope)
        sessions.ready.await()
        sessions.login(mapOf("MUSIC_U" to "synthetic-login", "__csrf" to "synthetic-csrf",
            "irrelevant" to "must-stay-local"), BoundProfile(42, "Fixture"), sessions.snapshot())
        val http = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .followRedirects(false).build()
        val gateway = NeteaseGateway(NeteaseTransport(sessions))
        api = ApiClient({ server.url("/cm/").toString() }, { "project-fixture" }, { unauthorized++ },
            client = http, netease = gateway, nativeMedia = { true })
        repository = BindingRepository(api, ::owner) {}
    }
    @After fun close() { scope.cancel(); server.shutdown() }

    @Test fun authenticatedHttpsUploadFiltersCookiesAndDeduplicatesAfterCacheRefresh() = runBlocking {
        server.enqueue(response("""{"ok":true,"profile":{"uid":42}}"""))
        withTimeout(5_000) { repository.autoSyncProjectBinding(owner()) }
        assertTrue(repository.projectBindingSync.value.synced)
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertEquals("/cm/ncmbind/import", request.path)
        assertEquals("Bearer project-fixture", request.getHeader("Authorization"))
        val body = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(42L, body.getValue("expectedNcmUid").jsonPrimitive.long)
        assertEquals("MUSIC_U=synthetic-login; __csrf=synthetic-csrf", body.getValue("cookie").jsonPrimitive.content)
        repository.clearSession()
        repository.syncProjectBinding()
        assertEquals(1, server.requestCount)
        assertTrue(sessions.state.value.loggedIn)
    }

    @Test fun projectImportTransportAcceptsTheExpectedLocalLoginOverHttps() = runBlocking {
        server.enqueue(response("""{"ok":true,"profile":{"uid":42}}"""))
        val result = api.request("POST", "ncmbind/import", body = buildJsonObject {
            put("cookie", "MUSIC_U=synthetic-login"); put("expectedNcmUid", 42)
        }, authenticated = true, expectedSession = owner(), projectOnly = true, retryConnection = false)
        assertTrue(result.jsonObject.getValue("ok").jsonPrimitive.boolean)
        assertEquals(1, server.requestCount)
    }

    @Test fun mismatchedServerIdentityPreservesLocalLoginAndAllowsManualRetry() = runBlocking {
        server.enqueue(response("""{"ok":true,"profile":{"uid":43}}"""))
        repository.syncProjectBinding()
        assertFalse(repository.projectBindingSync.value.synced)
        assertNotNull(repository.projectBindingSync.value.error)
        assertTrue(sessions.state.value.loggedIn)
        assertEquals(42L, sessions.state.value.profile!!.uid)
        server.enqueue(response("""{"ok":true,"profile":{"uid":42}}"""))
        repository.syncProjectBinding()
        assertTrue(repository.projectBindingSync.value.synced)
        assertEquals(2, server.requestCount)
    }

    @Test fun serverFailureNeverClearsTheLocalCredentialOrReportsSuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        repository.syncProjectBinding()
        assertFalse(repository.projectBindingSync.value.synced)
        assertFalse(repository.projectBindingSync.value.syncing)
        assertNotNull(repository.projectBindingSync.value.error)
        assertTrue(sessions.state.value.loggedIn)
        assertFalse(sessions.state.value.stale)
        assertEquals(0, unauthorized)
        assertEquals(1, server.requestCount)
    }
}
