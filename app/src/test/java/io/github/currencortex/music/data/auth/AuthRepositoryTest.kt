package io.github.currencortex.music.data.auth

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class AuthRepositoryTest {
    @Test fun passwordRecoveryIsAnonymousAndNeverReplacesTheActiveAccount() = runBlocking {
        MockWebServer().use { server ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val store = Store(); val accounts = AccountRepository(store, scope)
                val base = server.url("/cm/").toString(); accounts.server = base
                accounts.save("other-session", UserDto(9, "other"))
                val repo = AuthRepository(ApiClient({ base }, { accounts.token }, accounts::expired), accounts, "test-device") { _, _ -> }
                server.enqueue(MockResponse().setBody("{}"))
                assertTrue(repo.sendPasswordResetCode(" reset@example.com ") is AppResult.Success)
                val send = server.takeRequest()
                assertEquals("POST", send.method); assertEquals("/cm/auth/password/code", send.path)
                assertNull(send.getHeader("Authorization"))
                assertEquals("reset@example.com", ApiJson.parseToJsonElement(send.body.readUtf8()).jsonObject["email"]!!.jsonPrimitive.content)
                server.enqueue(MockResponse().setBody("""{"ok":true,"username":"reset-user"}"""))
                assertTrue(repo.resetPassword(" reset@example.com ", " 123456 ", "new-password") is AppResult.Success)
                val reset = server.takeRequest()
                assertEquals("POST", reset.method); assertEquals("/cm/auth/password/reset", reset.path)
                assertNull(reset.getHeader("Authorization"))
                val body = ApiJson.parseToJsonElement(reset.body.readUtf8()).jsonObject
                assertEquals("123456", body["code"]!!.jsonPrimitive.content)
                assertEquals("new-password", body["newPassword"]!!.jsonPrimitive.content)
                assertEquals("app", body["platform"]!!.jsonPrimitive.content)
                assertEquals("other-session", accounts.token); assertEquals("other-session", store.value)
                assertEquals(9L, accounts.state.value.account!!.id)
                server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"验证码无效或已过期"}"""))
                val failed = repo.resetPassword("reset@example.com", "000000", "new-password") as AppResult.Failure
                assertEquals("验证码无效或已过期", failed.message)
                server.takeRequest()
                assertTrue(repo.resetPassword("reset@example.com", "123456", "short") is AppResult.Failure)
                assertTrue(repo.resetPassword("reset@example.com", "123456", "x".repeat(65)) is AppResult.Failure)
                assertNull(server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS))
            } finally { scope.cancel() }
        }
    }
    @Test fun registrationAndVerificationCodesUseContactSpecificFieldsWithoutCurrentCredentials() = runBlocking {
        MockWebServer().use { server ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val store = Store(); val accounts = AccountRepository(store, scope)
                val base = server.url("/cm/").toString(); accounts.server = base
                val repo = AuthRepository(ApiClient({ base }, { accounts.token }, accounts::expired), accounts, "test-device") { _, _ -> }
                listOf(false, true).forEach { phone ->
                    server.enqueue(MockResponse().setBody("{}")); assertTrue(repo.sendRegistrationCode("contact", phone) is AppResult.Success)
                    val code = server.takeRequest(); assertNull(code.getHeader("Authorization"))
                    assertEquals(if (phone) "/cm/auth/phone/code" else "/cm/auth/email/code", code.path)
                    server.enqueue(MockResponse().setBody("""{"token":"registered-session","user":{"id":5,"nickname":"Name"}}"""))
                    assertTrue(repo.register(RegistrationInput("name", "sample-password", "Name", "contact", "1234", phone)) is AppResult.Success)
                    val request = server.takeRequest(); assertNull(request.getHeader("Authorization"))
                    val body = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
                    assertEquals("1234", body[if (phone) "phoneCode" else "emailCode"]!!.jsonPrimitive.content)
                    assertEquals("app", body["platform"]!!.jsonPrimitive.content)
                }
            } finally { scope.cancel() }
        }
    }
    private class Store : TokenStore {
        var value: String? = "stored"
        override fun read() = value
        override fun write(token: String) { value = token }
        override fun clear() { value = null }
    }
    @Test fun restoreKeepsCredentialOnServerFailureAndClearsOnly401() = runBlocking {
        MockWebServer().use { server ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val store = Store()
                val accounts = AccountRepository(store, scope)
                val base = server.url("/cm/").toString()
                accounts.server = base
                val repo = AuthRepository(ApiClient({ base }, { accounts.token }, accounts::expired), accounts, "test") { _, _ -> }
                server.enqueue(MockResponse().setResponseCode(503))
                assertTrue(repo.restore(base) is AppResult.Failure)
                assertEquals("stored", store.value); assertEquals("stored", accounts.token)
                assertTrue(accounts.state.value.offline)
                server.enqueue(MockResponse().setResponseCode(401))
                repo.restore(base)
                withTimeout(3000) { while (store.value != null) delay(10) }
                assertNull(accounts.token)
            } finally { scope.cancel() }
        }
    }
    @Test fun loginConsumesTokenAndUserAndRejectsStaleServerInvalidation() = runBlocking {
        MockWebServer().use { server ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val store = Store(); val accounts = AccountRepository(store, scope)
                val base = server.url("/cm/").toString(); accounts.server = base
                val repo = AuthRepository(ApiClient({ base }, { accounts.token }, accounts::expired), accounts, "device") { _, _ -> }
                server.enqueue(MockResponse().setBody("""{"token":"new-session","user":{"id":5,"nickname":"name"}}"""))
                assertEquals(5L, ((repo.login("user", "password") as AppResult.Success).value as LoginOutcome.SignedIn).account.id)
                assertEquals("new-session", store.value)
                val request = server.takeRequest()
                assertNull(request.getHeader("Authorization")); assertTrue(request.body.readUtf8().contains("platform"))
                accounts.expired(RequestSession("https://old.example/", "new-session"))
                assertEquals("new-session", accounts.token)
                accounts.expired(RequestSession(base, "old-session"))
                assertEquals("new-session", accounts.token)
            } finally { scope.cancel() }
        }
    }
}
