package io.github.currencortex.music.feature.settings

import android.os.SystemClock
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.logging.DiagnosticExporter
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.data.feedback.FEEDBACK_CONTENT_LENGTH
import io.github.currencortex.music.data.feedback.FeedbackOwner
import io.github.currencortex.music.data.feedback.FeedbackMedia
import io.github.currencortex.music.data.feedback.FeedbackMediaLimits
import io.github.currencortex.music.data.feedback.FeedbackMediaReceipt
import io.github.currencortex.music.data.feedback.MediaSlot
import io.github.currencortex.music.data.feedback.FeedbackAttachmentCapabilities
import io.github.currencortex.music.data.feedback.FeedbackMediaStore
import io.github.currencortex.music.data.feedback.FeedbackReceipt
import io.github.currencortex.music.data.feedback.FeedbackRepository
import io.github.currencortex.music.data.feedback.FeedbackStatus
import io.github.currencortex.music.data.feedback.FeedbackSubmission
import io.github.currencortex.music.data.feedback.feedbackContentLength
import io.github.currencortex.music.data.feedback.trimFeedbackContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

internal data class FeedbackAttempt(
    val requestId: String,
    val content: String,
    val startedAt: Long = System.currentTimeMillis(),
    val receipt: FeedbackReceipt? = null,
    val busy: Boolean = true,
    val error: String? = null,
    val exportLogs: Boolean = false,
    val attachLogs: Boolean = false,
    val attachAudioDiagnostics: Boolean = false,
    val accepted: Boolean = false,
    val media: List<MediaSlot> = emptyList(),
) {
    val hasAttachments get() = attachLogs || attachAudioDiagnostics
    val canEdit get() = !busy && receipt?.status in setOf(FeedbackStatus.FAILED, FeedbackStatus.REJECTED)
    val canRetry get() = !busy && error != null && (receipt == null || receipt.status.pending)
    val queryOnly get() = accepted || receipt != null
}

private val FeedbackStatus.pending get() = this == FeedbackStatus.PROCESSING || this == FeedbackStatus.PUBLISHING

