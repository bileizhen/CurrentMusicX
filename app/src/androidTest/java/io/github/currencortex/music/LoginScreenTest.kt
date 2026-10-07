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

    @Test fun loginFormEnablesSubmitTogglesPasswordAndSwitchesToRegister(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "login-ui-${UUID.randomUUID()}")
        try {
            container.ready.await(); container.sessionRestored.await()
            container.updateSettings.setAutoCheck(false)
            compose.setContent { CurrentMusicApp(container) }
            compose.onNodeWithTag("tab_2").performClick()
            compose.onNodeWithTag("login_card").assertExists()
            compose.onNodeWithTag("login_message").assertDoesNotExist()
            // Submit stays disabled until both fields are filled.
            compose.onNodeWithTag("login_submit").assertIsNotEnabled()
            compose.onNodeWithTag("login_username").performTextInput("user8")
            compose.onNodeWithTag("login_password").performTextInput("sample-password")
            compose.onNodeWithTag("login_submit").assertIsEnabled()
            compose.onNodeWithTag("login_password_toggle").performClick()
            // Registration reuses the same card and gates on a 6-character password.
            // The link sits below the scroll viewport, so scroll it in before tapping.
            compose.onNodeWithTag("login_switch_mode").performScrollTo().performClick()
            compose.waitUntil(3000) { compose.onAllNodesWithTag("register_card").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("register_card").assertExists()
            compose.onNodeWithTag("register_contact").assertExists()
            compose.onNodeWithTag("register_send_code").assertIsNotEnabled()
            compose.onNodeWithTag("register_contact").performTextInput("user@example.com")
            compose.onNodeWithTag("register_send_code").assertIsEnabled()
        } finally { container.close() }
    }
}
