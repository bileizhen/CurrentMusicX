package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.update.UpdateChannel
import io.github.currencortex.music.ui.component.SettingsSwitch
import io.github.currencortex.music.ui.component.MusicIcons
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference

@Composable
fun SettingsScreen(updateViewModel: UpdateSettingsViewModel, onAppearance: () -> Unit,
                   onLogs: () -> Unit, onAbout: () -> Unit, onUpdates: () -> Unit) {
    val settings by updateViewModel.settings.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().testTag("settings_screen"),
        contentPadding = PaddingValues(12.dp, 30.dp, 12.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("设置", fontSize = 32.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
        item {
            Card(Modifier.fillMaxWidth()) {
                SettingsSwitch("启动时自动检查更新", settings.autoCheckOnLaunch, updateViewModel::setAutoCheck,
                    summary = "打开应用后检查 GitHub 最新发布", startAction = { SettingIcon(MusicIcons.Update) })
                OverlaySpinnerPreference(title = "更新渠道", summary = "接收的版本类型",
                    startAction = { SettingIcon(MusicIcons.Rocket) },
                    items = listOf(DropdownItem("正式版", summary = "仅接收稳定发布"),
                        DropdownItem("预发布", summary = "提前获取 rc 测试版本")),
                    selectedIndex = settings.channel.ordinal,
                    onSelectedIndexChange = { updateViewModel.setChannel(UpdateChannel.entries[it]) },
                    modifier = Modifier.testTag("update_channel"))
                ArrowPreference(title = "检查更新", summary = "查看版本和更新说明",
                    startAction = { SettingIcon(MusicIcons.Download) }, onClick = onUpdates)
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                ArrowPreference(title = "外观", summary = "主题、颜色与界面效果",
                    startAction = { SettingIcon(MusicIcons.Image) }, onClick = onAppearance)
                ArrowPreference(title = "导出日志", summary = "保存或分享脱敏诊断文件",
                    startAction = { SettingIcon(MusicIcons.File) }, onClick = onLogs)
                ArrowPreference(title = "关于", summary = "版本、开源许可与隐私",
                    startAction = { SettingIcon(MusicIcons.Info) }, onClick = onAbout)
            }
        }
    }
}
