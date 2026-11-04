// SPDX-License-Identifier: GPL-3.0-only
// ThemeView section order, PreviewChoiceGroup, palette, font and text-scale layouts ported
// from chen08209's upstream project lib/views/theme.dart @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7.
package io.github.currencortex.music.feature.settings

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.theme.rememberAppearanceColors
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable internal fun AppearanceSettingsPage(viewModel: SettingsViewModel, onBack: () -> Unit,
    onOpenScale: () -> Unit, onPreviewLaunch: () -> Unit, onOpenFont: () -> Unit) {
    val config by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontLabel by produceState("默认", config.customFont) {
        value = withContext(Dispatchers.IO) {
            io.github.currencortex.music.ui.theme.appearanceFontLabel(context, config.customFont)
        }
    }
    val light = rememberAppearanceColors(config, false)
    val dark = rememberAppearanceColors(config, true)
    val standardDark = rememberAppearanceColors(config.copy(pureBlack = false), true)
    val black = rememberAppearanceColors(config.copy(pureBlack = true), true)
    val colors = MiuixTheme.colorScheme
    val glass = config.liquidGlass && Build.VERSION.SDK_INT >= 33
    SettingsUiScope {
        SettingsPage("外观", "appearance_screen", onBack, padding = PaddingValues(bottom = 32.dp)) {
            item("live_preview") { AppearanceLivePreview(config) }
            item("theme_mode") {
                SettingsPreviewChoiceGroup("主题模式", MusicIcons.Sun, listOf("自动", "浅色", "深色"),
                    config.themeMode.ordinal, { index -> viewModel.edit { it.copy(themeMode = ThemeMode.entries[index]) } }, "appearance_theme") { index ->
                    AppearanceMiniThumb(if (index == 2) dark else light, config.floatingBar, glass, split = if (index == 0) dark else null)
                }
            }
            item("pure_black") {
                SettingsPreviewChoiceGroup("纯黑模式", MusicIcons.PureBlack, listOf("标准", "纯黑"),
                    if (config.pureBlack) 1 else 0, { index -> viewModel.edit { it.copy(pureBlack = index == 1) } }, "appearance_black") { index ->
                    AppearanceMiniThumb(if (index == 0) standardDark else black, config.floatingBar, glass)
                }
            }
            item("primary_color") { AppearancePalette(config, viewModel) }
            item("liquid_glass") {
                SettingsPreviewChoiceGroup("液态玻璃", MusicIcons.Drop, listOf("标准", "液态"),
                    if (config.liquidGlass) 1 else 0, { index -> viewModel.edit { it.copy(liquidGlass = index == 1) } },
                    "setting_liquid", enabled = { index -> index == 0 || Build.VERSION.SDK_INT >= 33 }) { index ->
                    AppearanceMiniThumb(colors, true, index == 1, selected = 1)
                }
                if (Build.VERSION.SDK_INT < 33) SettingsHint("液态效果需要 Android 13 或更高版本")
            }
            item("launch_animation") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsPreviewChoiceGroup("开屏动画", MusicIcons.Play, LaunchStyle.entries.map { it.label },
                        config.launchStyle.ordinal, { index -> viewModel.edit { it.copy(launchStyle = LaunchStyle.entries[index]) } }, "setting_launch_style") { index ->
                        AppearanceLaunchThumb(LaunchStyle.entries[index], config)
                    }
                    SettingsGroup(Modifier.padding(horizontal = 16.dp)) {
                        SettingsPreference("预览开屏动画", "查看当前样式与用户名效果", Modifier.testTag("setting_launch_preview"),
                            enabled = config.launchStyle != LaunchStyle.NONE, onClick = onPreviewLaunch)
                    }
                }
            }
            item("player_cover") {
                SettingsPreviewChoiceGroup("播放封面", MusicIcons.Image, PlayerCoverStyle.entries.map { it.label },
                    config.playerCoverStyle.ordinal, { index -> viewModel.edit {
                        it.copy(playerCoverStyle = PlayerCoverStyle.entries[index])
                    } }, "appearance_player_cover") { index -> AppearancePlayerCoverThumb(PlayerCoverStyle.entries[index]) }
            }
            item("font_family") {
                AppearanceSection("字体", MusicIcons.Font) {
                    val source = rememberPageNavigationSource("字体", fontLabel, MusicIcons.Font)
                    SettingsPreference(fontLabel, modifier = Modifier.testTag("setting_custom_font").then(source.modifier),
                        onClick = { source.open(onOpenFont) }, leading = {
                            Text("Aa", fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                        }, trailing = { Icon(MusicIcons.Forward, null, Modifier.size(20.dp), tint = colors.onSurfaceVariantSummary) })
                }
            }
            item("text_scale") { AppearanceTextScale(config, viewModel) }
            item("interface_effects") {
                AppearanceSection("界面效果", MusicIcons.Blur) {
                    SettingsSwitch("模糊", config.blur, { value -> viewModel.edit { it.copy(blur = value) } },
                        enabled = Build.VERSION.SDK_INT >= 31, summary = when {
                            Build.VERSION.SDK_INT >= 33 -> "模糊悬浮底栏背景和转场中被盖住的页面"
                            Build.VERSION.SDK_INT >= 31 -> "模糊转场中被盖住的页面"
                            else -> "需要 Android 12 或更高版本"
                        },
                        modifier = Modifier.testTag("setting_blur"))
                    SettingsSwitch("底栏律动光效", config.navigationGlow, { value -> viewModel.edit { it.copy(navigationGlow = value) } },
                        summary = "封面色雾随音乐缓缓起伏", modifier = Modifier.testTag("setting_navigation_glow"))
                    SettingsSwitch("预测性返回手势", config.predictiveBack, { value -> viewModel.edit { it.copy(predictiveBack = value) } },
                        enabled = Build.VERSION.SDK_INT >= 34, modifier = Modifier.testTag("setting_predictive"))
                }
            }
            item("playlist_appearance") {
                AppearanceSection("歌单样式", MusicIcons.Image) {
                    SettingsSwitch("歌单立体缎带", config.playlistDepthRibbon, { value -> viewModel.edit {
                        it.copy(playlistDepthRibbon = value, playlistRecordShelf = if (value) false else it.playlistRecordShelf)
                    } }, summary = "沿歌曲封面穿行，左右拖动，长按查看全景", modifier = Modifier.testTag("setting_depth_ribbon"))
                    SettingsSwitch("歌单 CD 唱片架", config.playlistRecordShelf, { value -> viewModel.edit {
                        it.copy(playlistRecordShelf = value, playlistDepthRibbon = if (value) false else it.playlistDepthRibbon)
                    } }, summary = "透明唱片盒斜向排列，滑动浏览、抽出封面", modifier = Modifier.testTag("setting_record_shelf"))
                }
            }
            item("display_scale") {
                AppearanceSection("显示缩放", MusicIcons.Scale) {
                    var draft by remember(config.uiScale) { mutableFloatStateOf(config.uiScale) }
                    SettingsPreference("显示缩放", "调整界面整体缩放", onClick = onOpenScale,
                        trailing = { Text("${(config.uiScale * 100).roundToInt()}%", color = colors.onSurfaceVariantSummary) })
                    // Keep the slider outside the header's button semantics and tap target.
                    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            GlassSlider(draft, { draft = it }, onValueChangeFinished = { viewModel.edit { it.copy(uiScale = draft) } },
                                valueRange = AppearanceSettings.MIN_SCALE..AppearanceSettings.MAX_SCALE, showKeyPoints = true,
                                keyPoints = listOf(.8f, .9f, 1f, 1.1f, 1.2f), magnetThreshold = .01f,
                                modifier = Modifier.testTag("setting_scale").semantics { setProgress { value ->
                                    draft = value.coerceIn(AppearanceSettings.MIN_SCALE, AppearanceSettings.MAX_SCALE)
                                    viewModel.edit { it.copy(uiScale = draft) }; true
                                } })
                    }
                }
            }
            item("refresh_rate") {
                val display by rememberDisplayRefreshStatus()
                AppearanceSection("刷新率", MusicIcons.Update) {
                    SettingsPreference("应用刷新率", "持续请求所选刷新率；系统省电、温控仍可能限制，高刷会增加耗电。",
                        Modifier.testTag("setting_refresh_rate"), footer = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                RefreshRateSlider(config.refreshRate, display.supported) { option -> viewModel.edit { it.copy(refreshRate = option) } }
                                Text("当前刷新率：${display.current.roundToInt()} Hz · 屏幕支持：${display.supported.joinToString(" / ")} Hz",
                                    Modifier.testTag("refresh_rate_actual"), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                            }
                        })
                }
            }
        }
    }
}

