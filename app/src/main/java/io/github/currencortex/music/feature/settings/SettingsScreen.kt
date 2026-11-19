package io.github.currencortex.music.feature.settings

import io.github.currencortex.music.ui.component.rootMotionItems
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
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.feature.update.UpdatePageOrigin
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun SettingsScreen(onAppearance: () -> Unit, onNetwork: () -> Unit,
                   onStorage: () -> Unit, onLogs: () -> Unit, onAbout: () -> Unit, onUpdates: (UpdatePageOrigin?) -> Unit,
                   onDesktopLyrics: () -> Unit = {},
                   onAudioOutput: () -> Unit = {},
                   onPlayback: () -> Unit = {}, onEffects: () -> Unit = {},
                   updateTransfer: io.github.currencortex.music.core.update.UpdateTransferState = io.github.currencortex.music.core.update.UpdateTransferState(),
                   onNavigateFrom: ((PageContainerOrigin, () -> Unit) -> Unit)? = null,
                   onBack: (() -> Unit)? = null, onSupport: () -> Unit = {}, onFeedback: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    CompositionLocalProvider(LocalPageContainerSource provides (onNavigateFrom ?: LocalPageContainerSource.current)) {
    SettingsUiScope {
    SettingsScrollLayout(Modifier.fillMaxSize(), topBar = {
        if (onBack == null) SettingsRootTopBar("设置", Modifier.testTag("settings_root_toolbar"))
        else SettingsTopBar("设置", onBack, tag = "settings_root_toolbar")
    }) { topInset ->
    LazyColumn(Modifier.fillMaxSize().testTag("settings_screen"),
        contentPadding = musicScrollPadding(PaddingValues(16.dp, topInset + 24.dp, 16.dp, 20.dp))) {
        rootMotionItems {
        item {
            var origin by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val sourceLayer = rememberGraphicsLayer()
            val color = COUITheme.colorScheme.cardBackground
            SettingsCard(Modifier.fillMaxWidth().onGloballyPositioned { origin = it }.drawWithContent {
                sourceLayer.record { this@drawWithContent.drawContent() }; drawLayer(sourceLayer)
            }) {
                item {
                    val progress = updateTransfer.download
                    val updateTitle = when (progress) {
                        is io.github.currencortex.music.core.update.UpdateDownloadState.Downloading -> "更新下载中 · ${(progress.received.toDouble() / progress.total.coerceAtLeast(1) * 100).toInt()}%"
                        is io.github.currencortex.music.core.update.UpdateDownloadState.Ready -> "更新已下载"
                        is io.github.currencortex.music.core.update.UpdateDownloadState.Failed -> "更新下载失败"
                        else -> "检查更新"
                    }
                    val summary = if (progress is io.github.currencortex.music.core.update.UpdateDownloadState.Idle)
                        "查看版本和更新说明" else "查看下载进度、切换来源或安装"
                    ArrowPreference(
                        title = updateTitle,
                        summary = summary,
                        startAction = { SettingIcon(MusicIcons.Download) },
                        onClick = {
                            onUpdates(origin?.takeIf { it.isAttached }?.let {
                                UpdatePageOrigin(it, color, updateTitle, summary, sourceLayer = sourceLayer)
                            })
                        },
                        modifier = Modifier.testTag("open_updates"),
                    )
                }
            }
        }
        item {
            SettingsCategoryCard("个性化") {
                item {
                    ArrowPreference(
                        title = "外观",
                        summary = "主题、颜色与界面效果",
                        startAction = { SettingIcon(MusicIcons.Image) },
                        onClick = onAppearance,
                        modifier = Modifier.testTag("open_appearance"),
                    )
                }
                item {
                    ArrowPreference(
                        title = "桌面悬浮歌词",
                        summary = "实时预览、字幕样式与窗口控制",
                        startAction = { SettingIcon(MusicIcons.BottomBar) },
                        onClick = onDesktopLyrics,
                        modifier = Modifier.testTag("open_desktop_lyrics"),
                    )
                }
            }
        }
        item {
            SettingsCategoryCard("音乐") {
                item {
                    ArrowPreference(
                        title = "播放",
                        summary = "音质、歌曲衔接、预加载与播放行为",
                        startAction = { SettingIcon(MusicIcons.Play) },
                        onClick = onPlayback,
                        modifier = Modifier.testTag("open_playback"),
                    )
                }
                item {
                    ArrowPreference(
                        title = "音源与网络",
                        summary = "音乐音源、歌词来源与服务器",
                        startAction = { SettingIcon(MusicIcons.Storage) },
                        onClick = onNetwork,
                        modifier = Modifier.testTag("open_network"),
                    )
                }
                item {
                    ArrowPreference(
                        title = "音效",
                        summary = "智能调音、音效与均衡器",
                        startAction = { SettingIcon(MusicIcons.Headphones) },
                        onClick = onEffects,
                        modifier = Modifier.testTag("open_equalizer"),
                    )
                }
                item {
                    ArrowPreference(
                        title = "音频输出",
                        summary = "输出采样率、设备与能力信息",
                        startAction = { SettingIcon(MusicIcons.Device) },
                        onClick = onAudioOutput,
                        modifier = Modifier.testTag("open_audio_output"),
                    )
                }
            }
        }
        item {
            SettingsCategoryCard("应用") {
                item {
                    ArrowPreference(
                        title = "默认打开链接",
                        summary = "在系统中允许打开网易云和 CurrentMusic 网站链接",
                        startAction = { SettingIcon(MusicIcons.Info) },
                        onClick = { io.github.currencortex.music.ui.util.openAppLinkSettings(context) },
                        modifier = Modifier.testTag("open_app_links"),
                    )
                }
                item {
                    ArrowPreference(
                        title = "存储空间",
                        summary = "缓存与下载占用",
                        startAction = { SettingIcon(MusicIcons.Storage) },
                        onClick = onStorage,
                        modifier = Modifier.testTag("open_storage"),
                    )
                }
                item {
                    ArrowPreference(
                        title = "导出日志",
                        summary = "保存或分享脱敏诊断文件",
                        startAction = { SettingIcon(MusicIcons.File) },
                        onClick = onLogs,
                        modifier = Modifier.testTag("open_logs"),
                    )
                }
                item {
                    ArrowPreference(
                        title = "意见反馈",
                        summary = "报告问题或提出改进建议",
                        startAction = { SettingIcon(MusicIcons.Comment) },
                        onClick = onFeedback,
                        modifier = Modifier.testTag("open_feedback"),
                    )
                }
                item {
                    ArrowPreference(
                        title = "支持开发者",
                        summary = "自愿赞助项目开发与维护",
                        startAction = { SettingIcon(MusicIcons.Heart) },
                        onClick = onSupport,
                        modifier = Modifier.testTag("open_support"),
                    )
                }
                item {
                    ArrowPreference(
                        title = "关于",
                        summary = "版本、开源许可与隐私",
                        startAction = { SettingIcon(MusicIcons.Info) },
                        onClick = onAbout,
                        modifier = Modifier.testTag("open_about"),
                    )
                }
            }
        }
    }
    }
    }
    }
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    content: CardListGroupScope.() -> Unit,
) {
    Column {
        PreferenceCategory(title = title)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            insideMargin = PaddingValues(0.dp),
        ) {
            CardListGroup(content = content)
        }
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: CardListGroupScope.() -> Unit,
) {
    Card(modifier = modifier, insideMargin = PaddingValues(0.dp)) {
        CardListGroup(content = content)
    }
}
