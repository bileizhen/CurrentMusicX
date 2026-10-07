package io.github.currencortex.music

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.download.*
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.song.AudioSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricWord
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Synthetic tones only. Creates and removes only this test's exact download URIs. */
class SongDownloadTest {
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private val testContext get() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
    private fun fixture(ext: String) = testContext.assets.open("download/fixture.$ext").use { it.readBytes() }
    private fun cover(): ByteArray {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        return try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
        finally { bitmap.recycle() }
    }
    @Test fun sixContainersEmbedMetadataCoverAndLyricsAndPublishMatchedSidecars(): Unit = runBlocking {
        val server = MockWebServer().apply { start() }
        val cover = cover()
        val temp = File(context.cacheDir, "download-test-${UUID.randomUUID()}")
        try {
            for (ext in listOf("mp3", "flac", "m4a", "aac", "ogg", "wav")) {
                val original = fixture(ext)
                server.enqueue(MockResponse().setBody(Buffer().write(original)))
                server.enqueue(MockResponse().setBody(Buffer().write(cover)))
                val song = Song(990001, "下载验证-${UUID.randomUUID()}", "测试歌手 / Artist", "测试专辑", server.url("/cover").toString())
                val doc = LyricsDocument(listOf(LyricLine(0, 500, "你好，音乐",
                    words = listOf(LyricWord("你好", 0, 200, 0, 2), LyricWord("，音乐", 200, 500, 2, 5)), translation = "Hello, music")))
                val phases = mutableListOf<String>()
                val engine = SongDownloadEngine(temp, { _, requested ->
                    assertEquals(AudioQuality.LOSSLESS, requested)
                    AudioSource(server.url("/audio").toString(), "lossless", 44100, 1, "wrong-extension")
                }, { song }, { doc }, SongDownloadStore(context))
                val result = engine.download(song, AudioQuality.LOSSLESS, null, UUID.randomUUID().toString()) { phases += it.phase }
                try {
                    assertEquals("", result.warning)
                    assertEquals(3, result.saved.files.size)
                    assertTrue(phases.contains("写入歌曲信息"))
                    val expectedExt = if (ext == "aac") "m4a" else ext
                    assertTrue(result.saved.name.endsWith(".$expectedExt"))
                    val saved = File(temp, "saved.$expectedExt").apply { parentFile!!.mkdirs()
                        context.contentResolver.openInputStream(result.saved.audioUri)!!.use { writeBytes(it.readBytes()) } }
                    val source = File(temp, "original.$ext").apply { writeBytes(original) }
                    assertArrayEquals("Encoded audio remains unchanged for $ext", audioHash(source), audioHash(saved))
                    source.delete()
                    val containerTag = AudioFileIO.read(saved).tag
                    val tag = (containerTag as? org.jaudiotagger.tag.wav.WavTag)?.getID3Tag() ?: containerTag
                    assertEquals(song.name, tag.getFirst(FieldKey.TITLE)); assertEquals(song.artists, tag.getFirst(FieldKey.ARTIST))
                    assertEquals(song.album, tag.getFirst(FieldKey.ALBUM)); assertEquals(DownloadLyrics.lrc(song, doc), tag.getFirst(FieldKey.LYRICS))
                    assertTrue(tag.firstArtwork.binaryData.isNotEmpty())
                    val lrc = context.contentResolver.openInputStream(result.saved.files[1])!!.bufferedReader().use { it.readText() }
                    assertEquals(tag.getFirst(FieldKey.LYRICS), lrc)
                    for (uri in result.saved.files) context.contentResolver.query(uri,
                        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.IS_PENDING), null, null, null)!!.use {
                        assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(1))
                        assertEquals(result.saved.name.substringBeforeLast('.'), it.getString(0).substringBeforeLast('.'))
                    }
                    // Verify system players can still read the audio and its embedded picture.
                    MediaMetadataRetriever().use { retriever ->
                        retriever.setDataSource(context, result.saved.audioUri)
                        assertTrue("Playable $ext duration", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() > 400)
                        // Android's WAV extractor exposes only duration; title/lyrics/cover for wav are verified by the read-back above.
                        if (ext != "wav") {
                            assertNotNull("System can read $ext cover", retriever.embeddedPicture)
                            assertEquals("System can read $ext title", song.name, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                        }
                    }
                    saved.delete()
                    assertNull(server.takeRequest(3, TimeUnit.SECONDS)!!.getHeader("Token"))
                    assertNull(server.takeRequest(3, TimeUnit.SECONDS)!!.getHeader("Authorization"))
                } finally { result.saved.files.forEach { context.contentResolver.delete(it, null, null) } }
            }
        } finally { server.shutdown(); temp.deleteRecursively() }
    }
    private fun audioHash(file: File): ByteArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path); extractor.selectTrack(0)
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val digest = MessageDigest.getInstance("SHA-256")
            while (true) {
                buffer.clear(); val count = extractor.readSampleData(buffer, 0)
                if (count < 0) break
                buffer.limit(count); digest.update(buffer); extractor.advance()
            }
            return digest.digest()
        } finally { extractor.release() }
    }
    @Test fun corruptAudioAndCancellationDoNotLeaveFinishedFiles(): Unit = runBlocking {
        val server = MockWebServer().apply { start() }
        val temp = File(context.cacheDir, "download-test-${UUID.randomUUID()}")
        val song = Song(990002, "错误与取消验证")
        val engine = SongDownloadEngine(temp, { _, _ -> AudioSource(server.url("/audio").toString(), "standard", 0, 0) },
            { song }, { LyricsDocument() }, SongDownloadStore(context))
        try {
            server.enqueue(MockResponse().setBody("<html>expired</html>"))
            try { engine.download(song, AudioQuality.STANDARD, null, UUID.randomUUID().toString()) {}; fail("HTML cannot be a successful song") }
            catch (expected: java.io.IOException) { assertTrue(expected.message!!.contains("音频格式")) }
            assertTrue(temp.listFiles().orEmpty().isEmpty())
            server.enqueue(MockResponse().setBody(Buffer().write(fixture("mp3"))).throttleBody(200, 100, TimeUnit.MILLISECONDS))
            val started = CompletableDeferred<Unit>()
            val job = launch { engine.download(song, AudioQuality.STANDARD, null, UUID.randomUUID().toString()) {
                if (it.received > 0) started.complete(Unit)
            } }
            withTimeout(5000) { started.await() }
            withTimeout(3000) { job.cancelAndJoin() }
            assertTrue(temp.listFiles().orEmpty().isEmpty())
        } finally { server.shutdown(); temp.deleteRecursively() }
    }
    @Test fun failedPublishingRemovesAllPendingUris(): Unit = runBlocking {
        val temp = File(context.cacheDir, "download-test-${UUID.randomUUID()}").apply { mkdirs() }
        val base = "Rollback-${UUID.randomUUID()}"
        val audio = File(temp, "fixture.mp3").apply { writeBytes(fixture("mp3")) }
        try {
            try { SongDownloadStore(context).save(base, listOf(SongDownloadFile(audio, "audio/mpeg"),
                SongDownloadFile(File(temp, "missing.lrc"), "application/octet-stream")), null); fail("Missing sidecar must roll back") }
            catch (_: java.io.IOException) { }
            context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("$base%"), null)!!.use { assertEquals(0, it.count) }
        } finally { temp.deleteRecursively() }
    }
}
