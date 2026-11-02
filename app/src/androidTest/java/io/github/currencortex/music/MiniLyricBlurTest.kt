package io.github.currencortex.music

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.player.MiniPlayerSongText
import io.github.currencortex.music.ui.theme.LeiTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MiniLyricBlurTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    @Test fun captionChangesInPlaceAndTrackSwitchDropsThePreviousAnimation() {
        val short = "星光"
        val long = "让远方的风带我穿过漫长的夜空"
        val caption = mutableStateOf(short)
        val track = mutableStateOf("song-1")
        lateinit var hostView: View
        compose.mainClock.autoAdvance = false
        compose.setContent {
            hostView = LocalView.current
            LeiTheme(AppearanceSettings()) {
                Box(Modifier.width(180.dp).background(Color.Black).testTag("caption_frame")) {
                    MiniPlayerSongText(
                        text = caption.value,
                        modifier = Modifier.fillMaxWidth().testTag("mini_lyric"),
                        subtitle = true,
                        color = Color.White,
                        fontSize = 11.sp,
                        lyricTrackKey = track.value,
                    )
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val supportsBlur = compose.runOnIdle { Build.VERSION.SDK_INT >= 31 && hostView.isHardwareAccelerated }

        val line = compose.onNodeWithTag("mini_lyric", useUnmergedTree = true)
        val restBounds = line.fetchSemanticsNode().boundsInRoot
        val frame = compose.onNodeWithTag("caption_frame", useUnmergedTree = true)
        fun assertCaption(expected: String) {
            val textNodes = hasAnyAncestor(hasTestTag("caption_frame")) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
            compose.onAllNodes(textNodes, useUnmergedTree = true).assertCountEquals(1)
            compose.onNode(textNodes, useUnmergedTree = true).assertTextEquals(expected)
            assertEquals("Changing lyric width must not move or resize the line", restBounds, line.fetchSemanticsNode().boundsInRoot)
        }
        fun change(text: String, trackKey: String = "song-1") {
            compose.runOnIdle { track.value = trackKey; caption.value = text }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertCaption(text)
        }
        fun capture(name: String? = null): Bitmap {
            val bitmap = frame.captureToImage().asAndroidBitmap()
            if (name != null) {
                val cache = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir)
                File(cache, "mini-lyric-$name.png").outputStream().use { out ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                }
            }
            return bitmap
        }

        assertCaption(short)
        val initial = capture("start")
        change(long)
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        assertCaption(long)
        val middle = capture("middle")
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        assertCaption(long)
        val end = capture("end")
        assertFalse("The two lyric strings must render differently", initial.sameAs(end))
        assertFalse("The mid-frame must differ from the previous line", middle.sameAs(initial))
        assertFalse("The mid-frame must differ from the settled new line", middle.sameAs(end))
        fun assertBlurHalo(bitmap: Bitmap) {
            if (!supportsBlur) return
            // An opacity-only blend of these white labels leaves their shared black background black.
            val halo = (0 until bitmap.height).any { y ->
                (0 until bitmap.width).any { x ->
                    (bitmap.getPixel(x, y) and 255) > 16 &&
                        (initial.getPixel(x, y) and 255) == 0 && (end.getPixel(x, y) and 255) == 0
                }
            }
            assertTrue("Blur must spread light beyond the outlines of both settled captions", halo)
        }
        assertBlurHalo(middle)

        change("山川海洋")
        compose.mainClock.advanceTimeBy(48)
        change(short)
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        val interrupted = capture()
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val later = capture()
        assertFalse("A third cue must keep animating after interrupting the second", interrupted.sameAs(later))
        assertFalse("The interrupted transition must have a frame before the final line", later.sameAs(initial))
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        assertTrue("Rapid cue changes must settle on the latest line", initial.sameAs(capture()))

        change(long)
        compose.mainClock.advanceTimeBy(96)
        change(short)
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val reversing = capture()
        assertFalse("Returning to the previous cue must also animate", reversing.sameAs(initial))
        assertBlurHalo(reversing)
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        assertTrue("The reverse transition must settle back on the previous cue", initial.sameAs(capture()))

        change("另一段尚未结束的歌词")
        compose.mainClock.advanceTimeBy(48)
        change(long, trackKey = "song-2")
        assertTrue("A new track must immediately display its first line without old glyphs", end.sameAs(capture("new-track")))
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        assertCaption(long)
        assertTrue("An interrupted animation must not overwrite the new track", end.sameAs(capture()))
    }
}
