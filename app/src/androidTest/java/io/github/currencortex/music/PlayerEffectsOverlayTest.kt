package io.github.currencortex.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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

    @Test fun slowDragCrossesTouchSlopInsideOverlayWithoutReachingRetainedPlayer() {
        var underneathTaps = 0
        var rowTaps = 0
        var slop = 0f
        var scrollOffset: () -> Int = { 0 }
        compose.setContent { LeiTheme(AppearanceSettings()) { Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clickable { underneathTaps++ })
            PlayerEffectsOverlay(true, {}, {}, effects = { _, _ ->
                val state = rememberLazyListState()
                slop = LocalViewConfiguration.current.touchSlop
                scrollOffset = { state.firstVisibleItemIndex * 10000 + state.firstVisibleItemScrollOffset }
                Column(Modifier.fillMaxSize()) {
                    // Unhandled space must block the retained player's hit targets without
                    // consuming child gestures during the Final pointer pass.
                    Box(Modifier.fillMaxWidth().height(80.dp).testTag("overlay_blank"))
                    LazyColumn(Modifier.fillMaxSize().testTag("overlay_list"), state = state) {
                        items(40) { index -> BasicText("row $index", Modifier.fillMaxWidth().height(72.dp).clickable { rowTaps++ }) }
                    }
                }
            }, equalizer = {})
        } } }
        compose.waitForIdle()
        compose.onNodeWithTag("overlay_blank").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(0, underneathTaps) }
        compose.onNodeWithTag("overlay_list").performTouchInput {
            down(Offset(centerX, height * .7f))
            // Real fingers send many small deltas before crossing touch slop. A single
            // large move incorrectly passes even when the parent's Final pass cancels it.
            repeat(24) { moveBy(Offset(0f, -slop / 4), delayMillis = 16) }
            up()
        }
        compose.runOnIdle {
            assertTrue("Gradual motion must start scrolling inside the overlay", scrollOffset() > 0)
            assertEquals(0, underneathTaps)
            assertEquals(0, rowTaps)
        }
    }
}
