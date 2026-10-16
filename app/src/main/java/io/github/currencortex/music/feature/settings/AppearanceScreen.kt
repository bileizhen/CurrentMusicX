// Appearance layout migrated from LeiFetch MainActivity.appearanceItems (XBlocker / SukiSU-Ultra).
// SPDX-License-Identifier: GPL-3.0-only.
package io.github.currencortex.music.feature.settings

import io.github.currencortex.music.ui.component.musicScrollPadding
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.settings.RefreshRatePreference
import io.github.currencortex.music.ui.component.rememberDisplayRefreshStatus
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.SettingsSwitch
import io.github.currencortex.music.ui.theme.LocalDarkTheme
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Icon
import io.github.currencortex.music.ui.component.MusicOverlayDialog as OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppearanceScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOpenScale: () -> Unit) {
    val uiText: (String) -> String = { it }
    val config by viewModel.settings.collectAsStateWithLifecycle()
    top.yukonga.miuix.kmp.basic.Scaffold(
        topBar = { top.yukonga.miuix.kmp.basic.SmallTopAppBar(title = "外观", navigationIcon = {
            top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("navigate_back")) {
                Icon(MusicIcons.Back, contentDescription = "返回")
            }
        }) }, popupHost = {},
    ) { padding ->
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).testTag("appearance_screen"), contentPadding = musicScrollPadding(PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp))) {
        item {
            Spacer(Modifier.height(12.dp))
            ThemePreviewCardMiuix(LocalDarkTheme.current, config.monet, config.floatingBar, config.liquidGlass && config.blur && Build.VERSION.SDK_INT >= 33)
            Spacer(Modifier.height(28.dp))
            TabRow(tabs = listOf("跟随系统", "浅色", "深色"), selectedTabIndex = config.themeMode.ordinal,
                onTabSelected = { index -> viewModel.edit { it.copy(themeMode = ThemeMode.entries[index]) } }, height = 48.dp)
            Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                SettingsSwitch(uiText("Monet 动态颜色"), config.monet, { value -> viewModel.edit { it.copy(monet = value) } },
                    enabled = Build.VERSION.SDK_INT >= 31, summary = if (Build.VERSION.SDK_INT < 31) "需要 Android 12 或更高版本" else null,
                    startAction = { SettingIcon(MusicIcons.Image) }, modifier = Modifier.testTag("setting_monet"))
            }
            Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                SettingsSwitch(uiText("模糊"), config.blur, { value -> viewModel.edit { it.copy(blur = value) } },
                    enabled = Build.VERSION.SDK_INT >= 33, summary = if (Build.VERSION.SDK_INT >= 33) "模糊悬浮底栏背景" else "需要 Android 13 或更高版本",
                    startAction = { SettingIcon(MusicIcons.Blur) }, modifier = Modifier.testTag("setting_blur"))
                SettingsSwitch(uiText("悬浮底栏"), config.floatingBar, { value -> viewModel.edit { it.copy(floatingBar = value) } }, summary = uiText("首页与设置"),
                    startAction = { SettingIcon(MusicIcons.BottomBar) }, modifier = Modifier.testTag("setting_floating"))
                SettingsSwitch(uiText("液态玻璃"), config.liquidGlass, { value -> viewModel.edit { it.copy(liquidGlass = value) } },
                    enabled = config.floatingBar && config.blur && Build.VERSION.SDK_INT >= 33, summary = uiText("为悬浮底栏应用液态玻璃效果"),
                    startAction = { SettingIcon(MusicIcons.Drop) }, modifier = Modifier.testTag("setting_liquid"))
                SettingsSwitch("底栏律动光效", config.navigationGlow, { value -> viewModel.edit { it.copy(navigationGlow = value) } },
                summary = "封面色雾从底边缓缓晕开，随音乐起伏流动",
                    startAction = { SettingIcon(MusicIcons.Blur) }, modifier = Modifier.testTag("setting_navigation_glow"))
            }
            Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                val display by rememberDisplayRefreshStatus()
                BasicComponent(title = "应用刷新率", summary = "持续请求所选刷新率；系统省电、温控仍可能限制。高刷会增加耗电。",
                    modifier = Modifier.testTag("setting_refresh_rate"), bottomAction = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RefreshRatePreference.entries.forEach { option ->
                                    val selected = config.refreshRate == option
                                    Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer)
                                        .testTag("refresh_rate_${option.hz}")
                                        .selectable(selected, role = Role.RadioButton) { viewModel.edit { it.copy(refreshRate = option) } }
                                        .padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(option.label, color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                                            fontSize = 13.sp, maxLines = 1)
                                    }
                                }
                            }
                            Text("当前刷新率：${display.current.roundToInt()} Hz · 屏幕支持：${display.supported.joinToString(" / ")} Hz",
                                modifier = Modifier.testTag("refresh_rate_actual"), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    })
            }
            Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                SettingsSwitch("歌单立体缎带", config.playlistDepthRibbon, { value -> viewModel.edit { it.copy(playlistDepthRibbon = value) } },
                    summary = "沿歌曲封面穿行，左右拖动，长按查看全景",
                    startAction = { SettingIcon(MusicIcons.Image) }, modifier = Modifier.testTag("setting_depth_ribbon"))
                SettingsSwitch(uiText("预测性返回手势"), config.predictiveBack, { value -> viewModel.edit { it.copy(predictiveBack = value) } },
                    enabled = Build.VERSION.SDK_INT >= 34, summary = uiText("启用预测性返回手势支持"),
                    startAction = { SettingIcon(MusicIcons.Back) }, modifier = Modifier.testTag("setting_predictive"))
                var sliderValue by remember(config.uiScale) { mutableFloatStateOf(config.uiScale) }
                BasicComponent(title = uiText("显示缩放"), summary = uiText("调整界面整体缩放"), startAction = { SettingIcon(MusicIcons.Scale) },
                    endActions = {
                        Text("${(sliderValue * 100).roundToInt()}%", color = MiuixTheme.colorScheme.onSurfaceVariantActions)
                        Icon(MusicIcons.Forward, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
                    }, onClick = onOpenScale,
                    bottomAction = {
                        Slider(value = sliderValue, onValueChange = { sliderValue = it },
                            onValueChangeFinished = { viewModel.edit { it.copy(uiScale = sliderValue) } },
                            valueRange = AppearanceSettings.MIN_SCALE..AppearanceSettings.MAX_SCALE, showKeyPoints = true,
                            keyPoints = listOf(.8f, .9f, 1f, 1.1f, 1.2f), magnetThreshold = .01f,
                            hapticEffect = SliderDefaults.SliderHapticEffect.Step, modifier = Modifier.testTag("setting_scale").semantics {
                                // Miuix's SetProgress does not invoke onValueChangeFinished;
                                // accessibility/programmatic adjustments must persist as well.
                                setProgress { target ->
                                    sliderValue = target.coerceIn(AppearanceSettings.MIN_SCALE, AppearanceSettings.MAX_SCALE)
                                    viewModel.edit { it.copy(uiScale = sliderValue) }
                                    true
                                }
                            })
                    })
            }
        }

    }
}

}

// Hosted outside NavDisplay entries so a back gesture dismisses the popup first.
@Composable
fun ScaleDialog(show: Boolean, viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val config by viewModel.settings.collectAsStateWithLifecycle()
    OverlayDialog(show = show, title = "显示缩放", summary = "80% - 120%", onDismissRequest = onDismiss) {
        var input by remember(show) { mutableStateOf((config.uiScale * 100).roundToInt().toString()) }
        TextField(value = input, onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) input = it }, singleLine = true)
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
            TextButton("确定", enabled = input.toIntOrNull() in 80..120, onClick = {
                input.toIntOrNull()?.let { value -> viewModel.edit { it.copy(uiScale = value.coerceIn(80, 120) / 100f) } }
                onDismiss()
            }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun SettingIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 12.dp).size(24.dp))
}
