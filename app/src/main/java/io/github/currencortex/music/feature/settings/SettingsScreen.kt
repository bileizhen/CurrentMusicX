package io.github.currencortex.music.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.feature.update.UpdatePageOrigin
import io.github.currencortex.music.ui.component.DecoratedAvatar
import io.github.currencortex.music.ui.component.LocalPageContainerSource
import io.github.currencortex.music.ui.component.PageContainerOrigin
import io.github.currencortex.music.ui.component.musicScrollPadding
import io.github.currencortex.music.ui.component.rootMotionItems
import io.github.currencortex.music.ui.component.springChainSettingsItems
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.TopAppBarGlow
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.*
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.effect.rememberSpringChainState
import io.github.suqi8.coui.kmp.effect.springChain
import io.github.suqi8.coui.kmp.squircle.SquircleDefaults
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.utils.overScrollVertical

private data class SettingsDestination(
    val key: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val tint: Color? = null,
    val onClick: () -> Unit,
)

@Composable
fun SettingsScreen(
    onAppearance: () -> Unit,
    onNetwork: () -> Unit,
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
    val settingsListState = rememberLazyListState()
    val springChainState = rememberSpringChainState(settingsListState)
    val density = LocalDensity.current
    val titleHeight = 116.dp
    val titleHeightPx = with(density) { titleHeight.toPx() }
    var headerCollapsePx by remember { mutableFloatStateOf(0f) }
    val searchLift by animateFloatAsState(
        targetValue = if (searching) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 800f),
        label = "settings_search_lift",
    )
    val collapseFraction = maxOf(
        if (titleHeightPx > 0f) (headerCollapsePx / titleHeightPx).coerceIn(0f, 1f) else 0f,
        searchLift,
    )
    val headerConnection = remember(titleHeightPx, searching) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (searching) return Offset.Zero
                return if (available.y < 0f) {
                    val delta = (-available.y).coerceAtMost(titleHeightPx - headerCollapsePx)
                    if (delta <= 0f) Offset.Zero else {
                        headerCollapsePx += delta
                        Offset(0f, -delta)
                    }
                } else {
                    val delta = available.y.coerceAtMost(headerCollapsePx)
                    if (delta <= 0f) Offset.Zero else {
                        headerCollapsePx -= delta
                        Offset(0f, delta)
                    }
                }
            }
        }
    }
    val colors = COUITheme.colorScheme
    val backgroundColor = colors.background
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
        SettingsDestination("account", displayAccountName, "网易云账号与账号安全", COUIIcons.Contacts,
            colors.blue, onAccount),
        SettingsDestination("updates", updateTitle, updateSummary, COUIIcons.Update,
            colors.blue) { onUpdates(null) },
        SettingsDestination("appearance", "外观", "主题、颜色与界面效果", COUIIcons.Theme,
            colors.orange, onAppearance),
        SettingsDestination("desktop_lyrics", "桌面悬浮歌词", "实时预览、字幕样式与窗口控制",
            COUIIcons.ScreenMirroring, colors.azure, onDesktopLyrics),
        SettingsDestination("playback", "播放", "音质、歌曲衔接、预加载与播放行为", COUIIcons.Play,
            colors.green, onPlayback),
        SettingsDestination("network", "音源与网络", "音乐音源、歌词来源与服务器", COUIIcons.CloudFill,
            colors.blue, onNetwork),
        SettingsDestination("effects", "音效", "智能调音、音效与均衡器", COUIIcons.Tune,
            colors.mint, onEffects),
        SettingsDestination("audio_output", "音频输出", "输出采样率、设备与能力信息", COUIIcons.VolumeUp,
            colors.violet, onAudioOutput),
        SettingsDestination("app_links", "默认打开链接", "在系统中允许打开网易云和 CurrentMusic 网站链接",
            COUIIcons.Link, colors.blue) { io.github.currencortex.music.ui.util.openAppLinkSettings(context) },
        SettingsDestination("logs", "导出日志", "保存或分享脱敏诊断文件", COUIIcons.File,
            colors.azure, onLogs),
        SettingsDestination("feedback", "意见反馈", "报告问题或提出改进建议", COUIIcons.Messages,
            colors.blue, onFeedback),
        SettingsDestination("support", "支持开发者", "自愿赞助项目开发与维护", COUIIcons.Favorites,
            colors.red, onSupport),
        SettingsDestination("about", "关于", "版本、开源许可与隐私", COUIIcons.Info,
            colors.labelSecondaryVariant, onAbout),
    )

    CompositionLocalProvider(LocalPageContainerSource provides (onNavigateFrom ?: LocalPageContainerSource.current)) {
        SettingsUiScope {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = backgroundColor,
                topBar = {
                    SettingsHomeHeader(
                        title = "设置",
                        titleHeight = titleHeight,
                        collapseFraction = collapseFraction,
                        scrollOffsetPx = collapseFraction * titleHeightPx,
                        backdrop = backdrop,
                        isDark = isDark,
                        baseColor = backgroundColor,
                        query = query,
                        onQueryChange = { query = it },
                        searching = searching,
                        onExpandedChange = {
                            searching = it
                            if (!it) query = ""
                        },
                        onBack = onBack,
                    )
                },
            ) { padding ->
                val contentTopPadding = maxOf(0.dp, padding.calculateTopPadding() - 24.dp)
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    if (searchState.isContentVisible) {
                        LazyColumn(
                            state = settingsListState,
                            modifier = Modifier.fillMaxSize()
                                .testTag("settings_screen")
                                .topAppBarSearchContent(searchState)
                                .overScrollVertical()
                                .springChain(springChainState)
                                .nestedScroll(headerConnection),
                            contentPadding = musicScrollPadding(
                                PaddingValues(16.dp, contentTopPadding + 8.dp, 16.dp, 20.dp)
                            ),
                        ) {
                            springChainSettingsItems(springChainState) {
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
                                    SettingsCategoryCard(null) {
                                        item {
                                            ArrowPreference(
                                                title = "外观",
                                                startAction = { SettingIcon(COUIIcons.Theme, colors.orange) },
                                                onClick = onAppearance,
                                                modifier = Modifier.testTag("open_appearance"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "桌面悬浮歌词",
                                                startAction = { SettingIcon(COUIIcons.ScreenMirroring, colors.azure) },
                                                onClick = onDesktopLyrics,
                                                modifier = Modifier.testTag("open_desktop_lyrics"),
                                            )
                                        }
                                    }
                                }
                                item {
                                    SettingsCategoryCard(null) {
                                        item {
                                            ArrowPreference(
                                                title = "播放",
                                                startAction = { SettingIcon(COUIIcons.Play, colors.green) },
                                                onClick = onPlayback,
                                                modifier = Modifier.testTag("open_playback"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "音源与网络",
                                                startAction = { SettingIcon(COUIIcons.CloudFill, colors.blue) },
                                                onClick = onNetwork,
                                                modifier = Modifier.testTag("open_network"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "音效",
                                                startAction = { SettingIcon(COUIIcons.Tune, colors.mint) },
                                                onClick = onEffects,
                                                modifier = Modifier.testTag("open_equalizer"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "音频输出",
                                                startAction = { SettingIcon(COUIIcons.VolumeUp, colors.violet) },
                                                onClick = onAudioOutput,
                                                modifier = Modifier.testTag("open_audio_output"),
                                            )
                                        }
                                    }
                                }
                                item {
                                    SettingsCategoryCard(null) {
                                        item {
                                            ArrowPreference(
                                                title = "默认打开链接",
                                                startAction = { SettingIcon(COUIIcons.Link, colors.blue) },
                                                onClick = {
                                                    io.github.currencortex.music.ui.util.openAppLinkSettings(context)
                                                },
                                                modifier = Modifier.testTag("open_app_links"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "导出日志",
                                                startAction = { SettingIcon(COUIIcons.File, colors.azure) },
                                                onClick = onLogs,
                                                modifier = Modifier.testTag("open_logs"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "意见反馈",
                                                startAction = { SettingIcon(COUIIcons.Messages, colors.blue) },
                                                onClick = onFeedback,
                                                modifier = Modifier.testTag("open_feedback"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "支持开发者",
                                                startAction = { SettingIcon(COUIIcons.Favorites, colors.red) },
                                                onClick = onSupport,
                                                modifier = Modifier.testTag("open_support"),
                                            )
                                        }
                                        item {
                                            ArrowPreference(
                                                title = "关于",
                                                startAction = { SettingIcon(COUIIcons.Info, colors.labelSecondaryVariant) },
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
                    if (searchState.isResultsVisible) {
                        SettingsSearchResults(
                            query = query,
                            searchState = searchState,
                            headerConnection = headerConnection,
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
private fun SettingsHomeHeader(
    title: String,
    titleHeight: androidx.compose.ui.unit.Dp,
    collapseFraction: Float,
    scrollOffsetPx: Float,
    backdrop: Backdrop,
    isDark: Boolean,
    baseColor: Color,
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onBack: (() -> Unit)?,
) {
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val titleAreaHeight = (titleHeight * (1f - collapseFraction)).coerceAtLeast(0.dp)
    Box(Modifier.fillMaxWidth().testTag("settings_root_toolbar")) {
        TopAppBarGlow(
            backdrop = backdrop,
            scrollOffsetPx = scrollOffsetPx,
            isDark = isDark,
            baseColor = baseColor,
            enabled = true,
        )
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(statusBarInset))
            Box(Modifier.fillMaxWidth().height(titleAreaHeight).clipToBounds()) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = 1f - collapseFraction
                        scaleX = 1f - 0.2f * collapseFraction
                        scaleY = 1f - 0.2f * collapseFraction
                        transformOrigin = TransformOrigin(0f, 1f)
                    }
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                                .testTag("navigate_back"),
                        ) {
                            Icon(COUIIcons.Back, contentDescription = "返回")
                        }
                    }
                    Text(
                        text = title,
                        modifier = Modifier.align(Alignment.BottomStart)
                            .padding(start = if (onBack != null) 72.dp else 24.dp, bottom = 18.dp),
                        fontSize = 40.sp,
                        lineHeight = 52.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            TopAppBarSearchBar(
                query = query,
                onQueryChange = onQueryChange,
                expanded = searching,
                onExpandedChange = onExpandedChange,
                cancelText = "取消",
                label = "搜索设置项",
                modifier = Modifier.testTag("settings_search"),
            )
            Spacer(Modifier.height(24.dp))
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
                startAction = { SettingIcon(COUIIcons.Update, COUITheme.colorScheme.blue) },
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
                summary = "网易云账号与账号安全",
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
    headerConnection: NestedScrollConnection,
    padding: PaddingValues,
    destinations: List<SettingsDestination>,
    onBeforeNavigate: () -> Unit,
) {
    val normalized = query.trim()
    val results = if (normalized.isBlank()) destinations else destinations.filter {
        it.title.contains(normalized, ignoreCase = true) || it.summary.contains(normalized, ignoreCase = true)
    }
    val contentTopPadding = maxOf(0.dp, padding.calculateTopPadding() - 24.dp)
    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .testTag("settings_search_results")
            .topAppBarSearchResults(searchState)
            .overScrollVertical()
            .nestedScroll(headerConnection),
        contentPadding = musicScrollPadding(
            PaddingValues(16.dp, contentTopPadding, 16.dp, 20.dp)
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
                                startAction = { SettingIcon(destination.icon, destination.tint) },
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
    title: String?,
    content: CardListGroupScope.() -> Unit,
) {
    Column {
        if (title != null) PreferenceCategory(title = title)
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                .squircleSurface(
                    color = COUITheme.colorScheme.cardBackground,
                    cornerRadius = 24.dp,
                    weight = SquircleDefaults.Weight,
                ),
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
    Column(
        modifier = modifier.squircleSurface(
            color = COUITheme.colorScheme.cardBackground,
            cornerRadius = 24.dp,
            weight = SquircleDefaults.Weight,
        ),
    ) {
        CardListGroup(content = content)
    }
}
