package io.github.currencortex.music.feature.settings

import android.os.Build
import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.BuildConfig
import io.github.currencortex.music.core.logging.AppLogger
import io.github.currencortex.music.data.feedback.FEEDBACK_CONTENT_LENGTH
import io.github.currencortex.music.data.feedback.FEEDBACK_ISSUES_URL
import io.github.currencortex.music.data.feedback.FeedbackOwner
import io.github.currencortex.music.data.feedback.MediaSlot
import io.github.currencortex.music.data.feedback.FeedbackStatus
import io.github.currencortex.music.data.feedback.feedbackContentLength
import io.github.currencortex.music.feature.logs.LogExportDialog
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicDialogActions
import io.github.currencortex.music.ui.component.nativeui.GlassActionButton
import io.github.currencortex.music.ui.component.nativeui.SettingsDivider
import io.github.currencortex.music.ui.component.nativeui.SettingsGroup
import io.github.currencortex.music.ui.component.nativeui.SettingsHint
import io.github.currencortex.music.ui.component.nativeui.SettingsListSection
import io.github.currencortex.music.ui.component.nativeui.SettingsOption
import io.github.currencortex.music.ui.component.nativeui.SettingsOptionsRow
import io.github.currencortex.music.ui.component.nativeui.SettingsPage
import io.github.currencortex.music.ui.component.nativeui.SettingsPreference
import io.github.currencortex.music.ui.component.nativeui.SettingsSegmentedControl
import io.github.currencortex.music.ui.component.nativeui.SettingsToggle
import io.github.currencortex.music.ui.util.openExternalLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

internal const val FEEDBACK_ROUTE = "app-feedback"

private data class FeedbackConfirmation(val content: String, val attachLogs: Boolean, val attachAudioDiagnostics: Boolean, val requestId: String)

internal data class FeedbackFormState(
    val template: FeedbackTemplate = FeedbackTemplate.BUG,
    val drafts: Map<FeedbackTemplate, FeedbackDraft> = emptyMap(),
    val checked: Boolean = false,
    val attachLogs: Boolean = true,
    val attachAudioDiagnostics: Boolean = false,
    val requestId: String = UUID.randomUUID().toString().replace("-", ""),
    val media: List<MediaSlot> = emptyList(),
)

