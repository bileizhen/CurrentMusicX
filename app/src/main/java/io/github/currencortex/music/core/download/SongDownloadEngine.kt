package io.github.currencortex.music.core.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodec
import android.net.Uri
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.song.AudioSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.*
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SongDownloadProgress(val phase: String, val received: Long = 0, val total: Long = -1)
data class SongDownloadResult(val saved: SavedSongDownload, val warning: String, val quality: String)

class SongDownloadEngine(
    private val cache: File,
    private val resolve: suspend (Song, AudioQuality) -> AudioSource,
    private val detail: suspend (Song) -> Song,
    private val lyrics: suspend (Song) -> LyricsDocument,
    private val store: SongDownloadStore,
    // Re-checked before publishing: account/audio-source changes must not produce a mixed file.
    private val guard: suspend () -> Unit = {},
    // Audio/image hosts never receive account headers or API interceptors.
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS).build(),
) {
    suspend fun download(song: Song, quality: AudioQuality, tree: Uri?, attempt: String,
        progress: suspend (SongDownloadProgress) -> Unit): SongDownloadResult = withContext(Dispatchers.IO) {
        require(attempt.matches(Regex("[a-zA-Z0-9-]+")))
        require(!song.video) { "视频暂不支持歌曲下载" }
        val folder = File(cache, attempt).apply { check(mkdirs() || isDirectory) { "下载缓存不可用" } }
        val warnings = mutableListOf<String>()
        try {
            progress(SongDownloadProgress("获取歌曲与音质"))
            val source = resolve(song, quality)
            val info = try { detail(song) } catch (e: CancellationException) { throw e }
                catch (_: Exception) { warnings += "部分歌曲信息使用列表数据"; song }
            progress(SongDownloadProgress("下载音频"))
            val raw = File(folder, "audio.download")
            fetch(source.url, raw, 512L * 1024 * 1024) { received, total -> progress(SongDownloadProgress("下载音频", received, total)) }
            if (source.md5.matches(Regex("[a-fA-F0-9]{32}"))) {
                val digest = MessageDigest.getInstance("MD5")
                raw.inputStream().use { input -> val buffer = ByteArray(65536)
                    while (true) { currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
                check(digest.digest().joinToString("") { "%02x".format(it) }.equals(source.md5, true)) { "音频校验失败，请重新下载" }
            }
            val format = AudioDownloadFormat.detect(raw)
            val audio = File(folder, "audio.${if (format == "aac") "m4a" else format}")
            if (format == "aac") remuxAac(raw, audio) else check(raw.renameTo(audio)) { "音频文件准备失败" }
            progress(SongDownloadProgress("获取封面与歌词"))
            val document = try { lyrics(info) } catch (e: CancellationException) { throw e }
                catch (_: Exception) { warnings += "歌词获取失败"; LyricsDocument() }
            val lrc = DownloadLyrics.lrc(info, document)
            if (lrc.isBlank() && "歌词获取失败" !in warnings) warnings += "暂未获取到歌词"
            val cover = if (info.cover.isBlank()) { warnings += "此歌曲暂无封面"; null } else try {
                val image = File(folder, "cover.download")
                fetch(info.cover, image, 12L * 1024 * 1024) { _, _ -> }
                normalizeCover(image)
            } catch (e: CancellationException) { throw e }
                catch (_: Exception) { warnings += "封面获取失败"; null }
            currentCoroutineContext().ensureActive()
            guard()
            progress(SongDownloadProgress("写入歌曲信息"))
            SongTagWriter.write(audio, info, lrc, cover)
            val files = mutableListOf(SongDownloadFile(audio, AudioDownloadFormat.mime(audio.extension)))
            if (lrc.isNotBlank()) files += SongDownloadFile(File(folder, "lyrics.lrc").apply { writeText(lrc, Charsets.UTF_8) }, "application/octet-stream")
            if (cover != null) files += SongDownloadFile(File(folder, "cover.jpg").apply { writeBytes(cover.bytes) }, "image/jpeg")
            progress(SongDownloadProgress("保存到下载文件夹"))
            val saved = store.save(SongDownloadNames.base(info), files, tree)
            SongDownloadResult(saved, warnings.joinToString("；"), AudioQuality.from(source.level).label)
        } finally {
            // Only this attempt's private directory, never the user's download folder.
            folder.deleteRecursively()
        }
    }

    private suspend fun fetch(url: String, target: File, limit: Long, progress: suspend (Long, Long) -> Unit) {
        val call = http.newCall(Request.Builder().url(url).header("Accept-Encoding", "identity").build())
        val response = suspendCancellableCoroutine<Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) { continuation.resume(response, onCancellation = { _, value, _ -> value.close() }) }
            })
        }
        // Cancel blocking body reads promptly, including while the remote host stops sending.
        val watcher = CoroutineScope(currentCoroutineContext()).launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try { response.use {
            check(it.isSuccessful) { "下载服务器返回 ${it.code}，请重试" }
            val body = it.body ?: throw IOException("下载内容为空")
            val total = body.contentLength()
            check(total <= limit) { "下载内容超过支持大小" }
            var received = 0L; var lastProgress = 0L
            body.byteStream().use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(65536)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    received += count; check(received <= limit) { "下载内容超过支持大小" }
                    output.write(buffer, 0, count)
                    val now = System.nanoTime()
                    if (now - lastProgress > 300_000_000) { progress(received, total); lastProgress = now }
                }
            } }
            check(received > 0 && (total < 0 || received == total)) { "音频下载不完整，请重试" }
            progress(received, total)
        } } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            throw error
        } finally { watcher.cancel(); response.close() }
    }

    private fun normalizeCover(file: File): DownloadCover {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "封面格式无效" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("封面无法读取")
        return try {
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output))
            DownloadCover(output.toByteArray(), bitmap.width, bitmap.height)
        } finally { bitmap.recycle() }
    }

    private suspend fun remuxAac(input: File, output: File) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(input.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
            } ?: throw IOException("AAC 音频无法读取")
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val activeMuxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
            val target = activeMuxer.addTrack(format); activeMuxer.start()
            val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 1024 * 1024
            val buffer = ByteBuffer.allocate(size.coerceIn(1024 * 1024, 16 * 1024 * 1024))
            val info = MediaCodec.BufferInfo()
            while (true) {
                currentCoroutineContext().ensureActive(); buffer.clear()
                val count = extractor.readSampleData(buffer, 0)
                if (count < 0) break
                check(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "当前音源无法保存，请尝试其他音质" }
                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, count, extractor.sampleTime, flags)
                activeMuxer.writeSampleData(target, buffer, info); extractor.advance()
            }
            activeMuxer.stop()
        } finally { extractor.release(); muxer?.release() }
    }
}

