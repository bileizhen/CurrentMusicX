// SPDX-License-Identifier: GPL-3.0-only
package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.ui.component.SettingsSwitch
import io.github.currencortex.music.ui.component.nativeui.*

/** Hidden until the version row on the About page is tapped five times. */
@Composable internal fun DeveloperOptionsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val config by viewModel.settings.collectAsStateWithLifecycle()
    SettingsUiScope {
        SettingsPage("开发者选项", "developer_options_screen", onBack,
            padding = PaddingValues(bottom = 32.dp)) {
            item("developer_mode") {
                SettingsGroup(Modifier.padding(horizontal = 16.dp)) {
                    SettingsSwitch("开发者选项", config.developerMode,
                        { on -> viewModel.edit { it.copy(developerMode = on, showFrameRate = if (on) it.showFrameRate else false) } },
                        modifier = Modifier.testTag("developer_mode_switch"))
                }
            }
            item("interaction") {
                DeveloperSection("交互") {
                    SettingsSwitch("滚动边缘触感", config.scrollEdgeHaptic,
                        { on -> viewModel.edit { it.copy(scrollEdgeHaptic = on) } },
                        summary = "列表滚到尽头时振动",
                        modifier = Modifier.testTag("developer_scroll_edge_haptic"))
                }
            }
            item("frame_rate") {
                DeveloperSection("开发者") {
                    SettingsSwitch("显示帧率", config.showFrameRate,
                        { on -> viewModel.edit { it.copy(showFrameRate = on) } },
                        summary = "在屏幕上叠加当前帧率",
                        modifier = Modifier.testTag("developer_show_frame_rate"))
                }
            }
            }
    }
}

@Composable private fun DeveloperSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsListHeader(title)
        SettingsGroup(Modifier.padding(horizontal = 16.dp), content)
    }
}