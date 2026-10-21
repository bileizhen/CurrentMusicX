package io.github.currencortex.music.core.network

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.BoundProfile
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class NeteaseLoginPrerequisiteTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val upstream = MockWebServer()
    private val project = MockWebServer()
    private val token = AtomicReference<String?>(null)
    private val ready = AtomicBoolean(false)
    private val revision = AtomicLong(0)
    private var saved: String? = null
    private lateinit var sessions: NeteaseSessionStore
    private lateinit var gateway: NeteaseGateway
    @Before fun prepare() = runBlocking<Unit> {
        upstream.start(); project.start()
        sessions = NeteaseSessionStore(object : TokenStore {
            override fun read() = saved
            override fun write(token: String) { saved = token }
            override fun clear() { saved = null }
        }, scope)
        sessions.ready.await()
        val endpoint = upstream.url("/").toString()
        gateway = NeteaseGateway(NeteaseTransport(sessions, NeteaseEndpoints(endpoint, endpoint, endpoint)))
    }
    @After fun finish() { scope.cancel(); upstream.shutdown(); project.shutdown() }
    private fun api(native: Boolean = true, local: Boolean = true) = ApiClient(
        { project.url("/cm/").toString() }, token::get, {},
        netease = gateway.takeIf { local }, nativeMedia = { native },
        currentMusicAccountReady = ready::get, currentMusicSessionRevision = revision::get)
    private fun signIn() { token.set("project-secret"); ready.set(true); revision.incrementAndGet() }
    private fun confirmed(code: Int) = MockResponse().setBody(
        """{"code":$code,"profile":{"userId":42,"nickname":"New account"}}""")
        .addHeader("Set-Cookie", "MUSIC_U=new-cookie; Path=/; HttpOnly")
    private suspend fun oldLogin() = sessions.login(mapOf("MUSIC_U" to "old-cookie"), BoundProfile(7, "Existing"), sessions.snapshot())

    @Test fun anonymousQrSmsAndAlternateSmsAreBlockedBeforeEitherNetwork(): Unit = runBlocking {
        for (native in listOf(false, true)) for (local in listOf(false, true)) {
            val api = api(native, local)
            for (path in listOf("ncmbind/qr/key", "ncmbind/qr/check", "ncmbind/phone/code", "ncmbind/phone/login", "ncm/captcha/sent/v1")) {
                val result = appResult { api.request("POST", path, authenticated = true) } as AppResult.Failure
                assertEquals(path, ErrorKind.CurrentMusicAccountRequired, result.kind)
            }
            // Even a caller omitting authenticated=true or forcing project HTTP cannot bypass it.
            assertEquals(ErrorKind.CurrentMusicAccountRequired, (appResult {
                api.request("GET", "ncmbind/qr/key", projectOnly = true)
            } as AppResult.Failure).kind)
        }
        assertEquals(0, upstream.requestCount); assertEquals(0, project.requestCount)
        assertFalse(sessions.state.value.loggedIn)
    }
    @Test fun tokenWithoutRestoredCurrentMusicAccountCannotStartLogin(): Unit = runBlocking {
        token.set("unverified-token")
        assertEquals(ErrorKind.CurrentMusicAccountRequired, (appResult {
            api().request("POST", "ncmbind/qr/key", authenticated = true)
        } as AppResult.Failure).kind)
        assertEquals(0, upstream.requestCount)
    }
    @Test fun signedInCurrentMusicCanConfirmNativeQrWithoutSendingProjectCredentials(): Unit = runBlocking {
        signIn()
        upstream.enqueue(MockResponse().setBody("""{"code":200,"unikey":"fixture-key"}"""))
        upstream.enqueue(confirmed(803))
        val api = api()
        api.request("POST", "ncmbind/qr/key", authenticated = true)
        api.request("GET", "ncmbind/qr/check", mapOf("key" to "fixture-key"), authenticated = true)
        assertEquals(42L, sessions.state.value.profile?.uid)
        assertTrue(saved!!.contains("new-cookie"))
        repeat(2) {
            val request = upstream.takeRequest(1, TimeUnit.SECONDS)!!
            assertNull(request.getHeader("Authorization"))
            assertFalse(request.getHeader("Cookie").orEmpty().contains("project-secret"))
            assertFalse(request.body.readUtf8().contains("project-secret"))
        }
        assertEquals(0, project.requestCount)
    }
    private suspend fun rejectedLateLogin(path: String, code: Int, change: () -> Unit) = coroutineScope {
        oldLogin(); signIn()
        upstream.enqueue(confirmed(code).setBodyDelay(350, TimeUnit.MILLISECONDS))
        val result = async(Dispatchers.Default) { appResult { api().request("POST", path,
            mapOf("key" to "fixture-key"), buildJsonObject { put("phone", "13800000000"); put("captcha", "123456") },
            authenticated = true) } }
        assertNotNull(withContext(Dispatchers.IO) { upstream.takeRequest(2, TimeUnit.SECONDS) })
        change()
        assertEquals(ErrorKind.CurrentMusicAccountRequired, (result.await() as AppResult.Failure).kind)
        assertEquals(7L, sessions.state.value.profile?.uid)
        assertTrue(saved!!.contains("old-cookie")); assertFalse(saved!!.contains("new-cookie"))
    }
    @Test fun logoutWhileConfirmingQrDoesNotReplaceExistingNeteaseLogin(): Unit = runBlocking {
        rejectedLateLogin("ncmbind/qr/check", 803) { ready.set(false); token.set(null); revision.incrementAndGet() }
    }
    @Test fun accountSwitchWhilePhoneLoginIsInFlightDoesNotPersistLateCredential(): Unit = runBlocking {
        rejectedLateLogin("ncmbind/phone/login", 200) { token.set("another-project-account"); revision.incrementAndGet() }
    }
    @Test fun logoutAndLoginWithSameTokenStillInvalidatesOldQrConfirmation(): Unit = runBlocking {
        rejectedLateLogin("ncmbind/qr/check", 803) { revision.addAndGet(2) }
    }
    @Test fun lateQrKeyCannotSaveAuthenticationCookiesAfterCurrentMusicLogout(): Unit = runBlocking {
        signIn()
        upstream.enqueue(confirmed(200).setBody("""{"code":200,"unikey":"late-key"}""").setBodyDelay(350, TimeUnit.MILLISECONDS))
        val result = async(Dispatchers.Default) { appResult { api().request("POST", "ncmbind/qr/key", authenticated = true) } }
        assertNotNull(withContext(Dispatchers.IO) { upstream.takeRequest(2, TimeUnit.SECONDS) })
        token.set(null); ready.set(false); revision.incrementAndGet()
        assertEquals(ErrorKind.CurrentMusicAccountRequired, (result.await() as AppResult.Failure).kind)
        assertFalse(sessions.state.value.loggedIn); assertFalse(saved!!.contains("new-cookie"))
    }
    @Test fun publicNativeLyricsRemainAvailableWithoutCurrentMusicLogin(): Unit = runBlocking {
        upstream.enqueue(MockResponse().setBody("""{"code":200,"lrc":{"lyric":"[00:00.00]Hello"}}"""))
        val result = api().request("GET", "ncm/lyric", mapOf("id" to "7"))
        assertEquals(200, result.jsonObject["code"]!!.jsonPrimitive.int)
        assertEquals(0, project.requestCount)
    }
}
