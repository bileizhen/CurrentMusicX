package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.SoundEffectNoticeTier
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.lyrics.desktop.SoundEffectIslandFallback
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class SoundEffectIslandPageVisibilityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun inAppNoticeFadesOutAndRestoresWhenPageChanges(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(context.packageName.endsWith(".verification"))
        val container = AppContainer(context, "notice-pages-${UUID.randomUUID()}", useNativeNetease = false)
        try {
            container.ready.await()
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { container.soundEffectNotices.begin("全景环绕", SoundEffectNoticeTier.VIP) }
            compose.setContent { LeiTheme(AppearanceSettings(blur = false)) { SoundEffectIslandFallback(container) } }
            compose.mainClock.advanceTimeBy(1000)
            compose.onNodeWithTag("sound_effect_island_notice").assertIsDisplayed()
            compose.runOnIdle { container.desktopIslandPageHidden.value = true }
            compose.mainClock.advanceTimeBy(60)
            compose.onNodeWithTag("sound_effect_island_notice").assertExists()
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag("sound_effect_island_notice").assertDoesNotExist()
            compose.runOnIdle { container.desktopIslandPageHidden.value = false }
            compose.mainClock.advanceTimeBy(600)
            compose.onNodeWithTag("sound_effect_island_notice").assertIsDisplayed()
        } finally { container.close() }
    }
}
