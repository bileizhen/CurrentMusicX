package io.github.currencortex.music.data.effects

import io.github.currencortex.music.core.netease.NeteaseSession
import io.github.currencortex.music.core.netease.NeteaseTransport
import io.github.currencortex.music.core.netease.NeteaseClientProfile
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class NeteaseSoundEffect(val id: Long, val name: String, val description: String,
    val artwork: String = "", val memberType: Int = 0, val soundType: Int = 1,
    val presetLink: String = "", val presetMd5: String = "", val strategyCode: String = "",
    val hasVipAuthStrategy: Boolean = false) {
    val compatibleOriginalResource: Boolean get() = soundType == 1 &&
        presetMd5.matches(Regex("[a-fA-F0-9]{32}")) && originalPresetUrl(presetLink) != null
    val legacyOriginalStrategy: Boolean get() = strategyCode.isBlank() && !hasVipAuthStrategy
    val publicOriginal: Boolean get() = memberType == 1 && legacyOriginalStrategy && compatibleOriginalResource
    val legacyVipOriginal: Boolean get() = memberType == 3 && legacyOriginalStrategy && compatibleOriginalResource
}

/** Song descriptors supplied by NetEase, not a NetEase DSP preset or EQ curve. */
data class NeteaseSongAnalysis(val songId: Long, val emotions: List<String> = emptyList(),
    val bpm: Float? = null, val melBandsDb: List<Float> = emptyList(), val loudnessLufs: Float? = null,
    val vocalEnergy: Float? = null, val bassEnergy: Float? = null, val stereoWidth: Float? = null)

/** Server-verified current account rights; song classification is not a membership proof. */
data class NeteaseEffectEntitlement(val userId: Long = 0, val smartAllowed: Boolean = false,
    val expiresAt: Long = 0)

