package io.github.currencortex.music.data.feedback

import android.content.Context
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.security.SecureTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
internal data class FeedbackSubmission(
    val requestId: String,
    val content: String,
    val startedAt: Long,
    val exportLogs: Boolean = false,
    val diagnosticsZip: String? = null,
    val accepted: Boolean = false,
    val includeAppLogs: Boolean? = null,
    val includeAudioDiagnostics: Boolean = false,
)

internal data class FeedbackOwner(val server: String, val accountId: Long)

internal class FeedbackSubmissionStore(context: Context, private val storageSuffix: String) {
    private val context = context.applicationContext

    suspend fun read(owner: FeedbackOwner): FeedbackSubmission? = withContext(Dispatchers.IO) {
        store(owner).read()?.let { ApiJson.decodeFromString<FeedbackSubmission>(it) }
    }

    suspend fun write(owner: FeedbackOwner, submission: FeedbackSubmission) = withContext(Dispatchers.IO) {
        store(owner).write(ApiJson.encodeToString(submission))
    }

    suspend fun clear(owner: FeedbackOwner) = withContext(Dispatchers.IO) {
        store(owner).clear()
    }

    private fun store(owner: FeedbackOwner): SecureTokenStore {
        val serverHash = MessageDigest.getInstance("SHA-256").digest(owner.server.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return SecureTokenStore(context, "$storageSuffix.feedback.${owner.accountId}.$serverHash")
    }
}
