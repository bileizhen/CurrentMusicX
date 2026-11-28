package io.github.currencortex.music.feature.profile

import android.content.ContentResolver
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import io.github.currencortex.music.data.profile.AvatarCrop
import io.github.currencortex.music.data.profile.BannerCrop
import io.github.currencortex.music.data.profile.PROFILE_BANNER_ORIGINAL_IMAGE_LIMIT
import io.github.currencortex.music.data.profile.RemoteAvatarImage
import io.github.currencortex.music.data.profile.losslessBannerImageTypes
import io.github.currencortex.music.data.profile.queryUriSizeBytes
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

private const val MAX_BANNER_IMAGE_PIXELS = 25_000_000L

internal data class LosslessBannerImage(val mimeType: String, val width: Int, val height: Int)

/** The server's frame: the still image after its EXIF orientation is applied, before cropping. */
internal fun inspectLosslessBannerImage(resolver: ContentResolver, uri: Uri): LosslessBannerImage? =
    runCatching { readLosslessBannerImage(resolver, uri) }.getOrNull()

private fun readLosslessBannerImage(resolver: ContentResolver, uri: Uri): LosslessBannerImage? {
    if (queryUriSizeBytes(resolver, uri) > PROFILE_BANNER_ORIGINAL_IMAGE_LIMIT) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    val mimeType = bounds.outMimeType ?: return null
    if (mimeType !in losslessBannerImageTypes || bounds.outWidth.toLong() * bounds.outHeight > MAX_BANNER_IMAGE_PIXELS ||
        isAnimatedImage(resolver, uri, mimeType)) return null
    // ExifInterface reports 0 (undefined) for a file without an orientation tag, and 1 for normal.
    val orientation = imageOrientation(resolver, uri)
    val transformed = orientation != ExifInterface.ORIENTATION_UNDEFINED &&
        orientation != ExifInterface.ORIENTATION_NORMAL
    // Only a JPEG's orientation is known to reach the server's reader intact; a rotated PNG or
    // WebP takes the compressed path, where the cut would otherwise land on the wrong pixels.
    if (mimeType != "image/jpeg" && transformed) return null
    val swapped = orientation in ExifInterface.ORIENTATION_TRANSPOSE..ExifInterface.ORIENTATION_ROTATE_270
    val width = if (swapped) bounds.outHeight else bounds.outWidth
    val height = if (swapped) bounds.outWidth else bounds.outHeight
    return if (width >= 3 && height >= 2) LosslessBannerImage(mimeType, width, height) else null
}

/** The server keeps still images only, so animated sources stay on the compressed path. */
private fun isAnimatedImage(resolver: ContentResolver, uri: Uri, mimeType: String): Boolean = runCatching {
    resolver.openInputStream(uri)!!.use { input ->
        val png = mimeType == "image/png"
        val header = ByteArray(if (png) 8 else 12)
        if (!input.readFully(header)) return@use false
        if (png) {
            if (!header.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) return@use false
        } else if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF" || String(header, 8, 4, Charsets.US_ASCII) != "WEBP") {
            return@use false
        }
        val chunk = ByteArray(8)
        while (input.readFully(chunk)) {
            // PNG chunks are length then type, big-endian; RIFF chunks are type then length, little-endian.
            val type = String(chunk, if (png) 4 else 0, 4, Charsets.US_ASCII)
            val raw = ByteBuffer.wrap(chunk, if (png) 0 else 4, 4).int.toLong()
            val length = (if (png) raw else Integer.reverseBytes(raw.toInt()).toLong()) and 0xFFFF_FFFFL
            if (type == if (png) "acTL" else "ANIM") return@use true
            // Animation control always precedes the picture data.
            if (type == "IDAT" || type == "VP8 " || type == "VP8L") return@use false
            // PNG chunks end with a CRC, RIFF chunks are padded to an even size.
            var remaining = length + if (png) 4L else length and 1L
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped > 0) remaining -= skipped
                else if (input.read() < 0) return@use false
                else remaining--
            }
        }
        false
    }
}.getOrDefault(false)

private fun InputStream.readFully(buffer: ByteArray): Boolean {
    var read = 0
    while (read < buffer.size) {
        val count = read(buffer, read, buffer.size - read)
        if (count <= 0) return false
        read += count
    }
    return true
}

private fun imageOrientation(resolver: ContentResolver, uri: Uri): Int = runCatching {
    resolver.openInputStream(uri).use {
        ExifInterface(requireNotNull(it)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

internal fun loadAvatarCropImage(resolver: ContentResolver, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val source = requireNotNull(resolver.openInputStream(uri).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    })
    val orientation = imageOrientation(resolver, uri)
    val matrix = Matrix().apply { when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
    } }
    if (matrix.isIdentity) return source
    return try { Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true) }
        finally { source.recycle() }
}

internal fun avatarCropBytes(resolver: ContentResolver, uri: Uri, crop: AvatarCrop): ByteArray {
    val source = loadAvatarCropImage(resolver, uri)
    try {
        val bounds = crop.bounds(source.width, source.height)
        val size = bounds.size.roundToInt().coerceIn(1, 1024)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            Canvas(output).apply {
                drawColor(Color.WHITE)
                val matrix = Matrix().apply { setRectToRect(
                    RectF(bounds.left, bounds.top, bounds.left + bounds.size, bounds.top + bounds.size),
                    RectF(0f, 0f, size.toFloat(), size.toFloat()), Matrix.ScaleToFit.FILL) }
                drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            return ByteArrayOutputStream().use { stream ->
                check(output.compress(Bitmap.CompressFormat.JPEG, 90, stream))
                stream.toByteArray().also { require(it.isNotEmpty() && it.size <= 1024 * 1024) }
            }
        } finally { output.recycle() }
    } finally { source.recycle() }
}

internal fun bannerCropBytes(resolver: ContentResolver, uri: Uri, crop: BannerCrop, aspectRatio: Float): ByteArray {
    val source = loadAvatarCropImage(resolver, uri)
    try {
        val bounds = crop.bounds(source.width, source.height, aspectRatio)
        val outWidth = bounds.width.roundToInt().coerceIn(1, 1600)
        val outHeight = (outWidth / aspectRatio).roundToInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        try {
            Canvas(output).apply {
                drawColor(Color.WHITE)
                val matrix = Matrix().apply {
                    setRectToRect(
                        RectF(bounds.left, bounds.top, bounds.left + bounds.width, bounds.top + bounds.height),
                        RectF(0f, 0f, outWidth.toFloat(), outHeight.toFloat()),
                        Matrix.ScaleToFit.FILL,
                    )
                }
                drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            return RemoteAvatarImage.encodeBannerBitmap(output)
        } finally {
            output.recycle()
        }
    } finally {
        source.recycle()
    }
}
