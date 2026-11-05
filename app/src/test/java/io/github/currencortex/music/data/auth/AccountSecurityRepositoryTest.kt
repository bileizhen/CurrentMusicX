package io.github.currencortex.music.data.auth

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class AccountSecurityRepositoryTest {
    private class Fixture : AutoCloseable {
        val server = MockWebServer().apply { start() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val accounts = AccountRepository(object : TokenStore {
            override fun read(): String? = null
            override fun write(token: String) = Unit
            override fun clear() = Unit
        }, scope).apply { this.server = this@Fixture.server.url("/cm/").toString() }
        var now = 1_000L
        val repo = AccountSecurityRepository(ApiClient({ accounts.server }, { accounts.token }, accounts::expired), accounts) { now }
        suspend fun owner(): SecurityOwner { accounts.save("session", UserDto(1, "listener")); return repo.owner() }
        fun reply(body: String, code: Int = 200) { server.enqueue(MockResponse().setResponseCode(code).setBody(body)) }
        override fun close() { scope.cancel(); server.shutdown() }
    }
    private val setup = """{"secret":"JBSWY3DPEHPK3PXP","uri":"otpauth://totp/CurrentMusic:listener?secret=JBSWY3DPEHPK3PXP","expiresIn":600}"""

    @Test fun enrollmentIsAuthenticatedAndRecoveryCodesAreReturnedOnlyAfterEnabling(): Unit = runBlocking {
        Fixture().use { f ->
            val owner = f.owner()
            f.reply("""{"enabled":false,"securityQuestionEnabled":false,"recoveryCodesRemaining":0}""")
            assertFalse((f.repo.status(owner) as AppResult.Success).value.enabled)
            assertEquals("/cm/auth/2fa/status", f.server.takeRequest().path)
            f.reply(setup)
            val enrollment = (f.repo.setup(owner, "password") as AppResult.Success).value
            val request = f.server.takeRequest()
            assertEquals("Bearer session", request.getHeader("Authorization"))
            assertEquals("/cm/auth/2fa/setup", request.path)
            assertFalse(enrollment.toString().contains(enrollment.secret))
            assertEquals(600L, enrollment.remainingSeconds)
            f.reply("""{"enabled":true,"recoveryCodes":["abcd1234","efgh5678"]}""")
            val recovery = (f.repo.enable(enrollment, "123456") as AppResult.Success).value
            val enabled = f.server.takeRequest()
            assertEquals("/cm/auth/2fa/enable", enabled.path)
            assertEquals(setOf("code"), ApiJson.parseToJsonElement(enabled.body.readUtf8()).jsonObject.keys)
            assertEquals(listOf("abcd1234", "efgh5678"), recovery.codes)
            assertFalse(recovery.toString().contains("abcd1234"))
            assertEquals("session", f.accounts.token)
        }
    }
    @Test fun wrongPasswordAndMutuallyExclusiveProtectionAreShownWithoutLoggingOut(): Unit = runBlocking {
        Fixture().use { f ->
            val owner = f.owner()
            f.reply("""{"error":"当前密码不正确"}""", 400)
            assertEquals("当前密码不正确", (f.repo.setup(owner, "wrong") as AppResult.Failure).message)
            f.server.takeRequest()
            f.reply("""{"error":"已开启登录密保问题，不能同时开启验证器 2FA"}""", 409)
            assertTrue((f.repo.setup(owner, "password") as AppResult.Failure).message.contains("密保"))
            assertEquals("session", f.accounts.token)
        }
    }
    @Test fun expiredEnrollmentAndChangedAccountCannotSendSecrets(): Unit = runBlocking {
        Fixture().use { f ->
            val owner = f.owner(); f.reply(setup)
            val enrollment = (f.repo.setup(owner, "password") as AppResult.Success).value
            f.server.takeRequest()
            f.now += 600_000
            assertTrue(f.repo.enable(enrollment, "123456") is AppResult.Failure)
            f.accounts.save("other-session", UserDto(2, "other"))
            assertTrue(f.repo.disable(owner, "password", "123456") is AppResult.Failure)
            assertTrue(f.repo.setQuestion(owner, "password", "第一只宠物？", "小白") is AppResult.Failure)
            assertNull(f.server.takeRequest(100, TimeUnit.MILLISECONDS))
        }
    }
    @Test fun questionAndDisableRequestsUseTheirOwnFieldsAndKeepCurrentSession(): Unit = runBlocking {
        Fixture().use { f ->
            val owner = f.owner(); f.reply("""{"enabled":true}""")
            assertTrue(f.repo.setQuestion(owner, "password", "第一只宠物？", " 小白 ") is AppResult.Success)
            val enable = f.server.takeRequest()
            assertEquals("POST", enable.method); assertEquals("/cm/auth/security-question", enable.path)
            assertEquals(setOf("password", "question", "answer"), ApiJson.parseToJsonElement(enable.body.readUtf8()).jsonObject.keys)
            f.reply("""{"enabled":false}""")
            assertTrue(f.repo.disableQuestion(owner, "password", "小白") is AppResult.Success)
            val disable = f.server.takeRequest()
            assertEquals("DELETE", disable.method)
            assertEquals(setOf("password", "answer"), ApiJson.parseToJsonElement(disable.body.readUtf8()).jsonObject.keys)
            f.reply("""{"enabled":false}""")
            assertTrue(f.repo.disable(owner, "password", "abcd-1234") is AppResult.Success)
            assertEquals("/cm/auth/2fa/disable", f.server.takeRequest().path)
            assertEquals("session", f.accounts.token)
        }
    }
    @Test fun responseAfterAccountSwitchIsDiscarded(): Unit = runBlocking {
        Fixture().use { f ->
            val owner = f.owner()
            f.reply(setup)
            // A delayed body lets the caller change identity while the request is in flight.
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setBody(setup).setBodyDelay(300, TimeUnit.MILLISECONDS)
            }
            val task = async(Dispatchers.Default) { f.repo.setup(owner, "password") }
            assertNotNull(withContext(Dispatchers.IO) { f.server.takeRequest(3, TimeUnit.SECONDS) })
            f.accounts.save("new-session", UserDto(2, "new"))
            assertTrue(task.await() is AppResult.Failure)
        }
    }
}
