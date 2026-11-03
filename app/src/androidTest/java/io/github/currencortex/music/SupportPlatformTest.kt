package io.github.currencortex.music

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.feature.support.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SupportPlatformTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun httpsIsHandedToSystemAndInvalidLinksDoNotStartAnActivity() {
        var intent: Intent? = null
        val recording = object : ContextWrapper(context) {
            override fun startActivity(value: Intent) { intent = value }
        }
        assertEquals(SupportLinkResult.INVALID, openSupportLink(recording, ""))
        assertNull(intent)
        assertEquals(SupportLinkResult.OPENED, openSupportLink(recording, "https://example.com/support"))
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("https://example.com/support", intent?.dataString)
    }

    @Test fun noBrowserOrDeniedBrowserDoesNotCrash() {
        fun failing(error: RuntimeException): Context = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { throw error }
        }
        assertEquals(SupportLinkResult.NO_APP, openSupportLink(failing(ActivityNotFoundException()), "https://example.com"))
        assertEquals(SupportLinkResult.NO_APP, openSupportLink(failing(SecurityException()), "https://example.com"))
    }

    @Test fun offlineCodesAreDifferentSquaresWithWhiteMarginsAndShareOriginalBytes() = runBlocking {
        assertNotEquals(SupportCode.ALIPAY.image, SupportCode.WECHAT.image)
        SupportCode.entries.forEach { code ->
            val original = context.resources.openRawResource(code.image).use { it.readBytes() }
            val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size)
            assertEquals(bitmap.width, bitmap.height)
            // The whole outermost 3% must be white, not a cropped finder or tinted glass edge.
            val margin = bitmap.width * 3 / 100
            for (y in 0 until bitmap.height step 9) for (x in 0 until bitmap.width step 9) {
                if (x < margin || y < margin || x >= bitmap.width - margin || y >= bitmap.height - margin) {
                    val pixel = bitmap.getPixel(x, y)
                    assertTrue("${code.name}: margin at $x,$y", android.graphics.Color.red(pixel) > 245 &&
                        android.graphics.Color.green(pixel) > 245 && android.graphics.Color.blue(pixel) > 245)
                }
            }
            bitmap.recycle()
            val intent = SupportFiles.shareIntent(context, code)
            assertEquals("image/png", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val uri = intent.clipData!!.getItemAt(0).uri
            assertEquals("content", uri.scheme)
            assertArrayEquals(original, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            val saved = File(context.cacheDir, "support-save-test.png")
            try {
                SupportFiles.save(context, code, Uri.fromFile(saved))
                assertArrayEquals(original, saved.readBytes())
            } finally { saved.delete() }
        }
    }
}