@Composable
internal fun FeedbackScreen(vm: FeedbackViewModel, owner: FeedbackOwner?, logger: AppLogger, onBack: () -> Unit, onLogin: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val forms by vm.formState.collectAsStateWithLifecycle()
    val attempt = state[owner]
    val form = forms[owner] ?: FeedbackFormState()
    val draft = form.drafts[form.template] ?: initialFeedbackDraft(form.template)
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val resolver = context.contentResolver
    var showLogs by remember(owner) { mutableStateOf(false) }
    var confirmation by remember(owner) { mutableStateOf<FeedbackConfirmation?>(null) }
    var confirmNew by remember(owner) { mutableStateOf(false) }
    var restoring by remember(owner) { mutableStateOf(owner != null) }
    var restoreFailed by remember(owner) { mutableStateOf(false) }
    var restoreRetry by remember(owner) { mutableIntStateOf(0) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (owner != null) scope.launch {
            val existing = form.media.filter { it.slot < 3 }.map { it.slot }.toMutableSet()
            uris.take(3 - existing.size).forEach { uri ->
                val localSlot = (0..2).firstOrNull { it !in existing } ?: return@forEach
                existing += localSlot
                try {
                    readFeedbackMedia(resolver, uri, "screenshot-${localSlot + 1}", "image/jpeg")
                } catch (_: Exception) { null }?.let { media ->
                    vm.saveMedia(owner, form.requestId, media.copy(slot = localSlot))
                    vm.replaceFormMedia(owner, form.requestId)
                }
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (owner != null && uri != null) scope.launch {
            try { readFeedbackMedia(resolver, uri, "video-1", "video/mp4") }
            catch (_: Exception) { null }?.let { media ->
                vm.saveMedia(owner, form.requestId, media.copy(slot = 3))
                vm.replaceFormMedia(owner, form.requestId)
            }
        }
    }
    LaunchedEffect(owner, restoreRetry) {
        if (owner != null) {
            restoring = true
            restoreFailed = false
            try {
                vm.restore(owner)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                restoreFailed = true
            } finally {
                restoring = false
            }
        }
    }

    SettingsPage("意见反馈", "feedback_screen", onBack) {
        if (owner != null && !restoring && !restoreFailed && attempt == null) {
            feedbackForm(
                draft, form.checked, form.attachLogs, form.attachAudioDiagnostics, form.media, { template ->
                    focusManager.clearFocus()
                    keyboard?.hide()
                    vm.editForm(owner) { it.copy(template = template) }
                }, { fieldId, value ->
                    vm.editForm(owner) { latest ->
                        val current = latest.drafts[draft.template] ?: initialFeedbackDraft(draft.template)
                        val edited = current.copy(values = current.values + (fieldId to value))
                        latest.copy(
                            drafts = latest.drafts + (edited.template to edited),
                            attachAudioDiagnostics = latest.attachAudioDiagnostics &&
                                (edited.template != FeedbackTemplate.BUG || edited.values["module"] == FEEDBACK_AUDIO_OUTPUT_MODULE),
                        )
                    }
                },
                { checked -> vm.editForm(owner) { it.copy(checked = checked) } },
                { attachLogs -> vm.editForm(owner) { it.copy(attachLogs = attachLogs) } },
                { attachAudio -> vm.editForm(owner) { it.copy(attachAudioDiagnostics = attachAudio) } },
                { imagePicker.launch(arrayOf("image/jpeg", "image/png")) },
                { videoPicker.launch(arrayOf("video/mp4")) },
                { slot -> vm.removeMedia(owner, form.requestId, slot) },
                { openExternalLink(context, FEEDBACK_ISSUES_URL) },
            ) {
                focusManager.clearFocus()
                keyboard?.hide()
                val bug = draft.template == FeedbackTemplate.BUG
                confirmation = FeedbackConfirmation(
                    draft.content(), bug && form.attachLogs,
                    bug && draft.values["module"] == FEEDBACK_AUDIO_OUTPUT_MODULE && form.attachAudioDiagnostics,
                    form.requestId,
                )
            }
        } else item("feedback") {
            when {
                owner == null -> SettingsGroup {
                    FeedbackLink("登录 CurrentMusic", onLogin, "登录后可提交反馈和查询回执。")
                }
                restoring -> SettingsGroup {
                    SettingsPreference("正在读取反馈记录", trailing = { FeedbackProgress() })
                }
                restoreFailed -> SettingsGroup {
                    FeedbackLink("重新读取", { restoreRetry++ }, "无法读取本地反馈记录。")
                }
                attempt != null -> FeedbackReceiptSection(
                    attempt, { vm.retry(owner) }, { showLogs = true }, { openExternalLink(context, it) },
                ) {
                    if (attempt.receipt?.status == FeedbackStatus.UNCONFIRMED) {
                        confirmNew = true
                    } else {
                        vm.editForm(owner) {
                            if (attempt.canEdit) {
                                val next = FeedbackDraft.fromContent(attempt.content)
                                it.copy(template = next.template, drafts = it.drafts + (next.template to next),
                                    checked = false, attachLogs = attempt.attachLogs,
                                    attachAudioDiagnostics = attempt.attachAudioDiagnostics)
                            } else {
                                FeedbackFormState(drafts = it.drafts - it.template)
                            }
                        }
                        vm.clear(owner)
                    }
                }
            }
        }
    }

    LogExportDialog(
        showLogs, logger,
        includeAppLogs = attempt?.attachLogs != false,
        audioDiagnostics = if (attempt?.attachAudioDiagnostics == true) vm::audioDiagnosticReport else null,
    ) { showLogs = false }
    val pending = confirmation
    if (owner != null && pending != null && attempt == null) {
        MusicDialog("公开发布反馈？", onDismiss = { confirmation = null }, footer = {
            MusicDialogActions {
                GlassActionButton("取消", { confirmation = null }, Modifier.weight(1f))
                GlassActionButton("确认提交", {
                    confirmation = null
                    vm.submit(owner, pending.content, pending.attachLogs, pending.attachAudioDiagnostics, pending.requestId)
                }, Modifier.weight(1f).testTag("feedback_confirm"), primary = true)
            }
        }) {
            Text("服务端会脱敏并使用 AI 整理反馈，再发布到公开的 CurrentMusicX Issues。AI 暂不可用时，服务端可能直接发布脱敏后的原文。")
            if (pending.attachLogs || pending.attachAudioDiagnostics) {
                val attachments = when {
                    pending.attachLogs && pending.attachAudioDiagnostics -> "应用日志和音频诊断"
                    pending.attachAudioDiagnostics -> "音频诊断"
                    else -> "应用日志"
                }
                Text("脱敏后的${attachments}和设备信息仅供项目管理员查看，服务端保留七天。线路不支持上传时，可在回执页导出。")
            }
        }
    }
    if (owner != null && confirmNew) {
        MusicDialog("填写新反馈？", onDismiss = { confirmNew = false }, footer = {
            MusicDialogActions {
                GlassActionButton("取消", { confirmNew = false }, Modifier.weight(1f))
                GlassActionButton("填写新反馈", {
                    confirmNew = false
                    vm.editForm(owner) { FeedbackFormState(drafts = it.drafts - it.template) }
                    vm.clear(owner)
                }, Modifier.weight(1f), primary = true)
            }
        }) {
            Text("上一条反馈可能已发布。请先在 GitHub Issues 核对，避免重复提交。")
        }
    }
}

@Composable
private fun FeedbackReceiptSection(
    attempt: FeedbackAttempt,
    onRetry: () -> Unit,
    onExport: () -> Unit,
    onOpen: (String) -> Unit,
    onContinue: () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    val receipt = attempt.receipt
    val summary = attempt.error ?: when (receipt?.status) {
        FeedbackStatus.REJECTED -> "请补充具体的问题或建议后重新提交。"
        FeedbackStatus.FAILED -> "可以编辑反馈后重新提交。"
        FeedbackStatus.UNCONFIRMED -> "请先查看 Issues 列表，核对是否已发布。"
        else -> if (receipt?.hasDiagnostics == true) "诊断附件已上传，项目管理员可在 CurrentStation 下载。" else null
    }
    SettingsListSection("反馈回执") {
        SettingsPreference(
            attempt.statusMessage(),
            summary = summary,
            trailing = if (attempt.busy) ({ FeedbackProgress() }) else null,
        )
        receipt?.issueUrl?.let { url ->
            SettingsDivider()
            FeedbackLink("打开 GitHub Issue #${receipt.issueNumber}", { onOpen(url) },
                summary = "可登录 GitHub，在评论区添加截图。")
        }
        if (receipt?.status == FeedbackStatus.UNCONFIRMED) {
            SettingsDivider()
            FeedbackLink("查看 Issues 列表", { onOpen(FEEDBACK_ISSUES_URL) })
        }
        if (attempt.exportLogs && receipt != null && !receipt.hasDiagnostics) {
            SettingsDivider()
            FeedbackLink("导出诊断 ZIP", onExport, "检查文件后，可自行补传到公开 Issue。",
                modifier = Modifier.testTag("feedback_export_logs"))
        }
    }
    if (!attempt.busy && attempt.canRetry) {
        GlassActionButton(if (attempt.queryOnly) "重试查询" else "重试提交", onRetry, primary = true)
    }
    if (!attempt.busy && (attempt.canEdit || receipt?.status in setOf(FeedbackStatus.PUBLISHED, FeedbackStatus.UNCONFIRMED))) {
        GlassActionButton(if (attempt.canEdit) "编辑反馈" else "填写新反馈", onContinue, primary = attempt.canEdit)
    }
}

@Composable
private fun FeedbackProgress() {
    CircularProgressIndicator(modifier = Modifier.size(20.dp), size = 20.dp, strokeWidth = 2.dp)
}

@Composable
private fun FeedbackLink(title: String, onClick: () -> Unit, summary: String? = null, modifier: Modifier = Modifier) {
    SettingsPreference(title, summary, modifier, onClick = onClick, trailing = { Icon(MusicIcons.Forward, null, Modifier.size(16.dp)) })
}

private fun initialFeedbackDraft(template: FeedbackTemplate): FeedbackDraft {
    val defaults = if (template == FeedbackTemplate.BUG) mapOf(
        "version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        "device" to "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}",
    ) else emptyMap()
    return FeedbackDraft(template, defaults)
}

private fun LazyListScope.feedbackForm(
    draft: FeedbackDraft,
    checked: Boolean,
    attachLogs: Boolean,
    attachAudioDiagnostics: Boolean,
    media: List<MediaSlot>,
    onTemplate: (FeedbackTemplate) -> Unit,
    onEdit: (String, String) -> Unit,
    onCheck: (Boolean) -> Unit,
    onAttachLogs: (Boolean) -> Unit,
    onAttachAudioDiagnostics: (Boolean) -> Unit,
    onPickImages: () -> Unit,
    onPickVideo: () -> Unit,
    onRemoveMedia: (Int) -> Unit,
    onIssues: () -> Unit,
    onSubmit: () -> Unit,
) {
    item("feedback_template") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsSegmentedControl(FeedbackTemplate.entries.map { it.title }, draft.template.ordinal,
                onSelect = { onTemplate(FeedbackTemplate.entries[it]) }, tag = "feedback_template")
            SettingsGroup { FeedbackLink("搜索已有 Issue", onIssues) }
        }
    }
    val bug = draft.template == FeedbackTemplate.BUG
    val sections = if (bug) listOf(
        "基本信息" to listOf("title", "version", "device", "build", "module"),
        "问题描述" to listOf("steps", "result", "frequency"),
        "补充信息" to listOf("environment", "diagnostics"),
    ) else listOf(
        "基本信息" to listOf("title", "module"),
        "建议详情" to listOf("problem", "proposal", "alternatives"),
    )
    sections.forEachIndexed { index, (title, fields) ->
        item("${draft.template.name}_section_$index") {
            SettingsListSection(title, footer = if ("diagnostics" in fields)
                "截图可在反馈发布后，通过回执打开 GitHub Issue 并添加到评论中。" else null) {
                draft.template.fields.filter { it.id in fields }.forEachIndexed { fieldIndex, field ->
                    if (fieldIndex > 0) SettingsDivider()
                    FeedbackInput(field, draft.values[field.id].orEmpty()) {
                        onEdit(field.id, it)
                    }
                }
            }
        }
    }
    item("feedback_submit") {
        val length = draft.content().feedbackContentLength()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (bug) SettingsListSection("提交选项") {
                SettingsToggle("随反馈上传诊断日志", attachLogs, onAttachLogs,
                    summary = "附带脱敏应用日志和设备信息。",
                    modifier = Modifier.testTag("feedback_include_logs"))
                if (draft.values["module"] == FEEDBACK_AUDIO_OUTPUT_MODULE) {
                    SettingsDivider()
                    SettingsToggle("附带音频诊断", attachAudioDiagnostics, onAttachAudioDiagnostics,
                        summary = "报告包含输出模式、设备能力和原生音频记录。请在复现后提交。",
                        modifier = Modifier.testTag("feedback_include_audio_diagnostics"))
                }
                SettingsDivider()
                SettingsToggle("提交前确认", checked, onCheck,
                    summary = "我已搜索已有 Issue，并检查内容中没有密码、令牌或其他敏感信息。")
            }
            if (bug) SettingsListSection("附件") {
                FeedbackLink("添加截图", onPickImages, "最多 3 张 PNG 或 JPEG")
                if (media.isNotEmpty()) SettingsHint(media.filter { it.slot < 3 }
                    .joinToString("、") { it.displayName })
                SettingsDivider()
                FeedbackLink("添加录屏", onPickVideo, "最多 1 段 MP4，最长 30 秒")
                media.firstOrNull { it.slot == 3 }?.let {
                    SettingsHint(it.displayName)
                }
                media.forEach { item ->
                    SettingsDivider()
                    FeedbackLink("移除 ${item.displayName}", { onRemoveMedia(item.slot) })
                }
            }
            SettingsHint("标 * 的字段必填。反馈将公开发布在 GitHub。\n正文含字段名共 $length / ${FEEDBACK_CONTENT_LENGTH.last} 字。",
                error = length > FEEDBACK_CONTENT_LENGTH.last)
            GlassActionButton("提交反馈", onSubmit, Modifier.testTag("feedback_submit"), primary = true,
                enabled = draft.complete && (!bug || checked) && length in FEEDBACK_CONTENT_LENGTH)
        }
    }
}

