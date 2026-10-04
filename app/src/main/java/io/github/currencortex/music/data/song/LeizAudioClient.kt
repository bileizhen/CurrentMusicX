package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Dedicated client: project Bearer tokens and CDN requests never pass through this client. */
class LeizAudioClient(endpoint: String = "https://api.bileizhen.top/api/netease",
    client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(75, TimeUnit.SECONDS).build()) {
    private val base = endpoint.toHttpUrl()
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    suspend fun source(id: Long, quality: AudioQuality, key: String): AudioSource {
        if (key.isBlank()) throw ApiException(ErrorKind.AudioKeyRequired)
        val level = if (quality == AudioQuality.AUTO) AudioQuality.JYMASTER.value else quality.value
        val request = Request.Builder().url(base.newBuilder().addQueryParameter("id", "$id")
            .addQueryParameter("level", level).build()).header("x-api-key", key)
            .header("Accept", "application/json").header("User-Agent", "CurrentMusic-Android/0.1").build()
        val response = suspendCancellableCoroutine<Response> { cont ->
            val call = http.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response, onCancellation = { _, value, _ -> value.close() })
                }
            })
        }
        return response.use {
            if (!it.isSuccessful) throw ApiException(when (it.code) {
                401, 403 -> ErrorKind.AudioKeyRejected
                404 -> ErrorKind.AudioUnavailable
                429 -> ErrorKind.RateLimited
                in 500..599 -> ErrorKind.Server
                else -> ErrorKind.Parse
            }, it.code)
            val root = try { ApiJson.parseToJsonElement(it.body?.string().orEmpty()).jsonObject }
                catch (_: Exception) { throw ApiException(ErrorKind.Parse) }
            if ((root["success"] as? JsonPrimitive)?.booleanOrNull != true) throw ApiException(ErrorKind.AudioUnavailable)
            val dto = try { ApiJson.decodeFromJsonElement<SongUrlDto>(root["data"] ?: JsonNull) }
                catch (_: Exception) { throw ApiException(ErrorKind.Parse) }
            val url = dto.url?.toHttpUrlOrNull() ?: throw ApiException(ErrorKind.AudioUnavailable)
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) throw ApiException(ErrorKind.Parse)
            AudioSource(url.toString(), dto.level, maxOf(dto.sr, dto.sampleRate), maxOf(dto.ch, dto.channelCount), dto.type, dto.md5)
        }
    }
}
