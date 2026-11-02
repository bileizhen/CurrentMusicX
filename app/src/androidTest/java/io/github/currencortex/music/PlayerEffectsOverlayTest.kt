package io.github.currencortex.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.currencortex.music.feature.player.PlayerEffectsOverlay
import io.github.currencortex.music.ui.component.LocalPageContainerSettled
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerEffectsOverlayTest {
    @get:Rule val compose = createComposeRule()
    @Test fun effectsAndEqualizerReturnToTheRetainedPlayerWithoutReopeningIt() {
        val open = mutableStateOf(false)
        var presented = false
        var disposed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { LeiTheme(AppearanceSettings()) { Box {
            DisposableEffect(Unit) { onDispose { disposed++ } }
            BasicText("retained player")
            PlayerEffectsOverlay(open.value, { open.value = false }, { presented = it }, effects = { back, eq ->
                Column {
                    BasicText("effects equalizer", Modifier.clickable(onClick = eq))
                    BasicText("effects back", Modifier.clickable(onClick = back))
                    BasicText(if (LocalPageContainerSettled.current) "media ready" else "media deferred")
                }
            }, equalizer = { back -> BasicText("equalizer back", Modifier.clickable(onClick = back)) })
        } } }
        compose.runOnIdle { open.value = true }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("media deferred").assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("media ready").assertExists()
        compose.onNodeWithText("retained player").assertExists()
        compose.onNodeWithText("effects equalizer").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("equalizer back").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("effects back").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertTrue(presented); assertEquals(0, disposed) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("retained player").assertExists()
        compose.runOnIdle { assertFalse(presented); assertEquals(0, disposed) }
    }
}
