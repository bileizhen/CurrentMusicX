package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.auth.*
import io.github.currencortex.music.feature.profile.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** All writes use an isolated local fixture; never sends mail or changes a real account. */
class PasswordFlowsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var auth: AuthViewModel
    private val models = ViewModelStore()
    private val server = MockWebServer()
    private val writes = LinkedBlockingQueue<RecordedRequest>()
    @Volatile private var rejectCode = false
    @Volatile private var rejectReset = false

    @Before fun setup(): Unit = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(r: RecordedRequest): MockResponse {
                if (r.method != "GET") {
                    writes.add(r)
                    if (rejectCode && r.path == "/cm/profile/password/code")
                        return MockResponse().setResponseCode(400).setBody("""{"error":"请先绑定邮箱"}""")
                    if (rejectReset && r.path == "/cm/auth/password/reset")
                        return MockResponse().setResponseCode(400).setBody("""{"error":"验证码无效或已过期"}""")
                }
                return MockResponse().setBody(when (r.path) {
                    "/cm/auth/me" -> """{"id":7,"username":"listener","nickname":"Listener"}"""
                    "/cm/playlists" -> """{"playlists":[]}"""
                    "/cm/plays/recent" -> """{"songs":[]}"""
                    else -> "{}"
                })
            }
        }
        server.start()
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "password-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        container.updateSettings.setAutoCheck(false)
        val base = server.url("/cm/").toString()
        container.musicSettings.setServer(base); container.accountRepository.server = base
        compose.runOnIdle { auth = AuthViewModel(container); models.put("auth", auth) }
    }
    @After fun close() { compose.runOnIdle { models.clear() }; container.close(); server.shutdown() }
    private fun request(path: String): RecordedRequest = writes.poll(5, TimeUnit.SECONDS)!!.also { assertEquals(path, it.path) }
    private fun field(tag: String, value: String) = compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    private fun submit() = compose.onNodeWithTag("password_submit").performScrollTo().performClick()
    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun forgotPasswordKeepsFailedInputAllowsRetryAndReturnsToLogin(): Unit = runBlocking {
        compose.setContent { LeiTheme(AppearanceSettings()) { top.yukonga.miuix.kmp.basic.Scaffold {
            LoginScreen(auth)
        } } }
        compose.onNodeWithTag("forgot_password").performScrollTo().performClick()
        compose.onNodeWithTag("password_send_code").assertIsNotEnabled()
        capture("password-recovery.png")
        field("password_email", "listener@example.com")
        compose.onNodeWithTag("password_send_code").performScrollTo().performClick()
        assertNull(request("/cm/auth/password/code").getHeader("Authorization"))
        compose.waitUntil(5000) { auth.resetCodeUntil.value > 0 && !auth.state.value.loading }
        compose.onNodeWithTag("password_send_code").assertIsNotEnabled()
        field("password_code", "123456"); field("password_new", "new-password")
        field("password_confirm", "different")
        compose.onNodeWithTag("password_mismatch").assertExists()
        compose.onNodeWithTag("password_submit").assertIsNotEnabled()
        field("password_confirm", "new-password")
        rejectReset = true; submit()
        assertNull(request("/cm/auth/password/reset").getHeader("Authorization"))
        compose.waitUntil(5000) { auth.state.value.failed }
        assertEquals("验证码无效或已过期", auth.state.value.message)
        compose.onNodeWithTag("password_email").assertTextContains("listener@example.com")
        rejectReset = false; submit()
        val body = ApiJson.parseToJsonElement(request("/cm/auth/password/reset").body.readUtf8()).jsonObject
        assertEquals("new-password", body["newPassword"]!!.jsonPrimitive.content)
        compose.waitUntil(5000) { auth.state.value.message == "密码已重置，请使用新密码登录" }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("password_submit").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("login_submit").assertExists()
        assertNull(container.accountRepository.token)
    }
    private fun showChange(): ProfileViewModel {
        lateinit var profile: ProfileViewModel
        compose.runOnIdle { profile = ProfileViewModel(container); models.put("profile", profile); profile.dialog.value = "password" }
        compose.setContent { LeiTheme(AppearanceSettings()) { top.yukonga.miuix.kmp.basic.Scaffold { ProfileDialogs(profile, auth) } } }
        return profile
    }
    @Test fun boundEmailVerificationReportsUnboundErrorAndRetainsCurrentLoginAfterChange(): Unit = runBlocking {
        container.accountRepository.save("current-session", UserDto(7, "listener", "Listener"))
        val profile = showChange()
        compose.onNodeWithTag("password_email_mode").performClick()
        capture("password-change-email.png")
        rejectCode = true
        compose.onNodeWithTag("password_send_code").performScrollTo().performClick()
        assertEquals("Bearer current-session", request("/cm/profile/password/code").getHeader("Authorization"))
        compose.waitUntil(5000) { !profile.busy.value && container.errorNotices.state.value?.message == "请先绑定邮箱" }
        assertEquals(0L, profile.passwordCodeUntil.value)
        rejectCode = false
        compose.onNodeWithTag("password_send_code").performClick()
        request("/cm/profile/password/code")
        compose.waitUntil(5000) { profile.passwordCodeUntil.value > 0 && !profile.busy.value }
        compose.onNodeWithTag("password_send_code").assertIsNotEnabled()
        field("password_code", "123456"); field("password_new", "new-password"); field("password_confirm", "new-password")
        submit()
        val r = request("/cm/profile/password")
        assertEquals("PUT", r.method); assertEquals("Bearer current-session", r.getHeader("Authorization"))
        val body = ApiJson.parseToJsonElement(r.body.readUtf8()).jsonObject
        assertEquals(setOf("emailCode", "newPassword"), body.keys)
        compose.waitUntil(5000) { profile.dialog.value == null && !profile.busy.value }
        assertEquals("current-session", container.accountRepository.token)
        assertEquals("密码已更新，其他设备已下线", profile.message.value)
    }
    @Test fun originalPasswordVerificationStillUsesOriginalFields(): Unit = runBlocking {
        container.accountRepository.save("current-session", UserDto(7, "listener", "Listener"))
        val profile = showChange()
        compose.onNodeWithTag("password_code").assertDoesNotExist()
        field("password_old", "old-password"); field("password_new", "new-password"); field("password_confirm", "new-password")
        submit()
        val body = ApiJson.parseToJsonElement(request("/cm/profile/password").body.readUtf8()).jsonObject
        assertEquals(setOf("oldPassword", "newPassword"), body.keys)
        compose.waitUntil(5000) { profile.dialog.value == null && !profile.busy.value }
    }
}
