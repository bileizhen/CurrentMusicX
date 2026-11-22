package io.github.currencortex.music.core.storage

import android.content.Context
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import io.github.currencortex.music.core.media.AudioCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The clearable parts of the data cache, matching the rows on the storage page. */
enum class DataCacheCategory(val label: String) {
    IMAGES("封面缓存"),
    LYRICS("歌词缓存"),
    SHARES("分享图片"),
    OTHER("其他缓存"),
}

/**
 * Storage broken down the way the user thinks about it. Sizes are read from disk and never
 * derived from account data, credentials or lyrics content.
 */
data class StorageUsage(
    val audio: Long = 0L,
    val audioTracks: Int = 0,
    val images: Long = 0L,
    val lyrics: Long = 0L,
    val shares: Long = 0L,
    val other: Long = 0L,
    val downloads: Long = 0L,
    val downloadCount: Int = 0,
    val essential: Long = 0L,
) {
    val data: Long get() = images + lyrics + shares + other
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
        val parts = cacheParts()
        val (downloadBytes, downloadCount) = downloads()
        StorageUsage(
            audio = audioCache.bytes.value,
            audioTracks = audioCache.entries.value,
            images = parts.images,
            lyrics = parts.lyrics,
            shares = parts.shares,
            other = parts.other,
            downloads = downloadBytes,
            downloadCount = downloadCount,
            essential = essentialBytes(),
        )
    }

    private data class CacheParts(val images: Long = 0L, val lyrics: Long = 0L,
        val shares: Long = 0L, val other: Long = 0L) {
        val total: Long get() = images + lyrics + shares + other
    }

    /** One pass over cacheDir, so the four rows always add up to the reported data total. */
    private fun cacheParts(): CacheParts = runCatching {
        val entries = cacheEntries()
        fun sizeOf(predicate: (File) -> Boolean) = entries.filter(predicate).sumOf { size(it) }
        CacheParts(
            images = sizeOf { it.name == "image_cache" },
            lyrics = sizeOf { isLyrics(it.name) },
            shares = sizeOf { it.name == "song-shares" },
            other = sizeOf { !isImages(it.name) && !isLyrics(it.name) && it.name != "song-shares" },
        )
    }.getOrDefault(CacheParts())

    private fun dataCacheBytes(): Long = cacheParts().total

    /**
     * Clears one data-cache category, or every clearable file when [category] is null. The audio
     * cache, downloaded media and pending update packages are never touched.
     */
    suspend fun clearDataCache(category: DataCacheCategory? = null): Long = withContext(Dispatchers.IO) {
        if (category == null || category == DataCacheCategory.IMAGES) clearImages()
        val files = when (category) {
            null -> cacheEntries().filterNot { isImages(it.name) }
            DataCacheCategory.IMAGES -> emptyList()
            DataCacheCategory.LYRICS -> cacheEntries().filter { isLyrics(it.name) }
            DataCacheCategory.SHARES -> cacheEntries().filter { it.name == "song-shares" }
            DataCacheCategory.OTHER -> otherCacheEntries()
        }
        files.forEach { file ->
            check(file.deleteRecursively() || !file.exists()) { "部分缓存无法清理，请稍后重试" }
        }
        dataCacheBytes()
    }

    suspend fun clearAudioCache() = audioCache.clear()

    private fun cacheEntries(): List<File> = context.cacheDir.listFiles().orEmpty()
        .filter { it.name != audioDirName && !it.name.startsWith("audio.") &&
            it.name !in setOf("song-downloads", "mv-downloads", "updates") }

    private fun isImages(name: String) = name == "image_cache"

    /** LyricsCache writes one directory per storage namespace: `lyrics` or `lyrics.<ns>`. */
    private fun isLyrics(name: String) = name == "lyrics" || name.startsWith("lyrics.")

    private fun otherCacheEntries(): List<File> = cacheEntries()
        .filterNot { isImages(it.name) || isLyrics(it.name) || it.name == "song-shares" }

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
