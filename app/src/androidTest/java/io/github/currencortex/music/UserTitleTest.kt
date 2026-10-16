package io.github.currencortex.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.profile.UserTitleBadge
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class UserTitleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun blankTitleHasNoPlaceholderAndLongTitleFitsNarrowSpace() {
        var title by mutableStateOf("  ")
        compose.setContent { LeiTheme(AppearanceSettings()) {
            Box(Modifier.width(140.dp)) { UserTitleBadge(title) }
        } }
        compose.onNodeWithTag("user_title").assertDoesNotExist()
        compose.runOnIdle { title = "CurrentMusicX开发者与社区贡献者" }
        val badge = compose.onNodeWithTag("user_title").assertIsDisplayed()
        val bounds = badge.getUnclippedBoundsInRoot()
        assertTrue(bounds.right - bounds.left <= 140.dp)
        compose.runOnIdle { title = "" }
        compose.onNodeWithTag("user_title").assertDoesNotExist()
    }

    @Test fun liveAuthMeTitleMatchesProfileAndRestoredAccount(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("titleLive") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = app.container
        container.ready.await(); container.sessionRestored.await()
        val raw = container.apiClient.request("GET", "auth/me", authenticated = true)
        val user = container.apiClient.decode<UserDto>(raw)
        val title = raw.jsonObject["title"]!!.jsonPrimitive.content
        assertEquals(title, user.title)
        val profile = container.profileRepository.me()
        assertEquals(user.id, profile.id)
        assertEquals(title, profile.title)
        assertEquals(title, container.profileRepository.account(profile).title)
        assertEquals(title, container.accountRepository.state.value.account!!.title)
        val report = buildJsonObject { put("title", title); put("accountMatched", true) }
        File(app.getExternalFilesDir(null), "title-verification.json").writeText(report.toString())
        compose.setContent { LeiTheme(AppearanceSettings()) { UserTitleBadge(profile.title) } }
        if (title.isBlank()) compose.onNodeWithTag("user_title").assertDoesNotExist()
        else compose.onNodeWithText(title).assertIsDisplayed()
    }
}
