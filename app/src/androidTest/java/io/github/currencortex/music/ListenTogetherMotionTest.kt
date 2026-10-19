package io.github.currencortex.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.room.ListenTogetherPageTransition
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

class ListenTogetherMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun transitionMovesBetweenFramesHidesOutgoingActionsAndRestoresDraft() {
        val page = mutableStateOf("session")
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            ListenTogetherPageTransition(page.value, { it }, { if (it == "session") 0 else 1 }, Modifier.fillMaxSize()) { shown ->
                if (shown == "session") {
                    var draft by rememberSaveable { mutableStateOf("") }
                    TextField(draft, { draft = it }, modifier = Modifier.testTag("saved_invitation"))
                } else Text("一起听记录", Modifier.testTag("motion_archive"))
            }
        } }
        compose.onNodeWithTag("saved_invitation").performTextInput("https://163cn.tv/keep-draft")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { page.value = "archive" }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("saved_invitation").assertDoesNotExist()
        val start = compose.onNodeWithTag("motion_archive").fetchSemanticsNode().boundsInRoot.left
        assertTrue("The incoming page must slide in instead of appearing in its final position", start > 0f)
        compose.mainClock.advanceTimeBy(120)
        val middle = compose.onNodeWithTag("motion_archive").fetchSemanticsNode().boundsInRoot.left
        assertTrue("The page position must change during the animation", middle < start)
        compose.mainClock.advanceTimeBy(400)
        assertEquals(0f, compose.onNodeWithTag("motion_archive").fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.runOnIdle { page.value = "session" }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("saved_invitation").assertTextContains("https://163cn.tv/keep-draft")
        compose.mainClock.autoAdvance = true
    }

    @Test fun returningFromReportRestoresHistoryScrollPosition() {
        val page = mutableStateOf("history")
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            ListenTogetherPageTransition(page.value, { it }, { if (it == "history") 0 else 1 }, Modifier.fillMaxSize()) { shown ->
                if (shown == "history") LazyColumn(Modifier.fillMaxSize().testTag("motion_history_list")) {
                    items((0..60).toList()) { index -> Text("记录 $index", Modifier.height(64.dp).testTag("motion_row_$index")) }
                } else Text("本次报告")
            }
        } }
        compose.onNodeWithTag("motion_history_list").performScrollToIndex(30)
        compose.onNodeWithTag("motion_row_30").assertIsDisplayed()
        compose.runOnIdle { page.value = "report" }
        compose.waitForIdle()
        compose.runOnIdle { page.value = "history" }
        compose.onNodeWithTag("motion_row_30").assertIsDisplayed()
    }
}
