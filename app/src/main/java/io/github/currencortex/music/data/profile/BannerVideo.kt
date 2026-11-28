package io.github.currencortex.music.data.profile

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import io.github.currencortex.music.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

enum class BannerMediaKind { IMAGE, VIDEO, UNSUPPORTED }

data class BannerVideoInfo(
    val durationMs: Long,
    val displayWidth: Int,
    val displayHeight: Int,
    val sizeBytes: Long,
    val uploadMimeType: String,
    val hasAudio: Boolean,
)

data class BannerTrimSelection(
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

data class PreparedBannerVideo(
    val file: File,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
)

object BannerVideoRules {
    const val MIN_TRIM_DURATION_MS = 1_000L
    private const val STANDARD_MAX_DURATION_MS = 5_000L
    private const val ADMIN_MAX_DURATION_MS = 10_000L
    private const val EXPORT_MARGIN_MS = 100L

    private val MP4_BRANDS = setOf(
        "isom", "iso2", "iso3", "iso4", "iso5", "iso6",
        "mp41", "mp42", "avc1", "M4V ", "m4v ",
    )
    private val NON_VIDEO_ISOBMFF_BRANDS = setOf(
        "heic", "heix", "hevc", "hevx", "mif1", "msf1",
        "avif", "avis", "M4A ", "M4B ", "M4P ",
    )

    fun minSelectableDurationMs(totalDurationMs: Long): Long =
        if (totalDurationMs <= 0L) 0L else min(MIN_TRIM_DURATION_MS, totalDurationMs)

    fun maxDurationMs(role: UserRole?): Long =
        if (role == null) STANDARD_MAX_DURATION_MS else ADMIN_MAX_DURATION_MS

    fun defaultRange(totalDurationMs: Long, maxDurationMs: Long = STANDARD_MAX_DURATION_MS): BannerTrimSelection {
        if (totalDurationMs <= 0L) return BannerTrimSelection(0L, 0L)
        return BannerTrimSelection(0L, min(maxDurationMs, totalDurationMs))
    }

    fun normalizeRange(totalDurationMs: Long, startMs: Long, endMs: Long, maxDurationMs: Long): BannerTrimSelection {
        if (totalDurationMs <= 0L) return BannerTrimSelection(0L, 0L)
        val minLen = minSelectableDurationMs(totalDurationMs)
        val maxLen = min(maxDurationMs, totalDurationMs)
        val s = startMs.coerceIn(0L, (totalDurationMs - minLen).coerceAtLeast(0L))
        val e = endMs.coerceIn(s + minLen, min(totalDurationMs, s + maxLen))
        return BannerTrimSelection(s, e)
    }

    fun shiftWindow(totalDurationMs: Long, startMs: Long, endMs: Long, newStartMs: Long, maxDurationMs: Long = STANDARD_MAX_DURATION_MS): BannerTrimSelection {
        if (totalDurationMs <= 0L) return BannerTrimSelection(0L, 0L)
        val current = normalizeRange(totalDurationMs, startMs, endMs, maxDurationMs)
        val len = current.durationMs
        val clampedStart = newStartMs.coerceIn(0L, (totalDurationMs - len).coerceAtLeast(0L))
        return BannerTrimSelection(clampedStart, (clampedStart + len).coerceAtMost(totalDurationMs))
    }

    fun moveStart(totalDurationMs: Long, currentEndMs: Long, newStartMs: Long, maxDurationMs: Long = STANDARD_MAX_DURATION_MS): BannerTrimSelection {
        if (totalDurationMs <= 0L) return BannerTrimSelection(0L, 0L)
        val minLen = minSelectableDurationMs(totalDurationMs)
        val maxLen = min(maxDurationMs, totalDurationMs)
        val end = currentEndMs.coerceIn(minLen, totalDurationMs)
        val start = newStartMs.coerceIn((end - maxLen).coerceAtLeast(0L), end - minLen)
        return BannerTrimSelection(start, end)
    }

    fun moveEnd(totalDurationMs: Long, currentStartMs: Long, newEndMs: Long, maxDurationMs: Long = STANDARD_MAX_DURATION_MS): BannerTrimSelection {
        if (totalDurationMs <= 0L) return BannerTrimSelection(0L, 0L)
        val minLen = minSelectableDurationMs(totalDurationMs)
        val maxLen = min(maxDurationMs, totalDurationMs)
        val start = currentStartMs.coerceIn(0L, (totalDurationMs - minLen).coerceAtLeast(0L))
        val end = newEndMs.coerceIn(start + minLen, min(totalDurationMs, start + maxLen))
        return BannerTrimSelection(start, end)
    }

    /**
     * Leaves room for frame-rate rounding when re-encoding near the server's duration limit.
     */
    fun exportClipEndMs(totalDurationMs: Long, startMs: Long, endMs: Long, maxDurationMs: Long): Long {
        val range = normalizeRange(totalDurationMs, startMs, endMs, maxDurationMs)
        val safeDurationMs = maxDurationMs - EXPORT_MARGIN_MS
        return min(range.endMs, range.startMs + safeDurationMs)
    }

    fun canUploadDirectly(
        info: BannerVideoInfo,
        startMs: Long,
        endMs: Long,
        maxDurationMs: Long = STANDARD_MAX_DURATION_MS,
        keepAudio: Boolean = false,
    ): Boolean {
        if (info.hasAudio && !keepAudio) return false
        if (info.uploadMimeType != "video/mp4" && info.uploadMimeType != "video/quicktime") return false
        if (info.durationMs !in 1L..maxDurationMs) return false
        if (info.sizeBytes !in 1L..PROFILE_BANNER_VIDEO_LIMIT) return false
        if (info.displayWidth <= 0 || info.displayHeight <= 0) return false
        if (max(info.displayWidth, info.displayHeight) > PROFILE_BANNER_VIDEO_MAX_DIMENSION) return false
        if (info.displayWidth.toLong() * info.displayHeight.toLong() > PROFILE_BANNER_VIDEO_MAX_PIXELS) return false
        return startMs == 0L && endMs == info.durationMs
    }

    fun detectContainerMimeFromFtyp(header: ByteArray): String? {
        if (header.size < 12) return null
        if (header[4] != 'f'.code.toByte() || header[5] != 't'.code.toByte() ||
            header[6] != 'y'.code.toByte() || header[7] != 'p'.code.toByte()
        ) {
            return null
        }
        val major = String(header, 8, 4, Charsets.US_ASCII)
        if (major in NON_VIDEO_ISOBMFF_BRANDS) return null
        if (major == "qt  ") return "video/quicktime"
        if (major in MP4_BRANDS) return "video/mp4"
        var offset = 16
        while (offset + 4 <= header.size) {
            val brand = String(header, offset, 4, Charsets.US_ASCII)
            if (brand == "qt  ") return "video/quicktime"
            if (brand in MP4_BRANDS) return "video/mp4"
            offset += 4
        }
        return null
    }

    fun normalizeUploadVideoMime(rawMime: String?, sniffedMime: String?): String? {
        val cleaned = rawMime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        return when (cleaned) {
            "video/mp4", "video/m4v", "video/x-m4v" -> sniffedMime ?: "video/mp4"
            "video/quicktime" -> "video/quicktime"
            null, "", "video/*", "application/octet-stream" -> sniffedMime
            else -> if (cleaned.startsWith("video/")) sniffedMime else null
        }
    }

    fun formatSeconds(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val tenths = (clamped / 100L).toInt()
        return String.format(Locale.US, "%d.%ds", tenths / 10, tenths % 10)
    }
}

internal fun detectBannerMediaKind(context: Context, uri: Uri): BannerMediaKind {
    val resolver = context.contentResolver
    val rawMime = resolver.getType(uri)?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    val header = readHeaderBytes(resolver, uri, 64)
    val sniffedVideo = BannerVideoRules.detectContainerMimeFromFtyp(header)
    if (BannerVideoRules.normalizeUploadVideoMime(rawMime, sniffedVideo) != null) {
        return BannerMediaKind.VIDEO
    }
    if (rawMime?.startsWith("video/") == true) {
        return BannerMediaKind.UNSUPPORTED
    }
    if (rawMime?.startsWith("image/") == true || sniffImageBounds(resolver, uri)) {
        return BannerMediaKind.IMAGE
    }
    return if (sniffedVideo != null) BannerMediaKind.VIDEO else BannerMediaKind.UNSUPPORTED
}

internal fun inspectBannerVideo(context: Context, uri: Uri): BannerVideoInfo? {
    val resolver = context.contentResolver
    val rawMime = resolver.getType(uri)
    val sniffedMime = BannerVideoRules.detectContainerMimeFromFtyp(readHeaderBytes(resolver, uri, 64))
    val uploadMime = BannerVideoRules.normalizeUploadVideoMime(rawMime, sniffedMime) ?: return null
    val sizeBytes = queryUriSizeBytes(resolver, uri)

    val retriever = MediaMetadataRetriever()
    return try {
        setRetrieverDataSource(context, uri, retriever)
        val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
        if (!hasVideo.equals("yes", ignoreCase = true) && hasTrack(context, uri, "video/") != true) {
            return null
        }
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ?: readDurationFromExtractor(context, uri)
            ?: 0L
        if (durationMs <= 0L) return null

        val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val (displayWidth, displayHeight) = if (rotation % 180 != 0) rawHeight to rawWidth else rawWidth to rawHeight
        if (displayWidth <= 0 || displayHeight <= 0) return null

        BannerVideoInfo(
            durationMs = durationMs,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            sizeBytes = sizeBytes,
            uploadMimeType = uploadMime,
            // Unknown audio metadata must not let a source bypass audio removal on upload.
            hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                .equals("yes", ignoreCase = true) || hasTrack(context, uri, "audio/") != false,
        )
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

internal suspend fun loadBannerVideoThumbnails(
    context: Context,
    uri: Uri,
    durationMs: Long,
    count: Int = 8,
): List<Bitmap> = withContext(Dispatchers.IO) {
    if (durationMs <= 0L || count <= 0) return@withContext emptyList()
    val retriever = MediaMetadataRetriever()
    val frames = ArrayList<Bitmap>(count)
    try {
        setRetrieverDataSource(context, uri, retriever)
        for (i in 0 until count) {
            val fraction = (i + 0.5) / count.toDouble()
            val timeUs = (durationMs * fraction * 1000.0).roundToLong().coerceIn(0L, durationMs * 1000L)
            val frame = if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 180, 120)
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { raw ->
                    scaleBitmapMax(raw, 180, 120).also { scaled ->
                        if (scaled !== raw) raw.recycle()
                    }
                }
            }
            if (frame != null) frames += frame
        }
        frames
    } catch (e: CancellationException) {
        frames.forEach { it.recycle() }
        throw e
    } catch (_: Exception) {
        frames.forEach { it.recycle() }
        emptyList()
    } finally {
        runCatching { retriever.release() }
    }
}

internal suspend fun prepareBannerVideoForUpload(
    context: Context,
    uri: Uri,
    info: BannerVideoInfo,
    startMs: Long,
    endMs: Long,
    maxDurationMs: Long,
    keepAudio: Boolean,
    onStage: (String) -> Unit,
): PreparedBannerVideo {
    val range = BannerVideoRules.normalizeRange(info.durationMs, startMs, endMs, maxDurationMs)
    val cacheDir = File(context.cacheDir, "banner_upload").apply { mkdirs() }

    if (BannerVideoRules.canUploadDirectly(info, range.startMs, range.endMs, maxDurationMs, keepAudio)) {
        onStage("正在准备视频…")
        val ext = if (info.uploadMimeType == "video/quicktime") ".mov" else ".mp4"
        val outFile = File(cacheDir, "banner-direct-${System.currentTimeMillis()}$ext")
        val copied = withContext(Dispatchers.IO) {
            copyUriUpToLimit(context.contentResolver, uri, outFile, PROFILE_BANNER_VIDEO_LIMIT)
        }
        if (copied) {
            return PreparedBannerVideo(
                file = outFile,
                mimeType = info.uploadMimeType,
                width = info.displayWidth,
                height = info.displayHeight,
                durationMs = info.durationMs,
            )
        }
        outFile.delete()
    }

    onStage("正在裁剪并转码为 3:2 MP4…")
    val clipEndMs = BannerVideoRules.exportClipEndMs(info.durationMs, range.startMs, range.endMs, maxDurationMs)
    val highRes = max(info.displayWidth, info.displayHeight) >= 900
    val primaryWidth = if (highRes) 960 else 720
    val primaryHeight = if (highRes) 640 else 480
    val primaryBitrate = if (highRes) 4_200_000 else 2_600_000

    val primaryFile = File(cacheDir, "banner-trim-${System.currentTimeMillis()}.mp4")
    try {
        exportTrimmedBannerMp4(
            context = context,
            uri = uri,
            keepAudio = keepAudio,
            startMs = range.startMs,
            endMs = range.endMs,
            targetWidth = primaryWidth,
            targetHeight = primaryHeight,
            bitrate = primaryBitrate,
            outputFile = primaryFile,
        )
        val verified = inspectLocalVideoFile(primaryFile)
        if (primaryFile.length() in 1L..PROFILE_BANNER_VIDEO_LIMIT &&
            verified != null && verified.first <= maxDurationMs
        ) {
            return PreparedBannerVideo(
                file = primaryFile,
                mimeType = "video/mp4",
                width = primaryWidth,
                height = primaryHeight,
                durationMs = verified.first,
            )
        }
        primaryFile.delete()
    } catch (e: CancellationException) {
        primaryFile.delete()
        throw e
    } catch (_: Exception) {
        primaryFile.delete()
    }

    onStage("正在压缩视频体积…")
    val fallbackFile = File(cacheDir, "banner-fallback-${System.currentTimeMillis()}.mp4")
    try {
        exportTrimmedBannerMp4(
            context = context,
            uri = uri,
            keepAudio = keepAudio,
            startMs = range.startMs,
            endMs = clipEndMs,
            targetWidth = 720,
            targetHeight = 480,
            bitrate = 1_800_000,
            outputFile = fallbackFile,
        )
        val size = fallbackFile.length()
        if (size <= 0L) throw IOException("生成的视频为空")
        if (size > PROFILE_BANNER_VIDEO_LIMIT) {
            throw IOException("压缩后视频仍超过 16 MiB，请缩短裁切时长后重试")
        }
        val verified = inspectLocalVideoFile(fallbackFile)
        val outDuration = verified?.first ?: (clipEndMs - range.startMs)
        if (outDuration > maxDurationMs) {
            throw IOException("裁切后的视频超过 ${maxDurationMs / 1_000} 秒，请缩短选取片段")
        }
        return PreparedBannerVideo(
            file = fallbackFile,
            mimeType = "video/mp4",
            width = 720,
            height = 480,
            durationMs = outDuration,
        )
    } catch (e: CancellationException) {
        fallbackFile.delete()
        throw e
    } catch (e: Exception) {
        fallbackFile.delete()
        if (keepAudio && e is ExportException) {
            throw IOException(context.getString(R.string.profile_banner_video_export_with_audio_error), e)
        }
        throw e
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun exportTrimmedBannerMp4(
    context: Context,
    uri: Uri,
    startMs: Long,
    endMs: Long,
    targetWidth: Int,
    targetHeight: Int,
    bitrate: Int,
    outputFile: File,
    keepAudio: Boolean,
) {
    outputFile.delete()
    val mainHandler = Handler(Looper.getMainLooper())
    suspendCancellableCoroutine<Unit> { continuation ->
        var transformer: Transformer? = null
        mainHandler.post {
            if (!continuation.isActive) return@post
            try {
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs)
                            .setEndPositionMs(endMs)
                            .build(),
                    )
                    .build()
                val editedItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(!keepAudio)
                    .setEffects(
                        Effects(
                            emptyList<AudioProcessor>(),
                            listOf<Effect>(
                                Presentation.createForWidthAndHeight(
                                    targetWidth,
                                    targetHeight,
                                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                                ),
                            ),
                        ),
                    )
                    .build()
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(bitrate)
                            .build(),
                    )
                    .setEnableFallback(true)
                    .build()
                val built = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .setLooper(Looper.getMainLooper())
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        IOException(exportException.message ?: "视频转码失败", exportException),
                                    )
                                }
                            }
                        },
                    )
                    .build()
                transformer = built
                built.start(editedItem, outputFile.absolutePath)
            } catch (t: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(t)
            }
        }
        continuation.invokeOnCancellation {
            mainHandler.post {
                runCatching { transformer?.cancel() }
                outputFile.delete()
            }
        }
    }
}