@Composable private fun AppearanceSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsInfoHeader(title, icon)
        SettingsGroup(Modifier.padding(horizontal = 16.dp), content)
    }
}

@Composable private fun AppearancePalette(config: AppearanceSettings, vm: SettingsViewModel) {
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Long?>(null) }
    var showReset by remember { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsInfoHeader("主题色彩", MusicIcons.Palette, actions = {
            SettingsFadingReset(config.monet || config.primaryColors != DefaultThemeColors || config.primaryColor != DefaultThemeColors.first() || config.colorStyle != ThemeColorStyle.VIVID,
                { showReset = true }, "重置主题色", Modifier.testTag("appearance_color_reset"))
        })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppearanceColorSwatches(config,
                onSelect = { color -> vm.edit { it.copy(monet = color == null, primaryColor = color ?: it.primaryColor) } },
                onEdit = { color -> editing = color; showEditor = true },
                onAdd = { editing = null; showEditor = true })
            SettingsGroup {
                SettingsOptionsRow("配色风格", config.colorStyle.label, ThemeColorStyle.entries.map { style ->
                    SettingsOption("appearance_color_style_${style.name}", style.label, style == config.colorStyle,
                        onSelect = { vm.edit { it.copy(colorStyle = style) } })
                }, Modifier.testTag("appearance_color_style"))
            }
        }
    }
    if (showEditor) {
        AppearanceColorSheet(config, editing ?: config.primaryColor, onClosed = { showEditor = false },
            onConfirmed = { value ->
                vm.edit { it.copy(monet = false, primaryColor = value,
                    primaryColors = (if (editing == null) it.primaryColors + value else it.primaryColors.map { old -> if (old == editing) value else old }).distinct()) }
            }, onDeleted = if (editing == null) null else { {
                vm.edit { val palette = it.primaryColors.filterNot { color -> color == editing }.ifEmpty { DefaultThemeColors }
                    it.copy(primaryColors = palette, primaryColor = if (it.primaryColor == editing) palette.first() else it.primaryColor) }
            } })
    }
    if (showReset) MusicDialog("重置主题色？", { showReset = false }) {
        Text("恢复默认颜色和色彩风格。")
        MusicDialogButton("重置", onClick = { vm.edit { it.copy(monet = false, primaryColor = DefaultThemeColors.first(), primaryColors = DefaultThemeColors, colorStyle = ThemeColorStyle.VIVID) }; showReset = false },
            modifier = Modifier.testTag("appearance_color_reset_confirm"))
    }
}

