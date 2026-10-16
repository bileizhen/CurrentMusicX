package io.github.currencortex.music.data.effects

import android.content.Context
import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OriginalAiMasterMaterial(val base: JsonObject, val diy: JsonObject, val baseKey: String,
    val song: JsonObject?, val description: String, val baseName: String = "", val longText: String = "")

/** Original type-9 / catalog-104 data. The model is server-authored NCAE, distinct from mood 106/MNN. */
class OriginalAiMasterRepository internal constructor(private val directory: File, private val transport: NeteaseTransport,
    private val member: OriginalMoodModelRepository) {
    constructor(context: Context, transport: NeteaseTransport) : this(File(context.filesDir, "netease-ai-master"),
        transport, OriginalMoodModelRepository(context, transport))
    private val mutex = Mutex()
    private val materials = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).cookieJar(CookieJar.NO_COOKIES).build()

    // Both original type 9 and type 11 save curr_ae_vip=4 and use the same RedPlus 300 gate.
    suspend fun entitlement(): OriginalMoodEntitlement = member.entitlement()

    suspend fun prepare(songId: Long): OriginalAiMasterMaterial = mutex.withLock {
        require(songId > 0)
        val owner = transport.sessions.snapshot()
        val uid = owner.credential.profile?.uid ?: 0
        val right = entitlement()
        transport.sessions.check(owner)
        if (uid <= 0 || right.userId != uid || !right.canUse || right.expiresAt <= System.currentTimeMillis())
            throw ApiException(ErrorKind.Forbidden)
        val raw = transport.request("/api/vip-rights/svip/sound/effect/get",
            buildJsonObject { put("songIds", "[$songId]") }, session = owner,
            clientProfile = NeteaseClientProfile.ANDROID).body
        transport.sessions.check(owner)
        when (raw["code"]?.jsonPrimitive?.intOrNull) {
            200 -> Unit
            301, 401 -> throw ApiException(ErrorKind.NeteaseBindingRequired)
            403 -> throw ApiException(ErrorKind.Forbidden)
            else -> throw ApiException(ErrorKind.Server)
        }
        val rows = raw["data"] as? JsonArray ?: throw ApiException(ErrorKind.Parse)
        val row = rows.filterIsInstance<JsonObject>().firstOrNull { it["id"]?.jsonPrimitive?.longOrNull == songId }
            ?: throw IllegalStateException("网易云未返回此歌曲的 AI 调音方案")
        val key = row.text("baseKey").takeIf { it.matches(Regex("key_[a-z_]{1,48}")) }
            ?: throw ApiException(ErrorKind.Parse)
        val base = material(BASE_URL, BASE_MD5, 1611)
        val diy = material(DIY_URL, DIY_MD5, 596)
        val effect = row["effect"] as? JsonObject
        val song = if (effect != null) material(effect.text("url"), effect.text("md5"),
            effect["size"]?.jsonPrimitive?.longOrNull ?: throw ApiException(ErrorKind.Parse)) else null
        currentCoroutineContext().ensureActive()
        transport.sessions.check(owner)
        if (right.expiresAt <= System.currentTimeMillis()) throw ApiException(ErrorKind.Forbidden)
        OriginalAiMasterMaterial(base, diy, key, song, row.text("shortText").take(1024),
            row.text("baseName").take(128), row.text("longText").take(4096))
    }

    private suspend fun material(link: String, hash: String, size: Long): JsonObject = withContext(Dispatchers.IO) {
        require(hash.matches(Regex("[a-fA-F0-9]{32}")) && size in 18..2_097_152)
        val url = link.replace("http:", "https:").toHttpUrlOrNull()
        require(url != null && url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            url.host.endsWith(".music.126.net") && url.fragment == null) { "原版调音资源来源无效" }
        check(directory.isDirectory || directory.mkdirs())
        val file = File(directory, hash.lowercase() + ".ncae")
        fun valid(bytes: ByteArray) = bytes.size.toLong() == size && MessageDigest.getInstance("MD5").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }.equals(hash, ignoreCase = true)
        val cached = file.takeIf { it.isFile && it.length() == size }?.readBytes()?.takeIf(::valid)
        val bytes = cached ?: run {
            val downloaded = download(url)
            require(valid(downloaded)) { "原版 AI 调音资源校验失败" }
            currentCoroutineContext().ensureActive()
            val pending = File(directory, hash.lowercase() + ".pending")
            pending.writeBytes(downloaded)
            if (file.exists()) check(file.delete())
            check(pending.renameTo(file))
            downloaded
        }
        val decoded = NcaeDecoder.decode(bytes)
        require(decoded.type == 1) { "原版调音资源格式不支持" }
        Json.parseToJsonElement(decoded.bytes.toString(Charsets.UTF_8)).jsonObject
    }

    private suspend fun download(url: HttpUrl): ByteArray {
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = materials.newCall(Request.Builder().url(url).header("User-Agent", "NeteaseMusic/9.5.30")
                .header("Referer", "https://music.163.com/").build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response, onCancellation = { _, value, _ -> value.close() })
                }
            })
        }
        return response.use {
            check(it.isSuccessful) { "原版调音资源下载失败" }
            val body = requireNotNull(it.body)
            require(body.contentLength() <= 2_097_152)
            body.byteStream().use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    require(out.size() + count <= 2_097_152)
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
        }
    }

    companion object {
        private const val BASE_MD5 = "f146c66e5baa6a6020b423a08cb5f664"
        private const val DIY_MD5 = "d79417b1e6a0c01144b874dfd87359fa"
        // The original query only names the download; omitting it retains the exact MD5-verified bytes.
        private const val BASE_URL = "https://d1.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/62316197751/d688/856c/39fb/f146c66e5baa6a6020b423a08cb5f664.data"
        private const val DIY_URL = "https://d2.music.126.net/dmusic/obj/w5zCg8OAw6HDjzjDgMK_/62316193532/981d/97b6/8e40/d79417b1e6a0c01144b874dfd87359fa.data"
    }
}

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
