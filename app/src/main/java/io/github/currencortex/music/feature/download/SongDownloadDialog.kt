package io.github.currencortex.music.feature.download

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.provider.DocumentsContract
import androidx.work.WorkInfo
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.AudioQualitySheet
import io.github.currencortex.music.ui.component.MusicDestinationRow
import io.github.currencortex.music.ui.component.MusicDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun SongDownloadDialog(song: Song, container: AppContainer, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = container.songDownloads
    val directory by manager.directory.collectAsStateWithLifecycle()
    val info by remember(manager, song.id, song.musicSource) { manager.observe(song) }.collectAsStateWithLifecycle(null)
    var quality by rememberSaveable(song.id) { mutableStateOf(container.musicSettings.state.value.quality.value) }
    var choosingQuality by rememberSaveable { mutableStateOf(false) }
    var afterFolder by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val active = info?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
    val saved = info?.state == WorkInfo.State.SUCCEEDED
    fun start() {
        try {
            manager.start(song, AudioQuality.from(quality), container.accountRepository.state.value.account?.id ?: 0L,
                container.accountRepository.server, container.audioSettings.access().identity)
        } catch (_: Exception) { Toast.makeText(context, "无法开始下载，请重试", Toast.LENGTH_LONG).show() }
    }
    // Deletes only the URIs this task published; a stale/missing file must not abort the rest.
    fun deleteSaved() {
        val completed = info?.takeIf { it.state == WorkInfo.State.SUCCEEDED } ?: return
        if (deleting) return
        val output = completed.outputData
        val uris = (output?.getStringArray("files").orEmpty().toList() + listOfNotNull(output?.getString("uri")))
            .filter { it.isNotBlank() }.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }.distinct()
        deleting = true
        scope.launch(Dispatchers.Main.immediate) {
            try {
                val failed = withContext(Dispatchers.IO) { uris.filter { uri -> runCatching {
                    if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.deleteDocument(context.contentResolver, uri)
                    else { context.contentResolver.delete(uri, null, null); true }
                }.getOrDefault(false).not() } }
                if (failed.isEmpty()) withContext(Dispatchers.IO) { manager.hideCompleted(completed.id) }
                Toast.makeText(context, if (failed.isEmpty()) "已删除下载文件" else "部分文件删除失败，请重新尝试或到保存位置检查",
                    Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { Toast.makeText(context, "下载记录更新失败，请重试", Toast.LENGTH_LONG).show() }
              finally { deleting = false }
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try { manager.setDirectory(uri); if (afterFolder) start() }
        catch (_: Exception) { Toast.makeText(context, "此文件夹不可写，请重新选择", Toast.LENGTH_LONG).show() }
        afterFolder = false
    }
    // Reuse the playback picker so both quality sheets stay visually identical.
    if (choosingQuality) AudioQualitySheet(AudioQuality.from(quality), { quality = it.value },
        { choosingQuality = false }, enabled = !active, title = "下载音质")
    else MusicDialog("下载歌曲", onDismiss) {
        Column(Modifier.testTag("song_download_dialog").heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(song.name, fontSize = 18.sp)
            Text(song.artists, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
            MusicDestinationRow("下载音质", { choosingQuality = true }, summary = AudioQuality.from(quality).label,
                enabled = !active && !deleting, modifier = Modifier.testTag("song_download_quality"))
            val location = directory?.let { Uri.decode(it.lastPathSegment.orEmpty()).substringAfter(':') }
                ?: if (Build.VERSION.SDK_INT >= 29) "Download/CurrentMusic" else "请选择文件夹"
            MusicDestinationRow("保存位置", { afterFolder = false; folderLauncher.launch(directory) }, summary = location,
                enabled = !active && !deleting, modifier = Modifier.testTag("song_download_directory"))
            Text("原格式保存，内嵌歌曲信息、封面和歌词；同时保存同名封面与 LRC。", fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
            if (active) {
                val data = info?.progress
                val received = data?.getLong("received", 0) ?: 0
                val total = data?.getLong("total", -1) ?: -1
                Text(data?.getString("phase") ?: "等待下载", Modifier.testTag("song_download_phase"))
                if (total > 0) {
                    LinearProgressIndicator(progress = (received.toDouble() / total).toFloat().coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                    Text("${downloadSize(received)} / ${downloadSize(total)}", fontSize = 12.sp)
                } else { LinearProgressIndicator(Modifier.fillMaxWidth()); if (received > 0) Text(downloadSize(received), fontSize = 12.sp) }
            }
            when (info?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    Text("下载完成 · ${info?.outputData?.getString("quality").orEmpty()}", Modifier.testTag("song_download_complete"))
                    Text(info?.outputData?.getString("name").orEmpty(), fontSize = 12.sp)
                    info?.outputData?.getString("warning")?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp) }
                    MusicDestinationRow("重新下载", ::start, summary = "按当前所选音质保存另一份", enabled = !deleting, chevron = false)
                }
                WorkInfo.State.FAILED -> Text(info?.outputData?.getString("error") ?: "下载失败，请重试", Modifier.testTag("song_download_error"))
                WorkInfo.State.CANCELLED -> Text("已取消下载")
                else -> Unit
            }
        }
        // Three buttons must still fit the longest label ("打开歌曲") on one line, so the primary
        // action keeps the extra width instead of splitting the row into equal thirds.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton("关闭", onClick = onDismiss, modifier = Modifier.weight(0.9f))
            if (saved) TextButton(if (deleting) "删除中" else "删除", onClick = { deleteSaved() }, enabled = !deleting,
                colors = ButtonDefaults.textButtonColors(color = MiuixTheme.colorScheme.error,
                    textColor = MiuixTheme.colorScheme.onError),
                modifier = Modifier.weight(0.9f).testTag("song_download_delete"))
            TextButton(when { active -> "取消下载"; info?.state == WorkInfo.State.FAILED -> "重试下载"
                saved -> "打开歌曲"; else -> "开始下载" }, onClick = {
                when {
                    active -> manager.cancel(song)
                    saved -> try {
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(info?.outputData?.getString("uri")), "audio/*")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (_: Exception) { Toast.makeText(context, "无法打开文件，请到保存位置查看", Toast.LENGTH_LONG).show() }
                    Build.VERSION.SDK_INT < 29 && directory == null -> { afterFolder = true; folderLauncher.launch(null) }
                    else -> start()
                }
            }, enabled = !deleting, modifier = Modifier.weight(1.45f).testTag("song_download_start"))
        }
    }
}

private fun downloadSize(bytes: Long) = String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
