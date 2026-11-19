package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.feature.update.UpdatePageOrigin
import io.github.currencortex.music.ui.component.DecoratedAvatar
import io.github.currencortex.music.ui.component.LocalPageContainerSource
import io.github.currencortex.music.ui.component.PageContainerOrigin
import io.github.currencortex.music.ui.component.musicScrollPadding
import io.github.currencortex.music.ui.component.rootMotionItems
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.blur.TopAppBarGlow
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.*
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

private data class SettingsDestination(
    val key: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun SettingsScreen(
    onAppearance: () -> Unit,
    onNetwork: () -> Unit,
    onStorage: () -> Unit,
    onLogs: () -> Unit,
    onAbout: () -> Unit,
    onUpdates: (UpdatePageOrigin?) -> Unit,
    onDesktopLyrics: () -> Unit = {},
    onAudioOutput: () -> Unit = {},
    onPlayback: () -> Unit = {},
    onEffects: () -> Unit = {},
    updateTransfer: io.github.currencortex.music.core.update.UpdateTransferState =
        io.github.currencortex.music.core.update.UpdateTransferState(),
    onNavigateFrom: ((PageContainerOrigin, () -> Unit) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onSupport: () -> Unit = {},
    onFeedback: () -> Unit = {},
    accountName: String? = null,
    accountAvatarUrl: String? = null,
    onAccount: () -> Unit = {},
) {
    val context = LocalContext.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val searchState = rememberTopAppBarSearchState(active = searching)
    val scrollBehavior: ScrollBehavior = COUIScrollBehavior()
    val backgroundColor = COUITheme.colorScheme.background
    val isDark = COUITheme.isDark
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val updateProgress = updateTransfer.download
    val updateTitle = when (updateProgress) {
        is io.github.currencortex.music.core.update.UpdateDownloadState.Downloading ->
            "更新下载中 · ${(updateProgress.received.toDouble() / updateProgress.total.coerceAtLeast(1) * 100).toInt()}%"
        is io.github.currencortex.music.core.update.UpdateDownloadState.Ready -> "更新已下载"
        is io.github.currencortex.music.core.update.UpdateDownloadState.Failed -> "更新下载失败"
        else -> "检查更新"
    }
    val updateSummary = if (updateProgress is io.github.currencortex.music.core.update.UpdateDownloadState.Idle)
        "查看版本和更新说明" else "查看下载进度、切换来源或安装"
    val displayAccountName = accountName?.takeIf { it.isNotBlank() } ?: "登录账号"
    val destinations = listOf(
        SettingsDestination("account", displayAccountName, "管理账号、云服务、查找", COUIIcons.Contacts, onAccount),
        SettingsDestination("updates", updateTitle, updateSummary, COUIIcons.Update) { onUpdates(null) },
        SettingsDestination("appearance", "外观", "主题、颜色与界面效果", COUIIcons.Theme, onAppearance),
        SettingsDestination("desktop_lyrics", "桌面悬浮歌词", "实时预览、字幕样式与窗口控制",
            COUIIcons.ScreenMirroring, onDesktopLyrics),
        SettingsDestination("playback", "播放", "音质、歌曲衔接、预加载与播放行为", COUIIcons.Play, onPlayback),
        SettingsDestination("network", "音源与网络", "音乐音源、歌词来源与服务器", COUIIcons.CloudFill, onNetwork),
        SettingsDestination("effects", "音效", "智能调音、音效与均衡器", COUIIcons.Tune, onEffects),
        SettingsDestination("audio_output", "音频输出", "输出采样率、设备与能力信息", COUIIcons.VolumeUp,
            onAudioOutput),
        SettingsDestination("app_links", "默认打开链接", "在系统中允许打开网易云和 CurrentMusic 网站链接",
            COUIIcons.Link) { io.github.currencortex.music.ui.util.openAppLinkSettings(context) },
        SettingsDestination("storage", "存储空间", "缓存与下载占用", COUIIcons.FileDownloads, onStorage),
        SettingsDestination("logs", "导出日志", "保存或分享脱敏诊断文件", COUIIcons.File, onLogs),
        SettingsDestination("feedback", "意见反馈", "报告问题或提出改进建议", COUIIcons.Messages, onFeedback),
        SettingsDestination("support", "支持开发者", "自愿赞助项目开发与维护", COUIIcons.Favorites, onSupport),
        SettingsDestination("about", "关于", "版本、开源许可与隐私", COUIIcons.Info, onAbout),
    )

    CompositionLocalProvider(LocalPageContainerSource provides (onNavigateFrom ?: LocalPageContainerSource.current)) {
        SettingsUiScope {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = backgroundColor,
                topBar = {
                    TopAppBar(
                        title = "设置",
                        largeTitle = "设置",
                        modifier = Modifier.testTag("settings_root_toolbar"),
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        expandedHeight = 116.dp,
                        largeTitleFontSize = 40.sp,
                        titlePadding = 24.dp,
                        largeTitleBottomMargin = 18.dp,
                        fadeLargeTitleOnCollapse = true,
                        searchState = searchState,
                        glow = {
                            TopAppBarGlow(
                                backdrop = backdrop,
                                scrollOffsetPx = scrollBehavior.glowScrollOffsetPx(),
                                isDark = isDark,
                                baseColor = backgroundColor,
                                enabled = true,
                            )
                        },
                        navigationIcon = {
                            if (onBack != null) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier.testTag("navigate_back"),
                                ) {
                                    Icon(COUIIcons.Back, contentDescription = "返回")
                                }
                            }
                        },
                        bottomContent = {
                            TopAppBarSearchBar(
                                query = query,
                                onQueryChange = { query = it },
                                expanded = searching,
                                onExpandedChange = {
                                    searching = it
                                    if (!it) query = ""
                                },
                                cancelText = "取消",
                                label = "搜索设置项",
                                modifier = Modifier.testTag("settings_search"),
                            )
                        },
                    )
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    if (searchState.isContentVisible) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                                .testTag("settings_screen")
                                .topAppBarSearchContent(searchState)
                                .overScrollVertical()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = musicScrollPadding(
                                PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, 20.dp)
                            ),
                        ) {
                            rootMotionItems {
                                item {
                                    UpdateSettingsCard(
                                        updateTitle = updateTitle,
                                        updateSummary = updateSummary,
                                        onUpdates = onUpdates,
                                    )
                                }
                                item {
                                    SettingsAccountCard(
                                        name = displayAccountName,
                                        avatarUrl = accountAvatarUrl,
                                        onAccount = onAccount,
                                    )
                                }
                                item {
                                    SettingsCategoryCard("个性化") {
                                        item {
                                            ArrowPreference(
                                                title = "外观",
                                                summary = "主题、颜色与界面效果",
                                                startAction = { SettingIcon(COUIIcons.Theme) },
                                                onClick = onAppearance,
                                                modifier = Modifier.testTag("open_appearance"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "桌面悬浮歌词",
                                                summary = "实时预览、字幕样式与窗口控制",
                                                startAction = { SettingIcon(COUIIcons.ScreenMirroring) },
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
                                                startAction = { SettingIcon(COUIIcons.Play) },
                                                onClick = onPlayback,
                                                modifier = Modifier.testTag("open_playback"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "音源与网络",
                                                summary = "音乐音源、歌词来源与服务器",
                                                startAction = { SettingIcon(COUIIcons.CloudFill) },
                                                onClick = onNetwork,
                                                modifier = Modifier.testTag("open_network"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "音效",
                                                summary = "智能调音、音效与均衡器",
                                                startAction = { SettingIcon(COUIIcons.Tune) },
                                                onClick = onEffects,
                                                modifier = Modifier.testTag("open_equalizer"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "音频输出",
                                                summary = "输出采样率、设备与能力信息",
                                                startAction = { SettingIcon(COUIIcons.VolumeUp) },
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
                                                startAction = { SettingIcon(COUIIcons.Link) },
                                                onClick = {
                                                    io.github.currencortex.music.ui.util.openAppLinkSettings(context)
                                                },
                                                modifier = Modifier.testTag("open_app_links"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "存储空间",
                                                summary = "缓存与下载占用",
                                                startAction = { SettingIcon(COUIIcons.FileDownloads) },
                                                onClick = onStorage,
                                                modifier = Modifier.testTag("open_storage"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "导出日志",
                                                summary = "保存或分享脱敏诊断文件",
                                                startAction = { SettingIcon(COUIIcons.File) },
                                                onClick = onLogs,
                                                modifier = Modifier.testTag("open_logs"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "意见反馈",
                                                summary = "报告问题或提出改进建议",
                                                startAction = { SettingIcon(COUIIcons.Messages) },
                                                onClick = onFeedback,
                                                modifier = Modifier.testTag("open_feedback"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "支持开发者",
                                                summary = "自愿赞助项目开发与维护",
                                                startAction = { SettingIcon(COUIIcons.Favorites) },
                                                onClick = onSupport,
                                                modifier = Modifier.testTag("open_support"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "关于",
                                                summary = "版本、开源许可与隐私",
                                                startAction = { SettingIcon(COUIIcons.Info) },
                                                onClick = onAbout,
                                                modifier = Modifier.testTag("open_about"),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (searchState.isResultsVisible) {
                        SettingsSearchResults(
                            query = query,
                            searchState = searchState,
                            scrollBehavior = scrollBehavior,
                            padding = padding,
                            destinations = destinations,
                            onBeforeNavigate = {
                                searching = false
                                query = ""
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateSettingsCard(
    updateTitle: String,
    updateSummary: String,
    onUpdates: (UpdatePageOrigin?) -> Unit,
) {
    var origin by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val sourceLayer = rememberGraphicsLayer()
    val color = COUITheme.colorScheme.cardBackground
    SettingsCard(
        Modifier.fillMaxWidth()
            .padding(bottom = 16.dp)
            .onGloballyPositioned { origin = it }
            .drawWithContent {
                sourceLayer.record { this@drawWithContent.drawContent() }
                drawLayer(sourceLayer)
            }
    ) {
        item {
            ArrowPreference(
                title = updateTitle,
                summary = updateSummary,
                startAction = { SettingIcon(COUIIcons.Update) },
                onClick = {
                    onUpdates(
                        origin?.takeIf { it.isAttached }?.let {
                            UpdatePageOrigin(it, color, updateTitle, updateSummary, sourceLayer = sourceLayer)
                        }
                    )
                },
                modifier = Modifier.testTag("open_updates"),
            )
        }
    }
}

@Composable
private fun SettingsAccountCard(
    name: String,
    avatarUrl: String?,
    onAccount: () -> Unit,
) {
    SettingsCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        item {
            ArrowPreference(
                title = name,
                summary = "管理账号、云服务、查找",
                startAction = {
                    DecoratedAvatar(
                        avatarUrl = avatarUrl,
                        decorationUrl = null,
                        size = 48.dp,
                        reserveOverlay = false,
                    )
                },
                onClick = onAccount,
                modifier = Modifier.testTag("open_account"),
            )
        }
    }
}

@Composable
private fun SettingsSearchResults(
    query: String,
    searchState: TopAppBarSearchState,
    scrollBehavior: ScrollBehavior,
    padding: PaddingValues,
    destinations: List<SettingsDestination>,
    onBeforeNavigate: () -> Unit,
) {
    val normalized = query.trim()
    val results = if (normalized.isBlank()) destinations else destinations.filter {
        it.title.contains(normalized, ignoreCase = true) || it.summary.contains(normalized, ignoreCase = true)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .testTag("settings_search_results")
            .topAppBarSearchResults(searchState)
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = musicScrollPadding(
            PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, 20.dp)
        ),
    ) {
        if (results.isEmpty()) {
            item {
                PreferenceCategory(title = "搜索结果")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    insideMargin = PaddingValues(24.dp),
                ) {
                    Text(
                        text = "未找到“$normalized”相关的设置",
                        color = COUITheme.colorScheme.labelSecondaryVariant,
                    )
                }
            }
        } else {
            item {
                SettingsCategoryCard(if (normalized.isBlank()) "全部设置" else "搜索结果") {
                    results.forEach { destination ->
                        item(key = destination.key) {
                            ArrowPreference(
                                title = destination.title,
                                summary = destination.summary,
                                startAction = { SettingIcon(destination.icon) },
                                onClick = {
                                    onBeforeNavigate()
                                    destination.onClick()
                                },
                                modifier = Modifier.testTag("settings_search_${destination.key}"),
                            )
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
