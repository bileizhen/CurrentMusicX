package io.github.currencortex.music.data.feedback

import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.security.SecureTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest
import java.util.Locale

@Serializable
internal data class MediaSlot(
    val slot: Int,
    val contentType: String,
    val bytesBase64: String,
    val sha256: String,
    val displayName: String,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val uploadedSha256: String? = null,
)

/** Encrypted local copies of selected feedback media. Uri permissions are not persisted. */
internal class FeedbackMediaStore(context: Context, storageSuffix: String = "") {
    private val appContext = context.applicationContext
    private val root = File(appContext.noBackupFilesDir, "feedback-media$storageSuffix")
    private val mutex = Mutex()

    suspend fun read(owner: FeedbackOwner, requestId: String, slot: Int): MediaSlot? = mutex.withLock {
        withContext(Dispatchers.IO) {
            validateRequest(owner, requestId, slot)
            readRecord(owner, requestId).slots.firstOrNull { it.slot == slot }
        }
    }

    suspend fun write(owner: FeedbackOwner, requestId: String, media: MediaSlot) = mutex.withLock {
        withContext(Dispatchers.IO) {
            validateRequest(owner, requestId, media.slot)
            val checked = validateMedia(media)
            val current = readRecord(owner, requestId).slots
            val total = current.filterNot { it.slot == checked.slot }.sumOf { decodedSize(it) } + decodedSize(checked)
            require(total <= MAX_TOTAL_BYTES) { "反馈媒体暂存总大小超过限制" }
            val next = current.filterNot { it.slot == checked.slot } + checked
            withContext(NonCancellable) { writeRecord(owner, requestId, MediaRecord(slots = next.sortedBy { it.slot })) }
        }
    }

    suspend fun delete(owner: FeedbackOwner, requestId: String, slot: Int? = null) = mutex.withLock {
        withContext(Dispatchers.IO + NonCancellable) {
            validateRequest(owner, requestId, slot)
            val current = readRecord(owner, requestId).slots
            val next = if (slot == null) emptyList() else current.filterNot { it.slot == slot }
            if (next.isEmpty()) {
                store(owner, requestId).clear()
                directory(owner, requestId).delete()
            } else {
                writeRecord(owner, requestId, MediaRecord(slots = next))
            }
        }
    }

    suspend fun list(owner: FeedbackOwner, requestId: String): List<MediaSlot> = mutex.withLock {
        withContext(Dispatchers.IO) {
            validateRequest(owner, requestId)
            readRecord(owner, requestId).slots.sortedBy { it.slot }
        }
    }

    private fun readRecord(owner: FeedbackOwner, requestId: String): MediaRecord {
        val raw = store(owner, requestId).read() ?: return MediaRecord()
        val element = ApiJson.parseToJsonElement(raw)
        val slots = when (element) {
            is JsonArray -> element.map(::parseSlot)
            is JsonObject -> element["slots"]?.jsonArray?.map(::parseSlot).orEmpty()
            else -> error("反馈媒体记录格式无效")
        }
        require(slots.size <= MAX_SLOTS && slots.map { it.slot }.distinct().size == slots.size) {
            "反馈媒体记录槽位无效"
        }
        return MediaRecord(slots = slots.map(::validateMedia))
    }

    private fun parseSlot(element: JsonElement): MediaSlot {
        val objectValue = element as? JsonObject ?: error("反馈媒体记录格式无效")
        val slot = objectValue.int("slot") ?: error("反馈媒体槽位缺失")
        val contentType = objectValue.string("contentType") ?: objectValue.string("mimeType")
            ?: error("反馈媒体 MIME 缺失")
        val bytesBase64 = objectValue.string("bytesBase64")
            ?: objectValue["bytes"]?.let(::legacyBytesBase64)
            ?: error("反馈媒体数据缺失")
        val normalizedBytesBase64 = bytesBase64.let(::decode).let { Base64.encodeToString(it, Base64.NO_WRAP) }
        val storedSha256 = objectValue.string("sha256")?.takeIf { it.matches(SHA256_PATTERN) }
            ?: sha256(decode(normalizedBytesBase64))
        val uploadedSha256 = objectValue.string("uploadedSha256")?.takeIf { it.matches(SHA256_PATTERN) }
        return MediaSlot(
            slot = slot,
            contentType = contentType,
            bytesBase64 = normalizedBytesBase64,
            sha256 = storedSha256,
            displayName = objectValue.string("displayName") ?: objectValue.string("name") ?: "media-$slot",
            durationMs = objectValue.long("durationMs"),
            width = objectValue.int("width"),
            height = objectValue.int("height"),
            uploadedSha256 = uploadedSha256,
        )
    }

