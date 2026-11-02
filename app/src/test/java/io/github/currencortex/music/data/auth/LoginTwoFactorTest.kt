package io.github.currencortex.music.data.auth

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class LoginTwoFactorTest {
    private class Store : TokenStore {
        var value: String? = null
        override fun read() = value
        override fun write(token: String) { value = token }
        override fun clear() { value = null }
    }
    private class Fixture : AutoCloseable {
        val server = MockWebServer().apply { start() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = Store()
        val accounts = AccountRepository(store, scope).apply { this.server = this@Fixture.server.url("/cm/").toString() }
        var now = 1_000L
        var persisted = 0L
        val repo = AuthRepository(ApiClient({ accounts.server }, { accounts.token }, accounts::expired),
            accounts, "test-device", clock = { now }) { id, _ -> persisted = id }
        fun reply(body: String, status: Int = 200) { server.enqueue(MockResponse().setResponseCode(status).setBody(body)) }
        suspend fun challenge(token: String = "test-challenge", seconds: Int = 300): LoginChallenge {
            reply("""{"requires2FA":true,"challengeToken":"$token","expiresIn":$seconds}""")
            val result = repo.login("listener", "test-password") as AppResult.Success
            val request = server.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("/cm/auth/login", request.path); assertNull(request.getHeader("Authorization"))
            val body = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("app", body["platform"]!!.jsonPrimitive.content)
            assertEquals("test-device", body["device"]!!.jsonPrimitive.content)
            return (result.value as LoginOutcome.VerificationRequired).challenge
        }
        override fun close() { scope.cancel(); server.shutdown() }
    }
    private val signedIn = """{"token":"new-session","user":{"id":5,"nickname":"Listener"}}"""

    @Test fun challengeAndWrongCodeKeepTheExistingAccountUntilVerificationSucceeds(): Unit = runBlocking {
        Fixture().use { f ->
            f.accounts.save("other-session", UserDto(9, "Other"))
            val revision = f.accounts.sessionRevision.value
            val challenge = f.challenge()
            assertEquals("other-session", f.accounts.token); assertEquals("other-session", f.store.value)
            assertEquals(revision, f.accounts.sessionRevision.value); assertEquals(0L, f.persisted)
            assertFalse(challenge.toString().contains("test-challenge"))
            f.reply("""{"error":"验证码不正确"}""", 401)
            val failed = f.repo.verifyLogin(challenge, "000000") as AppResult.Failure
            assertEquals("验证码不正确", failed.message)
            val wrong = f.server.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("/cm/auth/2fa/login", wrong.path); assertNull(wrong.getHeader("Authorization"))
            assertEquals("other-session", f.accounts.token)
            f.reply(signedIn)
            val result = f.repo.verifyLogin(challenge, " 123456 ") as AppResult.Success
            assertEquals(5L, result.value.account.id)
            val request = f.server.takeRequest(3, TimeUnit.SECONDS)!!
            val body = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(setOf("challengeToken", "code"), body.keys)
            assertEquals("test-challenge", body["challengeToken"]!!.jsonPrimitive.content)
            assertEquals("123456", body["code"]!!.jsonPrimitive.content)
            assertNull(request.getHeader("Authorization"))
            assertEquals("new-session", f.store.value); assertEquals(5L, f.persisted)
            assertTrue(f.repo.verifyLogin(challenge, "123456") is AppResult.Failure)
            assertNull(f.server.takeRequest(100, TimeUnit.MILLISECONDS))
        }
    }
    @Test fun recoveryCodesArePassedUnchangedAndRequireAnExplicitVerification(): Unit = runBlocking {
        Fixture().use { f ->
            val challenge = f.challenge()
            assertNull(f.accounts.token); assertNull(f.store.value)
            f.reply(signedIn)
            assertTrue(f.repo.verifyLogin(challenge, " ABCD-EFGH-IJKL ") is AppResult.Success)
            val request = f.server.takeRequest(3, TimeUnit.SECONDS)!!
            val body = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("ABCD-EFGH-IJKL", body["code"]!!.jsonPrimitive.content)
        }
    }
    @Test fun expiredCancelledAndReplacedChallengesNeverSendVerificationRequests(): Unit = runBlocking {
        Fixture().use { f ->
            val expired = f.challenge(seconds = 2)
            f.now += 2_001
            assertTrue(f.repo.verifyLogin(expired, "123456") is AppResult.Failure)
            val cancelled = f.challenge(); f.repo.discardLoginChallenge(cancelled)
            assertTrue(f.repo.verifyLogin(cancelled, "123456") is AppResult.Failure)
            val old = f.challenge("first"); val current = f.challenge("second")
            f.repo.discardLoginChallenge(old)
            assertTrue(f.repo.verifyLogin(old, "123456") is AppResult.Failure)
            assertNull(f.server.takeRequest(100, TimeUnit.MILLISECONDS))
            f.reply(signedIn)
            assertTrue(f.repo.verifyLogin(current, "123456") is AppResult.Success)
            f.server.takeRequest()
        }
    }
    @Test fun serverOrAccountChangesRejectAnOldChallengeWithoutChangingTheNewAccount(): Unit = runBlocking {
        Fixture().use { f ->
            val challenge = f.challenge()
            val original = f.accounts.server
            f.accounts.server = "https://another.example/cm/"
            assertTrue(f.repo.verifyLogin(challenge, "123456") is AppResult.Failure)
            f.accounts.server = original
            f.accounts.save("changed-session", UserDto(8, "Changed"))
            assertTrue(f.repo.verifyLogin(challenge, "123456") is AppResult.Failure)
            assertEquals("changed-session", f.store.value)
            assertNull(f.server.takeRequest(100, TimeUnit.MILLISECONDS))
        }
    }
    @Test fun malformedChallengesNeverCreateALoginSession(): Unit = runBlocking {
        Fixture().use { f ->
            f.reply("""{"requires2FA":true,"challengeToken":"","expiresIn":300,"token":"not-a-session"}""")
            assertTrue(f.repo.login("listener", "test-password") is AppResult.Failure)
            assertNull(f.accounts.token); assertNull(f.store.value); assertEquals(0L, f.persisted)
        }
    }
}
