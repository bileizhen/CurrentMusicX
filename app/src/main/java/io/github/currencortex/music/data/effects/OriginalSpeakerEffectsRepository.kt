package io.github.currencortex.music.data.effects

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class OriginalSpeakerScene(val wireName: String, val label: String) {
    NORMAL("normal", "通用模式"), SHOWER("shower", "浴室"), SLEEP("sleep", "睡前陪伴")
}

data class OriginalSpeakerEntitlement(val userId: Long = 0, val canUse: Boolean = false, val expiresAt: Long = 0)
data class OriginalSpeakerMaterial(val id: Long, val name: String, val url: String, val md5: String, val size: Long)

/** Original type-10 speaker resources use soundType 9 and negative material IDs, not sound ID 105. */
class OriginalSpeakerEffectsRepository(private val transport: NeteaseTransport,
    client: OkHttpClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build(),
    private val deviceModel: String = android.os.Build.MODEL.orEmpty()) {
    private val materials = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES).build()

    suspend fun entitlement(): OriginalSpeakerEntitlement {
        val owner = transport.sessions.snapshot()
        val uid = owner.credential.profile?.uid ?: 0
        if (uid <= 0 || owner.credential.stale || owner.credential.cookies["MUSIC_U"].isNullOrBlank())
            return OriginalSpeakerEntitlement()
        val raw = official("/api/music-vip-membership/client/vip/info",
            buildJsonObject { put("userId", uid) }, owner)
        val member = raw["data"] as? JsonObject ?: throw ApiException(ErrorKind.Parse)
        transport.sessions.check(owner)
        if ((member["userId"] as? JsonPrimitive)?.longOrNull != uid) throw ApiException(ErrorKind.Parse)
        return originalSpeakerEntitlement(uid, member)
    }

    suspend fun catalog(scene: OriginalSpeakerScene): List<OriginalSpeakerMaterial> {
        val owner = transport.sessions.snapshot()
        val ids = originalSpeakerIds(scene)
        val raw = official("/api/usertool/home/sound/info", buildJsonObject {
            put("reqListStr", buildJsonArray { ids.forEach { id -> add(buildJsonObject {
                put("soundType", 9); put("soundId", id)
            }) } }.toString())
        }, owner)
        val rows = ((raw["data"] as? JsonObject)?.get("soundList") as? JsonArray)
            ?: throw ApiException(ErrorKind.Parse)
        val generic = rows.filterIsInstance<JsonObject>().mapNotNull { row ->
            (row["outerSoundDetailInfo"] as? JsonObject)?.let(::parseOriginalSpeakerMaterial)
        }.filter { it.id in ids }.distinctBy { it.id }
        val result = ids.mapNotNull { id -> originalSpeakerDeviceOverride(deviceModel, id) ?: generic.firstOrNull { it.id == id } }
        transport.sessions.check(owner)
        return result
    }

    /** Bounds and verifies actual downloaded bytes; no cookies are sent to the material CDN. */
    suspend fun download(material: OriginalSpeakerMaterial): NcaeDecoded = withContext(Dispatchers.IO) {
        val owner = transport.sessions.snapshot()
        val url = requireNotNull(originalSpeakerMaterialUrl(material.url)) { "外放音效素材地址无效" }
        require(material.size in 1..MAX_SPEAKER_BYTES.toLong()) { "外放音效素材大小无效" }
        require(material.md5.matches(Regex("[a-fA-F0-9]{32}"))) { "外放音效校验信息无效" }
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = materials.newCall(Request.Builder().url(url).build())
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
        response.use {
                check(response.isSuccessful) { "外放音效素材下载失败" }
                val body = requireNotNull(response.body)
                require(body.contentLength() <= MAX_SPEAKER_BYTES) { "外放音效素材过大" }
                val bytes = body.byteStream().use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= MAX_SPEAKER_BYTES) { "外放音效素材过大" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                require(bytes.size.toLong() == material.size && MessageDigest.getInstance("MD5").digest(bytes)
                    .joinToString("") { "%02x".format(it.toInt() and 255) }.equals(material.md5, true)) {
                    "外放音效素材校验失败"
                }
                transport.sessions.check(owner)
                NcaeDecoder.decode(bytes)
        }
    }

    private suspend fun official(path: String, body: JsonObject, owner: NeteaseSession): JsonObject {
        val raw = transport.request(path, body, session = owner, clientProfile = NeteaseClientProfile.ANDROID).body
        transport.sessions.check(owner)
        when ((raw["code"] as? JsonPrimitive)?.intOrNull) {
            200 -> return raw
            301, 401 -> throw ApiException(ErrorKind.NeteaseBindingRequired)
            403 -> throw ApiException(ErrorKind.Forbidden)
            else -> throw ApiException(ErrorKind.Server)
        }
    }
}

