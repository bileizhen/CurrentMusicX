package io.github.currencortex.music

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsHost
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

class DesktopIslandPageVisibilityTest {
    @Test fun openingUpdatePageFromExistingActivityPublishesVisibilityGate(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(context.packageName.endsWith(".verification"))
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
            context.container.ready.await()
            scenario.onActivity { context.container.updates.reopenDownload() }
            withTimeout(10000) { while (!context.container.desktopIslandPageHidden.value) delay(25) }
            assertTrue(context.container.appForeground.value)
            scenario.onActivity { context.container.updates.dismissDialog() }
            withTimeout(10000) { while (context.container.desktopIslandPageHidden.value) delay(25) }
        } finally { scenario.onActivity { context.container.updates.dismissDialog() }; scenario.close() }
    }

    @Test fun immersivePagesHideBothWindowsAndRestoreOnExitOrBackground(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(context.packageName.endsWith(".verification"))
        assumeTrue("Overlay permission required", Settings.canDrawOverlays(context))
        val container = AppContainer(context, "island-pages-${UUID.randomUUID()}", useNativeNetease = false)
        val foreground = MutableStateFlow(true)
        val hidden = MutableStateFlow(false)
        var host: DesktopLyricsHost? = null
        suspend fun await(condition: () -> Boolean) = withTimeout(10000) { while (!condition()) delay(25) }
        try {
            container.ready.await(); container.desktopLyrics.ready.await()
            container.desktopLyrics.edit { it.copy(enabled = true, locked = true) }
            withContext(Dispatchers.Main) {
                host = DesktopLyricsHost(context, MutableStateFlow(PlaybackLyricsState()), MutableStateFlow(PlayerState()),
                    container.desktopLyrics, container.desktopLyricsStatus, appForeground = foreground, pageHidden = hidden)
            }
            await { container.desktopLyricsStatus.value.visible && container.desktopLyricsStatus.value.islandTouchable }
            repeat(3) {
                hidden.value = true
                await { !container.desktopLyricsStatus.value.visible && !container.desktopLyricsStatus.value.islandTouchable }
                assertTrue("Keep the visual window attached for restoration", container.desktopLyricsStatus.value.attached)
                assertTrue("Never change the user's enabled setting", container.desktopLyrics.state.value.enabled)
                hidden.value = false
                await { container.desktopLyricsStatus.value.visible && container.desktopLyricsStatus.value.islandTouchable }
            }
            hidden.value = true
            await { !container.desktopLyricsStatus.value.visible }
            foreground.value = false
            await { container.desktopLyricsStatus.value.visible && container.desktopLyricsStatus.value.islandTouchable }
            foreground.value = true
            await { !container.desktopLyricsStatus.value.visible && !container.desktopLyricsStatus.value.islandTouchable }
            hidden.value = false
            await { container.desktopLyricsStatus.value.visible && container.desktopLyricsStatus.value.islandTouchable }
        } finally { withContext(Dispatchers.Main) { host?.close() }; container.close() }
    }
}
