package io.github.currencortex.music.data.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Public image requests use a separate client and never carry either account's credentials. */
internal class RemoteAvatarImage {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()

    suspend fun download(value: String): ByteArray = withContext(Dispatchers.IO) {
        val raw = fetchBytes(value, MAX_AVATAR_DOWNLOAD)
        encodeWithinLimit(raw, maxDimension = 1024, maxBytes = 1024 * 1024)
    }

    suspend fun downloadBanner(value: String): ByteArray = withContext(Dispatchers.IO) {
        val raw = fetchBytes(value, MAX_BANNER_DOWNLOAD)
        encodeBannerBytes(raw)
    }

    private fun fetchBytes(value: String, maxDownload: Int): ByteArray {
        val url = value.toHttpUrl().let { if (it.scheme == "http") it.newBuilder().scheme("https").build() else it }
        require(url.username.isEmpty() && url.password.isEmpty())
        return http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful)
            val body = requireNotNull(response.body)
            require(body.contentLength() <= maxDownload)
            body.byteStream().use { input -> ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    require(output.size() + size <= maxDownload)
                    output.write(buffer, 0, size)
                }
                output.toByteArray()
            } }
        }
    }

    companion object {
        private const val MAX_AVATAR_DOWNLOAD = 5 * 1024 * 1024
        private const val MAX_BANNER_DOWNLOAD = 10 * 1024 * 1024
        private const val MAX_BANNER_DIMENSION = 1600

        internal fun encodeBannerBytes(raw: ByteArray): ByteArray =
            encodeWithinLimit(raw, maxDimension = MAX_BANNER_DIMENSION, maxBytes = PROFILE_BANNER_LIMIT)

        internal fun encodeBannerBitmap(bitmap: Bitmap): ByteArray =
            encodeBitmapWithinLimit(bitmap, maxDimension = MAX_BANNER_DIMENSION, maxBytes = PROFILE_BANNER_LIMIT)

        private fun encodeWithinLimit(raw: ByteArray, maxDimension: Int, maxBytes: Int): ByteArray {
            val decoded = decodeScaledBitmap(raw, maxDimension)
            try {
                return encodeBitmapWithinLimit(decoded, maxDimension, maxBytes)
            } finally {
                decoded.recycle()
            }
        }

        private fun encodeBitmapWithinLimit(source: Bitmap, maxDimension: Int, maxBytes: Int): ByteArray {
            var current = if (maxOf(source.width, source.height) > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(source.width, source.height)
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).roundToInt().coerceAtLeast(1),
                    (source.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                )
            } else {
                source
            }
            try {
                for (quality in intArrayOf(90, 82, 72, 60)) {
                    val encoded = compressJpeg(current, quality)
                    if (encoded.size <= maxBytes) return encoded
                }
                while (maxOf(current.width, current.height) > 320) {
                    val nextW = (current.width * 0.75f).roundToInt().coerceAtLeast(1)
                    val nextH = (current.height * 0.75f).roundToInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(current, nextW, nextH, true)
                    if (current !== source && scaled !== current) current.recycle()
                    current = scaled
                    val encoded = compressJpeg(current, 78)
                    if (encoded.size <= maxBytes) return encoded
                }
                error("Encoded image exceeds $maxBytes bytes")
            } finally {
                if (current !== source) current.recycle()
            }
        }

        private fun decodeScaledBitmap(raw: ByteArray, maxDimension: Int): Bitmap {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(raw))
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val width = info.size.width
                    val height = info.size.height
                    require(width > 0 && height > 0)
                    val longest = maxOf(width, height)
                    if (longest > maxDimension) {
                        val scale = maxDimension.toFloat() / longest
                        decoder.setTargetSize(
                            (width * scale).roundToInt().coerceAtLeast(1),
                            (height * scale).roundToInt().coerceAtLeast(1),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
            return requireNotNull(BitmapFactory.decodeByteArray(raw, 0, raw.size,
                BitmapFactory.Options().apply { inSampleSize = sample }))
        }

        private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
            output.toByteArray().also { require(it.isNotEmpty()) }
        }
    }
}
