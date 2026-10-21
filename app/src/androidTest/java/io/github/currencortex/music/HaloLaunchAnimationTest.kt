package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.data.auth.Account
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.settings.LaunchStyleDialog
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HaloLaunchAnimationTest {
    private var reduced = false
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor get() = if (reduced) 0f else 1f
    })

    private fun awaitArtwork() = compose.waitUntil(10000) {
        if (!compose.mainClock.autoAdvance) compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithTag("halo_artwork_ready", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun capture(name: String) {
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        java.io.File(app.externalCacheDir, name).outputStream().use {
            compose.onNodeWithTag("halo_launch_frame", useUnmergedTree = true).captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun ringTitleAndUsernameRevealThenExitWithoutPassingTouchesThrough() {
        compose.mainClock.autoAdvance = false
        var done by mutableStateOf(false)
        var clicks = 0
        compose.setContent { Box(Modifier.fillMaxSize().background(Color.White).clickable { clicks++ }) {
            if (!done) HaloLaunchOverlay("bileizhen", ready = true) { done = true }
        } }
        awaitArtwork()
        compose.mainClock.advanceTimeBy(1500)
        capture("halo-ring.png")
        compose.onNodeWithTag("launch_animation", useUnmergedTree = true).performTouchInput { click(center) }
        assertEquals(0, clicks)
        compose.mainClock.advanceTimeBy(3300)
        capture("halo-title.png")
        compose.onNodeWithTag("halo_launch_frame", useUnmergedTree = true).assertContentDescriptionEquals("CurrentMusic · bileizhen")
        compose.mainClock.advanceTimeBy(4500)
        compose.onNodeWithTag("launch_animation", useUnmergedTree = true).assertDoesNotExist()
        assertTrue(done)
    }

    @Test fun nativeSplashGateDelaysTheTimeline() {
        compose.mainClock.autoAdvance = false
        var windowReady by mutableStateOf(false)
        var done by mutableStateOf(false)
        compose.setContent { if (!done) HaloLaunchOverlay("测试用户", true, windowReady) { done = true } }
        awaitArtwork()
        compose.mainClock.advanceTimeBy(200)
        val bitmap = compose.onNodeWithTag("halo_launch_frame").captureToImage().asAndroidBitmap()
        assertEquals(android.graphics.Color.BLACK, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        windowReady = true
        compose.mainClock.advanceTimeBy(4800)
        assertFalse(done)
        compose.mainClock.advanceTimeBy(4500)
        assertTrue(done)
    }

    @Test fun reducedMotionUsesStaticFrameAndWaitsForReadiness() {
        reduced = true
        var ready by mutableStateOf(false)
        var done by mutableStateOf(false)
        compose.setContent { if (!done) HaloLaunchOverlay("静态开屏", ready) { done = true } }
        compose.onNodeWithTag("halo_launch_frame").assertExists()
        assertFalse(done)
        ready = true
        compose.waitUntil(5000) { done }
    }

    @Test fun usernameFallsBackWithoutLeakingAnotherAccount() {
        assertEquals("account-name", launchUsername(Account(1, "昵称", "account-name")))
        assertEquals("昵称", launchUsername(Account(2, "昵称", " ")))
        assertEquals("欢迎聆听", launchUsername(null))
    }

    @Test fun longUnicodeNamesAndSmallLandscapeFitTheFrame() {
        var username by mutableStateOf("很长的用户名🎵CurrentMusic测试🎧非常非常长")
        compose.setContent { Box(Modifier.size(640.dp, 280.dp)) { HaloLaunchFrame(username, { 4800f }) } }
        awaitArtwork()
        capture("halo-landscape-name.png")
        username = "新账号"
        compose.onNodeWithTag("halo_launch_frame").assertContentDescriptionEquals("CurrentMusic · 新账号")
    }

    @Test fun captureReferenceMilestones() {
        var frame by mutableFloatStateOf(0f)
        compose.setContent { HaloLaunchFrame("bileizhen", { frame }) }
        awaitArtwork()
        for (ms in listOf(400, 800, 1000, 1400, 1800, 2200, 2467, 2900, 3267, 3400, 3700, 4300, 6200, 7800)) {
            frame = ms.toFloat()
            compose.waitForIdle()
            capture("halo-frame-$ms.png")
        }
    }

    @Test fun choosingStyleClosesDialogAndKeepsClassicAvailable() {
        var selected by mutableStateOf(LaunchStyle.CLASSIC)
        var show by mutableStateOf(true)
        compose.setContent { LeiTheme(AppearanceSettings()) { top.yukonga.miuix.kmp.basic.Scaffold {
            if (show) LaunchStyleDialog(selected, { selected = it }, { show = false })
        } } }
        compose.onNodeWithTag("launch_style_classic").assertIsSelected()
        compose.onNodeWithTag("launch_style_halo").performClick()
        compose.waitForIdle()
        assertEquals(LaunchStyle.HALO, selected)
        compose.onNodeWithTag("launch_style_halo").assertDoesNotExist()
        show = true
        compose.onNodeWithTag("launch_style_classic").performClick()
        compose.waitForIdle()
        assertEquals(LaunchStyle.CLASSIC, selected)
    }
}
