package io.github.currencortex.music.data.effects

import android.content.Context
import android.os.Build
import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OriginalMoodEntitlement(val userId: Long = 0, val canUse: Boolean = false,
    val expiresAt: Long = 0, val trial: Boolean = false)

/** Original DAI distribution and the exact mood benefit (resource 106, type 116). */
class OriginalMoodModelRepository internal constructor(private val directory: File, private val transport: NeteaseTransport) {
    constructor(context: Context, transport: NeteaseTransport) : this(File(context.filesDir, "netease-mood-model"), transport)
    private val mutex = Mutex()
    private var cached: Pair<Long, File>? = null
    private val materialClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).cookieJar(CookieJar.NO_COOKIES).build()

    suspend fun descriptor(songId: Long): JsonObject? {
        require(songId > 0)
        val owner = transport.sessions.snapshot()
        val raw = official("/api/vip-rights/emotion/audio/effect/get",
            buildJsonObject { put("songIds", "[$songId]") }, owner)
        val rows = raw["data"] as? JsonArray ?: throw ApiException(ErrorKind.Parse)
        transport.sessions.check(owner)
        return rows.filterIsInstance<JsonObject>().firstOrNull { it.long("song_id") == songId }
    }

    suspend fun entitlement(): OriginalMoodEntitlement {
        val owner = transport.sessions.snapshot()
        val uid = owner.credential.profile?.uid ?: 0
        if (uid <= 0 || owner.credential.stale || owner.credential.cookies["MUSIC_U"].isNullOrBlank())
            return OriginalMoodEntitlement()
        val member = membership(owner, uid)
        transport.sessions.check(owner)
        return originalMoodMembershipEntitlement(uid, member)
    }

    suspend fun prepare(): File = mutex.withLock {
        val owner = transport.sessions.snapshot()
        cached?.takeIf { it.first == owner.revision && it.second.isFile }?.let { return@withLock it.second }
        val uid = owner.credential.profile?.uid ?: 0
        val member = if (uid > 0 && !owner.credential.stale && !owner.credential.cookies["MUSIC_U"].isNullOrBlank())
            membership(owner, uid) else null
        val vipType = verifiedModelVipType(member)
        val raw = official("/api/dai/distribute/getModelInfoList", buildJsonObject {
            put("modelFilterInfo", buildJsonObject {
                put("vipType", vipType); put("model", Build.MODEL); put("brand", Build.BRAND)
                put("board", Build.BOARD); put("hardware", Build.HARDWARE)
            }); put("modelFiles", "[]")
        }, owner)
        val row = ((raw["data"] as? JsonObject)?.get("modelList") as? JsonArray)
            ?.filterIsInstance<JsonObject>()?.firstOrNull { it.text("modelId") == "motional_sound_effects_model" }
            ?: throw IllegalStateException("网易云未向此设备下发情绪模型")
        val hash = row.text("modelMd5").lowercase()
        require(hash.matches(Regex("[a-f0-9]{32}"))) { "情绪模型校验信息无效" }
        val expectedSize = row.long("modelSize") ?: 0
        require(expectedSize in 1..MAX_BYTES.toLong()) { "情绪模型大小超出限制" }
        val url = row.text("modelUrl").toHttpUrlOrNull()
        require(url != null && url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            url.host.endsWith(".music.126.net") && url.fragment == null) { "情绪模型来源无效" }
        val file = withContext(Dispatchers.IO) {
            check(directory.isDirectory || directory.mkdirs())
            val container = File(directory, "$hash.data")
            val bytes = if (container.isFile && container.length() == expectedSize &&
                md5(container.readBytes()) == hash) container.readBytes() else {
                val value = download(requireNotNull(url))
                require(value.size.toLong() == expectedSize && md5(value) == hash) { "情绪模型资源校验失败" }
                transport.sessions.check(owner)
                container.writeBytes(value)
                value
            }
            val model = decodeMoodModel(bytes)
            val target = File(directory, "$hash.mnn")
            if (!target.isFile || !target.readBytes().contentEquals(model)) {
                val pending = File(directory, "$hash.pending")
                pending.writeBytes(model)
                check(pending.renameTo(target)) { "情绪模型写入失败" }
            }
            target
        }
        currentCoroutineContext().ensureActive()
        transport.sessions.check(owner)
        cached = owner.revision to file
        file
    }

    private suspend fun membership(owner: NeteaseSession, uid: Long): JsonObject {
        val raw = official("/api/music-vip-membership/client/vip/info", buildJsonObject { put("userId", uid) }, owner)
        val data = raw["data"] as? JsonObject ?: throw ApiException(ErrorKind.Parse)
        if (data.long("userId") != uid) throw ApiException(ErrorKind.Parse)
        return data
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

    private suspend fun download(url: HttpUrl): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = materialClient.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                val outcome = runCatching { response.use {
                    check(it.isSuccessful) { "情绪模型下载失败" }
                    val body = requireNotNull(it.body)
                    require(body.contentLength() <= MAX_BYTES)
                    body.byteStream().use { input -> readMoodBounded(input, MAX_BYTES) }
                } }
                outcome.fold({ if (continuation.isActive) continuation.resume(it) },
                    { if (continuation.isActive) continuation.resumeWithException(it) })
            }
        })
    }

    companion object { private const val MAX_BYTES = 2 * 1024 * 1024 }
}

