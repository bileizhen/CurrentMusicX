package io.github.currencortex.music.core.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.embeddedAudioExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagTextField
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.framebody.FrameBodyCOMM
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.wav.WavTag
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.log10
import kotlin.math.max

/** Reads ReplayGain / R128 / iTunNORM tags from local audio files without altering or copying audio. */
internal class LocalLoudnessTagReader(private val context: Context) {
    suspend fun read(song: Song): TrackLoudnessMetadata? = withContext(Dispatchers.IO) {
        if (song.musicSource != MusicSource.LOCAL || song.localUri.isBlank()) return@withContext null
        val uri = Uri.parse(song.localUri)
        val name = displayName(uri)
        val resolver = context.contentResolver
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            .takeIf { it in AUDIO_EXTENSIONS }
            ?: runCatching { MIME_EXTENSIONS[resolver.getType(uri)?.substringBefore(';')?.lowercase(Locale.ROOT)] }.getOrNull()
        coroutineContext.ensureActive()
        try {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val file = File("/proc/self/fd/${descriptor.fd}")
                val container = extension ?: FileInputStream(file).use { input ->
                    val header = ByteArray(64)
                    var length = 0
                    while (length < header.size) {
                        val count = input.read(header, length, header.size - length)
                        if (count <= 0) break
                        length += count
                    }
                    embeddedAudioExtension(header.copyOf(length))
                } ?: return@use null
                if (container == "flac") {
                    parseTagPairs(readFlacVorbisTagPairs(file))
                } else {
                    val audio = AudioFileIO.readAs(file, container)
                    readFromJaudiotaggerTag(audio.tag)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment.orEmpty()
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "mp4", "aac", "ogg", "oga", "opus", "wav", "aiff", "aif")
        private val MIME_EXTENSIONS = mapOf(
            "audio/mpeg" to "mp3",
            "audio/flac" to "flac",
            "audio/x-flac" to "flac",
            "audio/mp4" to "m4a",
            "audio/m4a" to "m4a",
            "audio/x-m4a" to "m4a",
            "audio/ogg" to "ogg",
            "audio/opus" to "opus",
            "audio/wav" to "wav",
            "audio/x-wav" to "wav",
            "audio/aiff" to "aiff",
            "audio/x-aiff" to "aiff",
        )
        private const val MAX_VORBIS_COMMENT_BYTES = 2 * 1024 * 1024

        internal fun readFromJaudiotaggerTag(audioTag: Tag?): TrackLoudnessMetadata? {
            val tag = (audioTag as? WavTag)?.getID3Tag() ?: audioTag ?: return null
            val pairs = mutableListOf<Pair<String, String>>()
            val iterator = tag.fields
            while (iterator.hasNext()) {
                val field = iterator.next()
                when (field) {
                    is AbstractID3v2Frame -> {
                        when (val body = field.body) {
                            is FrameBodyTXXX -> pairs += body.description.orEmpty() to body.text.orEmpty()
                            is FrameBodyCOMM -> if (body.description.equals("iTunNORM", ignoreCase = true)) {
                                pairs += "ITUNNORM" to body.text.orEmpty()
                            }
                        }
                    }
                    is TagTextField -> {
                        val rawId = field.id.orEmpty()
                        val normalizedKey = if (tag is Mp4Tag) {
                            rawId.substringAfterLast(':').ifBlank { rawId }
                        } else rawId
                        pairs += normalizedKey to field.content.orEmpty()
                    }
                }
            }
            return parseTagPairs(pairs)
        }

        internal fun readFlacVorbisTagPairs(file: File): List<Pair<String, String>> {
            val pairs = mutableListOf<Pair<String, String>>()
            try {
                RandomAccessFile(file, "r").use { raf ->
                    val magic = ByteArray(4)
                    raf.readFully(magic)
                    if (!magic.contentEquals("fLaC".toByteArray(Charsets.US_ASCII))) return emptyList()
                    while (true) {
                        val header = raf.read()
                        if (header < 0) break
                        val last = header and 0x80 != 0
                        val type = header and 0x7F
                        val length = (raf.read() shl 16) or (raf.read() shl 8) or raf.read()
                        if (length < 0 || length > MAX_VORBIS_COMMENT_BYTES) break
                        if (type == 4) {
                            val data = ByteArray(length)
                            raf.readFully(data)
                            parseVorbisCommentsBlock(data, pairs)
                            break
                        } else {
                            raf.seek(raf.filePointer + length)
                        }
                        if (last) break
                    }
                }
            } catch (_: Exception) {
                return pairs
            }
            return pairs
        }

        internal fun parseVorbisCommentsBlock(data: ByteArray, out: MutableList<Pair<String, String>>) {
            var offset = 0
            fun le32(): Int {
                if (offset + 4 > data.size) throw IOException("truncated vorbis comment")
                val v = (data[offset].toInt() and 255) or ((data[offset + 1].toInt() and 255) shl 8) or
                    ((data[offset + 2].toInt() and 255) shl 16) or ((data[offset + 3].toInt() and 255) shl 24)
                offset += 4
                return v
            }
            val vendorLen = le32()
            if (vendorLen < 0 || offset + vendorLen > data.size) return
            offset += vendorLen
            val count = le32()
            if (count < 0 || count > 4096) return
            repeat(count) {
                val len = le32()
                if (len < 0 || offset + len > data.size) return
                val entry = String(data, offset, len, Charsets.UTF_8)
                offset += len
                val idx = entry.indexOf('=')
                if (idx > 0) {
                    out += entry.substring(0, idx) to entry.substring(idx + 1)
                }
            }
        }

        internal fun parseTagPairs(pairs: List<Pair<String, String>>): TrackLoudnessMetadata? {
            if (pairs.isEmpty()) return null
            var rgTrackGain: Float? = null
            var rgAlbumGain: Float? = null
            var rgTrackPeak: Float? = null
            var rgAlbumPeak: Float? = null
            var r128TrackGain: Int? = null
            var r128AlbumGain: Int? = null
            var iTunNorm: Pair<Float, Float?>? = null

            for ((rawKey, rawVal) in pairs) {
                val key = rawKey.trim().uppercase(Locale.ROOT)
                when (key) {
                    "REPLAYGAIN_TRACK_GAIN" -> if (rgTrackGain == null) rgTrackGain = parseReplayGainDb(rawVal)
                    "REPLAYGAIN_ALBUM_GAIN" -> if (rgAlbumGain == null) rgAlbumGain = parseReplayGainDb(rawVal)
                    "REPLAYGAIN_TRACK_PEAK" -> if (rgTrackPeak == null) rgTrackPeak = parseReplayGainPeak(rawVal)
                    "REPLAYGAIN_ALBUM_PEAK" -> if (rgAlbumPeak == null) rgAlbumPeak = parseReplayGainPeak(rawVal)
                    "R128_TRACK_GAIN" -> if (r128TrackGain == null) r128TrackGain = rawVal.trim().toIntOrNull()
                    "R128_ALBUM_GAIN" -> if (r128AlbumGain == null) r128AlbumGain = rawVal.trim().toIntOrNull()
                    "ITUNNORM" -> if (iTunNorm == null) iTunNorm = parseItunNorm(rawVal)
                }
            }

            LoudnessMetadataMapper.fromReplayGain(
                trackGainDb = rgTrackGain,
                trackPeak = rgTrackPeak,
                albumGainDb = rgAlbumGain,
                albumPeak = rgAlbumPeak,
                sourceLabel = "内嵌 ReplayGain 标签",
            )?.let { return it }

            LoudnessMetadataMapper.fromR128Gain(
                trackGainQ78 = r128TrackGain,
                albumGainQ78 = r128AlbumGain,
                sourceLabel = "内嵌 R128 标签",
            )?.let { return it }

            iTunNorm?.let { (gainDb, peak) ->
                return TrackLoudnessMetadata(
                    trackLufs = ITUNNORM_REFERENCE_LUFS - gainDb,
                    trackPeak = peak,
                    authoritative = true,
                    sourceLabel = "内嵌 iTunNORM 标签",
                )
            }
            return null
        }

        internal fun parseReplayGainDb(raw: String): Float? {
            val cleaned = raw.trim().replace(",", ".")
            val match = GAIN_REGEX.find(cleaned) ?: return null
            return match.groupValues[1].toFloatOrNull()?.takeIf { it.isFinite() && it in -40f..40f }
        }

        internal fun parseReplayGainPeak(raw: String): Float? {
            val cleaned = raw.trim().replace(",", ".")
            return cleaned.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f && it <= 4f }
        }

        internal fun parseItunNorm(raw: String): Pair<Float, Float?>? {
            val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.size < 8) return null
            val v0 = tokens[0].toLongOrNull(16) ?: return null
            val v1 = tokens[1].toLongOrNull(16) ?: return null
            val scale = max(v0, v1)
            if (scale <= 0L) return null
            val gainDb = (10.0 * log10(1000.0 / scale)).toFloat()
            if (!gainDb.isFinite() || gainDb !in -40f..40f) return null
            val p0 = tokens[6].toLongOrNull(16) ?: 0L
            val p1 = tokens[7].toLongOrNull(16) ?: 0L
            val peak = (max(p0, p1) / 32768.0).toFloat().takeIf { it.isFinite() && it > 0f && it <= 4f }
            return gainDb to peak
        }

        private val GAIN_REGEX = Regex("^([+-]?\\d+(?:\\.\\d+)?)\\s*(?:db)?$", RegexOption.IGNORE_CASE)
    }
}