internal class FeedbackViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = FeedbackRepository(container.apiClient)
    private val store = container.feedbackStore
    private val mediaStore = container.feedbackMediaStore
    private val attempts = MutableStateFlow<Map<FeedbackOwner, FeedbackAttempt>>(emptyMap())
    val state = attempts.asStateFlow()
    private val forms = MutableStateFlow<Map<FeedbackOwner, FeedbackFormState>>(emptyMap())
    val formState = forms.asStateFlow()

    suspend fun audioDiagnosticReport() = audioOutputDiagnosticReport(container)

    fun editForm(owner: FeedbackOwner, edit: (FeedbackFormState) -> FeedbackFormState) {
        forms.update { it + (owner to edit(it[owner] ?: FeedbackFormState())) }
    }

    fun owner(session: RequestSession = container.projectSession()): FeedbackOwner? =
        container.accountRepository.state.value.account
            ?.takeIf { !session.token.isNullOrBlank() }
            ?.let { FeedbackOwner(session.server, it.id) }

    suspend fun media(owner: FeedbackOwner, requestId: String): List<MediaSlot> = mediaStore.list(owner, requestId)

    fun saveMedia(owner: FeedbackOwner, requestId: String, media: MediaSlot) {
        viewModelScope.launch {
            runCatching {
                mediaStore.write(owner, requestId, media)
                val current = attempts.value[owner]
                if (current?.requestId == requestId) setAttempt(owner, current.copy(media = mediaStore.list(owner, requestId)))
            }
        }
    }

    suspend fun replaceFormMedia(owner: FeedbackOwner, requestId: String) {
        val current = forms.value[owner] ?: return
        forms.update { it + (owner to current.copy(media = mediaStore.list(owner, requestId))) }
    }

    fun removeMedia(owner: FeedbackOwner, requestId: String, slotNumber: Int) {
        viewModelScope.launch {
            mediaStore.delete(owner, requestId, slotNumber)
            replaceFormMedia(owner, requestId)
            val current = attempts.value[owner]
            if (current?.requestId == requestId) setAttempt(owner, current.copy(media = mediaStore.list(owner, requestId)))
        }
    }

    suspend fun uploadMedia(owner: FeedbackOwner, requestId: String, slot: String): FeedbackMediaReceipt? {
        val session = container.projectSession()
        val slotNumber = if (slot == "video-1") 3 else slot.substringAfterLast('-').toIntOrNull()?.minus(1) ?: return null
        val media = mediaStore.read(owner, requestId, slotNumber) ?: return null
        val limits = repository.attachmentCapabilities(session)?.media ?: return null
        val receipt = repository.uploadMedia(session, requestId, slot, media.contentType,
            FeedbackMediaStore.decode(media.bytesBase64), limits)
        mediaStore.write(owner, requestId, media.copy(uploadedSha256 = receipt.sha256))
        return receipt
    }

    suspend fun restore(owner: FeedbackOwner) {
        if (attempts.value[owner] != null) return
        val saved = store.read(owner) ?: return
        val session = container.projectSession()
        if (owner(session) != owner || attempts.value[owner] != null) return
        val attempt = FeedbackAttempt(saved.requestId, saved.content, saved.startedAt, exportLogs = saved.exportLogs,
            attachLogs = saved.includeAppLogs ?: (saved.diagnosticsZip != null || saved.exportLogs),
            attachAudioDiagnostics = saved.includeAudioDiagnostics, accepted = saved.accepted,
            media = mediaStore.list(owner, saved.requestId))
        if (attempt.expired()) {
            if (saved.diagnosticsZip != null) store.write(owner, saved.copy(diagnosticsZip = null, accepted = true))
            setAttempt(owner, attempt.unconfirmed("回执已过期，请先检查 Issues 列表。"))
        } else {
            run(owner, session, attempt, lookupOnly = true)
        }
    }

    fun submit(owner: FeedbackOwner, content: String, attachLogs: Boolean, attachAudioDiagnostics: Boolean, requestId: String) {
        val session = container.projectSession()
        if (owner(session) != owner || attempts.value[owner] != null || content.feedbackContentLength() !in FEEDBACK_CONTENT_LENGTH)
            return
        val text = content.trimFeedbackContent()
        if ((attachLogs || attachAudioDiagnostics) && !text.startsWith("[Bug] ")) return
        if (attachAudioDiagnostics && FeedbackDraft.fromContent(text).values["module"] != FEEDBACK_AUDIO_OUTPUT_MODULE) return
        val attempt = FeedbackAttempt(requestId, text, attachLogs = attachLogs)
        run(owner, session, attempt.copy(attachAudioDiagnostics = attachAudioDiagnostics))
    }

    fun retry(owner: FeedbackOwner) {
        val session = container.projectSession()
        val attempt = attempts.value[owner] ?: return
        if (owner(session) != owner || !attempt.canRetry) return
        if (attempt.expired()) {
            setAttempt(owner, attempt.copy(busy = true))
            viewModelScope.launch {
                finishLocalUpload(owner, attempt.requestId)
                setAttempt(owner, attempt.unconfirmed("回执已过期，请先检查 Issues 列表。"))
            }
            return
        }
        run(owner, session, attempt.copy(busy = true, error = null))
    }

    fun clear(owner: FeedbackOwner) {
        val attempt = attempts.value[owner]?.takeUnless { it.busy } ?: return
        setAttempt(owner, attempt.copy(busy = true))
        viewModelScope.launch {
            try {
                store.clear(owner)
                attempts.update { it - owner }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setAttempt(owner, attempt.copy(error = "无法清除本地回执，请重试。"))
            }
        }
    }

    private fun run(owner: FeedbackOwner, session: RequestSession, initial: FeedbackAttempt, lookupOnly: Boolean = false) {
        setAttempt(owner, initial)
        viewModelScope.launch {
            var attempt = initial
            var preparing = false
            try {
                val saved = store.read(owner)?.takeIf { it.requestId == attempt.requestId }
                val queryOnly = lookupOnly || attempt.queryOnly || saved?.accepted == true
                var receipt = when {
                    queryOnly -> repository.receipt(session, attempt.requestId)
                    else -> {
                        preparing = saved == null && attempt.hasAttachments
                        val limits = if (preparing) repository.capabilities(session) else null
                        val submission = saved ?: run {
                            val zip = limits?.let {
                                val audio = if (attempt.attachAudioDiagnostics) audioDiagnosticReport() else null
                                DiagnosticExporter.feedbackZip(container.logger, it.maxZipBytes, it.maxUncompressedBytes,
                                    includeAppLogs = attempt.attachLogs, audioDiagnostics = audio)
                                    ?: throw ApiException(ErrorKind.Unknown, 413)
                            }
                            FeedbackSubmission(attempt.requestId, attempt.content, attempt.startedAt,
                                exportLogs = attempt.exportLogs || (attempt.hasAttachments && limits == null),
                                diagnosticsZip = zip?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
                                includeAppLogs = attempt.attachLogs,
                                includeAudioDiagnostics = attempt.attachAudioDiagnostics,
                            ).also { store.write(owner, it) }
                        }
                        attempt = attempt.copy(exportLogs = submission.exportLogs)
                        val zip = submission.diagnosticsZip?.let { Base64.decode(it, Base64.NO_WRAP) }
                        preparing = false
                        val media = mediaStore.list(owner, submission.requestId)
                        if (media.isNotEmpty()) {
                            media.filter { it.uploadedSha256 == null }.forEach { item ->
                                uploadMedia(owner, submission.requestId, localToApiMedia(item).slot)
                            }
                        }
                        val refreshedMedia = mediaStore.list(owner, submission.requestId)
                        val uploaded = refreshedMedia.filter { it.uploadedSha256 != null }
                        if (uploaded.size != refreshedMedia.size) throw ApiException(ErrorKind.Unknown, 409, "附件尚未上传完成")
                        repository.submit(session, submission.requestId, submission.content, zip, limits,
                            uploaded.map(::localToApiMedia))
                    }
                }
                attempt = attempt.copy(accepted = true)
                finishLocalUpload(owner, attempt.requestId)
                val pollUntil = SystemClock.elapsedRealtime() + POLLING_WINDOW_MS
                while (true) {
                    val pending = receipt.status.pending
                    val editable = receipt.status in setOf(FeedbackStatus.FAILED, FeedbackStatus.REJECTED)
                    attempt = attempt.copy(receipt = receipt, busy = pending,
                        content = attempt.content.takeIf { pending || editable }.orEmpty())
                    setAttempt(owner, attempt)
                    if (!pending) break
                    if (SystemClock.elapsedRealtime() >= pollUntil) {
                        setAttempt(owner, attempt.copy(busy = false, error = "反馈仍在处理，请稍后查询。"))
                        break
                    }
                    delay(3_000)
                    if (owner() != owner) throw ApiException(ErrorKind.Unauthorized)
                    receipt = repository.receipt(session, attempt.requestId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val status = (error as? ApiException)?.status
                val updated = when {
                    status in setOf(400, 413) && !attempt.queryOnly && !lookupOnly ->
                        attempt.copy(receipt = FeedbackReceipt(FeedbackStatus.REJECTED), busy = false,
                            error = if (status == 413) "诊断包超过大小上限。可减少附件后重新提交。"
                                else "此反馈未被接受，请检查内容和诊断附件后重新提交。")
                    preparing ->
                        attempt.copy(receipt = FeedbackReceipt(FeedbackStatus.REJECTED), busy = false,
                            error = "暂时无法准备诊断附件。可重新提交，或移除附件后提交文字。")
                    status == 409 -> {
                        finishLocalUpload(owner, attempt.requestId)
                        attempt.unconfirmed("该请求与已接收的反馈冲突，请先检查 Issues 列表。")
                    }
                    status == 404 && attempt.queryOnly ->
                        attempt.unconfirmed("找不到反馈回执，请先检查 Issues 列表。")
                    status == 404 && lookupOnly ->
                        attempt.copy(busy = false, error = "上次提交结果待确认，可重试同一条反馈。")
                    else -> attempt.copy(busy = false, error = when (status) {
                        401 -> "登录已过期，请重新登录后继续。"
                        408 -> "上传超时，请重试同一条反馈。"
                        429 -> "当前账号已有反馈在处理或已达到每日上限，请稍后重试。"
                        503 -> "反馈服务暂不可用，请稍后重试。"
                        else -> if (!attempt.queryOnly) "未能确认提交结果，请重试同一条反馈。"
                            else "暂时无法查询回执，请重试查询。"
                    })
                }
                setAttempt(owner, updated)
            }
        }
    }

    private suspend fun finishLocalUpload(owner: FeedbackOwner, requestId: String) {
        try {
            val saved = store.read(owner)?.takeIf { it.requestId == requestId && !it.accepted } ?: return
            store.write(owner, saved.copy(diagnosticsZip = null, accepted = true))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { }
    }

    private fun FeedbackAttempt.unconfirmed(message: String) = copy(
        receipt = FeedbackReceipt(FeedbackStatus.UNCONFIRMED), content = "", busy = false, error = message,
    )

    private fun localToApiMedia(media: MediaSlot): FeedbackMedia = FeedbackMedia(
        slot = if (media.slot == 3) "video-1" else "screenshot-${media.slot + 1}",
        sha256 = media.uploadedSha256 ?: media.sha256,
    )

    private fun FeedbackAttempt.expired() = System.currentTimeMillis() - startedAt >= RECEIPT_RETENTION_MS

    private fun setAttempt(owner: FeedbackOwner, attempt: FeedbackAttempt) {
        attempts.update { it + (owner to attempt) }
    }

    private companion object {
        const val RECEIPT_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
        const val POLLING_WINDOW_MS = 90_000L
    }
}