/** Exact type-11 playback gate: co0/b.A -> isIgnoreAeSVip -> UserPrivilege.isRedPlus.
 * Generic benefit queries can disagree and must not veto a valid original SVIP membership.
 * Trials stay disabled until the mood-specific activation and expiration path is verified.
 */
internal fun originalMoodMembershipEntitlement(uid: Long, member: JsonObject,
    now: Long = System.currentTimeMillis()): OriginalMoodEntitlement {
    if (uid <= 0 || member.long("userId") != uid) return OriginalMoodEntitlement(uid)
    val plus = member["redplus"] as? JsonObject
    val deadline = if (plus?.long("vipCode") == 300L) plus.long("expireTime") ?: 0 else 0
    return OriginalMoodEntitlement(uid, deadline > now, deadline)
}

internal fun verifiedModelVipType(member: JsonObject?, now: Long = System.currentTimeMillis()): String {
    for ((name, expected) in listOf("redplus" to 300L, "associator" to 100L)) {
        val row = member?.get(name) as? JsonObject ?: continue
        if (row.long("vipCode") == expected && (row.long("expireTime") ?: 0) > now) return expected.toString()
    }
    for (name in listOf("musicPackage", "albumVip")) {
        val row = member?.get(name) as? JsonObject ?: continue
        val code = row.long("vipCode") ?: continue
        if (code > 0 && (row.long("expireTime") ?: 0) > now) return code.toString()
    }
    return "-1"
}

/** Exact DAI material container format; account authorization is checked separately. */
internal fun decodeMoodModel(container: ByteArray): ByteArray {
    require(container.size in 16..2_097_152 && container.size % 16 == 0)
    val plain = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
        init(Cipher.DECRYPT_MODE, SecretKeySpec("163.dai.com".toByteArray().copyOf(16), "AES"),
            IvParameterSpec("DAI".toByteArray().copyOf(16)))
        doFinal(container)
    }
    return ZipInputStream(ByteArrayInputStream(plain)).use { zip ->
        var result: ByteArray? = null
        var entries = 0
        while (true) {
            val entry = zip.nextEntry ?: break
            require(++entries <= 8)
            if (entry.name == "emofxnet.mnn" && !entry.isDirectory) {
                require(result == null && entry.size <= 2_097_152)
                result = readMoodBounded(zip, 2_097_152).also { require(it.isNotEmpty()) }
            } else if (!entry.isDirectory) readMoodBounded(zip, 4096)
            zip.closeEntry()
        }
        requireNotNull(result) { "情绪模型容器缺少原版网络" }
    }
}

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.long(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
private fun md5(value: ByteArray) = MessageDigest.getInstance("MD5").digest(value).joinToString("") { "%02x".format(it.toInt() and 255) }
private fun readMoodBounded(input: java.io.InputStream, limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "情绪模型数据超出限制" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
