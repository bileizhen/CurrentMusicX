package io.github.currencortex.music

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.AudioCache
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.media.AudioRequest
import io.github.currencortex.music.core.media.AudioSourceResolver
import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.scanLocalMusic
import io.github.currencortex.music.ui.theme.deleteCustomFont
import io.github.currencortex.music.ui.theme.importCustomFont
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LocalMusicAndFontTest {
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()

    @Test fun embeddedLyricsReadThroughMediaStoreDescriptor() = runBlocking {
        for ((extension, mime) in listOf("mp3" to "audio/mpeg", "flac" to "audio/flac",
            "m4a" to "audio/mp4", "wav" to "audio/wav")) {
        val file = File(context.cacheDir, "embedded-lyrics-${UUID.randomUUID()}.$extension")
        val title = file.nameWithoutExtension
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values))
        try {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
                .open("download/fixture.$extension").use { input -> file.outputStream().use { input.copyTo(it) } }
            val song = Song(-1, title, durationMs = 1000, musicSource = MusicSource.LOCAL, localUri = uri.toString())
            val text = "[00:00.10]本地歌词\n[00:00.50]离线读取"
            io.github.currencortex.music.core.download.SongTagWriter.write(file, song, text, null)
            context.contentResolver.openOutputStream(uri)!!.use { output -> file.inputStream().use { it.copyTo(output) } }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            val result = io.github.currencortex.music.feature.lyrics.data.LocalLyricsProvider(context).load(song)
            assertEquals("local", result?.origin?.provider)
            assertEquals(listOf("本地歌词", "离线读取"), result?.document?.lines?.map { it.text })
            assertEquals(100L, result?.document?.lines?.first()?.startTimeMs)
        } finally { context.contentResolver.delete(uri, null, null); file.delete() }
        }
    }

    @Test fun importFontMakesPrivateCopyAndInvalidFileDoesNotReplaceIt() {
        val uri = Uri.parse("android.resource://${context.packageName}/${R.font.lxgw_wenkai_regular}")
        val name = importCustomFont(context, uri)
        val imported = File(context.filesDir, "custom-fonts/$name")
        try {
            assertTrue(imported.isFile)
            val bad = File(context.cacheDir, "diagnostics/bad-font-${UUID.randomUUID()}")
            bad.parentFile?.mkdirs(); bad.writeText("not a font")
            try {
                val badUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", bad)
                assertThrows(Exception::class.java) { importCustomFont(context, badUri) }
                assertTrue(imported.isFile)
            } finally { bad.delete() }
        } finally { deleteCustomFont(context, name) }
        assertFalse(imported.exists())
    }

    @Test fun mediaStoreTrackScansAndReadsLocallyWithoutResolvingNetwork() = runBlocking {
        if (Build.VERSION.SDK_INT < 29) return@runBlocking
        val title = "cmx-local-test-${UUID.randomUUID()}"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.wav"); put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav"); put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values))
        val cacheDir = File(context.cacheDir, "local-audio-test-${UUID.randomUUID()}")
        val cache = AudioCache(context, cacheDir)
        try {
            val wave = ByteBuffer.allocate(44 + 8000 * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(capacity() - 8); put("WAVEfmt ".toByteArray())
                putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000)
                putShort(2); putShort(16); put("data".toByteArray()); putInt(16000)
            }.array()
            context.contentResolver.openOutputStream(uri)!!.use { it.write(wave) }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            val song = scanLocalMusic(context).first { it.localUri == uri.toString() }
            assertEquals(MusicSource.LOCAL, song.musicSource); assertTrue(song.id < 0)
            var loads = 0
            val session = RequestSession("https://fixture.invalid", null)
            val resolver = AudioSourceResolver(cache, { session }, currentProvider = { "changed" }) { _, _, _ ->
                loads++; error("Local music must not resolve an online source")
            }
            val request = AudioRequest(song.id, AudioQuality.STANDARD, 0, session, "old", song)
            val result = resolver.resolve(request)
            assertEquals(0, loads); assertEquals("", result.key); assertEquals(song.localUri, result.source.url)
            val source = cache.dataSourceFactory.createDataSource()
            try {
                source.open(DataSpec.Builder().setUri(result.source.url).build())
                val actual = ByteArray(wave.size); var read = 0
                while (read < actual.size) {
                    val n = source.read(actual, read, actual.size - read)
                    if (n == C.RESULT_END_OF_INPUT) break
                    read += n
                }
                assertEquals(wave.size, read); assertArrayEquals(wave, actual)
            } finally { source.close() }
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val ready = java.util.concurrent.CountDownLatch(1)
            var player: androidx.media3.exoplayer.ExoPlayer? = null
            var playerError: androidx.media3.common.PlaybackException? = null
            try {
                instrumentation.runOnMainSync {
                    player = androidx.media3.exoplayer.ExoPlayer.Builder(context)
                        .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(cache.dataSourceFactory))
                        .build().apply {
                            volume = 0f
                            addListener(object : androidx.media3.common.Player.Listener {
                                override fun onPlaybackStateChanged(state: Int) { if (state == androidx.media3.common.Player.STATE_READY) ready.countDown() }
                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { playerError = error; ready.countDown() }
                            })
                            setMediaItem(androidx.media3.common.MediaItem.fromUri(result.source.url))
                            prepare()
                        }
                }
                assertTrue("Local audio did not prepare", ready.await(8, java.util.concurrent.TimeUnit.SECONDS))
                assertNull(playerError)
            } finally { instrumentation.runOnMainSync { player?.release() } }
            assertEquals(song.localUri, kotlinx.serialization.json.Json.decodeFromString(Song.serializer(),
                kotlinx.serialization.json.Json.encodeToString(Song.serializer(), song)).localUri)
        } finally {
            cache.close(); context.contentResolver.delete(uri, null, null)
            check(cacheDir.parentFile == context.cacheDir && cacheDir.name.startsWith("local-audio-test-"))
            cacheDir.deleteRecursively()
        }
    }
}