@Composable private fun AppearanceTextScale(config: AppearanceSettings, vm: SettingsViewModel) {
    var draft by remember(config.textScale, config.customTextScale) { mutableFloatStateOf(config.textScale) }
    val systemScale = LocalConfiguration.current.fontScale.coerceIn(.8f, 1.4f)
    val value = if (config.customTextScale) draft else systemScale
    val colors = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsInfoHeader("文字缩放", MusicIcons.TextSize, actions = {
            SettingsFadingReset(config.customTextScale && config.textScale != 1f, { vm.edit { it.copy(textScale = 1f) } }, "重置文字缩放",
                Modifier.testTag("appearance_text_scale_reset"))
        })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsSegmentedControl(listOf("跟随系统", "自定义"), if (config.customTextScale) 1 else 0,
                { index -> vm.edit { it.copy(customTextScale = index == 1) } }, tag = "appearance_text_scale_mode")
            SettingsGroup {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().animateContentSize(tween(200, easing = CubicBezierEasing(.215f, .61f, .355f, 1f))), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, value)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Aa", fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
                                Text("应用内的文字将以这个大小显示", fontSize = 15.sp, lineHeight = 20.sp, color = colors.onSurfaceVariantSummary)
                            }
                        }
                        Text("${(value * 100).roundToInt()}%", Modifier.clip(CircleShape).background(colors.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariantSummary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("A", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                        GlassSlider(value, { draft = it }, enabled = config.customTextScale, valueRange = .8f..1.4f, steps = 11, showKeyPoints = true,
                            keyPoints = (0..12).map { .8f + it * .05f }, magnetThreshold = .015f,
                            onValueChangeFinished = { vm.edit { it.copy(textScale = draft) } },
                            modifier = Modifier.weight(1f).testTag("appearance_text_scale_slider").semantics { setProgress { target ->
                                if (config.customTextScale) { draft = target.coerceIn(.8f, 1.4f); vm.edit { it.copy(textScale = draft) }; true } else false
                            } })
                        Text("A", fontSize = 22.sp, color = colors.onSurfaceVariantSummary)
                    }
                }
            }
        }
    }
}