object AudioDownloadFormat {
    fun detect(file: File): String {
        val head = file.inputStream().use { input -> ByteArray(64).let { bytes ->
            bytes.copyOf(input.read(bytes).coerceAtLeast(0))
        } }
        fun starts(text: String) = head.take(text.length).toByteArray().contentEquals(text.toByteArray(Charsets.US_ASCII))
        return when {
            starts("fLaC") -> "flac"
            starts("OggS") -> {
                if (head.toString(Charsets.ISO_8859_1).contains("OpusHead"))
                    throw IOException("Opus 格式暂不支持写入歌曲信息，请选择标准或无损音质")
                "ogg"
            }
            starts("RIFF") && head.size >= 12 && head.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WAVE" -> "wav"
            head.size >= 8 && head.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp" -> "m4a"
            starts("ID3") -> "mp3"
            head.size >= 2 && head[0].toInt() and 255 == 255 && head[1].toInt() and 246 == 240 -> "aac"
            head.size >= 2 && head[0].toInt() and 255 == 255 && head[1].toInt() and 224 == 224 && head[1].toInt() and 6 != 0 -> "mp3"
            else -> throw IOException("当前音频格式暂不支持下载，请尝试标准或无损音质")
        }
    }
    fun mime(format: String) = when (format.lowercase(Locale.ROOT)) {
        "mp3" -> "audio/mpeg"; "flac" -> "audio/flac"; "m4a" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"; "wav" -> "audio/wav"; else -> "application/octet-stream"
    }
}
