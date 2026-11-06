package io.github.currencortex.music.feature.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import io.github.currencortex.music.core.media.LocalArtworkProvider
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class LocalDocument(val id: String, val name: String, val mime: String) {
    val directory get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    val audio get() = mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in
        setOf("mp3", "flac", "aac", "m4a", "ogg", "opus", "wav", "aiff", "aif", "ape", "amr")
}

/** Walk only descendants returned for the granted tree; never fall back to a device-wide scan. */
internal suspend fun walkLocalAudio(root: String, children: suspend (String) -> List<LocalDocument>,
    onAudio: suspend (LocalDocument) -> Unit) {
    val pending = ArrayDeque<String>().apply { add(root) }
    val seen = mutableSetOf(root)
    while (pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        for (document in children(pending.removeFirst())) {
            currentCoroutineContext().ensureActive()
            if (!seen.add(document.id)) continue
            if (document.directory) pending.add(document.id) else if (document.audio) onAudio(document)
        }
    }
}

internal suspend fun scanLocalFolder(context: Context, tree: Uri): List<Song> {
    val resolver = context.contentResolver
    val songs = mutableListOf<Song>()
    val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
    walkLocalAudio(DocumentsContract.getTreeDocumentId(tree), children = { id ->
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            val documents = mutableListOf<LocalDocument>()
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                documents += LocalDocument(cursor.getString(0), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty())
            }
            check(!cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) { "文件夹仍在加载，请重试" }
            documents
        } ?: error("无法读取所选文件夹")
    }, onAudio = { document ->
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, document.id)
        var title = document.name.substringBeforeLast('.').ifBlank { "未知歌曲" }
        var artist = ""
        var album = ""
        var duration = 0L
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(context, uri)
            fun text(key: Int) = metadata.extractMetadata(key).orEmpty().takeUnless { it == "<unknown>" }.orEmpty()
            title = text(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank { title }
            artist = text(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = text(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            duration = text(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull()?.coerceAtLeast(0) ?: 0
        } catch (denied: SecurityException) { throw denied }
        catch (_: RuntimeException) { /* Unsupported tags do not prevent importing a playable file. */ }
        finally { metadata.release() }
        songs += Song(id = localSongId(uri.toString()), name = title, artists = artist, album = album,
            cover = LocalArtworkProvider.artworkUri(context, uri).toString(),
            durationMs = duration, musicSource = MusicSource.LOCAL, localUri = uri.toString())
    })
    return songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}