private fun inspectLocalVideoFile(file: File): Pair<Long, Long>? {
    if (!file.exists()) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ?: return null
        durationMs to file.length()
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun copyUriUpToLimit(resolver: ContentResolver, uri: Uri, dest: File, limitBytes: Long): Boolean {
    val input = resolver.openInputStream(uri) ?: return false
    var total = 0L
    return input.use { stream ->
        dest.outputStream().use { out ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                total += read
                if (total > limitBytes) return@use false
                out.write(buf, 0, read)
            }
            true
        }
    }
}

private fun readHeaderBytes(resolver: ContentResolver, uri: Uri, maxBytes: Int): ByteArray {
    return try {
        resolver.openInputStream(uri)?.use { stream ->
            val buf = ByteArray(maxBytes)
            var offset = 0
            while (offset < maxBytes) {
                val read = stream.read(buf, offset, maxBytes - offset)
                if (read <= 0) break
                offset += read
            }
            buf.copyOf(offset)
        } ?: ByteArray(0)
    } catch (_: Exception) {
        ByteArray(0)
    }
}

private fun sniffImageBounds(resolver: ContentResolver, uri: Uri): Boolean {
    return try {
        resolver.openInputStream(uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, opts)
            opts.outWidth > 0 && opts.outHeight > 0
        } ?: false
    } catch (_: Exception) {
        false
    }
}

