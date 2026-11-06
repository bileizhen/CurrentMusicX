package io.github.currencortex.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.core.media.DockAudioLevel
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.PlayerBackdrop
import io.github.currencortex.music.feature.player.rememberPlayerFluidSpeed
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class PlayerFluidMotionTest {
    @get:Rule val compose = createComposeRule()
    private fun requireShader() = assumeTrue(android.os.Build.VERSION.SDK_INT >= 33 &&
        top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported())

    @Test fun liveSamplesChangeSpeedAndDisposingPlayerPreservesOtherConsumers() {
        requireShader()
        val meter = DockAudioLevel()
        val dock = meter.acquire()
        var playing by mutableStateOf(true)
        var visible by mutableStateOf(true)
        lateinit var speed: State<Float>
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent { LeiTheme(AppearanceSettings()) {
                speed = rememberPlayerFluidSpeed(meter, playing, visible, Song(1, "Live audio"))
                Box(Modifier.size(100.dp))
            } }
            compose.mainClock.advanceTimeBy(32)
            fun feed(bpm: Float) {
                repeat(15) {
                    compose.runOnIdle { meter.publish(.5f, .3f, bpm = bpm) }
                    compose.mainClock.advanceTimeBy(80)
                }
            }
            feed(75f); val slow = speed.value
            feed(150f); val fast = speed.value
            assertTrue("Live tempo must change speed without changing songs", fast > slow + .3f)
            feed(50f); assertTrue(speed.value < fast - .3f)
            val beforePause = speed.value
            compose.runOnIdle { playing = false }
            compose.mainClock.advanceTimeBy(32)
            assertTrue("Pause must decelerate instead of snapping", speed.value > .12f)
            assertTrue(speed.value <= beforePause)
            compose.mainClock.advanceTimeBy(3000)
            assertEquals(.12f, speed.value, .02f)
            assertTrue("Pausing player must not disable the dock", meter.enabled)
            compose.runOnIdle { visible = false }
            compose.mainClock.advanceTimeBy(32)
            dock.close()
            assertFalse(meter.enabled)
            assertEquals(0L, meter.sample.timestampNanos)
        } finally { dock.close(); compose.mainClock.autoAdvance = true }
    }

    @Test fun shaderReadsLiveSpeedWithoutRecompositionAndKeepsPausedMotionSlow() {
        requireShader()
        val speed = mutableFloatStateOf(0f)
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent { LeiTheme(AppearanceSettings()) {
                PlayerBackdrop("", Modifier.size(260.dp, 360.dp).testTag("fluid_render"),
                    motionSpeed = { speed.floatValue })
            } }
            fun image() = compose.onNodeWithTag("fluid_render").captureToImage().asAndroidBitmap()
            compose.mainClock.advanceTimeBy(1200)
            val stopped = image()
            compose.mainClock.advanceTimeBy(600)
            assertTrue(stopped.sameAs(image()))
            compose.runOnIdle { speed.floatValue = 2f }
            compose.mainClock.advanceTimeBy(600)
            val moving = image()
            assertFalse("The draw loop must read the updated speed", stopped.sameAs(moving))
            compose.runOnIdle { speed.floatValue = .12f }
            compose.mainClock.advanceTimeBy(32)
            val slow = image()
            compose.mainClock.advanceTimeBy(1200)
            assertFalse("Paused speed must keep the background gently moving", slow.sameAs(image()))
            compose.runOnIdle { speed.floatValue = 0f }
            compose.mainClock.advanceTimeBy(32)
            val paused = image()
            compose.mainClock.advanceTimeBy(600)
            assertTrue(paused.sameAs(image()))
        } finally { compose.mainClock.autoAdvance = true }
    }
}
