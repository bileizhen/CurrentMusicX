package io.github.currencortex.music.core.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

data class SongDownloadFile(val file: File, val mime: String)
data class SavedSongDownload(val audioUri: Uri, val name: String, val files: List<Uri>)

/** Publish only fully tagged files; cancellation/failure rolls back this attempt's exact URIs. */
class SongDownloadStore(private val context: Context) {
    suspend fun save(base: String, files: List<SongDownloadFile>, tree: Uri?): SavedSongDownload {
        val resolver = context.contentResolver
        val created = mutableListOf<Uri>()
        var actualBase = base
        try {
            for ((index, item) in files.withIndex()) {
                currentCoroutineContext().ensureActive()
                val filename = "$actualBase.${item.file.extension}"
                val uri = (if (tree != null) {
                    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                    DocumentsContract.createDocument(resolver, parent, item.mime, filename)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/CurrentMusic")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    })
                } else {
                    throw IOException("请先选择下载文件夹")
                }) ?: throw IOException("无法创建下载文件，请重新选择文件夹")
                created.add(uri)
                // The provider may add a collision suffix. Keep sidecars matched to that audio name.
                if (index == 0) resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) actualBase = it.getString(0).substringBeforeLast('.')
                }
                resolver.openOutputStream(uri, "w")?.use { output ->
                    item.file.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: throw IOException("下载文件无法写入")
            }
            currentCoroutineContext().ensureActive()
            if (tree == null) created.forEach { uri ->
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1) {
                    "下载文件保存失败"
                }
            }
            return SavedSongDownload(created.first(), "$actualBase.${files.first().file.extension}", created.toList())
        } catch (error: Exception) {
            created.forEach { uri -> runCatching {
                if (tree != null) DocumentsContract.deleteDocument(resolver, uri) else resolver.delete(uri, null, null)
            } }
            throw error
        }
    }
}