/** Direct read-only requests. Original presets are downloaded only after explicit selection. */
class NeteaseSoundEffectsRepository(private val transport: NeteaseTransport,
    presetClient: OkHttpClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build(),
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    // No authentication headers, cookie jar, or cross-host redirects for public CDN materials.
    private val materials = presetClient.newBuilder().followRedirects(false).followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES).build()
    private data class Cached<T>(val value: T, val expiresAt: Long)
    private val mutex = Mutex()
    private var revision = -1L
    private var cachedCatalog: Cached<List<NeteaseSoundEffect>>? = null
    private var cachedSmartEntitlement: Cached<NeteaseEffectEntitlement>? = null
    private val analyses = LinkedHashMap<Long, Cached<NeteaseSongAnalysis?>>(16, .75f, true)
    private val originalSmartKeys = LinkedHashMap<Long, Cached<String>>(16, .75f, true)

    suspend fun catalog(): List<NeteaseSoundEffect> = mutex.withLock {
        val owner = transport.sessions.snapshot()
        resetFor(owner)
        cachedCatalog?.takeIf { it.expiresAt > now() }?.let {
            transport.sessions.check(owner)
            return@withLock it.value
        }
        val raw = official("/api/usertool/sound/mobile/all", buildJsonObject {}, owner)
        val rows = raw["data"] as? JsonArray ?: throw ApiException(ErrorKind.Parse)
        val value = rows.mapNotNull { item ->
            val row = item as? JsonObject ?: return@mapNotNull null
            val id = row.long("id") ?: return@mapNotNull null
            val name = row.text("name").trim()
            if (id <= 0 || name.isBlank()) return@mapNotNull null
            NeteaseSoundEffect(id, name, row.text("subTitle").ifBlank { row.text("playerFloatLayerDesc") },
                row.text("imgUrl").replace("http:", "https:"),
                (row["type"] as? JsonPrimitive)?.intOrNull ?: 0,
                (row["soundType"] as? JsonPrimitive)?.intOrNull ?: 1,
                row.text("link").replace("http:", "https:"), row.text("md5"),
                when (val strategy = row["newVipStrategyCode"]) {
                    null, JsonNull -> ""
                    is JsonPrimitive -> strategy.contentOrNull?.trim().orEmpty()
                    else -> "unsupported"
                }, row["vipAuthRespItem"]?.let { it != JsonNull } ?: false)
        }.distinctBy { it.id }.take(MAX_CATALOG)
        currentCoroutineContext().ensureActive()
        transport.sessions.check(owner)
        cachedCatalog = Cached(value, now() + CACHE_MS)
        value
    }

    /** Re-resolve a newly introduced official ID; saved metadata contains no trusted material. */
    suspend fun resolve(id: Long): NeteaseSoundEffect? {
        require(id > 0)
        val owner = transport.sessions.snapshot()
        val raw = try {
            official("/api/usertool/home/sound/info", buildJsonObject {
                put("reqListStr", buildJsonArray { add(buildJsonObject { put("soundType", 8); put("soundId", id) }) }.toString())
            }, owner, NeteaseClientProfile.ANDROID)
        } catch (failure: ApiException) {
            if (failure.kind != ErrorKind.NotFound) throw failure
            transport.sessions.check(owner)
            return null
        }
        val rows = ((raw["data"] as? JsonObject)?.get("soundList") as? JsonArray)
            ?: throw ApiException(ErrorKind.Parse)
        val result = rows.filterIsInstance<JsonObject>()
            .mapNotNull { (it["soundMobileDetailDTO"] as? JsonObject)?.let(::parseOriginalSoundEffect) }
            .firstOrNull { it.id == id }
        currentCoroutineContext().ensureActive()
        transport.sessions.check(owner)
        return result
    }

    suspend fun downloadPreset(effect: NeteaseSoundEffect): ByteArray = withContext(Dispatchers.IO) {
        require(effect.publicOriginal || effect.legacyVipOriginal) { "Unsupported original effect authorization strategy" }
        val owner = transport.sessions.snapshot()
        val entitlement = if (effect.legacyVipOriginal) {
            val uid = owner.credential.profile?.uid ?: 0
            if (uid <= 0 || owner.credential.stale || owner.credential.cookies["MUSIC_U"].isNullOrBlank())
                throw ApiException(ErrorKind.NeteaseBindingRequired)
            originalPresetEntitlement().also {
                transport.sessions.check(owner)
                if (it.userId != uid || !it.smartAllowed || it.expiresAt <= System.currentTimeMillis())
                    throw ApiException(ErrorKind.Forbidden)
            }
        } else null
        transport.sessions.check(owner)
        val url = requireNotNull(originalPresetUrl(effect.presetLink))
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = materials.newCall(Request.Builder().url(url).get().build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response, onCancellation = { _, value, _ -> value.close() })
                }
            })
        }
        response.use {
            transport.sessions.check(owner)
            if (!it.isSuccessful) throw IOException("Preset HTTP ${it.code}")
            val body = it.body ?: throw IOException("Missing preset body")
            require(body.contentLength() <= MAX_PRESET_BYTES) { "Preset is too large" }
            val bytes = body.byteStream().use { input ->
                val result = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(result.size() + count <= MAX_PRESET_BYTES) { "Preset is too large" }
                    result.write(buffer, 0, count)
                }
                result.toByteArray()
            }
            currentCoroutineContext().ensureActive()
            transport.sessions.check(owner)
            if (entitlement != null && entitlement.expiresAt <= System.currentTimeMillis())
                throw ApiException(ErrorKind.Forbidden)
            require(bytes.isNotEmpty() && MessageDigest.getInstance("MD5").digest(bytes)
                .joinToString("") { value -> "%02x".format(value.toInt() and 255) }
                .equals(effect.presetMd5, ignoreCase = true)) { "Preset checksum mismatch" }
            bytes
        }
    }

    /** An explicit legacy VIP effect selection rechecks rights rather than trusting a previous click. */
    suspend fun originalPresetEntitlement(): NeteaseEffectEntitlement {
        mutex.withLock { cachedSmartEntitlement = null }
        return originalSmartEntitlement()
    }

    /** Original client song-to-preset key. A successful lookup does not grant VIP entitlement. */
    suspend fun originalSmartKey(songId: Long): String = mutex.withLock {
        require(songId > 0)
        val owner = transport.sessions.snapshot()
        resetFor(owner)
        originalSmartKeys[songId]?.takeIf { it.expiresAt > now() }?.let {
            transport.sessions.check(owner)
            return@withLock it.value
        }
        val raw = official("/api/usertool/smart/sound/get",
            buildJsonObject { put("songId", songId.toString()) }, owner)
        val key = (raw["data"] as? JsonObject)?.text("soundKey")?.trim()
            ?.takeIf { it.matches(Regex("[0-9]{1,8}")) } ?: throw ApiException(ErrorKind.Parse)
        currentCoroutineContext().ensureActive()
        transport.sessions.check(owner)
        originalSmartKeys[songId] = Cached(key, now() + CACHE_MS)
        while (originalSmartKeys.size > MAX_ANALYSES) originalSmartKeys.remove(originalSmartKeys.keys.first())
        key
    }

    /** The original SmartAEModeView checks active black VIP, not the public sound-key response. */
    suspend fun originalSmartEntitlement(): NeteaseEffectEntitlement = mutex.withLock {
        val owner = transport.sessions.snapshot()
        resetFor(owner)
        val uid = owner.credential.profile?.uid ?: 0
        if (uid <= 0 || owner.credential.stale || owner.credential.cookies["MUSIC_U"].isNullOrBlank())
            return@withLock NeteaseEffectEntitlement()
        cachedSmartEntitlement?.takeIf { it.expiresAt > now() &&
            (!it.value.smartAllowed || it.value.expiresAt > System.currentTimeMillis()) }?.let {
            transport.sessions.check(owner)
            return@withLock it.value
        }
        val raw = official("/api/music-vip-membership/client/vip/info",
            buildJsonObject { put("userId", uid) }, owner)
        val data = raw["data"] as? JsonObject ?: throw ApiException(ErrorKind.Parse)
        if (data.long("userId") != uid) throw ApiException(ErrorKind.Parse)
        val associator = data["associator"] as? JsonObject
        val expires = associator?.long("expireTime") ?: 0
        val allowed = associator?.long("vipCode") == 100L && expires > System.currentTimeMillis()
        val result = NeteaseEffectEntitlement(uid, allowed, expires.coerceAtLeast(0))
        currentCoroutineContext().ensureActive()
        transport.sessions.check(owner)
        cachedSmartEntitlement = Cached(result, now() + EMPTY_CACHE_MS)
        result
    }

    suspend fun analyze(songId: Long): NeteaseSongAnalysis? = mutex.withLock {
        require(songId > 0)
        val owner = transport.sessions.snapshot()
        resetFor(owner)
        analyses[songId]?.takeIf { it.expiresAt > now() }?.let {
            transport.sessions.check(owner)
            return@withLock it.value
        }
        val raw = official("/api/vip-rights/emotion/audio/effect/get",
            buildJsonObject { put("songIds", "[$songId]") }, owner)
        val rows = raw["data"] as? JsonArray ?: throw ApiException(ErrorKind.Parse)
        val row = rows.asSequence().mapNotNull { it as? JsonObject }.firstOrNull { it.long("song_id") == songId }
        val result = row?.let {
            // Do not remove individual invalid bins: that would shift all frequency positions.
            val mel = (it["mel_spectrogram_32"] as? JsonArray)?.map { bin ->
                (bin as? JsonPrimitive)?.floatOrNull?.takeIf(Float::isFinite)
            }.orEmpty().takeIf { bins -> bins.size == 32 && bins.all { bin -> bin != null } }
                ?.filterNotNull().orEmpty()
            NeteaseSongAnalysis(songId,
                it.text("emotion_label").split(',').map(String::trim).filter(String::isNotEmpty).distinct().take(64),
                it.number("repbpm")?.takeIf { bpm -> bpm > 0 }, mel, it.number("loudness_lufs"),
                it.ratio("vocal_energy_ratio"), it.ratio("bass_energy_ratio"),
                it.number("stereo_width_ratio")?.takeIf { width -> width >= 0 })
        }
        currentCoroutineContext().ensureActive()
        transport.sessions.check(owner)
        analyses[songId] = Cached(result, now() + if (result == null) EMPTY_CACHE_MS else CACHE_MS)
        while (analyses.size > MAX_ANALYSES) analyses.remove(analyses.keys.first())
        result
    }

    private fun resetFor(owner: NeteaseSession) {
        transport.sessions.check(owner)
        if (revision == owner.revision) return
        cachedCatalog = null
        cachedSmartEntitlement = null
        analyses.clear()
        originalSmartKeys.clear()
        revision = owner.revision
    }

    private suspend fun official(path: String, body: JsonObject, owner: NeteaseSession,
        clientProfile: NeteaseClientProfile = NeteaseClientProfile.DESKTOP): JsonObject {
        val raw = transport.request(path, body, session = owner, clientProfile = clientProfile).body
        transport.sessions.check(owner)
        val code = (raw["code"] as? JsonPrimitive)?.intOrNull ?: throw ApiException(ErrorKind.Parse)
        if (code != 200) throw ApiException(when (code) {
            301, 401 -> ErrorKind.NeteaseBindingRequired
            403 -> ErrorKind.Forbidden
            404 -> ErrorKind.NotFound
            406, 429 -> ErrorKind.RateLimited
            else -> ErrorKind.Server
        }, code)
        return raw
    }

    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.long(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
    private fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.floatOrNull?.takeIf(Float::isFinite)
    private fun JsonObject.ratio(key: String) = number(key)?.takeIf { it in 0f..1f }

    private companion object {
        const val CACHE_MS = 15 * 60_000L
        const val EMPTY_CACHE_MS = 60_000L
        const val MAX_ANALYSES = 64
        const val MAX_CATALOG = 256
        const val MAX_PRESET_BYTES = 2 * 1024 * 1024
    }
}