@Composable
private fun FeedbackInput(field: FeedbackField, value: String, onEdit: (String) -> Unit) {
    val label = when {
        field.id == "diagnostics" -> "诊断说明（可选）"
        field.required -> "${field.label} *"
        else -> field.label
    }
    val tag = Modifier.testTag("feedback_${field.id}")
    if (field.options.isNotEmpty()) {
        val options = if (field.required) field.options else listOf("") + field.options
        SettingsOptionsRow(label, value.ifBlank { if (field.required) "请选择" else "未选择" }, options.map { option ->
            SettingsOption(option.ifBlank { "none" }, option.ifBlank { "未选择" }, option == value, onSelect = { onEdit(option) })
        }, modifier = tag)
    } else {
        TextField(value, onEdit, label = label, singleLine = !field.multiline,
            minLines = if (field.multiline) 2 else 1, maxLines = if (field.multiline) 6 else 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).then(tag))
    }
}

private suspend fun readFeedbackMedia(
    resolver: ContentResolver,
    uri: Uri,
    slotName: String,
    fallbackType: String,
): MediaSlot? = withContext(Dispatchers.IO) {
    val contentType = resolver.getType(uri)?.lowercase() ?: fallbackType
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            val maxBytes = if (contentType.startsWith("image/")) 2 * 1024 * 1024 else 25 * 1024 * 1024
            require(total <= maxBytes) { "反馈媒体超过大小上限" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: return@withContext null
    if (bytes.isEmpty()) return@withContext null
    val slot = if (slotName.startsWith("video-")) 3 else slotName.substringAfterLast('-').toInt() - 1
    val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: "media-$slotName"
    val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    return@withContext MediaSlot(slot, contentType, io.github.currencortex.music.data.feedback.FeedbackMediaStore.encode(bytes), sha, name)
}

private fun FeedbackAttempt.statusMessage() = when (receipt?.status) {
    null -> if (busy) "正在确认反馈…" else "提交结果待确认"
    FeedbackStatus.PROCESSING -> "已接收，正在整理反馈…"
    FeedbackStatus.PUBLISHING -> "正在发布到 GitHub…"
    FeedbackStatus.PUBLISHED -> "反馈已公开发布。"
    FeedbackStatus.REJECTED -> "反馈未发布"
    FeedbackStatus.FAILED -> "发布失败"
    FeedbackStatus.UNCONFIRMED -> "发布结果待确认"
}
