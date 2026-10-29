package io.github.currencortex.music.data.netease.playlistimport

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PlaylistImportImage(val bytes: ByteArray, val fileName: String, val mimeType: String)
data class PlaylistImportTarget(val id: Long, val name: String)

/** A separate, cookie-free client: NOS receives its scoped token, never account credentials. */
internal class PlaylistImportImageUpload(base: String) {
    private val endpoint = base.toHttpUrl()
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).followRedirects(false).retryOnConnectionFailure(false).build()

    suspend fun upload(image: PlaylistImportImage, key: String, token: String, checkOwner: () -> Unit) {
        var offset = 0
        var context: String? = null
        while (offset < image.bytes.size) {
            checkOwner()
            val count = minOf(256 * 1024, image.bytes.size - offset)
            val complete = offset + count == image.bytes.size
            val url = endpoint.newBuilder().addPathSegment("yyimgs").apply { key.split('/').forEach(::addPathSegment) }
                .addQueryParameter("offset", offset.toString()).addQueryParameter("complete", complete.toString())
                .addQueryParameter("version", "1.0").apply { context?.let { addQueryParameter("context", it) } }.build()
            val request = Request.Builder().url(url).header("x-nos-token", token)
                .post(image.bytes.toRequestBody(image.mimeType.toMediaType(), offset, count)).build()
            val raw = suspendCancellableCoroutine<JsonObject> { cont ->
                val call = http.newCall(request)
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, error: IOException) { if (!cont.isCancelled) cont.resumeWithException(error) }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val result = response.use {
                                if (!it.isSuccessful) throw ApiException(ErrorKind.Server, userMessage = "图片上传失败，请稍后重试")
                                val source = it.body?.source() ?: throw ApiException(ErrorKind.Parse)
                                source.request(64 * 1024L + 1)
                                if (source.buffer.size > 64 * 1024) throw ApiException(ErrorKind.Parse)
                                Json.parseToJsonElement(source.readUtf8()).jsonObject
                            }
                            if (cont.isActive) cont.resume(result)
                        } catch (error: Exception) { if (cont.isActive) cont.resumeWithException(error) }
                    }
                })
            }
            checkOwner()
            if ((raw["offset"] as? JsonPrimitive)?.intOrNull != offset + count)
                throw ApiException(ErrorKind.Parse, userMessage = "网易云未确认图片上传完整，请重新选择图片后重试")
            offset += count
            context = (raw["context"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
            if (!complete && context == null) throw ApiException(ErrorKind.Parse)
        }
    }
}
