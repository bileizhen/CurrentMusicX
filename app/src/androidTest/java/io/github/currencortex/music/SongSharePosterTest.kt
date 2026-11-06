package io.github.currencortex.music

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import io.github.currencortex.music.data.song.MusicSource
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
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
            compose.onNodeWithTag("song_share_style_1").performClick().assertIsSelected()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("song_share_preview").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("song_share_style_0").performClick().assertIsSelected()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("song_share_preview").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("song_share_copy").performScrollTo().performClick()
            compose.runOnIdle { shown.value = false }
        } finally { generated.file.delete(); cover.delete() }
    }

    @Test fun missingCoverStillCreatesAUsefulPoster(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val poster = SongShareFiles.create(context, Song(42, "无封面歌曲", "歌手",
            cover = "file:///nonexistent-currentmusic-share-cover.png", musicSource = MusicSource.QQ_MUSIC), SongShareStyle.IMMERSIVE)
        try { assertFalse(poster.coverLoaded); assertTrue(poster.file.length() > 1000) }
        finally { poster.file.delete() }
    }

    @Test fun immersiveKeepsCenterCropAndReadableQrForWideAndTallCovers() {
        val content = SongShareContent.from(Song(3340114785, "长歌名".repeat(60), "歌手".repeat(40)))
        for ((width, height) in listOf(600 to 1200, 1200 to 600)) {
            val cover = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            Canvas(cover).drawRect(width * .25f, 0f, width * .75f, height.toFloat(), Paint().apply { color = Color.GREEN })
            val poster = SongSharePoster.render(content, cover, SongShareStyle.IMMERSIVE)
            try {
                assertEquals(1080, poster.width); assertEquals(1440, poster.height)
                val center = poster.getPixel(540, 450)
                assertTrue(Color.green(center) > 200)
                assertEquals(0, Color.red(center))
                val pixels = IntArray(248 * 248)
                poster.getPixels(pixels, 0, 248, 736, 1136, 248, 248)
                assertEquals(content.publicUrl, MultiFormatReader().decode(BinaryBitmap(
                    HybridBinarizer(RGBLuminanceSource(248, 248, pixels)))).text)
            } finally { poster.recycle(); cover.recycle() }
        }
    }

    @Test fun everySourceAndStyleRendersLocallyWithoutRequestingOnlinePosters(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503)
                .message("Unexpected network request").body("Unavailable".toResponseBody()).build()
        }.build()
        val loader = ImageLoader.Builder(context).memoryCache(null).diskCache(null)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }.build()
        val cover = File(context.cacheDir, "share-local-only-cover.png")
        val image = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        cover.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        try {
            for (source in MusicSource.entries) for (style in SongShareStyle.entries) {
                val song = Song(3340114785, "Reply", "kz", cover = Uri.fromFile(cover).toString(), musicSource = source)
                val poster = SongShareFiles.create(context, song, style, loader)
                try {
                    assertTrue("$source / $style", poster.coverLoaded)
                    val bitmap = BitmapFactory.decodeFile(poster.file.path)
                    try {
                        assertEquals(1080, bitmap.width); assertEquals(1440, bitmap.height)
                        assertTrue(Color.blue(bitmap.getPixel(540, 500)) > 200)
                    } finally { bitmap.recycle() }
                    assertTrue("No poster service may be contacted: $requests", requests.isEmpty())
                } finally { poster.file.delete() }
            }
        } finally {
            cover.delete(); loader.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
        }
    }

    @Test fun remoteArtworkIsTheOnlyRequestAndFailureStillProducesBothLocalStyles(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requests = mutableListOf<String>()
        val coverUrl = "https://artwork.example/cover.png"
        val image = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        val bytes = java.io.ByteArrayOutputStream().apply { image.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        image.recycle()
        var fail = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (fail) 503 else 200)
                .message(if (fail) "Unavailable" else "OK")
                .body(if (fail) "Unavailable".toResponseBody() else bytes.toResponseBody("image/png".toMediaType())).build()
        }.build()
        val loader = ImageLoader.Builder(context).memoryCache(null).diskCache(null)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }.build()
        try {
            for (unavailable in listOf(false, true)) {
                fail = unavailable
                for (style in SongShareStyle.entries) {
                    val before = requests.size
                    val poster = SongShareFiles.create(context, Song(3340114785, "Reply", "kz", cover = coverUrl), style, loader)
                    try {
                        assertEquals(!unavailable, poster.coverLoaded)
                        assertTrue(poster.file.length() > 1000)
                        assertEquals(listOf(coverUrl), requests.drop(before))
                        val bitmap = BitmapFactory.decodeFile(poster.file.path)
                        try {
                            val pixels = IntArray(248 * 248)
                            bitmap.getPixels(pixels, 0, 248, 736, 1136, 248, 248)
                            assertEquals(poster.content.publicUrl, MultiFormatReader().decode(BinaryBitmap(
                                HybridBinarizer(RGBLuminanceSource(248, 248, pixels)))).text)
                        } finally { bitmap.recycle() }
                    } finally { poster.file.delete() }
                }
            }
        } finally { loader.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
