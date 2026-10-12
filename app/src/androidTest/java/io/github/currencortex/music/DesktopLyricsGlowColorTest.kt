package io.github.currencortex.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.ui.KaraokeText
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DesktopLyricsGlowColorTest {
    @get:Rule val compose = createComposeRule()
    private fun capture() = compose.onNodeWithTag("colored_glow").captureToImage().toPixelMap()
    private fun greenHalo(p: PixelMap): Int {
        var count = 0
        for (y in 0 until p.height) for (x in 0 until p.width)
            if (p[x,y].green > .02f && p[x,y].green > p[x,y].red + .01f) count++
        return count
    }
    @Test fun tintStrengthSpreadAndDisabledGlowRemainIndependentOfTextInk() {
        val enabled = mutableStateOf(true)
        val strength = mutableFloatStateOf(.5f)
        val radius = mutableFloatStateOf(.5f)
        val line = LyricLine(0, 4000, "O", listOf(LyricWord("O", 0, 4000, 0, 1)), roles = listOf("chorus"))
        val clock = mutableLongStateOf(2200)
        compose.setContent {
            Box(Modifier.size(240.dp, 140.dp).background(Color.Black).testTag("colored_glow")) {
                KaraokeText(line, clock, true, Modifier.padding(24.dp), fontSize = 40f,
                    color = Color.Red, glowColor = Color.Green, glow = enabled.value,
                    glowOpacity = strength, glowRadiusScale = radius.value)
            }
        }
        val small = capture(); assertTrue(greenHalo(small) > 20)
        compose.runOnIdle { strength.floatValue = 2f; radius.floatValue = 2f }
        val large = capture(); assertTrue("Wider, stronger bloom must cover more pixels", greenHalo(large) > greenHalo(small))
        var redInk = 0
        for (y in 0 until large.height) for (x in 0 until large.width)
            if (large[x,y].red > .8f && large[x,y].green < .1f) redInk++
        assertTrue("Green halo must not replace the red text", redInk > 30)
        compose.runOnIdle { enabled.value = false }
        assertEquals(0, greenHalo(capture()))
        compose.runOnIdle { enabled.value = true; strength.floatValue = 0f }
        assertEquals(0, greenHalo(capture()))
    }
}
