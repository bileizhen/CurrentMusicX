package io.github.currencortex.music

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.IslandNoticeKind
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit visual feedback probe: never changes desktop preferences, audio, room or download jobs. */
class InAppIslandPolicyLiveTest {
    @Test fun foregroundHidesTheRegularIslandAndErrorsAppearThenRetract() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("islandPolicy") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val container = app.container
        container.ready.await(); container.desktopLyrics.ready.await()
        val original = container.desktopLyrics.state.value
        assumeTrue("An existing overlay is required for this window-level probe", original.enabled)
        assumeTrue("Preserve any real download/effect already in progress", container.islandNotices.value == null)
        val foreground = ActivityScenario.launch(MainActivity::class.java)
        suspend fun until(condition: () -> Boolean) = withTimeout(10_000) { while (!condition()) delay(25) }
        var id: Long? = null
        try {
            until { container.appForeground.value && container.desktopLyricsStatus.value.attached &&
                !container.desktopLyricsStatus.value.visible && !container.desktopLyricsStatus.value.visibilityAnimating }
            id = container.errorNotices.show("错误提示显示验证")
            until { container.islandNotices.value?.kind == IslandNoticeKind.ERROR &&
                container.desktopLyricsStatus.value.visible && !container.desktopLyricsStatus.value.visibilityAnimating }
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            assertNotNull(screenshot)
            try { File(app.getExternalFilesDir(null), "island-error-live.png").outputStream().use {
                screenshot!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            } } finally { screenshot?.recycle() }
            container.errorNotices.clear(id)
            until { container.islandNotices.value == null && !container.desktopLyricsStatus.value.visible &&
                !container.desktopLyricsStatus.value.visibilityAnimating }
            assertEquals(original, container.desktopLyrics.state.value)
            File(app.getExternalFilesDir(null), "island-policy-live.txt").writeText(
                "foregroundRegularHidden=true\nerrorOverlayVisible=true\nerrorRetracted=true\npreferencesUnchanged=true\n")
        } finally { container.errorNotices.clear(id); foreground.close() }
    }
}
