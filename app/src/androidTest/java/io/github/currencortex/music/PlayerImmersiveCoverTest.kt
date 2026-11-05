package io.github.currencortex.music

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.feature.player.PlayerImmersiveCover
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Changing the checkerboard proves the fade reveals the live layer beneath the artwork. */
class PlayerImmersiveCoverTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val testName = TestName()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private var coverFile: File? = null
    private var alternateBackground by mutableStateOf(false)
    private var compact by mutableStateOf(false)
    private var immersion by mutableFloatStateOf(1f)
    private var viewport by mutableStateOf(Size.Zero)
    private var morphBounds by mutableStateOf<Rect?>(null)
    private var artworkAlpha by mutableFloatStateOf(1f)
    private var roundedMorph by mutableStateOf(false)

    @After fun cleanup() { coverFile?.delete() }

    private fun coverBounds(): Rect {
        morphBounds?.let { return it }
        if (!compact) return Rect(Offset.Zero, viewport)
        val side = viewport.minDimension * .45f
        return Rect((viewport.width - side) / 2, (viewport.height - side) / 2,
            (viewport.width + side) / 2, (viewport.height + side) / 2)
    }

    private fun show(wide: Boolean, verticalStripes: Boolean = false,
        contentBoundary: () -> Float? = { null }) {
        val image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFFFF6020.toInt())
        }
        val file = File(context.cacheDir, "immersive-alpha-${UUID.randomUUID()}.png")
        file.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
        coverFile = file
        compose.setContent {
            Box(Modifier.size(if (wide) 360.dp else 200.dp, if (wide) 200.dp else 320.dp)
                .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
                .testTag("immersive_cover_fixture")) {
                Canvas(Modifier.matchParentSize()) {
                    val rows = if (verticalStripes) 1 else 8
                    val cell = Size(size.width / 8, size.height / rows)
                    for (row in 0 until rows) for (column in 0..7) {
                        val cyan = (row + column) % 2 == if (alternateBackground) 1 else 0
                        drawRect(if (cyan) Color.Cyan else Color.Magenta,
                            Offset(column * cell.width, row * cell.height), cell)
                    }
                }
                PlayerImmersiveCover(file.toURI().toString(), Modifier.fillMaxSize(),
                    bounds = ::coverBounds, radius = { if (roundedMorph) 32.5f else if (compact) 6f else 0f },
                    immersion = { immersion }, alpha = { artworkAlpha },
                    wideViewport = wide, contentBoundary = contentBoundary)
            }
        }
        compose.waitUntil(10000) {
            capture().let { bitmap ->
                try { isArtwork(pixel(bitmap, if (wide) .10f else .5f, if (wide) .5f else .2f)) }
                finally { bitmap.recycle() }
            }
        }
    }

    private fun capture(): Bitmap = compose.onNodeWithTag("immersive_cover_fixture").captureToImage().asAndroidBitmap()

    private fun pixel(bitmap: Bitmap, x: Float, y: Float) = bitmap.getPixel(
        (bitmap.width * x).toInt().coerceIn(0, bitmap.width - 1),
        (bitmap.height * y).toInt().coerceIn(0, bitmap.height - 1))

    private fun isArtwork(color: Int) = AndroidColor.red(color) > 210 &&
        AndroidColor.green(color) in 55..125 && AndroidColor.blue(color) < 55

    private fun difference(first: Int, second: Int): Int = max(
        abs(AndroidColor.red(first) - AndroidColor.red(second)), max(
            abs(AndroidColor.green(first) - AndroidColor.green(second)),
            abs(AndroidColor.blue(first) - AndroidColor.blue(second))))

    private fun sampleDescription(first: Bitmap, second: Bitmap, x: Float, y: Float): String {
        val a = pixel(first, x, y)
        val b = pixel(second, x, y)
        return "at ($x,$y), px=(${(first.width * x).toInt()},${(first.height * y).toInt()}), " +
            "A=#${Integer.toHexString(a)}, B=#${Integer.toHexString(b)}, diff=${difference(a, b)}"
    }

    private fun twoBackgrounds(check: (Bitmap, Bitmap) -> Unit) {
        compose.runOnIdle { alternateBackground = false }
        val first = capture()
        compose.runOnIdle { alternateBackground = true }
        val second = capture()
        try {
            check(first, second)
        } catch (failure: AssertionError) {
            val directory = requireNotNull(context.externalCacheDir)
            val prefix = "immersive-${testName.methodName}-$immersion"
            File(directory, "$prefix-a.png").outputStream().use { first.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(directory, "$prefix-b.png").outputStream().use { second.compress(Bitmap.CompressFormat.PNG, 100, it) }
            throw AssertionError("${failure.message}\nArtifacts: ${File(directory, prefix).absolutePath}-[a/b].png", failure)
        } finally {
            first.recycle(); second.recycle()
        }
    }

    @Test fun portraitBottomRevealsChangingBackgroundWhileUpperArtworkStaysOpaque() {
        show(wide = false)
        twoBackgrounds { first, second ->
            val upper = pixel(first, .5f, .2f)
            assertTrue("The main artwork must remain clear", isArtwork(upper))
            assertTrue("Background changes must not leak through the preserved cover", difference(upper, pixel(second, .5f, .2f)) < 8)
            // Sample inside a checker cell, not on its anti-aliased column seam.
            assertTrue("The lower edge must reveal the real changing background: ${sampleDescription(first, second, .43f, .985f)}",
                difference(pixel(first, .43f, .985f), pixel(second, .43f, .985f)) > 225)
            val intermediate = (20..95).map { step ->
                difference(pixel(first, .43f, step / 100f), pixel(second, .43f, step / 100f))
            }
            assertTrue("A real transition region must partially transmit the background", intermediate.count { it in 20..210 } >= 5)
        }
    }

    @Test fun landscapeRightRevealsChangingBackgroundWhileLeftArtworkStaysOpaque() {
        show(wide = true)
        twoBackgrounds { first, second ->
            val left = pixel(first, .1f, .5f)
            assertTrue("The left artwork must remain clear", isArtwork(left))
            assertTrue("The preserved artwork must not change with the background", difference(left, pixel(second, .1f, .5f)) < 8)
            for (x in listOf(.55f, .7f, .96f)) for (y in listOf(.2f, .55f, .8f)) {
                assertTrue("The whole lyrics/control pane must show the background: ${sampleDescription(first, second, x, y)}",
                    difference(pixel(first, x, y), pixel(second, x, y)) > 225)
            }
            val intermediate = (15..90).map { step ->
                difference(pixel(first, step / 100f, .43f), pixel(second, step / 100f, .43f))
            }
            assertTrue("The horizontal transition must partially transmit the background", intermediate.count { it in 20..210 } >= 5)
        }
    }

    @Test fun collapsingToASmallCoverRestoresItsEdgesWithoutErasingTheBackground() {
        show(wide = false, contentBoundary = { .52f })
        compose.runOnIdle { compact = true; immersion = 0f }
        twoBackgrounds { first, second ->
            val bounds = coverBounds()
            for (u in listOf(.12f, .5f, .88f)) for (v in listOf(.12f, .5f, .88f)) {
                val x = (bounds.left + bounds.width * u) / first.width
                val y = (bounds.top + bounds.height * v) / first.height
                assertTrue("The compact cover must retain its full artwork near every edge", isArtwork(pixel(first, x, y)))
                assertTrue("The compact cover must no longer carry the immersive alpha mask", difference(pixel(first, x, y), pixel(second, x, y)) < 8)
            }
            for ((x, y) in listOf(.1f to .1f, .9f to .1f, .1f to .9f, .9f to .9f)) {
                assertTrue("Artwork masking must not erase the surrounding background", difference(pixel(first, x, y), pixel(second, x, y)) > 225)
            }
        }
    }

    @Test fun portraitTitleBoundaryAndEverythingBelowItStayClearWhenTheTitleMovesUp() {
        var boundary by mutableFloatStateOf(.52f)
        show(wide = false, verticalStripes = true, contentBoundary = { boundary })
        for (titleTop in listOf(.52f, .38f)) {
            compose.runOnIdle { boundary = titleTop }
            twoBackgrounds { first, second ->
                val upper = pixel(first, .43f, .1f)
                assertTrue("Moving the title boundary must preserve the upper cover", isArtwork(upper))
                assertTrue("The preserved cover remains opaque", difference(upper, pixel(second, .43f, .1f)) < 8)
                val firstContentRow = ceil(first.height * titleTop).toInt()
                // Use the supplied content boundary as a public layout contract, not the
                // renderer's internal fade formula. Check the first safe row and the full area below.
                val rows = (firstContentRow until first.height step maxOf(1, first.height / 31)).toList() + (first.height - 1)
                for (x in listOf(.18f, .43f, .68f, .93f)) for (y in rows) {
                    val column = (first.width * x).toInt()
                    val actual = difference(first.getPixel(column, y), second.getPixel(column, y))
                    assertTrue("Title/content region must be fully clear at boundary=$titleTop, pixel=($column,$y), diff=$actual",
                        actual > 245)
                }
                val column = (first.width * .43f).toInt()
                val partialRows = (0 until firstContentRow).count { y ->
                    difference(first.getPixel(column, y), second.getPixel(column, y)) in 20..210
                }
                assertTrue("Repositioning the content boundary must retain a gradual transition", partialRows >= 6)
            }
        }
    }

    @Test fun intermediateMorphKeepsAContinuousFadeWithATransparentOuterEdge() {
        // A vertical scan stays within one stripe, so background row boundaries cannot
        // create a false jump. Swapping cyan/magenta gives a 255-channel difference beneath it.
        show(wide = false, verticalStripes = true)
        for (amount in listOf(.5f, .25f)) {
            compose.runOnIdle { immersion = amount }
            twoBackgrounds { first, second ->
                val x = (first.width * .43f).toInt()
                val transmission = (0 until first.height).map { y ->
                    difference(first.getPixel(x, y), second.getPixel(x, y))
                }
                assertTrue("The preserved cover must stay opaque during the morph at $amount",
                    transmission[first.height / 5] < 8)
                assertTrue("The outer edge must already expose the background during the morph at $amount",
                    transmission.last() > 225)
                assertTrue("Intermediate morphs need a visible partial-transparency region at $amount",
                    transmission.count { it in 20..210 } >= 6)
                assertTrue("The cropped artwork must not end in a hard alpha step at $amount",
                    transmission.zipWithNext { before, after -> abs(after - before) }.max() < 32)
                assertTrue("The fade must not become opaque again below the cropped artwork at $amount",
                    transmission.zipWithNext().all { (before, after) -> after >= before - 4 })
            }
        }
    }

    @Test fun roundedPortraitMorphLeavesNoOutlineBelowTheFade() {
        show(wide = false, contentBoundary = { .35f })
        compose.runOnIdle {
            roundedMorph = true
            // Moving artwork has fractional pixel bounds and rounded lower corners.
            morphBounds = Rect(24.25f, 20.75f, viewport.width - 24.5f, viewport.height - 20.25f)
        }
        assertClearRoundedTail(wide = false)
    }

    @Test fun roundedLandscapeMorphLeavesNoOutlineBeyondTheFade() {
        show(wide = true)
        compose.runOnIdle { roundedMorph = true }
        assertClearRoundedTail(wide = true)
    }

    private fun assertClearRoundedTail(wide: Boolean) {
        for (amount in listOf(1f, .5f, .25f)) for (opacity in listOf(1f, .6f)) {
            compose.runOnIdle { immersion = amount; artworkAlpha = 0f }
            val background = capture()
            compose.runOnIdle { artworkAlpha = opacity }
            val actual = capture()
            try {
                val bounds = if (wide) Rect(Offset.Zero, viewport) else coverBounds()
                // The last 8% is beyond the fade for all these morph states. Scan
                // every pixel, including AA edges/corners and fractional bounds.
                val startX = if (wide) (bounds.left + bounds.width * .92f).toInt() else 0
                val startY = if (wide) 0 else (bounds.top + bounds.height * .92f).toInt()
                var worst = 0
                var location = ""
                for (y in startY until actual.height) for (x in startX until actual.width) {
                    val delta = difference(actual.getPixel(x, y), background.getPixel(x, y))
                    if (delta > worst) { worst = delta; location = "($x,$y)" }
                }
                assertTrue("Transparent rounded tail must match the backdrop: wide=$wide, " +
                    "immersion=$amount, alpha=$opacity, max difference=$worst at $location", worst <= 2)
            } finally {
                background.recycle(); actual.recycle()
            }
        }
    }
}
