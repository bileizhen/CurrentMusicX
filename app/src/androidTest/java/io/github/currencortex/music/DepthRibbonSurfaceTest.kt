package io.github.currencortex.music

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import io.github.currencortex.music.feature.library.drawDepthRibbonFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.geometry.Rect
import io.github.currencortex.music.feature.library.RibbonRecordFlight
import org.junit.Test

class DepthRibbonSurfaceTest {
    @Test fun recordLeavesSleeveAndLandsExactlyOnTheMiniCover() {
        val source = Rect(100f, 400f, 500f, 800f)
        val target = Rect(40f, 1100f, 100f, 1160f)
        val flight = RibbonRecordFlight(source, target, null)
        assertEquals(source.center, flight.center)
        repeat(10) { flight.advance(.048f) }
        assertTrue("The record emerges above the sleeve before flying", flight.center.y < source.top)
        assertTrue(flight.opening > .99f)
        // A layout change during flight must use the newly measured destination.
        flight.target = target.translate(androidx.compose.ui.geometry.Offset(20f, -30f))
        repeat(40) { flight.advance(.05f) }
        assertEquals(flight.target.center, flight.center)
        assertEquals(flight.target.width, flight.diameter, .001f)
        assertEquals(0f, flight.opening, .001f)
    }

    @Test fun distantAndTransparentArtworkStillOccludesTheAlbumBehindIt() {
        val texture = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(0x66D08050) }
        val shader = BitmapShader(texture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        fun sample(background: Int, brightness: Float): Int {
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(background)
            canvas.scale(128f, 128f)
            drawDepthRibbonFace(canvas, Paint(Paint.ANTI_ALIAS_FLAG), shader, brightness)
            return bitmap.getPixel(64, 64).also { bitmap.recycle() }
        }
        for (brightness in listOf(.3f, .65f, 1f)) {
            val overRed = sample(Color.RED, brightness)
            assertEquals("A rear album must never bleed through distance fog", overRed, sample(Color.GREEN, brightness))
            assertEquals(255, Color.alpha(overRed))
        }
        texture.recycle()
    }
}
