package io.github.currencortex.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.feature.update.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Android font outlines/rasterization, including counter and width changes. */
class VersionNumberMorphTest {
    @get:Rule val compose = createComposeRule()

    @Test fun countersAndNewDigitsHaveRealIntermediateGeometry() {
        val glyphs = VersionGlyphs()
        assertEquals(2, glyphs.outline("0").digits.single().contours.size)
        assertEquals(3, glyphs.outline("8").digits.single().contours.size)
        for ((a, b) in listOf("1" to "8", "8" to "1", "1.1.9" to "1.1.10", "16" to "17")) {
            val from = glyphs.outline(a); val to = glyphs.outline(b)
            val first = raster(from, to, 0f)
            val middle = raster(from, to, .5f)
            val last = raster(from, to, 1f)
            assertFalse("$a → $b has a distinct middle outline", middle.sameAs(first))
            assertFalse("$a → $b has a distinct middle outline", middle.sameAs(last))
            assertTrue(first.sameAs(raster(from, from, 1f)))
            assertTrue(last.sameAs(raster(to, to, 1f)))
            first.recycle(); middle.recycle(); last.recycle()
        }
    }

    @Test fun interruptedMorphStartsFromTheVisibleOutline() {
        val glyphs = VersionGlyphs()
        val from = glyphs.outline("1.1.9"); val target = glyphs.outline("1.1.10")
        val visible = interpolateVersionOutline(from, target, .43f)
        val before = raster(from, target, .43f)
        val after = raster(visible, glyphs.outline("2.0.8"), 0f)
        assertTrue("Retargeting must preserve the exact visible geometry", before.sameAs(after))
        before.recycle(); after.recycle()
    }

    @Test fun disappearingCountersCannotCreateDetachedFilledIslands() {
        val glyphs = VersionGlyphs()
        val from = glyphs.outline("6")
        val target = glyphs.outline("7")
        for (step in 1..9) {
            val bitmap = raster(from, target, step / 10f)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val seen = BooleanArray(pixels.size)
            var components = 0
            for (index in pixels.indices) {
                if (seen[index] || AndroidColor.alpha(pixels[index]) < 128) continue
                val pending = java.util.ArrayDeque<Int>()
                pending.add(index); seen[index] = true
                var count = 0
                while (pending.isNotEmpty()) {
                    val current = pending.removeFirst(); count++
                    val x = current % bitmap.width; val y = current / bitmap.width
                    for (dy in -1..1) for (dx in -1..1) {
                        val nx = x + dx; val ny = y + dy
                        if (nx !in 0 until bitmap.width || ny !in 0 until bitmap.height) continue
                        val next = ny * bitmap.width + nx
                        if (!seen[next] && AndroidColor.alpha(pixels[next]) >= 128) {
                            seen[next] = true; pending.add(next)
                        }
                    }
                }
                if (count > 3) components++
            }
            assertEquals("6 → 7 at $step/10 must remain one filled shape", 1, components)
            bitmap.recycle()
        }
    }

    @Test fun completedDigitKeepsItsCounterTransparent() {
        val glyph = VersionGlyphs().outline("0")
        val bitmap = raster(glyph, glyph, 1f)
        assertEquals("The center of zero must remain a hole", 0, AndroidColor.alpha(bitmap.getPixel(320, 128)))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("The digit body is visible", pixels.count { AndroidColor.alpha(it) >= 128 } > 100)
        bitmap.recycle()
    }

    @Test fun composeMorphSurvivesRapidRetargetAndExportsAnimationFrames() {
        val version = mutableStateOf("16")
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF100D29)), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MorphingVersionNumber(version.value, "16", 150.sp, Color(0xFFF7F4FF),
                        Modifier.testTag("morph_version"))
                }
            }
        }
        compose.mainClock.autoAdvance = false
        assertEquals(0f, compose.onNodeWithTag("morph_version").fetchSemanticsNode().config[VersionMorphBlurKey], .01f)
        capture("morph-00.png")
        compose.runOnIdle { version.value = "17" }
        compose.mainClock.advanceTimeByFrame()
        for (index in 1..16) {
            compose.mainClock.advanceTimeBy(64)
            if (index == 4) assertTrue("The changing outline should soften near the middle",
                compose.onNodeWithTag("morph_version").fetchSemanticsNode().config[VersionMorphBlurKey] > 2f)
            capture("morph-${index.toString().padStart(2, '0')}.png")
        }
        compose.onNodeWithTag("morph_version").assertTextEquals("17")
        assertEquals("A settled version must be sharp", 0f,
            compose.onNodeWithTag("morph_version").fetchSemanticsNode().config[VersionMorphBlurKey], .01f)
        compose.runOnIdle { version.value = "18" }
        compose.mainClock.advanceTimeBy(320)
        compose.runOnIdle { version.value = "19" }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("Retargeting must retain the visible blur",
            compose.onNodeWithTag("morph_version").fetchSemanticsNode().config[VersionMorphBlurKey] > 1f)
        compose.mainClock.advanceTimeBy(128)
        compose.runOnIdle { version.value = "16" }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithTag("morph_version").assertTextEquals("16")
        assertEquals(0f, compose.onNodeWithTag("morph_version").fetchSemanticsNode().config[VersionMorphBlurKey], .01f)
    }

    private fun raster(from: VersionOutline, to: VersionOutline, progress: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(320f, 180f)
        canvas.scale(1.5f, 1.5f)
        val path = Path()
        versionOutlinePath(from, to, progress, path)
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE })
        return bitmap
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val bitmap = compose.onNodeWithTag("morph_version").captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
