package io.github.currencortex.music.core.storage

import android.content.Context
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import io.github.currencortex.music.core.media.AudioCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Storage broken down the way the user thinks about it. Sizes are read from disk and never
 * derived from account data, credentials or lyrics content.
 */
data class StorageUsage(
    val audio: Long = 0L,
    val audioTracks: Int = 0,
    val data: Long = 0L,
    val downloads: Long = 0L,
    val downloadCount: Int = 0,
    val essential: Long = 0L,
) {
    val total: Long get() = audio + data + downloads + essential
}

class StorageStore(private val context: Context, private val audioCache: AudioCache,
    private val clearImages: () -> Unit = { coil3.SingletonImageLoader.get(context).let { loader ->
        loader.memoryCache?.clear(); loader.diskCache?.clear()
    } }) {
    /** The audio cache lives under cacheDir but is reported and cleared on its own. */
    private val audioDirName: String get() = File(context.cacheDir, "audio").name

    suspend fun measure(): StorageUsage = withContext(Dispatchers.IO) {
        audioCache.refreshUsage()
        val (downloadBytes, downloadCount) = downloads()
        StorageUsage(
            audio = audioCache.bytes.value,
            audioTracks = audioCache.entries.value,
            data = dataCacheBytes(),
            downloads = downloadBytes,
            downloadCount = downloadCount,
            essential = essentialBytes(),
        )
    }

    private fun dataCacheBytes(): Long = runCatching {
        cacheEntries().sumOf { size(it) }
    }.getOrDefault(0L)

    /** Image cache, lyrics cache and every other temporary file; the audio cache is kept. */
    suspend fun clearDataCache(): Long = withContext(Dispatchers.IO) {
        clearImages()
        cacheEntries().filter { it.name != "image_cache" }.forEach { file ->
            check(file.deleteRecursively() || !file.exists()) { "部分缓存无法清理，请稍后重试" }
        }
        dataCacheBytes()
    }

    suspend fun clearAudioCache() = audioCache.clear()

    private fun cacheEntries(): List<File> = context.cacheDir.listFiles().orEmpty()
        .filter { it.name != audioDirName && !it.name.startsWith("audio.") &&
            it.name !in setOf("song-downloads", "updates") }

    private fun essentialBytes(): Long = runCatching {
        val info = context.applicationInfo
        File(info.sourceDir).length() +
            size(info.nativeLibraryDir?.let(::File)) +
            size(context.getDatabasePath("currentmusic").parentFile) +
            size(context.filesDir)
    }.getOrDefault(0L)

    /** Downloads live in the shared Downloads collection, so they are counted through MediaStore. */
    private fun downloads(): Pair<Long, Int> {
        if (Build.VERSION.SDK_INT < 29) return 0L to 0
        return runCatching {
        var bytes = 0L
        var count = 0
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.SIZE),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("${Environment.DIRECTORY_DOWNLOADS}/CurrentMusic/"),
            null,
        )?.use { cursor ->
            val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                if (sizeColumn >= 0) bytes += cursor.getLong(sizeColumn)
                count++
            }
        }
        bytes to count
        }.getOrDefault(0L to 0)
    }

    private fun size(file: File?): Long = when {
        file == null || !file.exists() -> 0L
        file.isFile -> file.length()
        else -> file.listFiles().orEmpty().sumOf(::size)
    }
}
