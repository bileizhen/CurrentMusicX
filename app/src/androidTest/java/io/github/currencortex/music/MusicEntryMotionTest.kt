package io.github.currencortex.music

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.component.MusicPullToRefresh
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Text

/** Pure UI fixture: no accounts, repositories, audio, or production storage. */
class MusicEntryMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun initialLoadingDoesNotAnimateListHeightOrBlockScrolling() {
        compose.setContent {
            LeiTheme(AppearanceSettings(blur = false)) {
                MusicPullToRefresh(loading = true, onRefresh = {}, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize().testTag("entry_list")) {
                        items((0..80).toList()) { Text("Fixture row $it") }
                    }
                }
            }
        }
        val before = compose.onNodeWithText("Fixture row 0").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(1200)
        assertEquals("Entry loading must not expand a refresh header", before,
            compose.onNodeWithText("Fixture row 0").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("entry_list").performTouchInput { swipeUp(durationMillis = 500) }
        compose.onNodeWithText("Fixture row 0").assertIsNotDisplayed()
    }

    @Test fun userPullRefreshHoldsHeaderUntilLoadingCompletes() {
        val loading = mutableStateOf(false)
        var requests = 0
        compose.setContent {
            LeiTheme(AppearanceSettings(blur = false)) {
                MusicPullToRefresh(loading.value, { requests++; loading.value = true }, Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize().testTag("entry_list")) {
                        items((0..80).toList()) { Text("Fixture row $it") }
                    }
                }
            }
        }
        val before = compose.onNodeWithText("Fixture row 0").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("entry_list").performTouchInput { swipeDown(startY = 20f, endY = height * .8f, durationMillis = 600) }
        compose.waitUntil(5000) { requests == 1 }
        compose.mainClock.advanceTimeBy(1200)
        assertTrue("A user refresh must retain its header while loading", compose.onNodeWithText("Fixture row 0").fetchSemanticsNode().boundsInRoot.top > before.top)
        compose.runOnIdle { loading.value = false }
        compose.mainClock.advanceTimeBy(2000)
        assertEquals(before, compose.onNodeWithText("Fixture row 0").fetchSemanticsNode().boundsInRoot)
        assertEquals(1, requests)
    }
}