private fun queryUriSizeBytes(resolver: ContentResolver, uri: Uri): Long {
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (col >= 0 && cursor.moveToFirst() && !cursor.isNull(col)) {
                val value = cursor.getLong(col)
                if (value > 0L) return value
            }
        }
    }
    runCatching {
        resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            if (afd.length > 0L) return afd.length
        }
    }
    return -1L
}

private fun setRetrieverDataSource(context: Context, uri: Uri, retriever: MediaMetadataRetriever) {
    try {
        retriever.setDataSource(context, uri)
    } catch (_: Exception) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("无法打开视频文件")
        pfd.use { retriever.setDataSource(it.fileDescriptor) }
    }
}

private fun hasTrack(context: Context, uri: Uri, mimePrefix: String): Boolean? {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(context, uri, null)
        (0 until extractor.trackCount).any { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true
        }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { extractor.release() }
    }
}

private fun readDurationFromExtractor(context: Context, uri: Uri): Long? {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(context, uri, null)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_DURATION)) {
                return format.getLong(MediaFormat.KEY_DURATION) / 1000L
            }
        }
        null
    } catch (_: Exception) {
        null
    } finally {
        runCatching { extractor.release() }
    }
}

private fun scaleBitmapMax(src: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
    if (src.width <= maxWidth && src.height <= maxHeight) return src
    val scale = min(maxWidth.toFloat() / src.width, maxHeight.toFloat() / src.height)
    val w = (src.width * scale).toInt().coerceAtLeast(1)
    val h = (src.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, w, h, true)
}
