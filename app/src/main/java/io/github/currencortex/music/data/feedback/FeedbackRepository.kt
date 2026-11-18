package io.github.currencortex.music.data.feedback

import io.github.currencortex.music.core.network.ApiClient
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.core.network.RequestSession
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

internal const val FEEDBACK_ISSUES_URL = "https://github.com/bileizhen/CurrentMusicX/issues"
internal val FEEDBACK_CONTENT_LENGTH = 8..2000

internal fun String.trimFeedbackContent(): String = trim { it.isWhitespace() || it == '\u0085' }

internal fun String.feedbackContentLength(): Int {
    val text = trimFeedbackContent()
    return text.codePointCount(0, text.length)
}

internal class FeedbackRepository(private val api: ApiClient) {
    suspend fun capabilities(session: RequestSession): FeedbackDiagnosticsLimits? =
        readCapabilities(session)?.diagnosticsLimits()

    suspend fun attachmentCapabilities(session: RequestSession): FeedbackAttachmentCapabilities? {
        val capabilities = readCapabilities(session) ?: return null
        val diagnostics = capabilities.diagnosticsLimits()
        val media = capabilities.enabledLimits("media")?.let(::parseMediaLimits)
        return if (diagnostics == null && media == null) null else FeedbackAttachmentCapabilities(diagnostics, media)
    }

    private suspend fun readCapabilities(session: RequestSession): JsonObject? {
        val response = try {
            api.feedbackCapabilities(session)
        } catch (error: ApiException) {
            if (error.status == 404) return null
            throw error
        } ?: return null
        val capabilities = response as? JsonObject ?: throw ApiException(ErrorKind.Parse)
        if ("diagnostics" !in capabilities && "media" !in capabilities) throw ApiException(ErrorKind.Parse)
        return capabilities
    }

    private fun JsonObject.diagnosticsLimits(): FeedbackDiagnosticsLimits? =
        enabledLimits("diagnostics")?.let {
            FeedbackDiagnosticsLimits(
                maxZipBytes = it.readLimit("maxZipBytes").coerceAtMost(protocolLimits.maxZipBytes),
                maxUncompressedBytes = it.readLimit("maxUncompressedBytes").coerceAtMost(protocolLimits.maxUncompressedBytes),
                maxRequestBytes = it.readLimit("maxRequestBytes").coerceAtMost(protocolLimits.maxRequestBytes),
            )
        }

    suspend fun uploadMedia(
        session: RequestSession,
        requestId: String,
        slot: String,
        contentType: String,
        bytes: ByteArray,
        limits: FeedbackMediaLimits? = null,
    ): FeedbackMediaReceipt {
        validateRequestId(requestId)
        validateMediaSlot(slot)
        require(bytes.isNotEmpty()) { "反馈媒体不能为空" }
        val video = slot == "video-1"
        val allowedTypes = if (video) videoContentTypes else screenshotContentTypes
        val advertisedTypes = if (video) limits?.video?.contentTypes else limits?.screenshot?.contentTypes
        if (contentType !in allowedTypes || (advertisedTypes != null && contentType !in advertisedTypes))
            throw ApiException(ErrorKind.Unknown, 415, "反馈媒体格式不受支持")
        val protocolMax = if (video) 25 * 1024 * 1024 else 2 * 1024 * 1024
        val advertisedMax = if (video) limits?.video?.maxBytes else limits?.screenshot?.maxBytes
        if (bytes.size > minOf(protocolMax, advertisedMax ?: protocolMax, limits?.maxTotalBytes ?: protocolMax))
            throw ApiException(ErrorKind.Unknown, 413, "反馈媒体超过大小上限")
        val payload = withContext(Dispatchers.Default) { bytes.copyOf().toRequestBody(contentType.toMediaType()) }
        val receipt = api.uploadFeedbackMedia(session, requestId, slot, payload) as? JsonObject
            ?: throw ApiException(ErrorKind.Parse)
        if (receipt["slot"] != JsonPrimitive(slot)) throw ApiException(ErrorKind.Parse)
        val sha256 = receipt.readString("sha256")
        if (!sha256Pattern.matches(sha256)) throw ApiException(ErrorKind.Parse)
        val expiresAt = receipt.readString("expiresAt")
        if (runCatching { Instant.parse(expiresAt) }.isFailure) throw ApiException(ErrorKind.Parse)
        return FeedbackMediaReceipt(slot, sha256, expiresAt)
    }

    suspend fun deleteMedia(session: RequestSession, requestId: String, slot: String) {
        validateRequestId(requestId)
        validateMediaSlot(slot)
        api.deleteFeedbackMedia(session, requestId, slot)
    }

