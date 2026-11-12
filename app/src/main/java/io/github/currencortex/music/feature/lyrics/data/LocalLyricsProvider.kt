package io.github.currencortex.music.feature.lyrics.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale

/** Reads the audio tag through an existing media permission; never modifies or copies the audio. */
class LocalLyricsProvider(private val context: Context) {
    suspend fun load(song: Song): SavedLyrics? = withContext(Dispatchers.IO) {
        if (song.musicSource != MusicSource.LOCAL || song.localUri.isBlank()) return@withContext null
        val uri = Uri.parse(song.localUri)
        val name = displayName(uri)
        // A loose .lrc beside the audio is the common "local lyrics" shape; prefer it over tags.
        sidecarLyricsDocument(uri, name)?.let {
            return@withContext SavedLyrics(it, LyricsOrigin("本地歌词文件", "local", song.localUri))
        }
        val candidates = try {
            val resolver = context.contentResolver
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                .takeIf { it in audioExtensions }
                ?: try { audioMimeExtensions[resolver.getType(uri)?.substringBefore(';')?.lowercase(Locale.ROOT)] }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            ensureActive()
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val file = File("/proc/self/fd/${descriptor.fd}")
                // readAs supplies the container type for descriptors. jaudiotagger's readMagic
                // does not identify this FLAC on Android, so use a bounded header fallback.
                val container = extension ?: FileInputStream(file).use { input ->
                    val header = ByteArray(64)
                    var length = 0
                    while (length < header.size) {
                        val count = input.read(header, length, header.size - length)
                        if (count <= 0) break
                        length += count
                    }
                    embeddedAudioExtension(header.copyOf(length))
                } ?: return@use emptyList()
                if (container == "flac") {
                    // FlacTag construction decodes cover art through javax.imageio, absent on
                    // Android; read the lyric comments directly and only fall back when empty.
                    val direct = flacEmbeddedLyricsCandidates(file)
                    if (direct.isNotEmpty()) direct else try {
                        embeddedLyricsCandidates(AudioFileIO.readAs(file, container).tag)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { emptyList() }
                    catch (_: LinkageError) { emptyList() }
                } else {
                    val audio = AudioFileIO.readAs(file, container)
                    embeddedLyricsCandidates(audio.tag)
                }
            } ?: throw IOException("Content provider returned no file descriptor")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw EmbeddedLyricsReadException(e) }
        catch (e: LinkageError) { throw EmbeddedLyricsReadException(e) }
        ensureActive()
        decodeEmbeddedLyricsCandidates(candidates, song.durationMs) { ensureActive() }?.let {
            SavedLyrics(it, LyricsOrigin("本地内嵌歌词", "local", song.localUri))
        }
    }

    private fun displayName(uri: Uri): String {
        // Display names are optional for document providers and file:// does not need a query.
        if (uri.scheme == "file") return uri.lastPathSegment.orEmpty()
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                val column = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && it.moveToFirst()) it.getString(column) else null
            } ?: uri.lastPathSegment.orEmpty()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { uri.lastPathSegment.orEmpty() }
    }

    private fun sidecarLyricsDocument(uri: Uri, audioName: String): LyricsDocument? {
        val names = sidecarFileNames(audioName)
        if (names.isEmpty()) return null
        val bytes = readSidecarBytes(uri, names) ?: return null
        return runCatching { LyricsFileCodec.decode(bytes, names.first()).document }
            .getOrNull()?.takeIf { it.lines.isNotEmpty() }
    }

    private fun readSidecarBytes(uri: Uri, names: List<String>): ByteArray? {
        if (uri.scheme == "file") {
            val parent = File(uri.path ?: return null).parentFile ?: return null
            return readSiblingFile(parent, names)
        }
        val resolver = context.contentResolver
        if (DocumentsContract.isDocumentUri(context, uri)) {
            return runCatching {
                val docId = DocumentsContract.getDocumentId(uri)
                val parent = docId.substringBeforeLast('/', "")
                if (parent.isEmpty()) return@runCatching null
                val tree = DocumentsContract.buildTreeDocumentUri(uri.authority, DocumentsContract.getTreeDocumentId(uri))
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
                resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { cursor ->
                    val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    while (cursor.moveToNext()) {
                        val display = cursor.getString(nameColumn).orEmpty()
                        if (names.any { it.equals(display, ignoreCase = true) }) {
                            val child = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(idColumn))
                            return@runCatching resolver.openInputStream(child)?.use { input ->
                                input.readBytes().takeIf { it.size <= LyricsFileCodec.MAX_BYTES }
                            }
                        }
                    }
                    null
                }
            }.getOrNull()
        }
        // MediaStore audio ids carry no path; resolve DATA and try the sibling file directly.
        return runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@runCatching null
                val parent = File(cursor.getString(0).orEmpty()).parentFile ?: return@runCatching null
                readSiblingFile(parent, names)
            }
        }.getOrNull()
    }

    private fun readSiblingFile(parent: File, names: List<String>): ByteArray? =
        names.firstNotNullOfOrNull { name ->
            val file = File(parent, name)
            if (file.isFile && file.length() <= LyricsFileCodec.MAX_BYTES) runCatching { file.readBytes() }.getOrNull() else null
        }
}

/** Same-base-name lyric sidecars, case variants included; blank names yield nothing. */
internal fun sidecarFileNames(audioName: String): List<String> {
    val base = audioName.substringBeforeLast('.', "").trim()
    if (base.isBlank()) return emptyList()
    return listOf("$base.lrc", "$base.LRC")
}

private val audioExtensions = setOf("mp3", "flac", "m4a", "mp4", "ogg", "opus", "wav", "wma", "aif", "aiff")
private val audioMimeExtensions = mapOf(
    "audio/mpeg" to "mp3", "audio/mp3" to "mp3", "audio/flac" to "flac", "audio/x-flac" to "flac",
    "audio/mp4" to "m4a", "audio/x-m4a" to "m4a", "audio/ogg" to "ogg", "application/ogg" to "ogg",
    "audio/opus" to "opus", "audio/wav" to "wav", "audio/x-wav" to "wav", "audio/vnd.wave" to "wav",
    "audio/x-ms-wma" to "wma", "audio/aiff" to "aiff", "audio/x-aiff" to "aiff",
)
