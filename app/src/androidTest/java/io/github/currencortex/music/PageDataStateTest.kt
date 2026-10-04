package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.currencortex.music.ui.util.collectAsPageState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Text

class PageDataStateTest {
    @get:Rule val compose = createComposeRule()
    @Test fun movingPageRetainsItsDataAndResumedPageReceivesLatestResult() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle = registry
        }
        val data = MutableStateFlow("Visible before transition")
        owner.registry.currentState = Lifecycle.State.RESUMED
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val text by data.collectAsPageState()
                Text(text)
            }
        }
        compose.onNodeWithText("Visible before transition").assertExists()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { data.value = "First arriving dataset" }
        compose.runOnIdle { data.value = "Latest arriving dataset" }
        compose.onNodeWithText("Visible before transition").assertExists()
        compose.onNodeWithText("Latest arriving dataset").assertDoesNotExist()
        // Both completed navigation and a cancelled predictive-back gesture resume their page.
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("Latest arriving dataset").assertExists()
        compose.onNodeWithText("First arriving dataset").assertDoesNotExist()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.DESTROYED }
    }
}
