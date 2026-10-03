package io.github.currencortex.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Switch

@Composable
fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    startAction: (@Composable () -> Unit)? = null,
) {
    BasicComponent(
        modifier = modifier.semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off },
        title = title,
        startAction = startAction,
        summary = summary,
        enabled = enabled,
        role = Role.Switch,
        onClick = { onCheckedChange(!checked) },
        endActions = { Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange) },
    )
}
