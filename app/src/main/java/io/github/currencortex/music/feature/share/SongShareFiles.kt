package io.github.currencortex.music.feature.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.coverRequestUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

internal data class SongShareFile(val file: File, val content: SongShareContent, val coverLoaded: Boolean)

internal object SongShareFiles {
    suspend fun create(context: Context, song: Song, style: SongShareStyle = SongShareStyle.SQUARE,
        imageLoader: ImageLoader = SingletonImageLoader.get(context)): SongShareFile {
        val content = SongShareContent.from(song)
        val result = try {
            if (song.cover.isBlank()) null else withTimeoutOrNull(8000) {
                imageLoader.execute(ImageRequest.Builder(context)
                    .data(coverRequestUrl(song.cover, 1000)).size(800).allowHardware(false).build())
            }
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { null }
        val cover = (result as? SuccessResult)?.image?.toBitmap()
        return withContext(Dispatchers.Default) {
            val bitmap = SongSharePoster.render(content, cover, style)
            try {
                write(context, content, bitmap, cover != null)
            } finally { bitmap.recycle() }
        }
    }

    // Preview, export and sharing use the same locally rendered PNG.
    // Cover bitmaps belong to Coil's cache and must not be recycled here.
    private suspend fun write(context: Context, content: SongShareContent, bitmap: Bitmap,
        coverLoaded: Boolean): SongShareFile = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "song-shares").apply { check(mkdirs() || isDirectory) }
        val cutoff = System.currentTimeMillis() - 86_400_000L
        directory.listFiles()?.filter { it.isFile && it.name.startsWith("poster-") && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
        val file = File.createTempFile("poster-", ".png", directory)
        try {
            file.outputStream().buffered().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            SongShareFile(file, content, coverLoaded)
        } catch (error: Exception) { file.delete(); throw error }
    }

    fun shareIntent(context: Context, poster: SongShareFile): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", poster.file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, poster.content.text)
            putExtra(Intent.EXTRA_TITLE, poster.content.title)
            clipData = ClipData.newUri(context.contentResolver, "歌曲分享海报", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun save(context: Context, poster: SongShareFile, uri: Uri) = withContext(Dispatchers.IO) {
        checkNotNull(context.contentResolver.openOutputStream(uri, "w")) { "无法打开保存位置" }.use { target ->
            poster.file.inputStream().use { it.copyTo(target) }
        }
    }
}
