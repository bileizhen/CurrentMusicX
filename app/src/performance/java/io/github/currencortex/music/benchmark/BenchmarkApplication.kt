package io.github.currencortex.music.benchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.BuildConfig
import io.github.currencortex.music.CurrentMusicApplication
import io.github.currencortex.music.core.media.DockAudioLevel
import io.github.currencortex.music.core.media.DockRenderersFactory
import io.github.currencortex.music.core.network.ServerCheck
import io.github.currencortex.music.core.network.ServerHealth
import io.github.currencortex.music.core.network.ServerStatus
import io.github.currencortex.music.core.network.ApiClient
import io.github.currencortex.music.core.update.ReleaseChecker
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.LyricsOrigin
import io.github.currencortex.music.feature.lyrics.data.LyricsProvider
import io.github.currencortex.music.feature.lyrics.data.LyricsProviderResult
import io.github.currencortex.music.feature.lyrics.data.SavedLyrics
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import io.github.currencortex.music.feature.lyrics.model.LyricWord
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import okio.Path.Companion.toOkioPath

/** Exists only in the isolated performance APK. No account, HTTP or MediaSessionService. */
@androidx.annotation.OptIn(UnstableApi::class)
class BenchmarkApplication : CurrentMusicApplication() {
    internal var playerOwner = 0
    internal var localPlayerCreated = false
        private set
    private val offlineHttp by lazy {
        ApiClient.defaultHttpClient().newBuilder().retryOnConnectionFailure(false)
            .addInterceptor { throw IOException("Offline performance fixture") }.build()
    }
    override fun createContainer(): AppContainer {
        check(BuildConfig.APPLICATION_ID == "com.bileizhen.currentmusic.benchmark")
        return AppContainer(this, storageNamespace = "performance", useNativeNetease = false,
            ttmlProvider = LyricsProvider { LyricsProviderResult.Parsed(BenchmarkData.lyrics) },
            automaticLyricsProvider = { SavedLyrics(BenchmarkData.lyrics, LyricsOrigin("Local fixture", "benchmark")) },
            serverCheck = ServerCheck { ServerHealth(ServerStatus.UNAVAILABLE) },
            apiHttpClient = offlineHttp,
            releaseChecker = object : ReleaseChecker {
                override fun check(channel: io.github.currencortex.music.core.update.UpdateChannel):
                    io.github.currencortex.music.core.update.AppRelease? = throw IOException("Offline performance fixture")
            })
    }

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context).components {
        // Public API/announcements and image requests fail as normal transport errors before
        // DNS/socket creation. Local file artwork keeps using Coil's real decoder/cache path.
        add(OkHttpNetworkFetcherFactory(callFactory = { offlineHttp }))
    }.diskCache(DiskCache.Builder().directory(context.cacheDir.resolve("image_cache").toOkioPath())
        .maxSizeBytes(128L * 1024 * 1024).build()).build()

    // Application scope ensures Activity replacement never creates two simultaneous players.
    val localPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this, DockRenderersFactory(this, DockAudioLevel(),
            outputSettings = { container.audioOutput.state.value },
            outputStatus = { container.audioOutput.status.value = it },
            diagnostics = container.audioDiagnostics)).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
            repeatMode = Player.REPEAT_MODE_ONE
            localPlayerCreated = true
        }
    }

    val songs: List<Song> by lazy {
        val cover = File(cacheDir, "performance-artwork.png")
        if (!cover.isFile) {
            val bitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
            val paint = Paint().apply { shader = LinearGradient(0f, 0f, 384f, 384f,
                intArrayOf(0xFF365DCA.toInt(), 0xFF29C3B5.toInt(), 0xFF342766.toInt()), null, Shader.TileMode.CLAMP) }
            Canvas(bitmap).drawPaint(paint)
            cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val wav = File(cacheDir, "performance-silence.wav")
        if (!wav.isFile || wav.length() != SILENCE_PCM_BYTES + 44L) writeSilenceWav(wav)
        List(32) { index -> Song(index + 1L, "Local melody ${index + 1}", "CurrentMusicX",
            "Performance fixture", cover.toURI().toString(), 10_000,
            musicSource = MusicSource.LOCAL, localUri = wav.toURI().toString()) }
    }

    fun prepareLocal(index: Int = 0) {
        val song = songs[index]
        container.playbackQueue.replace(songs, index)
        localPlayer.setMediaItem(MediaItem.fromUri(song.localUri))
        localPlayer.prepare()
    }

    private fun writeSilenceWav(target: File) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(SILENCE_PCM_BYTES + 36)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1); putShort(2) // PCM, stereo.
            putInt(48_000); putInt(48_000 * 2 * 2)
            putShort(4); putShort(16) // Block alignment, bits per sample.
            put("data".toByteArray(Charsets.US_ASCII)); putInt(SILENCE_PCM_BYTES)
        }.array()
        val temporary = File.createTempFile("performance-silence-", ".tmp", cacheDir)
        try {
            temporary.outputStream().use { output ->
                output.write(header)
                val zeros = ByteArray(64 * 1024)
                var remaining = SILENCE_PCM_BYTES
                while (remaining > 0) {
                    val count = minOf(zeros.size, remaining)
                    output.write(zeros, 0, count)
                    remaining -= count
                }
            }
            check(temporary.renameTo(target)) { "Unable to save local silence fixture" }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private companion object {
        const val SILENCE_PCM_BYTES = 48_000 * 2 * 2 * 10
    }
}

internal object BenchmarkData {
    val lyrics = LyricsDocument(List(32) { index ->
        val text = listOf("沿着星光慢慢向前走", "The music carries on", "夜空に響くメロディー")[index % 3]
        val start = index * 2_000L
        LyricLine(start, start + 2_000, text, text.indices.map { offset ->
            LyricWord(text.substring(offset, offset + 1), start + offset * 2_000 / text.length,
                start + (offset + 1) * 2_000 / text.length, offset, offset + 1)
        }, translation = "Fixed local translation · 固定本地翻译")
    })
}
