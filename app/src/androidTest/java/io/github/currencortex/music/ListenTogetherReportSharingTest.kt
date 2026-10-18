package io.github.currencortex.music

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import io.github.currencortex.music.data.netease.listen.ListenTogetherParticipant
import io.github.currencortex.music.data.netease.listen.ListenTogetherRecordReport
import io.github.currencortex.music.feature.room.ListenTogetherReportSharing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Offline export fixture; never starts a share target or contacts a real listening room. */
class ListenTogetherReportSharingTest {
    @Test fun pngExportsRealReportQrAndReadGrantedFileProviderStream() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = ListenTogetherRecordReport("share_fixture_123", self = ListenTogetherParticipant(7, "很长的本机昵称".repeat(10)),
            peer = ListenTogetherParticipant(9, "匿名听友"), durationMs = 5_940_000, songCount = 32,
            totalDurationMs = null, totalSongCount = null)
        val file = ListenTogetherReportSharing.create(context, report)
        try {
            assertTrue(file.name.startsWith("listen-report-"))
            val bitmap = checkNotNull(BitmapFactory.decodeFile(file.absolutePath))
            try {
                assertEquals(1080, bitmap.width)
                assertEquals(1440, bitmap.height)
                // Decode pixels from the exported poster, rather than trusting the QR input string.
                val pixels = IntArray(268 * 268)
                bitmap.getPixels(pixels, 0, 268, 716, 1080, 268, 268)
                val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(268, 268, pixels))))
                assertEquals(report.officialUrl(), decoded.text)
            } finally { bitmap.recycle() }
            val intent = ListenTogetherReportSharing.intent(context, report, file)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/png", intent.type)
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            assertNotNull(uri)
            assertEquals("${context.packageName}.files", uri?.authority)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
            assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().contains(report.officialUrl()))
            context.contentResolver.openInputStream(checkNotNull(uri)).use { stream ->
                assertNotNull(stream)
                val header = ByteArray(8)
                assertEquals(8, stream!!.read(header))
                assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), header)
            }
        } finally { file.delete() }
    }
}
