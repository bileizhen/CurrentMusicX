// Bottom dialog layout adapted from 123PanX / XBlocker / MIUIX, GPL-3.0-only.
package io.github.currencortex.music.feature.update

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    var source by remember(release?.version) { mutableStateOf(UpdateSource.default) }
    var previewProgress by remember { mutableFloatStateOf(-1f) }
    val previewDownloading = previewProgress in 0f..<1f
    val downloading = transferState.download is UpdateDownloadState.Downloading || previewDownloading
    val ready = transferState.download is UpdateDownloadState.Ready || previewProgress >= 1f
    val animatedProgress by animateFloatAsState(targetValue = when (val value = transferState.download) {
        is UpdateDownloadState.Downloading -> (value.received.toDouble() / value.total.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
        is UpdateDownloadState.Ready -> 1f
        else -> previewProgress.coerceIn(0f, 1f)
    }, animationSpec = tween(180), label = "update_progress")
    OverlayDialog(show = true, insideMargin = DpSize(22.dp, 18.dp),
        onDismissRequest = if (transferState.installing) null else onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max =
            (LocalConfiguration.current.screenHeightDp * .82f).dp.coerceAtMost(680.dp))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (release != null) "发现新版本" else "检查更新", fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (release != null) Text("v${release.version}", fontSize = 14.sp,
                fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (release != null) Text("${if (release.prerelease) "预发布版" else "正式版"} · 安装包 ${formatSize(release.size)}",
            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(14.dp))
        val message = when (state) {
            UpdateState.Idle, UpdateState.Checking -> "正在检查更新…"
            UpdateState.UpToDate -> "当前已是最新版本"
            is UpdateState.Failed -> state.reason
            is UpdateState.Available -> state.release.notes.ifBlank { "新版本已发布。" }
        }
        val notesHeight = (LocalConfiguration.current.screenHeightDp - 240).coerceIn(88, 440).dp
        val notesModifier = Modifier.fillMaxWidth().heightIn(max = notesHeight)
            .weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("update_message")
        if (release != null) MarkdownText(stripVersionHeadings(release.version, message), modifier = notesModifier, compact = true)
        else Text(message, fontSize = 15.sp, modifier = notesModifier)
        if (release != null) {
            if (preview) Text("测试预览", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            OverlaySpinnerPreference(title = "下载源 · ${source.compactLabel()}", items = UpdateSource.available.map { DropdownItem(it.label) },
                selectedIndex = UpdateSource.available.indexOf(source).coerceAtLeast(0),
                showValue = false, insideMargin = PaddingValues(vertical = 8.dp),
                enabled = !downloading && !ready && !transferState.installing,
                onSelectedIndexChange = { source = UpdateSource.available[it] }, modifier = Modifier.testTag("update_source"))
            when {
                previewDownloading -> {
                    Text("${formatReceived((release.size * previewProgress).toLong())} / ${formatSize(release.size)}", fontSize = 13.sp)
                    LinearProgressIndicator(progress = animatedProgress, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                previewProgress >= 1f -> Text("测试下载完成，未下载真实安装包")
                else -> when (val download = transferState.download) {
                    is UpdateDownloadState.Downloading -> {
                        transferState.activeSource?.let { active ->
                            Text(if (transferState.attempt > 1) "已切换至 ${active.label}（${transferState.attempt}/${transferState.totalSources}）"
                                else "正在使用 ${active.label}", fontSize = 12.sp, modifier = Modifier.testTag("update_active_source"))
                        }
                        Text("${formatReceived(download.received)} / ${formatSize(download.total)} · ${(animatedProgress * 100).toInt()}%", fontSize = 13.sp)
                        LinearProgressIndicator(progress = animatedProgress,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    is UpdateDownloadState.Ready -> Text("下载完成，已校验安装包", fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.primary)
                    is UpdateDownloadState.Failed -> Text(download.message, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    UpdateDownloadState.Idle -> Text("下载失败时自动尝试其他来源", fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            transferState.message?.let { Text(it, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(if (downloading) "取消下载" else "关闭", onClick = onDismiss,
                enabled = !transferState.installing, minHeight = 48.dp, cornerRadius = 16.dp,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                textStyle = MiuixTheme.textStyles.body1.copy(fontSize = 15.sp),
                modifier = Modifier.weight(1f).testTag("update_close"))
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
                    minHeight = 48.dp, cornerRadius = 16.dp, insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f).testTag("update_download")) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            } else if (state is UpdateState.Failed) Button(onClick = onRetry,
                colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.weight(1f)) { Text("重试") }
        }
        if (release != null && !downloading && !ready && !transferState.installing) Box(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onIgnore)
                .testTag("update_ignore"), contentAlignment = Alignment.Center) {
            Text("忽略此版本", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        }
    }
}

private fun UpdateSource.compactLabel() = when (this) {
    UpdateSource.CURRENTMUSIC -> "专用镜像"
    UpdateSource.GITHUB -> "GitHub 原站"
    UpdateSource.GEEKERTAO -> "Geekertao 镜像"
    UpdateSource.DPIK -> "DPIK 镜像"
}

private fun formatReceived(value: Long) = if (value <= 0) "0 MB" else formatSize(value)

private fun formatSize(value: Long): String = if (value <= 0) "大小未提供" else when {
    value >= 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB", value / (1024.0 * 1024))
    value >= 1024 -> String.format(java.util.Locale.ROOT, "%.1f KB", value / 1024.0)
    else -> "$value B"
}
