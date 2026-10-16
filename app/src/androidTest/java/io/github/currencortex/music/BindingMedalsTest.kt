package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.binding.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.binding.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class BindingMedalsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun earnedMedalsScrollAndFailureCanRetryWithoutClaimingTheCollectionIsEmpty() {
        var retries = 0
        var state by mutableStateOf(BindingMedalsState(42, value = NeteaseMedals(42, 12,
            (1..12).map { NeteaseMedal("$it", "勋章 $it", "") })))
        compose.setContent { LeiTheme(AppearanceSettings()) { BindingMedalsCard(state) { retries++ } } }
        compose.onNodeWithText("12 枚").assertExists()
        compose.onNodeWithTag("binding_medal_0").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(11)
        compose.onNodeWithText("勋章 12").assertIsDisplayed()
        compose.runOnIdle { state = BindingMedalsState(42, error = "勋章暂时无法加载") }
        compose.onNodeWithTag("binding_medals_empty").assertDoesNotExist()
        compose.onNodeWithTag("binding_medals_retry").performClick()
        compose.runOnIdle { assertEquals(1, retries); state = BindingMedalsState(42, value = NeteaseMedals(42, 0)) }
        compose.onNodeWithTag("binding_medals_empty").assertExists()
    }

    /** Read-only validation using the device's existing NetEase login. */
    @Test fun actualAccountLoadsEarnedMedalsThroughTheViewModel(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("medalsLive") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName == "com.bileizhen.currentmusic")
        val container = app.container
        container.ready.await(); container.neteaseSessions.ready.await()
        val account = container.neteaseSessions.state.value
        assertTrue(account.loggedIn && !account.stale)
        val models = ViewModelStore()
        lateinit var vm: BindingViewModel
        try {
            compose.runOnIdle { vm = BindingViewModel(container); models.put("medals", vm) }
            compose.waitUntil(30_000) { vm.medals.value.value != null || vm.medals.value.error != null }
            assertNull(vm.medals.value.error, vm.medals.value.error)
            val medals = requireNotNull(vm.medals.value.value)
            assertEquals(account.profile!!.uid, medals.userId)
            val report = buildJsonObject {
                put("accountMatched", true); put("total", medals.total); put("earnedCount", medals.medals.size)
                put("names", JsonArray(medals.medals.map { JsonPrimitive(it.name) }))
                put("imageCount", medals.medals.count { it.image.startsWith("https://") })
            }
            File(app.getExternalFilesDir(null), "medals-verification.json").writeText(report.toString())
            compose.setContent { LeiTheme(AppearanceSettings()) {
                BindingMedalsCard(BindingMedalsState(medals.userId, value = medals)) {}
            } }
            compose.onNodeWithTag("binding_medals").assertIsDisplayed()
            if (medals.medals.isEmpty()) compose.onNodeWithTag("binding_medals_empty").assertExists()
            else compose.onNodeWithTag("binding_medal_0").assertIsDisplayed()
        } finally { compose.runOnIdle { models.clear() } }
    }
}
