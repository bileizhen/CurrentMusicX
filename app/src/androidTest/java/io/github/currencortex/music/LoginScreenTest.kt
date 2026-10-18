package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** The login form lives in the "我的" tab; drive it the way a user does. */
class LoginScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun upstreamRegistrationErrorReachesTheIslandInsteadOfAnInlineGenericFailure(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "register-error-${UUID.randomUUID()}")
        val server = okhttp3.mockwebserver.MockWebServer()
        try {
            server.start()
            container.ready.await(); container.sessionRestored.await()
            container.updateSettings.setAutoCheck(false)
            val base = server.url("/cm/").toString()
            container.accountRepository.server = base
            container.musicSettings.setServer(base)
            val vm = io.github.currencortex.music.feature.auth.AuthViewModel(container)
            compose.setContent { io.github.currencortex.music.ui.theme.LeiTheme(io.github.currencortex.music.data.settings.AppearanceSettings()) {
                androidx.compose.foundation.layout.Box {
                    io.github.currencortex.music.feature.auth.LoginScreen(vm)
                    io.github.currencortex.music.feature.lyrics.desktop.SoundEffectIslandFallback(container)
                }
            } }
            compose.onNodeWithTag("login_switch_mode").performScrollTo().performClick()
            compose.onNodeWithTag("login_username").performTextInput("a")
            compose.onNodeWithTag("login_password").performTextInput("sample-password")
            compose.onNodeWithTag("register_contact").performScrollTo().performTextInput("test@example.com")
            compose.onNodeWithTag("register_code").performScrollTo().performTextInput("123456")
            val message = "用户名需 2~24 位字母/数字/下划线/中文"
            server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(400).setBody("""{"error":"$message"}"""))
            compose.onNodeWithTag("login_submit").performScrollTo().performClick()
            compose.waitUntil(5000) { container.errorNotices.state.value?.message == message }
            assertTrue(vm.state.value.failed)
            assertEquals(message, vm.state.value.message)
            compose.onNodeWithTag("login_message").assertDoesNotExist()
            compose.onNodeWithTag("island_error_notice").assertIsDisplayed()
            compose.onNodeWithText(message).assertIsDisplayed()
            val request = server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS)!!
            assertEquals("/cm/auth/register", request.path)
            assertNull(request.getHeader("Authorization"))
            compose.onNodeWithTag("login_username").performScrollTo().performTextReplacement("valid_name")
            assertNull(vm.state.value.message)
            assertFalse(vm.state.value.failed)
        } finally { container.close(); server.shutdown() }
    }

    @Test fun loginFormEnablesSubmitTogglesPasswordAndSwitchesToRegister(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "login-ui-${UUID.randomUUID()}")
        try {
            container.ready.await(); container.sessionRestored.await()
            container.updateSettings.setAutoCheck(false)
            compose.setContent { CurrentMusicApp(container) }
            compose.onNodeWithTag("tab_2")
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("login_card").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("login_card").assertExists()
            compose.onNodeWithTag("login_message").assertDoesNotExist()
            // Submit stays disabled until both fields are filled.
            compose.onNodeWithTag("login_submit").assertIsNotEnabled()
            compose.onNodeWithTag("login_username").performTextInput("user8")
            compose.onNodeWithTag("login_password").performTextInput("sample-password")
            compose.onNodeWithTag("login_submit").assertIsEnabled()
            compose.onNodeWithTag("login_password_toggle").performClick()
            // Registration reuses the same card and gates on a 6-character password.
            // IME resize can still move the root viewport; invoke the accessible action.
            compose.onNodeWithTag("login_switch_mode").performScrollTo()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            compose.waitUntil(3000) { compose.onAllNodesWithTag("register_card").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("register_card").assertExists()
            compose.onNodeWithTag("register_contact").assertExists()
            compose.onNodeWithTag("register_send_code").assertIsNotEnabled()
            compose.onNodeWithTag("register_contact").performTextInput("user@example.com")
            compose.onNodeWithTag("register_send_code").assertIsEnabled()
        } finally { container.close() }
    }
}
