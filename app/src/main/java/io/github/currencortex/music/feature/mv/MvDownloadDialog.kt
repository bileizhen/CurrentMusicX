package io.github.currencortex.music.feature.mv

import io.github.currencortex.music.ui.component.MusicChoiceDialog
import io.github.currencortex.music.ui.component.MusicDialogChoiceRow

import io.github.currencortex.music.ui.component.MusicDialogActions
import io.github.currencortex.music.ui.component.MusicDialogButton
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.work.WorkInfo
import io.github.currencortex.music.ui.util.showMusicError
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.MusicDestinationRow
import io.github.currencortex.music.ui.component.MusicDialog
import kotlinx.coroutines.CancellationException
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun WorkInfo?.mvActive() = this?.state in listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
internal fun WorkInfo?.mvLabel(): String = when {
    mvActive() -> {
        val total = this?.progress?.getLong("total", -1) ?: -1
        if (total > 0) "下载中 ${((this?.progress?.getLong("received", 0) ?: 0).toDouble() / total * 100).toInt().coerceIn(0, 100)}%" else "等待下载"
    }
    this?.state == WorkInfo.State.SUCCEEDED -> "已下载"
    this?.state == WorkInfo.State.FAILED -> "下载失败"
    else -> "下载 MV"
}

@Composable internal fun MvQualityDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    MusicChoiceDialog("清晰度", onDismiss) {
        Text("优先使用所选清晰度，实际画质以视频源为准。", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontSize = 13.sp)
        listOf(1080, 720, 480, 240).forEach { value ->
            MusicDialogChoiceRow("${value}P", current == value, { onSelect(value); onDismiss() },
                modifier = Modifier.testTag("mv_quality_$value"))
        }
    }
}

@Composable fun MvDownloadDialog(song: Song, container: AppContainer, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = container.mvDownloads
    val directory by manager.directory.collectAsStateWithLifecycle()
    val items by remember(manager, song.mv) { manager.observe(song) }.collectAsStateWithLifecycle(emptyList())
    val info = items.lastOrNull()
    val active = info.mvActive()
    val saved = info?.state == WorkInfo.State.SUCCEEDED
    var quality by rememberSaveable(song.mv) { mutableIntStateOf(song.videoQuality) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var afterFolder by rememberSaveable { mutableStateOf(false) }
    fun start() {
        try {
            manager.start(song, quality, container.accountRepository.state.value.account?.id ?: 0L,
                container.musicSession().server,
                if (container.nativeNetease != null) container.neteaseSessions.state.value.profile?.uid ?: 0L else -1)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { context.showMusicError("无法开始 MV 下载，请重试") }
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try { manager.setDirectory(uri); if (afterFolder) start() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { context.showMusicError("此文件夹不可写，请重新选择") }
        afterFolder = false
    }
    if (choosing) MvQualityDialog(quality, { quality = it }, { choosing = false })
    else MusicDialog("下载 MV", onDismiss) {
        Column(Modifier.heightIn(max = 370.dp).verticalScroll(rememberScrollState()).testTag("mv_download_dialog"),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(song.name, fontSize = 20.sp)
            Text(song.artists, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
            MusicDestinationRow("下载清晰度", { choosing = true }, summary = "优先 ${quality}P", enabled = !active,
                modifier = Modifier.testTag("mv_download_quality"))
            MusicDestinationRow("保存位置", { afterFolder = false; folder.launch(directory) }, enabled = !active,
                summary = directory?.let { Uri.decode(it.lastPathSegment.orEmpty()).substringAfter(':') }
                    ?: if (Build.VERSION.SDK_INT >= 29) "Download/CurrentMusic" else "请选择文件夹")
            Text("保存 MP4 视频，离开播放页后继续下载。", fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
            if (active) {
                val received = info?.progress?.getLong("received", 0) ?: 0
                val total = info?.progress?.getLong("total", -1) ?: -1
                Text(info?.progress?.getString("phase") ?: "等待网络…")
                if (total > 0) LinearProgressIndicator(progress = (received.toDouble() / total).toFloat().coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                if (received > 0) Text("${mvSize(received)}${if (total > 0) " / ${mvSize(total)}" else ""}", fontSize = 12.sp)
            }
            when (info?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    Text("下载完成 · ${info.outputData.getInt("height", 0)}P", Modifier.testTag("mv_download_complete"))
                    Text(info.outputData.getString("name").orEmpty(), fontSize = 12.sp)
                    MusicDestinationRow("重新下载", ::start, summary = "按所选清晰度保存另一份", chevron = false)
                }
                WorkInfo.State.FAILED -> Text(info.outputData.getString("error") ?: "下载失败，请重试", Modifier.testTag("mv_download_error"))
                WorkInfo.State.CANCELLED -> Text("已取消下载")
                else -> Unit
            }
        }
        MusicDialogActions(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusicDialogButton("关闭", onDismiss, Modifier.weight(1f))
            MusicDialogButton(when { active -> "取消下载"; saved -> "打开视频"; info?.state == WorkInfo.State.FAILED -> "重试下载"; else -> "开始下载" }, {
                when {
                    active -> manager.cancel(song)
                    saved -> try {
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(requireNotNull(info?.outputData?.getString("uri"))), "video/mp4")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { context.showMusicError("文件不可用或没有视频播放器，可到保存位置查看或重新下载") }
                    Build.VERSION.SDK_INT < 29 && directory == null -> { afterFolder = true; folder.launch(null) }
                    else -> start()
                }
            }, Modifier.weight(1f).testTag("mv_download_start"))
        }
    }
}

private fun mvSize(value: Long) = String.format(java.util.Locale.ROOT, "%.1f MB", value / 1048576.0)
