// Layout adapted from 123PanX / XBlocker / SukiSU-Ultra SendLogDialog, GPL-3.0-only.
package io.github.currencortex.music.feature.logs

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.core.logging.AppLogger
import io.github.currencortex.music.core.logging.DiagnosticExporter
import io.github.currencortex.music.ui.component.MusicIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun LogExportDialog(show: Boolean, logger: AppLogger, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    suspend fun generate(action: suspend () -> Unit) {
        busy = true
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            logger.warn("Diagnostics", "Unable to export logs", error)
            Toast.makeText(context, "导出失败，请重试", Toast.LENGTH_LONG).show()
        } finally { busy = false }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && !busy) scope.launch { generate {
            val file = DiagnosticExporter.create(context, logger)
            try { DiagnosticExporter.save(context, file, uri) } finally { file.delete() }
            Toast.makeText(context, "日志已保存", Toast.LENGTH_SHORT).show()
        } }
    }
    OverlayDialog(show = show && !busy, title = "导出日志",
        summary = "应用日志与设备信息会打包为 ZIP，并自动隐藏敏感信息。", onDismissRequest = onDismiss) {
        ArrowPreference(title = "保存日志", summary = "选择保存位置",
            startAction = { Icon(MusicIcons.Download, null, Modifier.padding(end = 12.dp)) },
            modifier = Modifier.testTag("export_logs_save"), onClick = {
                onDismiss()
                try { save.launch("${io.github.currencortex.music.core.config.AppMetadata.GITHUB_REPO}_logs_${System.currentTimeMillis()}.zip") }
                catch (_: Exception) { Toast.makeText(context, "无法打开文件选择器", Toast.LENGTH_LONG).show() }
            })
        ArrowPreference(title = "分享日志", summary = "使用系统分享",
            startAction = { Icon(MusicIcons.Share, null, Modifier.padding(end = 12.dp)) },
            modifier = Modifier.testTag("export_logs_share"), onClick = {
                if (!busy) scope.launch { onDismiss(); generate {
                    val file = DiagnosticExporter.create(context, logger)
                    context.startActivity(Intent.createChooser(DiagnosticExporter.shareIntent(context, file), "分享日志"))
                } }
            })
        TextButton("取消", onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
    OverlayDialog(show = busy, title = "正在生成日志", onDismissRequest = {}) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