    suspend fun submit(
        session: RequestSession,
        requestId: String,
        content: String,
        diagnostics: ByteArray? = null,
        limits: FeedbackDiagnosticsLimits? = null,
        media: List<FeedbackMedia> = emptyList(),
    ): FeedbackReceipt {
        validateRequestId(requestId)
        val text = content.trimFeedbackContent()
        require(text.codePointCount(0, text.length) in FEEDBACK_CONTENT_LENGTH) { "反馈内容须为 8 至 2000 字" }
        val mediaManifest = media.takeIf { it.isNotEmpty() }?.let(::buildMediaManifest)
        val attachments = diagnostics?.let {
            diagnosticsBody(requestId, text, it, limits ?: protocolLimits, mediaManifest)
        }
        val response = api.submitFeedback(
            expectedSession = session,
            jsonBody = buildJsonObject {
                put("requestId", requestId)
                put("content", text)
                put("public", true)
                if (mediaManifest != null) {
                    put("mediaConsent", true)
                    put("media", mediaManifest)
                }
            },
            attachmentBody = attachments,
            hasMedia = mediaManifest != null,
        )
        return parseReceipt(response, requestId)
    }

    private fun diagnosticsBody(
        requestId: String,
        content: String,
        diagnostics: ByteArray,
        limits: FeedbackDiagnosticsLimits,
        mediaManifest: JsonArray?,
    ): MultipartBody {
        require(diagnostics.isNotEmpty()) { "诊断包不能为空" }
        if (diagnostics.size > minOf(limits.maxZipBytes, protocolLimits.maxZipBytes))
            throw ApiException(ErrorKind.Unknown, 413, "诊断包超过压缩大小上限")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("requestId", requestId)
            .addFormDataPart("content", content)
            .addFormDataPart("public", "true")
            .addFormDataPart("diagnosticsConsent", "true")
            .addFormDataPart("diagnostics", "diagnostics.zip", diagnostics.copyOf().toRequestBody("application/zip".toMediaType()))
            .apply {
                if (mediaManifest != null) {
                    addFormDataPart("mediaConsent", "true")
                    addFormDataPart("media", mediaManifest.toString())
                }
            }
            .build()
        if (body.contentLength() !in 1L..minOf(limits.maxRequestBytes, protocolLimits.maxRequestBytes).toLong())
            throw ApiException(ErrorKind.Unknown, 413, "反馈请求超过大小上限")
        return body
    }

    suspend fun receipt(session: RequestSession, requestId: String): FeedbackReceipt {
        validateRequestId(requestId)
        val response = api.request(
            method = "GET",
            path = "feedback",
            query = mapOf("requestId" to requestId),
            authenticated = true,
            expectedSession = session,
            projectOnly = true,
        )
        return parseReceipt(response, requestId)
    }

    private fun validateRequestId(requestId: String) {
        require(requestIdPattern.matches(requestId)) { "requestId 须为 32 位小写十六进制字符串" }
    }

    private fun validateMediaSlot(slot: String) {
        require(slot in mediaSlots) { "反馈媒体槽位无效" }
    }

    private fun buildMediaManifest(media: List<FeedbackMedia>): JsonArray {
        require(media.size <= mediaSlots.size && media.distinctBy { it.slot }.size == media.size) { "反馈媒体槽位重复或超限" }
        return JsonArray(media.map { item ->
            validateMediaSlot(item.slot)
            require(sha256Pattern.matches(item.sha256)) { "反馈媒体 SHA-256 无效" }
            buildJsonObject {
                put("slot", item.slot)
                put("sha256", item.sha256)
            }
        })
    }

    private fun JsonObject.enabledLimits(key: String): JsonObject? {
        val section = this[key] ?: return null
        val limits = section as? JsonObject ?: throw ApiException(ErrorKind.Parse)
        return when (limits["enabled"]) {
            JsonPrimitive(true) -> limits
            JsonPrimitive(false) -> null
            null -> if (key == "media") null else throw ApiException(ErrorKind.Parse)
            else -> throw ApiException(ErrorKind.Parse)
        }
    }

    private fun parseMediaLimits(media: JsonObject): FeedbackMediaLimits {
        val screenshot = media["screenshot"] as? JsonObject ?: throw ApiException(ErrorKind.Parse)
        val video = media["video"] as? JsonObject ?: throw ApiException(ErrorKind.Parse)
        return FeedbackMediaLimits(
            screenshot = FeedbackMediaLimits.Screenshot(
                maxCount = screenshot.readLimit("maxCount"),
                maxBytes = screenshot.readLimit("maxBytes"),
                contentTypes = screenshot.readContentTypes(),
                maxLongEdge = screenshot.readLimit("maxLongEdge"),
                maxPixels = screenshot.readLimit("maxPixels"),
            ),
            video = FeedbackMediaLimits.Video(
                maxCount = video.readLimit("maxCount"),
                maxBytes = video.readLimit("maxBytes"),
                contentTypes = video.readContentTypes(),
                maxDurationSeconds = video.readLimit("maxDurationSeconds"),
                maxLongEdge = video.readLimit("maxLongEdge"),
                maxShortEdge = video.readLimit("maxShortEdge"),
            ),
            maxTotalBytes = media.readLimit("maxTotalBytes"),
            stagingTtlSeconds = media.readLimit("stagingTtlSeconds"),
        )
    }

