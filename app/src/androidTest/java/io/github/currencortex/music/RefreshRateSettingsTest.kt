package io.github.currencortex.music

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.*
import io.github.currencortex.music.feature.settings.*
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState
import io.github.currencortex.music.feature.lyrics.desktop.*
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class RefreshRateSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var container: AppContainer
    private lateinit var drawingView: View

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName.endsWith(".verification"))
        container = AppContainer(app, "refresh-ui-${UUID.randomUUID()}", useNativeNetease = false)
        runBlocking { container.ready.await() }
    }
    @After fun finish() { container.close() }

    private fun assertRequest(rate: RefreshRatePreference) {
        compose.runOnIdle {
            assertEquals(rate.hz.toFloat(), compose.activity.window.attributes.preferredRefreshRate, .001f)
            val modeId = compose.activity.window.attributes.preferredDisplayModeId
            if (rate.hz == 0) assertEquals(0, modeId) else {
                val display = drawingView.display
                val mode = display.supportedModes.single { it.modeId == modeId }
                assertEquals(display.mode.physicalWidth, mode.physicalWidth)
                assertEquals(display.mode.physicalHeight, mode.physicalHeight)
            }
            if (Build.VERSION.SDK_INT >= 35) {
                assertEquals(rate.hz == 0, compose.activity.window.attributes.isFrameRatePowerSavingsBalanced)
            }
        }
    }

    @Test fun choicesApplyImmediatelyPersistAndSystemClearsFixedRequests() {
        val vm = SettingsViewModel(container.settings)
        compose.setContent {
            val config by container.settings.state.collectAsState()
            drawingView = LocalView.current
            AppRefreshRateRequest(config.refreshRate)
            LeiTheme(config) { Box(Modifier.fillMaxSize().musicRefreshRate(config.refreshRate)) { AppearanceScreen(vm, {}, {}) } }
        }
        for (rate in listOf(RefreshRatePreference.HZ120, RefreshRatePreference.HZ90, RefreshRatePreference.HZ60, RefreshRatePreference.SYSTEM)) {
            compose.onNodeWithTag("appearance_screen").performScrollToNode(hasTestTag("refresh_rate_${rate.hz}"))
            compose.onNodeWithTag("refresh_rate_${rate.hz}").performClick()
            compose.waitUntil(5000) { container.settings.state.value.refreshRate == rate }
            compose.onNodeWithTag("refresh_rate_${rate.hz}").assertIsSelected()
            assertEquals(rate, runBlocking { container.settings.snapshot() }.refreshRate)
            assertRequest(rate)
            if (rate == RefreshRatePreference.HZ120) {
                // Idle beyond touch boost; the fixed physical mode must still be requested.
                Thread.sleep(3000)
                assertRequest(rate)
                compose.onNodeWithTag("appearance_screen").performScrollToNode(hasTestTag("refresh_rate_actual"))
                val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
                java.io.File(app.getExternalFilesDir(null), "refresh-rate-120.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        compose.onNodeWithTag("refresh_rate_actual").assertTextContains("屏幕支持", substring = true)
    }

    @Test fun backgroundRestoresPolicyAndResumeKeepsSavedSelection() {
        runBlocking { container.settings.edit { it.copy(refreshRate = RefreshRatePreference.HZ120) } }
        compose.setContent {
            val config by container.settings.state.collectAsState()
            drawingView = LocalView.current
            AppRefreshRateRequest(config.refreshRate)
        }
        compose.waitUntil(5000) { container.settings.state.value.refreshRate == RefreshRatePreference.HZ120 }
        assertRequest(RefreshRatePreference.HZ120)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(0f, compose.activity.window.attributes.preferredRefreshRate, .001f)
            if (Build.VERSION.SDK_INT >= 35) assertTrue(compose.activity.window.attributes.isFrameRatePowerSavingsBalanced)
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertRequest(RefreshRatePreference.HZ120)
        assertEquals(RefreshRatePreference.HZ120, runBlocking { container.settings.snapshot() }.refreshRate)
    }

    @Test fun nativeRibbonReceivesRateAndReportsActualAnimationCadence() {
        val rate = mutableStateOf(RefreshRatePreference.HZ120)
        compose.setContent {
            drawingView = LocalView.current
            AppRefreshRateRequest(rate.value)
            CompositionLocalProvider(LocalRefreshRatePreference provides rate.value) {
                LeiTheme(AppearanceSettings()) { Box(Modifier.fillMaxSize().musicRefreshRate(rate.value)) {
                    PlaylistDepthRibbon((1L..72).map { Song(it, "Song $it", "Artist") }, false, {}, { {} }, {})
                } }
            }
        }
        fun View.ribbon(): DepthRibbonView? = if (this is DepthRibbonView) this else
            (this as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { group.getChildAt(it).ribbon() } }
        lateinit var native: DepthRibbonView
        val samples = StringBuilder("target_hz,display_hz,rendered_fps,compose_vote\n")
        compose.runOnIdle { native = requireNotNull(compose.activity.window.decorView.ribbon()) }
        for (target in listOf(RefreshRatePreference.HZ120, RefreshRatePreference.HZ60, RefreshRatePreference.SYSTEM)) {
            compose.runOnIdle { rate.value = target }
            assertRequest(target)
            var frames = 0L
            var started = 0L
            compose.runOnIdle {
                if (Build.VERSION.SDK_INT >= 35) assertEquals(if (target.hz == 0) View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT else target.hz.toFloat(), native.requestedFrameRate, .001f)
                frames = native.renderedFrames; started = System.nanoTime()
            }
            // Wall time samples the native renderer independently of Compose's test clock.
            Thread.sleep(2200)
            compose.runOnIdle {
                val fps = (native.renderedFrames - frames) * 1e9 / (System.nanoTime() - started)
                android.util.Log.i("RefreshRateTest", "target=${target.hz} display=${native.display.refreshRate} rendered=$fps composeVote=${drawingView.requestedFrameRate}")
                samples.append("${target.hz},${native.display.refreshRate},$fps,${drawingView.requestedFrameRate}\n")
                assertTrue("Native animation must keep rendering", fps > 20)
            }
        }
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        java.io.File(app.getExternalFilesDir(null), "refresh-rate-cadence.csv").writeText(samples.toString())
    }

    @Test fun desktopOverlayUpdatesItsOwnWindow(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 35 && android.provider.Settings.canDrawOverlays(context))
        val lyrics = MutableStateFlow(PlaybackLyricsState())
        val player = MutableStateFlow(PlayerState(song = Song(1, "Refresh fixture")))
        var host: DesktopLyricsHost? = null
        val originalWindows = withContext(Dispatchers.Main) { WindowInspector.getGlobalWindowViews().toSet() }
        try {
            withContext(Dispatchers.Main) {
                host = DesktopLyricsHost(context, lyrics, player, container.desktopLyrics, container.desktopLyricsStatus,
                    appearance = container.settings.state)
            }
            container.desktopLyrics.edit { it.copy(enabled = true, locked = true, appearance = DesktopAppearance.ISLAND) }
            for (rate in listOf(RefreshRatePreference.HZ120, RefreshRatePreference.HZ60, RefreshRatePreference.SYSTEM)) {
                container.settings.edit { it.copy(refreshRate = rate) }
                withTimeout(5000) {
                    while (!withContext(Dispatchers.Main) {
                        WindowInspector.getGlobalWindowViews().filter { it !in originalWindows }.any { root ->
                            val p = root.layoutParams as? WindowManager.LayoutParams
                            p?.type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY && p.width > 10 &&
                                p.preferredRefreshRate == rate.hz.toFloat() && p.isFrameRatePowerSavingsBalanced == (rate.hz == 0)
                        }
                    }) delay(50)
                }
            }
        } finally { withContext(Dispatchers.Main) { host?.close() } }
    }
}
