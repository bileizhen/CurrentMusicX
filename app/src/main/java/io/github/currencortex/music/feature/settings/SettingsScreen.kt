package io.github.currencortex.music.feature.settings

import io.github.currencortex.music.ui.component.musicScrollPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.feature.update.UpdatePageOrigin
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(onAppearance: () -> Unit, onNetwork: () -> Unit,
                   onStorage: () -> Unit, onLogs: () -> Unit, onAbout: () -> Unit, onUpdates: (UpdatePageOrigin?) -> Unit,
                   onDesktopLyrics: () -> Unit = {},
                   onAudioOutput: () -> Unit = {},
                   onLocalMusic: () -> Unit = {},
                   updateTransfer: io.github.currencortex.music.core.update.UpdateTransferState = io.github.currencortex.music.core.update.UpdateTransferState()) {
    LazyColumn(Modifier.fillMaxSize().testTag("settings_screen"),
        contentPadding = musicScrollPadding(PaddingValues(12.dp, 30.dp, 12.dp, 32.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("设置", fontSize = 32.sp, modifier = Modifier.fillMaxWidth()
            .then(io.github.currencortex.music.ui.component.LocalRootTabSwipe.current).padding(horizontal = 12.dp, vertical = 8.dp)) }
        item {
            var origin by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val color = MiuixTheme.colorScheme.surface
            Card(Modifier.fillMaxWidth().onGloballyPositioned { origin = it }) {
                val progress = updateTransfer.download
                val updateTitle = when (progress) {
                    is io.github.currencortex.music.core.update.UpdateDownloadState.Downloading -> "更新下载中 · ${(progress.received.toDouble() / progress.total.coerceAtLeast(1) * 100).toInt()}%"
                    is io.github.currencortex.music.core.update.UpdateDownloadState.Ready -> "更新已下载"
                    is io.github.currencortex.music.core.update.UpdateDownloadState.Failed -> "更新下载失败"
                    else -> "检查更新"
                }
                val summary = if (progress is io.github.currencortex.music.core.update.UpdateDownloadState.Idle)
                    "查看版本和更新说明" else "查看下载进度、切换来源或安装"
                ArrowPreference(title = updateTitle, summary = summary,
                    startAction = { SettingIcon(MusicIcons.Download) }, onClick = {
                        onUpdates(origin?.takeIf { it.isAttached }?.let { UpdatePageOrigin(it, color, updateTitle, summary) })
                    }, modifier = Modifier.testTag("open_updates"))
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                ArrowPreference(title = "外观", summary = "主题、颜色与界面效果",
                    startAction = { SettingIcon(MusicIcons.Image) }, onClick = onAppearance)
                ArrowPreference(title = "桌面悬浮歌词", summary = "实时预览、字幕样式与窗口控制",
                    startAction = { SettingIcon(MusicIcons.BottomBar) }, onClick = onDesktopLyrics,
                    modifier = Modifier.testTag("open_desktop_lyrics"))
                ArrowPreference(title = "网络与播放", summary = "音源、歌词来源、音质与预加载",
                    startAction = { SettingIcon(MusicIcons.Device) }, onClick = onNetwork,
                    modifier = Modifier.testTag("open_network"))
                ArrowPreference(title = "本地音乐", summary = "浏览并播放手机上的歌曲",
                    startAction = { SettingIcon(MusicIcons.Device) }, onClick = onLocalMusic,
                    modifier = Modifier.testTag("open_local_music_settings"))
                ArrowPreference(title = "音频输出", summary = "输出采样率、设备与能力信息",
                    startAction = { SettingIcon(MusicIcons.Device) }, onClick = onAudioOutput,
                    modifier = Modifier.testTag("open_audio_output"))
                ArrowPreference(title = "存储空间", summary = "缓存与下载占用",
                    startAction = { SettingIcon(MusicIcons.Storage) }, onClick = onStorage,
                    modifier = Modifier.testTag("open_storage"))
                ArrowPreference(title = "导出日志", summary = "保存或分享脱敏诊断文件",
                    startAction = { SettingIcon(MusicIcons.File) }, onClick = onLogs)
                ArrowPreference(title = "关于", summary = "版本、开源许可与隐私",
                    startAction = { SettingIcon(MusicIcons.Info) }, onClick = onAbout)
            }
        }
    }
}
