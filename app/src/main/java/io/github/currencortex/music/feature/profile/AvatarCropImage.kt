package io.github.currencortex.music.feature.profile

import android.content.ContentResolver
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import io.github.currencortex.music.data.profile.AvatarCrop
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

internal fun loadAvatarCropImage(resolver: ContentResolver, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val source = requireNotNull(resolver.openInputStream(uri).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    })
    val orientation = runCatching { resolver.openInputStream(uri).use {
        ExifInterface(requireNotNull(it)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
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