    private fun JsonObject.readLimit(key: String): Int =
        this[key].strictInt()?.takeIf { it > 0 } ?: throw ApiException(ErrorKind.Parse)

    private fun JsonObject.readString(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw ApiException(ErrorKind.Parse)

    private fun JsonObject.readContentTypes(): List<String> =
        ((this["contentTypes"] as? JsonArray)?.takeIf { it.isNotEmpty() } ?: throw ApiException(ErrorKind.Parse))
            .map { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content ?: throw ApiException(ErrorKind.Parse) }

    private fun JsonElement?.strictInt(): Int? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

    private fun parseReceipt(response: JsonElement, requestId: String): FeedbackReceipt {
        val receipt = response as? JsonObject ?: throw ApiException(ErrorKind.Parse)
        if (receipt["requestId"] != JsonPrimitive(requestId)) throw ApiException(ErrorKind.Parse)
        val status = when (receipt["status"]) {
            JsonPrimitive("processing") -> FeedbackStatus.PROCESSING
            JsonPrimitive("publishing") -> FeedbackStatus.PUBLISHING
            JsonPrimitive("published") -> FeedbackStatus.PUBLISHED
            JsonPrimitive("rejected") -> FeedbackStatus.REJECTED
            JsonPrimitive("failed") -> FeedbackStatus.FAILED
            JsonPrimitive("unconfirmed") -> FeedbackStatus.UNCONFIRMED
            else -> throw ApiException(ErrorKind.Parse)
        }
        val issueNumber = if (status == FeedbackStatus.PUBLISHED) {
            receipt["issueNumber"].strictInt()
                ?.takeIf { it > 0 } ?: throw ApiException(ErrorKind.Parse)
        } else null
        val hasDiagnostics = when (receipt["hasDiagnostics"]) {
            null, JsonPrimitive(false) -> false
            JsonPrimitive(true) -> true
            else -> throw ApiException(ErrorKind.Parse)
        }
        val mediaCount = receipt["mediaCount"]?.let {
            it.strictInt()?.takeIf { count -> count >= 0 } ?: throw ApiException(ErrorKind.Parse)
        } ?: 0
        return FeedbackReceipt(status, issueNumber, hasDiagnostics, mediaCount)
    }

    private companion object {
        val requestIdPattern = Regex("[0-9a-f]{32}")
        val sha256Pattern = Regex("[0-9a-f]{64}")
        val mediaSlots = setOf("screenshot-1", "screenshot-2", "screenshot-3", "video-1")
        val screenshotContentTypes = setOf("image/jpeg", "image/png")
        val videoContentTypes = setOf("video/mp4")
        val protocolLimits = FeedbackDiagnosticsLimits(
            maxZipBytes = 2 * 1024 * 1024,
            maxUncompressedBytes = 4 * 1024 * 1024,
            maxRequestBytes = 2 * 1024 * 1024 + 64 * 1024,
        )
    }
}

internal data class FeedbackAttachmentCapabilities(
    val diagnostics: FeedbackDiagnosticsLimits?,
    val media: FeedbackMediaLimits?,
)

internal data class FeedbackDiagnosticsLimits(
    val maxZipBytes: Int,
    val maxUncompressedBytes: Int,
    val maxRequestBytes: Int,
)

internal data class FeedbackMediaLimits(
    val screenshot: Screenshot,
    val video: Video,
    val maxTotalBytes: Int,
    val stagingTtlSeconds: Int,
) {
    data class Screenshot(
        val maxCount: Int,
        val maxBytes: Int,
        val contentTypes: List<String>,
        val maxLongEdge: Int,
        val maxPixels: Int,
    )

    data class Video(
        val maxCount: Int,
        val maxBytes: Int,
        val contentTypes: List<String>,
        val maxDurationSeconds: Int,
        val maxLongEdge: Int,
        val maxShortEdge: Int,
    )
}

internal data class FeedbackMedia(val slot: String, val sha256: String)

internal data class FeedbackMediaReceipt(val slot: String, val sha256: String, val expiresAt: String) {
    val media: FeedbackMedia
        get() = FeedbackMedia(slot, sha256)
}

internal data class FeedbackReceipt(
    val status: FeedbackStatus,
    val issueNumber: Int? = null,
    val hasDiagnostics: Boolean = false,
    val mediaCount: Int = 0,
) {
    val issueUrl: String?
        get() = issueNumber?.takeIf { status == FeedbackStatus.PUBLISHED && it > 0 }
            ?.let { "$FEEDBACK_ISSUES_URL/$it" }
}

internal enum class FeedbackStatus { PROCESSING, PUBLISHING, PUBLISHED, REJECTED, FAILED, UNCONFIRMED }
