package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.auth.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Local accounts and a local server only: never sends mail, enables 2FA or uses a real OTP. */
class LoginTwoFactorFlowsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var vm: AuthViewModel
    private val models = ViewModelStore()
    private val server = MockWebServer()
    private val completions = AtomicInteger()
    private val loginCalls = AtomicInteger()
    private val verifyCalls = AtomicInteger()
    private val verifyEntered = CountDownLatch(1)
    private val releaseVerify = CountDownLatch(1)
    private val verifyFinished = CountDownLatch(1)
    @Volatile private var twoFactor = true
    @Volatile private var expiresIn = 300
    @Volatile private var holdVerify = false
    @Volatile private var recovery = false
    private val success = """{"token":"new-login-session","user":{"id":5,"username":"listener","nickname":"Listener"}}"""
    @Before fun setup(): Unit = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/cm/auth/login") {
                    assertNull(request.getHeader("Authorization")); loginCalls.incrementAndGet()
                    return MockResponse().setBody(if (twoFactor)
                        """{"requires2FA":true,"challengeToken":"local-challenge","expiresIn":$expiresIn}""" else success)
                }
                if (request.path == "/cm/auth/2fa/login") {
                    assertNull(request.getHeader("Authorization")); verifyCalls.incrementAndGet(); verifyEntered.countDown()
                    if (holdVerify) releaseVerify.await(5, TimeUnit.SECONDS)
                    val body = io.github.currencortex.music.core.network.ApiJson.parseToJsonElement(request.body.readUtf8())
                        .let { it as kotlinx.serialization.json.JsonObject }
                    assertEquals(setOf("challengeToken", "code"), body.keys)
                    assertEquals("local-challenge", (body["challengeToken"] as kotlinx.serialization.json.JsonPrimitive).content)
                    val code = (body["code"] as kotlinx.serialization.json.JsonPrimitive).content
                    val accepted = code == if (recovery) "ABCD-EFGH-IJKL" else "123456"
                    verifyFinished.countDown()
                    return if (accepted) MockResponse().setBody(success)
                        else MockResponse().setResponseCode(401).setBody("""{"error":"验证码不正确，请重试"}""")
                }
                return MockResponse().setResponseCode(404)
            }
        }
        server.start()
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "two-factor-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await(); container.updateSettings.setAutoCheck(false)
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.accountRepository.save("existing-session", UserDto(9, "existing", "Existing"))
        compose.runOnIdle { vm = AuthViewModel(container); models.put("auth", vm) }
    }
    @After fun close() {
        releaseVerify.countDown(); compose.runOnIdle { models.clear() }; container.close(); server.shutdown()
    }
    private fun open(dark: Boolean = false, root: Boolean = false) {
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
            customTextScale = dark, textScale = if (dark) 1.3f else 1f)) {
            top.yukonga.miuix.kmp.basic.Scaffold {
                val account by vm.account.collectAsState()
                if (!root || account.account == null) LoginScreen(vm, addingAccount = !root,
                    onDone = { completions.incrementAndGet() })
                else top.yukonga.miuix.kmp.basic.Text("Signed in", modifier = androidx.compose.ui.Modifier.testTag("signed_in"))
            }
        } }
    }
    private fun field(tag: String, value: String) = compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    private fun begin() {
        field("login_username", "listener"); field("login_password", "test-password")
        compose.onNodeWithTag("login_submit").performScrollTo().performClick()
        compose.waitUntil(5000) { vm.loginChallenge.value != null }
        compose.onNodeWithTag("login_two_factor").assertExists()
        assertEquals("existing-session", container.accountRepository.token)
        assertEquals(0, completions.get()); assertEquals(0, verifyCalls.get())
    }
    private fun submit() = compose.onNodeWithTag("login_two_factor_submit").performScrollTo().performClick()

    @Test fun wrongOtpRetainsTheChallengeAndOnlyAValidOtpCompletesLogin() {
        open(); begin()
        compose.onNodeWithTag("login_two_factor_submit").assertIsNotEnabled()
        field("login_two_factor_code", "000000"); submit()
        compose.waitUntil(5000) { vm.state.value.failed }
        compose.onNodeWithTag("login_two_factor_error").assertTextEquals("验证码不正确，请重试")
        assertEquals("existing-session", container.accountRepository.token)
        assertEquals(0, completions.get())
        field("login_two_factor_code", "123456"); submit()
        compose.waitUntil(5000) { completions.get() == 1 && container.accountRepository.token == "new-login-session" }
        assertEquals(2, verifyCalls.get()); assertNull(vm.loginChallenge.value)
    }
    @Test fun recoveryCodeCanCompleteTheSecondStepInDarkLargeType() {
        recovery = true; open(true); begin()
        compose.onNodeWithTag("login_two_factor_method_1").performScrollTo().performClick()
        field("login_two_factor_code", "ABCD-EFGH-IJKL"); submit()
        compose.waitUntil(5000) { completions.get() == 1 }
        assertEquals("new-login-session", container.accountRepository.token)
    }
    @Test fun expiredChallengeRequiresRestartAndNeverAutomaticallySubmits() {
        expiresIn = 1; open(); begin()
        compose.waitUntil(4000) { vm.loginChallenge.value?.remainingSeconds == 0L }
        compose.onNodeWithTag("login_two_factor_submit").assertIsNotEnabled()
        compose.onNodeWithTag("login_two_factor_cancel").performScrollTo().performClick()
        assertEquals("", compose.onNodeWithTag("login_password").fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        assertNull(vm.loginChallenge.value); assertEquals(0, verifyCalls.get())
        assertEquals("existing-session", container.accountRepository.token)
    }
    @Test fun cancellingAnInFlightVerificationIgnoresItsLateResponse() {
        holdVerify = true; open(); begin()
        field("login_two_factor_code", "123456"); submit()
        assertTrue(verifyEntered.await(3, TimeUnit.SECONDS))
        compose.onNodeWithTag("login_two_factor_cancel").performScrollTo().performClick()
        releaseVerify.countDown(); assertTrue(verifyFinished.await(3, TimeUnit.SECONDS))
        compose.waitForIdle()
        assertNull(vm.loginChallenge.value); assertEquals(0, completions.get())
        assertEquals("existing-session", container.accountRepository.token)
        compose.onNodeWithTag("login_submit").assertExists()
    }
    @Test fun verifiedLoginStillFinishesWhenTheRootLoginFormDisposes(): Unit = runBlocking {
        container.accountRepository.clear(); open(root = true)
        field("login_username", "listener"); field("login_password", "test-password")
        compose.onNodeWithTag("login_submit").performScrollTo().performClick()
        compose.waitUntil(5000) { vm.loginChallenge.value != null }
        assertNull(container.accountRepository.token)
        field("login_two_factor_code", "123456"); submit()
        compose.waitUntil(5000) { vm.state.value.message == "登录成功" }
        compose.onNodeWithTag("signed_in").assertIsDisplayed()
        assertEquals("new-login-session", container.accountRepository.token)
        assertEquals(1, verifyCalls.get())
    }
    @Test fun ordinaryLoginStillFinishesWhenTheRootLoginFormDisposes(): Unit = runBlocking {
        twoFactor = false; container.accountRepository.clear(); open(root = true)
        field("login_username", "listener"); field("login_password", "test-password")
        compose.onNodeWithTag("login_submit").performScrollTo().performClick()
        compose.waitUntil(5000) { vm.state.value.message == "登录成功" }
        compose.onNodeWithTag("signed_in").assertIsDisplayed()
        assertEquals("new-login-session", container.accountRepository.token)
        assertEquals(1, loginCalls.get()); assertEquals(0, verifyCalls.get())
    }
}