    private fun validateMedia(media: MediaSlot): MediaSlot {
        val contentType = media.contentType.trim().lowercase(Locale.ROOT)
        require(contentType in SUPPORTED_CONTENT_TYPES) { "不支持的反馈媒体类型" }
        val bytes = decode(media.bytesBase64)
        require(bytes.isNotEmpty() && bytes.size.toLong() <= MAX_MEDIA_BYTES) { "反馈媒体大小超过限制" }
        val actualSha256 = sha256(bytes)
        require(media.sha256.matches(SHA256_PATTERN) && media.sha256.equals(actualSha256, ignoreCase = true)) {
            "反馈媒体 SHA-256 无效"
        }
        require(media.displayName.isNotBlank() && media.displayName.length <= MAX_DISPLAY_NAME &&
            media.displayName.none { it == '\u0000' || it == '/' || it == '\\' }) { "反馈媒体文件名无效" }
        require(media.durationMs == null || media.durationMs in 0..MAX_DURATION_MS) { "反馈媒体时长无效" }
        require(media.width == null || media.width in 1..MAX_DIMENSION) { "反馈媒体宽度无效" }
        require(media.height == null || media.height in 1..MAX_DIMENSION) { "反馈媒体高度无效" }
        require(media.slot in 0 until MAX_SLOTS) { "反馈媒体槽位无效" }
        return media.copy(contentType = contentType, bytesBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP), sha256 = actualSha256)
    }

    private fun writeRecord(owner: FeedbackOwner, requestId: String, record: MediaRecord) {
        ensureDirectory(owner, requestId)
        val json = buildJsonObject {
            put("version", record.version)
            put("slots", ApiJson.parseToJsonElement(ApiJson.encodeToString(record.slots)))
        }
        store(owner, requestId).write(json.toString())
    }

    private fun store(owner: FeedbackOwner, requestId: String): SecureTokenStore =
        SecureTokenStore(directoryContext(owner, requestId))

    private fun directoryContext(owner: FeedbackOwner, requestId: String): ContextWrapper =
        object : ContextWrapper(appContext) {
            override fun getNoBackupFilesDir(): File {
                val directory = directory(owner, requestId)
                if (!directory.isDirectory) check(directory.mkdirs()) { "无法创建反馈媒体目录" }
                return directory
            }
        }

    private fun directory(owner: FeedbackOwner, requestId: String): File =
        File(root, sha256("${owner.server}\n${owner.accountId}\n$requestId".toByteArray(Charsets.UTF_8)))

    private fun ensureDirectory(owner: FeedbackOwner, requestId: String) {
        check(root.isDirectory || root.mkdirs()) { "无法创建反馈媒体根目录" }
        val directory = directory(owner, requestId)
        check(directory.isDirectory || directory.mkdirs()) { "无法创建反馈媒体目录" }
        check(directory.canonicalFile.parentFile == root.canonicalFile) { "反馈媒体目录无效" }
    }

    private fun validateRequest(owner: FeedbackOwner, requestId: String, slot: Int? = null) {
        require(owner.server.isNotBlank() && owner.server.length <= MAX_OWNER_LENGTH) { "反馈服务地址无效" }
        require(requestId.isNotBlank() && requestId.length <= MAX_REQUEST_ID_LENGTH) { "反馈 requestId 无效" }
        require(slot == null || slot in 0 until MAX_SLOTS) { "反馈媒体槽位无效" }
    }

    private fun decodedSize(media: MediaSlot): Long = decode(media.bytesBase64).size.toLong()

    private fun decode(value: String): ByteArray = try {
        Base64.decode(value, Base64.DEFAULT)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("反馈媒体 Base64 无效", error)
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.content
    private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull
    private fun JsonObject.int(name: String): Int? = long(name)?.let { value -> value.toInt().takeIf { it.toLong() == value } }

    private fun legacyBytesBase64(element: JsonElement): String? = when (element) {
        is JsonArray -> Base64.encodeToString(element.map { it.jsonPrimitive.content.toInt().toByte() }.toByteArray(), Base64.NO_WRAP)
        is JsonPrimitive -> element.content
        else -> null
    }

    @Serializable
    private data class MediaRecord(val version: Int = 1, val slots: List<MediaSlot> = emptyList())

    companion object {
        private const val MAX_MEDIA_BYTES = 20L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 40L * 1024 * 1024
        private const val MAX_SLOTS = 8
        private const val MAX_DISPLAY_NAME = 256
        private const val MAX_OWNER_LENGTH = 2048
        private const val MAX_REQUEST_ID_LENGTH = 256
        private const val MAX_DURATION_MS = 24L * 60 * 60 * 1000
        private const val MAX_DIMENSION = 32768
        private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
        private val SUPPORTED_CONTENT_TYPES = setOf(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif", "image/avif",
            "video/mp4", "video/webm", "video/3gpp", "video/3gpp2", "video/quicktime", "video/x-matroska",
        )

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

        fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
        fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
    }
}
