package io.github.currencortex.music.data.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Public image requests use a separate client and never carry either account's credentials. */
internal class RemoteAvatarImage {
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()

    suspend fun download(value: String): ByteArray = withContext(Dispatchers.IO) {
        val url = value.toHttpUrl().let { if (it.scheme == "http") it.newBuilder().scheme("https").build() else it }
        require(url.username.isEmpty() && url.password.isEmpty())
        val raw = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful)
            val body = requireNotNull(response.body)
            require(body.contentLength() <= MAX_DOWNLOAD)
            body.byteStream().use { input -> ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    require(output.size() + size <= MAX_DOWNLOAD)
                    output.write(buffer, 0, size)
                }
                output.toByteArray()
            } }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(raw, 0, raw.size,
            BitmapFactory.Options().apply { inSampleSize = sample }))
        try { ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
            output.toByteArray().also { require(it.isNotEmpty() && it.size <= 1024 * 1024) }
        } } finally { bitmap.recycle() }
    }

    companion object { private const val MAX_DOWNLOAD = 5 * 1024 * 1024 }
}
