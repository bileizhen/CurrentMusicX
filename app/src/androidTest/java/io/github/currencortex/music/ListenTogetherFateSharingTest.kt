package io.github.currencortex.music

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import io.github.currencortex.music.data.netease.listen.ListenTogetherFateReport
import io.github.currencortex.music.data.netease.listen.ListenTogetherParticipant
import io.github.currencortex.music.feature.room.ListenTogetherFateSharing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Account-free fixtures: no room request, chooser or external share target is opened. */
class ListenTogetherFateSharingTest {
    private val report = ListenTogetherFateReport(9, ListenTogetherParticipant(7, "匿名听友".repeat(16)),
        ListenTogetherParticipant(9, "好友"), totalDurationMs = 38_400_000, totalSongCount = 124,
        commonRedSongCount = null, firstListenTimeMs = 1_728_662_400_000, daysSinceFirstListen = 13,
        latestLateNightTimeMs = 1_728_662_400_000, mostPlayedSongName = "最多一起听过的歌曲".repeat(24),
        longestDurationMs = null, ltType = 5)

    @Test fun exportedPngContainsDecodableOfficialReportQrAndReadableGrantedUri() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = ListenTogetherFateSharing.create(context, report)
        try {
            assertEquals(File(context.cacheDir, "song-shares").canonicalFile, file.canonicalFile.parentFile)
            assertTrue(file.name.startsWith("fate-report-"))
            val bitmap = checkNotNull(BitmapFactory.decodeFile(file.absolutePath))
            try {
                assertEquals(1080, bitmap.width)
                assertEquals(1800, bitmap.height)
                val pixels = IntArray(268 * 268)
                bitmap.getPixels(pixels, 0, 268, 716, 1500, 268, 268)
                val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(268, 268, pixels))))
                assertEquals(report.officialUrl(), decoded.text)
                assertTrue(decoded.text.contains("partnerUid=9"))
                assertTrue(decoded.text.contains("scene=5"))
            } finally { bitmap.recycle() }
            val intent = ListenTogetherFateSharing.intent(context, report, file)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/png", intent.type)
            @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            assertEquals("${context.packageName}.files", uri?.authority)
            assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().contains(report.officialUrl()))
            context.contentResolver.openInputStream(checkNotNull(uri)).use { stream ->
                val header = ByteArray(8)
                assertEquals(8, checkNotNull(stream).read(header))
                assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), header)
            }
        } finally { file.delete() }
    }

    @Test fun cleanupKeepsOtherPosterFamiliesAndIntentRejectsFilesOutsideItsOwnNamespace() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "song-shares").apply { mkdirs() }
        val oldOwn = File.createTempFile("fate-report-stale-", ".png", directory)
        val oldSong = File.createTempFile("poster-fate-fixture-", ".png", directory)
        val oldEndReport = File.createTempFile("listen-report-fate-fixture-", ".png", directory)
        val outside = File.createTempFile("fate-report-outside-", ".png", context.cacheDir)
        val wrongExtension = File.createTempFile("fate-report-invalid-", ".txt", directory)
        var generated: File? = null
        try {
            listOf(oldOwn, oldSong, oldEndReport).forEach { assertTrue(it.setLastModified(1L)) }
            generated = ListenTogetherFateSharing.create(context, report)
            assertFalse(oldOwn.exists())
            assertTrue(oldSong.exists())
            assertTrue(oldEndReport.exists())
            listOf(outside, oldSong, oldEndReport, wrongExtension).forEach { file ->
                var rejected = false
                try { ListenTogetherFateSharing.intent(context, report, file) }
                catch (_: IllegalArgumentException) { rejected = true }
                assertTrue("Unexpectedly shared ${file.name}", rejected)
            }
        } finally {
            listOfNotNull(oldOwn, oldSong, oldEndReport, outside, wrongExtension, generated).forEach { it.delete() }
        }
    }
}
