package io.github.currencortex.music

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.share.*
import io.github.currencortex.music.ui.component.SongActionsSheet
import io.github.currencortex.music.ui.component.SongMenu
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SongSharePosterTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exportedPosterKeepsLongTitlesSeparateFromAScannablePublicQr() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cover = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        Canvas(cover).apply {
            drawColor(Color.rgb(35, 65, 155))
            drawCircle(320f, 350f, 260f, Paint().apply { color = Color.rgb(240, 80, 100) })
        }
        val content = SongShareContent.from(Song(3438848556, "「邦友的酒(DJ版)」梦的结唱halo 音乐与你 👩🏽‍🚀".repeat(4), "活跃黑江乐 / Long Artist"))
        val poster = SongSharePoster.render(content, cover)
        try {
            assertEquals(1080, poster.width); assertEquals(1440, poster.height)
            assertEquals("Artwork must keep its original opacity", Color.rgb(35, 65, 155), poster.getPixel(300, 700))
            val pixels = IntArray(248 * 248)
            poster.getPixels(pixels, 0, 248, 736, 1136, 248, 248)
            val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(248, 248, pixels))))
            assertEquals(content.publicUrl, decoded.text)
            File(context.getExternalFilesDir(null), "song-share-long.png").outputStream().use {
                poster.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val regular = SongSharePoster.render(content.copy(title = "Reply", artists = "kz / かぐや"), cover)
            try { File(context.getExternalFilesDir(null), "song-share-regular.png").outputStream().use {
                regular.compress(Bitmap.CompressFormat.PNG, 100, it)
            } } finally { regular.recycle() }
        } finally { poster.recycle(); cover.recycle() }
    }

    @Test fun previewUsesTheExportedFileAndGrantsReadAccessWithoutSendingAnything(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cover = File(context.cacheDir, "share-fixture-cover.png")
        val image = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(43, 86, 180)) }
        cover.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        val song = Song(3340114785, "Reply", "kz / かぐや", cover = Uri.fromFile(cover).toString())
        val generated = SongShareFiles.create(context, song)
        try {
            assertTrue(generated.coverLoaded)
            val intent = SongShareFiles.shareIntent(context, generated)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/png", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.files", uri.authority)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertArrayEquals(generated.file.readBytes(), bytes)
            val copy = File(context.cacheDir, "share-saved-copy.png")
            try { SongShareFiles.save(context, generated, Uri.fromFile(copy)); assertArrayEquals(bytes, copy.readBytes()) }
            finally { copy.delete() }
            val shown = mutableStateOf(true)
            compose.setContent { LeiTheme(io.github.currencortex.music.data.settings.AppearanceSettings(blur = false)) {
                top.yukonga.miuix.kmp.basic.Scaffold {
                    if (shown.value) SongActionsSheet(SongMenu(song, {}, {}, {}), onDismiss = { shown.value = false })
                }
            } }
            compose.onNodeWithTag("song_share").performClick()
            compose.onNodeWithTag("song_share_dialog").assertIsDisplayed()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("song_share_preview").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("song_share_preview").assertIsDisplayed()
            compose.onNodeWithTag("song_share_send").assertIsEnabled()
            compose.onNodeWithTag("song_share_save").assertIsEnabled()
            compose.onNodeWithTag("song_share_copy").performClick()
            compose.runOnIdle { shown.value = false }
        } finally { generated.file.delete(); cover.delete() }
    }

    @Test fun missingCoverStillCreatesAUsefulPoster(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val poster = SongShareFiles.create(context, Song(42, "无封面歌曲", "歌手",
            cover = "file:///nonexistent-currentmusic-share-cover.png"))
        try { assertFalse(poster.coverLoaded); assertTrue(poster.file.length() > 1000) }
        finally { poster.file.delete() }
    }
}
