package io.github.currencortex.music.core.download

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import java.io.File

data class MvDownloadResult(val saved: SavedSongDownload, val height: Int)

class MvDownloadEngine(private val cache: File, private val resolve: suspend (Long, Int) -> String,
    private val store: SongDownloadStore, private val guard: suspend () -> Unit = {}) {
    suspend fun download(song: Song, resolution: Int, directory: Uri?, attempt: String,
        progress: suspend (SongDownloadProgress) -> Unit): MvDownloadResult = withContext(Dispatchers.IO) {
        require(song.video && song.mv > 0)
        require(resolution in listOf(240, 480, 720, 1080))
        require(attempt.matches(Regex("[a-zA-Z0-9-]+")))
        val folder = File(cache, attempt).apply { check(mkdirs() || isDirectory) { "下载缓存不可用" } }
        try {
            guard()
            progress(SongDownloadProgress("获取 MV 地址"))
            val url = resolve(song.mv, resolution)
            val file = File(folder, "video.mp4")
            MediaDownloadTransfer().fetch(url, file, 2L * 1024 * 1024 * 1024) { received, total ->
                progress(SongDownloadProgress("下载 MV", received, total))
            }
            progress(SongDownloadProgress("检查视频文件"))
            val header = file.inputStream().use { input -> ByteArray(12).also { check(input.read(it) == 12) { "MV 文件不完整" } } }
            check(header.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp") { "当前 MV 不是可下载的 MP4 视频" }
            val extractor = MediaExtractor()
            val height = try {
                extractor.setDataSource(file.path)
                val video = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                    .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                check(video != null) { "下载内容中没有可播放的视频" }
                video.getInteger(MediaFormat.KEY_HEIGHT)
            } finally { extractor.release() }
            currentCoroutineContext().ensureActive(); guard()
            progress(SongDownloadProgress("保存到下载文件夹"))
            val saved = store.save("${SongDownloadNames.base(song.copy(id = song.mv))} - MV ${height}P",
                listOf(SongDownloadFile(file, "video/mp4")), directory)
            MvDownloadResult(saved, height)
        } finally { folder.deleteRecursively() }
    }
}
