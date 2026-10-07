package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Opens the real player and drives the sleep-timer sheet. No network, no audio. */
class SleepTimerSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sleepSheetSelectsPresetCustomTimeAndExtension(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = AppContainer(context, "sleep-ui-${UUID.randomUUID()}")
        try {
            container.ready.await(); container.sessionRestored.await()
            container.updateSettings.setAutoCheck(false)
            val song = Song(880101, "定时关闭界面验证", "测试歌手")
            container.playbackQueue.replace(listOf(song), 0)
            container.playerController.state.value = PlayerState(song = song, durationMs = 180_000)
            compose.setContent { CurrentMusicApp(container) }
            compose.onNodeWithTag("mini_cover").performClick()
            // The function-bar alarm button is the shortcut entry point.
            compose.onNodeWithTag("player_sleep_timer").performClick()
            compose.onNodeWithTag("sleep_timer_sheet").assertIsDisplayed()
            compose.onNodeWithTag("sleep_timer_switch").assertIsDisplayed()
            // Presets fill the countdown and flip the switch on.
            compose.onNodeWithTag("sleep_preset_30").performClick()
            assertTrue(container.sleepTimer.state.value.enabled)
            assertEquals(30, container.sleepTimer.state.value.minutes)
            compose.onNodeWithTag("sleep_timer_countdown").assertTextContains(":", substring = true)
            compose.onNodeWithTag("sleep_extend_switch").performClick()
            assertTrue(container.sleepTimer.state.value.extendToSongEnd)
            // Custom hours + minutes confirm a duration.
            compose.onNodeWithTag("sleep_timer_custom").performClick()
            compose.onNodeWithTag("sleep_custom_confirm").assertIsNotEnabled()
            compose.onNodeWithTag("sleep_custom_minutes").performTouchInput { swipeUp() }
            compose.waitForIdle()
            compose.onNodeWithTag("sleep_custom_confirm").performClick()
            compose.waitForIdle()
            assertTrue(container.sleepTimer.state.value.enabled)
            assertTrue("Custom minutes must be applied",
                container.sleepTimer.state.value.minutes in 1..59)
        } finally { container.close() }
    }
}
