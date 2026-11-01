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
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.PageContainerOrigin
import io.github.currencortex.music.ui.component.LocalPageContainerSource
import io.github.currencortex.music.ui.component.PageNavigationPreference
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.feature.update.UpdatePageOrigin
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(onAppearance: () -> Unit, onNetwork: () -> Unit,
                   onStorage: () -> Unit, onLogs: () -> Unit, onAbout: () -> Unit, onUpdates: (UpdatePageOrigin?) -> Unit,
                   onDesktopLyrics: () -> Unit = {},
                   onAudioOutput: () -> Unit = {},
                   onPlayback: () -> Unit = {}, onEffects: () -> Unit = {},
                   updateTransfer: io.github.currencortex.music.core.update.UpdateTransferState = io.github.currencortex.music.core.update.UpdateTransferState(),
                   onNavigateFrom: ((PageContainerOrigin, () -> Unit) -> Unit)? = null,
                   onBack: (() -> Unit)? = null) {
    CompositionLocalProvider(LocalPageContainerSource provides (onNavigateFrom ?: LocalPageContainerSource.current)) {
    SettingsUiScope {
    SettingsScrollLayout(Modifier.fillMaxSize(), topBar = {
        if (onBack == null) SettingsRootTopBar("设置", Modifier.testTag("settings_root_toolbar"))
        else SettingsTopBar("设置", onBack, tag = "settings_root_toolbar")
    }) { topInset ->
    LazyColumn(Modifier.fillMaxSize().testTag("settings_screen"),
        contentPadding = musicScrollPadding(PaddingValues(16.dp, topInset + 24.dp, 16.dp, 20.dp))) {
        item {
            var origin by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val sourceLayer = rememberGraphicsLayer()
            val color = MiuixTheme.colorScheme.surfaceContainer
            SettingsGroup(Modifier.fillMaxWidth().onGloballyPositioned { origin = it }.drawWithContent {
                sourceLayer.record { this@drawWithContent.drawContent() }; drawLayer(sourceLayer)
            }) {
                val progress = updateTransfer.download
                val updateTitle = when (progress) {
                    is io.github.currencortex.music.core.update.UpdateDownloadState.Downloading -> "更新下载中 · ${(progress.received.toDouble() / progress.total.coerceAtLeast(1) * 100).toInt()}%"
                    is io.github.currencortex.music.core.update.UpdateDownloadState.Ready -> "更新已下载"
                    is io.github.currencortex.music.core.update.UpdateDownloadState.Failed -> "更新下载失败"
                    else -> "检查更新"
                }
                val summary = if (progress is io.github.currencortex.music.core.update.UpdateDownloadState.Idle)
                    "查看版本和更新说明" else "查看下载进度、切换来源或安装"
                SettingsPreference(title = updateTitle, summary = summary,
                    leading = { SettingIcon(MusicIcons.Download) }, trailing = { Icon(MusicIcons.Forward, null, Modifier.size(16.dp)) }, onClick = {
                        onUpdates(origin?.takeIf { it.isAttached }?.let { UpdatePageOrigin(it, color, updateTitle, summary, sourceLayer = sourceLayer) })
                    }, modifier = Modifier.testTag("open_updates"))
            }
        }
        item {
            SettingsListSection("个性化") {
                SettingsPagePreference("外观", "主题、颜色与界面效果", MusicIcons.Image,"open_appearance",onAppearance)
                SettingsDivider(52.dp)
                SettingsPagePreference("桌面悬浮歌词", "实时预览、字幕样式与窗口控制", MusicIcons.BottomBar,"open_desktop_lyrics",onDesktopLyrics)
            }
        }
        item {
            SettingsListSection("音乐") {
                SettingsPagePreference("播放", "音质、歌曲衔接、预加载与播放行为", MusicIcons.Play,"open_playback",onPlayback)
                SettingsDivider(52.dp)
                SettingsPagePreference("音源与网络", "音乐音源、歌词来源与服务器", MusicIcons.Storage,"open_network",onNetwork)
                SettingsDivider(52.dp)
                SettingsPagePreference("音效", "智能调音、音效与均衡器", MusicIcons.Headphones,"open_equalizer",onEffects)
                SettingsDivider(52.dp)
                SettingsPagePreference("音频输出", "输出采样率、设备与能力信息", MusicIcons.Device,"open_audio_output",onAudioOutput)
            }
        }
        item {
            SettingsListSection("应用") {
                SettingsPagePreference("存储空间", "缓存与下载占用", MusicIcons.Storage,"open_storage",onStorage)
                SettingsDivider(52.dp)
                SettingsPreference("导出日志", "保存或分享脱敏诊断文件", Modifier.testTag("open_logs"),
                    leading = { SettingIcon(MusicIcons.File) }, onClick = onLogs,
                    trailing = { Icon(MusicIcons.Forward, null, Modifier.size(16.dp)) })
                SettingsDivider(52.dp)
                SettingsPagePreference("关于", "版本、开源许可与隐私", MusicIcons.Info,"open_about",onAbout)
            }
        }
    }
    }
    }
    }
}

@Composable private fun SettingsPagePreference(title:String,summary:String,icon:ImageVector,tag:String,onClick:()->Unit) {
    PageNavigationPreference(title,summary,Modifier.testTag(tag),icon,onClick)
}
