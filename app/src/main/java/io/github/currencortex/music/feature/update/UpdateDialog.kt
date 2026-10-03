// Bottom dialog layout adapted from 123PanX / XBlocker / MIUIX, GPL-3.0-only.
package io.github.currencortex.music.feature.update

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.ui.util.openExternalLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference

@Composable
fun UpdateDialog(service: UpdateService, transfer: UpdateTransfer) {
    val visible by service.dialogVisible.collectAsStateWithLifecycle()
    val state by service.state.collectAsStateWithLifecycle()
    val download by transfer.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, transfer) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) transfer.onResume() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val release = (state as? UpdateState.Available)?.release
    LaunchedEffect(release) { if (release != null) transfer.selectRelease(release) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (!visible) return
    UpdateDialogContent(state, onDismiss = { transfer.cancel(); service.dismissDialog() },
        onRetry = { scope.launch { service.check(manual = true) } },
        onIgnore = { scope.launch { service.ignoreCurrentRelease() } },
        onOpenRelease = { openExternalLink(context, it) }, transferState = download,
        onDownload = transfer::download, onInstall = transfer::install)
}

@Composable
fun UpdateDialogContent(state: UpdateState, onDismiss: () -> Unit, onRetry: () -> Unit,
                        onIgnore: () -> Unit, onOpenRelease: (String) -> Unit, preview: Boolean = false,
                        transferState: UpdateTransferState = UpdateTransferState(),
                        onDownload: ((UpdateSource) -> Unit)? = null, onInstall: () -> Unit = {}) {
    val release = (state as? UpdateState.Available)?.release
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var source by remember { mutableStateOf(UpdateSource.GITHUB) }
    var previewProgress by remember { mutableFloatStateOf(-1f) }
    val previewDownloading = previewProgress in 0f..<1f
    val downloading = transferState.download is UpdateDownloadState.Downloading || previewDownloading
    val ready = transferState.download is UpdateDownloadState.Ready || previewProgress >= 1f
    val title = release?.let { "发现新版本 v${it.version}" } ?: "检查更新"
    OverlayDialog(show = true, title = title, onDismissRequest = if (transferState.installing) null else onDismiss) {
        val message = when (state) {
            UpdateState.Idle, UpdateState.Checking -> "正在检查更新…"
            UpdateState.UpToDate -> "当前已是最新版本"
            is UpdateState.Failed -> state.reason
            is UpdateState.Available -> state.release.notes.ifBlank { "新版本已发布。" }
        }
        val notesModifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState()).testTag("update_message")
        if (release != null) MarkdownText(stripVersionHeadings(release.version, message), modifier = notesModifier)
        else Text(message, modifier = notesModifier)
        if (release != null) {
            if (preview) Text("测试预览", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            if (release.prerelease) Text("预发布版", fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            OverlaySpinnerPreference(title = "下载源", items = UpdateSource.available.map { DropdownItem(it.label) },
                selectedIndex = UpdateSource.available.indexOf(source).coerceAtLeast(0),
                enabled = !downloading && !ready && !transferState.installing,
                onSelectedIndexChange = { source = UpdateSource.available[it] }, modifier = Modifier.testTag("update_source"))
            when {
                previewDownloading -> {
                    Text("${formatSize((release.size * previewProgress).toLong())} / ${formatSize(release.size)}")
                    LinearProgressIndicator(progress = previewProgress, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                previewProgress >= 1f -> Text("测试下载完成，未下载真实安装包")
                else -> when (val download = transferState.download) {
                    is UpdateDownloadState.Downloading -> {
                        Text("${formatSize(download.received)} / ${formatSize(download.total)}")
                        LinearProgressIndicator(progress = (download.received.toDouble() / download.total.coerceAtLeast(1)).toFloat(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    is UpdateDownloadState.Ready -> Text("下载完成，已校验安装包")
                    is UpdateDownloadState.Failed -> Text(download.message)
                    UpdateDownloadState.Idle -> Text("安装包 ${formatSize(release.size)}")
                }
            }
            transferState.message?.let { Text(it) }
            TextButton("忽略此版本", onClick = onIgnore, enabled = !downloading && !transferState.installing,
                modifier = Modifier.fillMaxWidth().testTag("update_ignore"))
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(if (downloading) "取消下载" else "关闭", onClick = onDismiss,
                enabled = !transferState.installing, modifier = Modifier.weight(1f).testTag("update_close"))
            if (release != null) {
                val verifiedAsset = release.apkUrl != null && release.size in 1..UpdateDownloader.MAX_APK_BYTES &&
                    Regex("[a-f0-9]{64}").matches(release.sha256)
                val label = when {
                    transferState.installing -> "请求安装…"
                    ready -> "请求安装"
                    !verifiedAsset && !preview -> "查看发布"
                    transferState.download is UpdateDownloadState.Failed -> "重试下载"
                    else -> "下载更新"
                }
                Button(onClick = {
                    if (preview) {
                        if (ready) Toast.makeText(context, "测试预览不会安装应用", Toast.LENGTH_SHORT).show()
                        else scope.launch {
                            previewProgress = 0f
                            repeat(10) { delay(120); previewProgress = (it + 1) / 10f }
                        }
                    } else if (ready) onInstall()
                    else if (verifiedAsset && onDownload != null) onDownload(source)
                    else onOpenRelease(release.pageUrl)
                }, enabled = !downloading && !transferState.installing,
                    colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f).testTag("update_download")) { Text(label) }
            } else if (state is UpdateState.Failed) Button(onClick = onRetry,
                colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Text("重试") }
        }
    }
}

private fun formatSize(value: Long): String = if (value <= 0) "大小未提供" else when {
    value >= 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB", value / (1024.0 * 1024))
    value >= 1024 -> String.format(java.util.Locale.ROOT, "%.1f KB", value / 1024.0)
    else -> "$value B"
}