internal fun originalPresetUrl(value: String): HttpUrl? = value.toHttpUrlOrNull()?.takeIf {
    it.isHttps && it.port == 443 && it.username.isEmpty() && it.password.isEmpty() &&
        (it.host == "music.126.net" || it.host.endsWith(".music.126.net")) &&
        it.encodedPath.endsWith(".ncae", ignoreCase = true) && it.fragment == null
}

/** Preserve original authorization fields; the material file hash differs from the sound hash. */
internal fun parseOriginalSoundEffect(row: JsonObject): NeteaseSoundEffect? {
    fun text(key: String) = (row[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun number(key: String) = (row[key] as? JsonPrimitive)?.longOrNull
    val id = number("id")?.takeIf { it > 0 } ?: return null
    val name = text("name").trim().takeIf { it.isNotBlank() && it.length <= 200 } ?: return null
    if (row["soundMaterialList"] != null && row["soundMaterialList"] != JsonNull && row["soundMaterialList"] !is JsonArray) return null
    val files = row["soundMaterialList"] as? JsonArray
    fun validMaterial(file: JsonObject) =
        (file["md5"] as? JsonPrimitive)?.contentOrNull.orEmpty().matches(Regex("[a-fA-F0-9]{32}")) &&
            originalPresetUrl((file["link"] as? JsonPrimitive)?.contentOrNull.orEmpty().replace("http:", "https:")) != null
    // The Android home DTO can omit material type for its sole NCAE file. Do not infer
    // a type for an ambiguous list or override an explicit unsupported material type.
    val selected = if (!files.isNullOrEmpty()) {
        files.filterIsInstance<JsonObject>().firstOrNull {
            (it["type"] as? JsonPrimitive)?.longOrNull == 1L && validMaterial(it)
        } ?: (files.singleOrNull() as? JsonObject)?.takeIf {
            (it["type"] == null || it["type"] == JsonNull) && validMaterial(it)
        } ?: return null
    } else row
    val link = (selected["link"] as? JsonPrimitive)?.contentOrNull.orEmpty().replace("http:", "https:")
    val hash = (selected["md5"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    if (!hash.matches(Regex("[a-fA-F0-9]{32}")) || originalPresetUrl(link) == null) return null
    val memberType = number("type")?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt() ?: return null
    val soundType = if (row.containsKey("soundType")) number("soundType")
        ?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt() ?: return null else 1
    return NeteaseSoundEffect(id, name, text("subTitle").ifBlank { text("playerFloatLayerDesc") },
        text("newLightImg").ifBlank { text("imgUrl") }.replace("http:", "https:"),
        memberType = memberType, soundType = soundType, presetLink = link, presetMd5 = hash,
        strategyCode = when (val strategy = row["newVipStrategyCode"]) {
            null, JsonNull -> ""
            is JsonPrimitive -> strategy.contentOrNull?.trim().orEmpty()
            else -> "unsupported"
        }, hasVipAuthStrategy = row["vipAuthRespItem"]?.let { it != JsonNull } ?: false)
}
