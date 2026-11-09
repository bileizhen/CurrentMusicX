package io.github.currencortex.music.feature.lyrics.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
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
        val candidates = try {
            val resolver = context.contentResolver
            // Display names are optional for document providers and file:// does not need a query.
            val name = if (uri.scheme == "file") uri.lastPathSegment.orEmpty() else try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    val column = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && it.moveToFirst()) it.getString(column) else null
                } ?: uri.lastPathSegment.orEmpty()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { uri.lastPathSegment.orEmpty() }
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
                val audio = AudioFileIO.readAs(file, container)
                embeddedLyricsCandidates(audio.tag)
            } ?: throw IOException("Content provider returned no file descriptor")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw EmbeddedLyricsReadException(e) }
        catch (e: LinkageError) { throw EmbeddedLyricsReadException(e) }
        ensureActive()
        decodeEmbeddedLyricsCandidates(candidates, song.durationMs) { ensureActive() }?.let {
            SavedLyrics(it, LyricsOrigin("本地内嵌歌词", "local", song.localUri))
        }
    }
}

private val audioExtensions = setOf("mp3", "flac", "m4a", "mp4", "ogg", "opus", "wav", "wma", "aif", "aiff")
private val audioMimeExtensions = mapOf(
    "audio/mpeg" to "mp3", "audio/mp3" to "mp3", "audio/flac" to "flac", "audio/x-flac" to "flac",
    "audio/mp4" to "m4a", "audio/x-m4a" to "m4a", "audio/ogg" to "ogg", "application/ogg" to "ogg",
    "audio/opus" to "opus", "audio/wav" to "wav", "audio/x-wav" to "wav", "audio/vnd.wave" to "wav",
    "audio/x-ms-wma" to "wma", "audio/aiff" to "aiff", "audio/x-aiff" to "aiff",
)
