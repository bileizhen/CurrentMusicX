package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.auth.LoginProtectionDialog
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Local fixture only: no real account security settings are changed. */
class LoginProtectionFlowsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private val server = MockWebServer()
    private val writes = LinkedBlockingQueue<RecordedRequest>()
    @Volatile private var totpEnabled = false
    @Volatile private var questionEnabled = false
    @Before fun setup(): Unit = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertEquals("Bearer security-session", request.getHeader("Authorization"))
                if (request.method != "GET") writes.add(request)
                return when (request.path) {
                    "/cm/auth/2fa/status" -> MockResponse().setBody("""{"enabled":$totpEnabled,"securityQuestionEnabled":$questionEnabled,"securityQuestion":"第一只宠物？","recoveryCodesRemaining":7}""")
                    "/cm/auth/2fa/setup" -> MockResponse().setBody("""{"secret":"JBSWY3DPEHPK3PXP","uri":"otpauth://totp/test?secret=JBSWY3DPEHPK3PXP","expiresIn":600}""")
                    "/cm/auth/2fa/enable" -> {
                        val body = ApiJson.parseToJsonElement(request.body.clone().readUtf8()).jsonObject
                        if (body["code"]!!.jsonPrimitive.content == "123456") MockResponse().setBody("""{"enabled":true,"recoveryCodes":["abcd1234","efgh5678"]}""")
                        else MockResponse().setResponseCode(400).setBody("""{"error":"验证码不正确"}""")
                    }
                    "/cm/auth/security-question", "/cm/auth/2fa/disable" -> MockResponse().setBody("{}")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "protection-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await(); container.updateSettings.setAutoCheck(false)
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        container.accountRepository.save("security-session", UserDto(5, "listener"))
    }
    @After fun close() { container.close(); server.shutdown() }
    private fun open(question: Boolean) {
        compose.setContent {
            LeiTheme(AppearanceSettings()) { top.yukonga.miuix.kmp.basic.Scaffold {
                var visible by remember { mutableStateOf(true) }
                if (visible) LoginProtectionDialog(container, question) { visible = false }
            } }
        }
    }
    private fun field(tag: String, value: String) = compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun waitFor(tag: String) = compose.waitUntil(5000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun setupShowsLocalQrAndOnlyValidCodeRevealsRecoveryCodes() {
        open(false); waitFor("security_password")
        field("security_password", "test-password"); click("security_totp_submit")
        waitFor("security_totp_secret")
        assertEquals("/cm/auth/2fa/setup", writes.poll(3, TimeUnit.SECONDS)!!.path)
        compose.onNodeWithTag("security_recovery_codes").assertDoesNotExist()
        field("security_totp_code", "000000"); click("security_enable_totp")
        waitFor("security_error")
        compose.onNodeWithTag("security_error").assertTextEquals("验证码不正确")
        field("security_totp_code", "123456"); click("security_enable_totp")
        waitFor("security_recovery_codes")
        compose.onNodeWithTag("security_recovery_codes").assertTextEquals("abcd1234\nefgh5678")
        compose.onNodeWithTag("security_totp_secret").assertDoesNotExist()
        assertEquals("security-session", container.accountRepository.token)
    }
    @Test fun questionRequiresMatchingAnswersAndUsesSeparateEndpoint() {
        open(true); waitFor("security_password")
        field("security_password", "test-password")
        field("security_question", "第一只宠物？")
        field("security_answer", "小白"); field("security_answer_confirm", "不一致")
        compose.onNodeWithTag("security_question_submit").assertIsNotEnabled()
        field("security_answer_confirm", "小白"); click("security_question_submit")
        val request = writes.poll(3, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method); assertEquals("/cm/auth/security-question", request.path)
        val body = ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("小白", body["answer"]!!.jsonPrimitive.content)
        compose.waitUntil(5000) { compose.onAllNodesWithText("安全设置已更新，其他设备需重新登录。").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("security_password").assertDoesNotExist()
    }
    @Test fun existingTotpPreventsOpeningQuestionEnrollment() {
        totpEnabled = true; open(true)
        compose.waitUntil(5000) { compose.onAllNodesWithText("已开启验证器。请先关闭验证器，再设置密保问题。").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("security_password").assertDoesNotExist()
        assertTrue(writes.isEmpty())
    }
}
