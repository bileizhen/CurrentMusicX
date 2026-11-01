// SPDX-License-Identifier: GPL-3.0-only
// upstream appearance adapter and the display-scale input dialog.
package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.ui.component.MusicDialogActions
import io.github.currencortex.music.ui.component.MusicDialogButton
import io.github.currencortex.music.ui.component.nativeui.SettingsUiScope
import io.github.currencortex.music.ui.component.nativeui.LocalSettingsComponents
import io.github.currencortex.music.ui.component.MusicOverlayDialog as OverlayDialog
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextField
import kotlin.math.roundToInt

@Composable
fun AppearanceScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOpenScale: () -> Unit,
    onPreviewLaunch: () -> Unit = {}, onOpenFont: () -> Unit = {}) {
    AppearanceSettingsPage(viewModel, onBack, onOpenScale, onPreviewLaunch, onOpenFont)
}

// Hosted outside NavDisplay entries so a back gesture dismisses the popup first.
@Composable
fun ScaleDialog(show: Boolean, viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    SettingsUiScope {
    val config by viewModel.settings.collectAsStateWithLifecycle()
    OverlayDialog(show = show, title = "显示缩放", summary = "80% - 120%", onDismissRequest = onDismiss) {
        var input by remember(show) { mutableStateOf((config.uiScale * 100).roundToInt().toString()) }
        TextField(value = input, onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) input = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        MusicDialogActions(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MusicDialogButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
            MusicDialogButton("确定", enabled = input.toIntOrNull() in 80..120, onClick = {
                input.toIntOrNull()?.let { value -> viewModel.edit { it.copy(uiScale = value.coerceIn(80, 120) / 100f) } }
                onDismiss()
            }, modifier = Modifier.weight(1f))
        }
    }
    }
}

@Composable
internal fun SettingIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(icon, contentDescription = null, modifier = (if(LocalSettingsComponents.current)Modifier else Modifier.padding(end=12.dp)).size(24.dp))
}