internal fun originalSpeakerIds(scene: OriginalSpeakerScene): List<Long> = when (scene) {
    OriginalSpeakerScene.NORMAL -> listOf(-100, -101, -102, -200, -201, -202)
    OriginalSpeakerScene.SHOWER -> listOf(-300)
    OriginalSpeakerScene.SLEEP -> listOf(-400)
}

/** ff1/e.b uses 20% and 75% volume thresholds independently for both orientations. */
internal fun originalSpeakerId(scene: OriginalSpeakerScene, landscape: Boolean, volume: Float): Long = when (scene) {
    OriginalSpeakerScene.SHOWER -> -300
    OriginalSpeakerScene.SLEEP -> -400
    OriginalSpeakerScene.NORMAL -> (if (landscape) -100L else -200L) - when {
        volume.isFinite() && volume.coerceIn(0f, 1f) < .2f -> 0
        volume.isFinite() && volume.coerceIn(0f, 1f) < .75f -> 1
        else -> 2
    }
}

internal fun parseOriginalSpeakerMaterial(row: JsonObject): OriginalSpeakerMaterial? {
    fun text(name: String) = (row[name] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val id = (row["materialId"] as? JsonPrimitive)?.longOrNull ?: return null
    if (id !in OriginalSpeakerScene.entries.flatMap(::originalSpeakerIds)) return null
    val url = originalSpeakerMaterialUrl(text("meta")) ?: return null
    val hash = text("md5")
    val size = (row["fileSize"] as? JsonPrimitive)?.longOrNull ?: return null
    if (!hash.matches(Regex("[a-fA-F0-9]{32}")) || size !in 1..MAX_SPEAKER_BYTES.toLong()) return null
    return OriginalSpeakerMaterial(id, text("materialName").ifBlank { text("name") }.take(100), url.toString(), hash, size)
}

internal fun originalSpeakerMaterialUrl(raw: String): HttpUrl? = raw.replace("http://", "https://")
    .toHttpUrlOrNull()?.takeIf { it.isHttps && it.port == 443 && it.host.endsWith(".music.126.net") &&
        it.username.isEmpty() && it.password.isEmpty() && it.fragment == null }

/** ze1/h.w0 sets curr_ae_vip=4, checked by co0/b.A -> UserPrivilege.isRedPlus. */
internal fun originalSpeakerEntitlement(uid: Long, member: JsonObject, now: Long = System.currentTimeMillis()): OriginalSpeakerEntitlement {
    if (uid <= 0 || (member["userId"] as? JsonPrimitive)?.longOrNull != uid) return OriginalSpeakerEntitlement(uid)
    val plus = member["redplus"] as? JsonObject
    val expires = if ((plus?.get("vipCode") as? JsonPrimitive)?.intOrNull == 300)
        (plus["expireTime"] as? JsonPrimitive)?.longOrNull ?: 0 else 0
    return OriginalSpeakerEntitlement(uid, expires > now, expires)
}

private const val MAX_SPEAKER_BYTES = 2 * 1024 * 1024
