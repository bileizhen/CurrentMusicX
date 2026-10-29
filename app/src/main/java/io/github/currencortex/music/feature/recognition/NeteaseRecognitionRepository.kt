package io.github.currencortex.music.feature.recognition

import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** NetEase's public fingerprint endpoint. No application account, cookies or raw audio is sent. */
class NeteaseRecognitionRepository(client: OkHttpClient = OkHttpClient(),
    baseUrl: String = "https://interface.music.163.com/") {
    private val base = baseUrl.toHttpUrl()
    private val client = client.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS).cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).apply { interceptors().clear(); networkInterceptors().clear() }.build()

    suspend fun recognize(fingerprint: String, durationSeconds: Double): List<Song> {
        require(durationSeconds in 3.0..10.0 && fingerprint.length in 4..128_000 &&
            fingerprint.matches(Regex("[A-Za-z0-9+/=]+"))) { "识别片段无效，请重新录音" }
        val body = FormBody.Builder().add("sessionId", UUID.randomUUID().toString())
            .add("algorithmCode", "shazam_v2").add("duration", durationSeconds.toString())
            .add("rawdata", fingerprint).add("times", "1").add("decrypt", "1").build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(base.resolve("api/music/audio/match")!!)
                .header("Referer", "https://music.163.com/").header("User-Agent", "Mozilla/5.0")
                .post(body).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException(
                        if (e is java.io.InterruptedIOException) "网易云识曲请求超时，请检查网络后重试" else "网易云识曲请求失败，请检查网络后重试", e))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val songs = response.use {
                            if (!it.isSuccessful) throw IOException("网易云识曲暂不可用（HTTP ${it.code}）")
                            val payload = it.body ?: throw IOException("网易云返回了空响应")
                            if (payload.contentLength() > MAX_BYTES) throw IOException("网易云识曲响应过大")
                            val source = payload.source()
                            source.request(MAX_BYTES + 1)
                            if (source.buffer.size > MAX_BYTES) throw IOException("网易云识曲响应过大")
                            parse(Json.parseToJsonElement(source.readUtf8()).jsonObject)
                        }
                        if (continuation.isActive) continuation.resume(songs)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(if (e is IOException) e else IOException("网易云识曲响应格式无效，请稍后重试", e))
                    }
                }
            })
        }
    }

    companion object {
        private const val MAX_BYTES = 2L * 1024 * 1024
        internal fun parse(root: JsonObject): List<Song> {
            val code = root["code"]?.jsonPrimitive?.intOrNull ?: throw IOException("网易云识曲响应格式无效")
            if (code != 200) throw IOException("网易云识曲暂不可用（$code），请稍后重试")
            val data = root["data"] as? JsonObject ?: throw IOException("网易云识曲响应缺少结果")
            val rows = data["result"] as? JsonArray ?: if (data["result"] == JsonNull) JsonArray(emptyList())
                else throw IOException("网易云识曲结果格式无效")
            fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
            fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.longOrNull ?: 0L
            return rows.take(10).mapNotNull { row ->
                val track = (row as? JsonObject)?.get("song") as? JsonObject ?: return@mapNotNull null
                val id = track.number("id").takeIf { it > 0 } ?: return@mapNotNull null
                val name = track.text("name").trim().takeIf(String::isNotEmpty)?.take(512) ?: return@mapNotNull null
                val album = (track["album"] ?: track["al"]) as? JsonObject ?: buildJsonObject {}
                val artists = ((track["artists"] ?: track["ar"]) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                Song(id, name, artists.joinToString(" / ") { it.text("name").take(256) }, album.text("name").take(512),
                    album.text("picUrl").replace("http:", "https:"),
                    (track.number("duration").takeIf { it > 0 } ?: track.number("dt")).coerceIn(0, 86_400_000),
                    track.number("mvid").takeIf { it > 0 } ?: track.number("mv"), artists.map { it.number("id") }.filter { it > 0 })
            }.distinctBy { it.id }
        }
    }
}
