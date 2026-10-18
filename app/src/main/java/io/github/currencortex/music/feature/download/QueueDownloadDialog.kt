package io.github.currencortex.music.feature.download

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/** Submit the captured shared list to the existing download manager only after confirmation. */
@Composable internal fun QueueDownloadDialog(container: AppContainer, songs: List<Song>, show: Boolean, onDismiss: () -> Unit) {
    val directory by container.songDownloads.directory.collectAsStateWithLifecycle()
    val settings by container.musicSettings.state.collectAsStateWithLifecycle()
    val quality = AudioQuality.from(settings.quality.value)
    var error by remember { mutableStateOf<String?>(null) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try { container.songDownloads.setDirectory(uri); error = null }
        catch (_: Exception) { error = "此文件夹不可写，请重新选择" }
    }
    LaunchedEffect(show) { if (show) error = null }
    MusicOverlayDialog(show = show, title = "下载 ${songs.size} 首歌曲", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("下载音质：${quality.label}")
            MusicDestinationRow("保存位置", { folder.launch(directory) }, summary = directory?.let { android.net.Uri.decode(it.lastPathSegment.orEmpty()) }
                ?: if (Build.VERSION.SDK_INT >= 29) "Download/CurrentMusic" else "请选择文件夹")
            Text("按当前下载设置加入任务，已在下载的歌曲会保留原任务。")
            error?.let { Text(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("取消", onDismiss, modifier = Modifier.weight(1f))
                TextButton("开始下载", {
                    try {
                        val account = container.accountRepository.state.value.account?.id ?: 0L
                        val server = container.accountRepository.server
                        val provider = container.audioSettings.access().identity
                        val uid = if (container.nativeNetease != null) container.neteaseSessions.state.value.profile?.uid ?: 0L else -1L
                        songs.distinctBy { it.id }.forEach { container.songDownloads.start(it, quality, account, server, provider, uid) }
                        onDismiss()
                    } catch (_: Exception) { error = "部分下载任务未能创建，请重试" }
                }, enabled = songs.isNotEmpty() && (Build.VERSION.SDK_INT >= 29 || directory != null),
                    modifier = Modifier.weight(1f).testTag("queue_download_confirm"))
            }
        }
    }
}
