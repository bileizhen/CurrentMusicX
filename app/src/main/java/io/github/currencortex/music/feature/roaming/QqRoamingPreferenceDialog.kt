package io.github.currencortex.music.feature.roaming

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.appResult
import io.github.currencortex.music.data.qqmusic.QqPreferenceGroup
import io.github.currencortex.music.data.qqmusic.QqPreferenceOption
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicDialogButton
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.nativeui.SettingsChoiceRow
import io.github.currencortex.music.ui.component.nativeui.SettingsDivider
import io.github.currencortex.music.ui.component.nativeui.SettingsGroup
import io.github.currencortex.music.ui.component.nativeui.SettingsHint
import io.github.currencortex.music.ui.component.nativeui.SettingsUiScope
import kotlinx.coroutines.launch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** QQ Music "我的音乐偏好": heat / mood / familiarity, stored server-side per account. */
@Composable internal fun QqRoamingPreferenceDialog(container: AppContainer, onDismiss: () -> Unit) {
    var groups by remember { mutableStateOf<List<QqPreferenceGroup>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        when (val result = appResult { container.qqMusic.radioPreferences() }) {
            is AppResult.Success -> groups = result.value
            is AppResult.Failure -> error = result.message
        }
    }

    fun select(groupIndex: Int, option: QqPreferenceOption) {
        val current = groups ?: return
        val updated = current.mapIndexed { index, group ->
            if (index != groupIndex) group else group.copy(options = group.options.map {
                it.copy(status = if (it.id == option.id) 1 else 0)
            })
        }
        groups = updated
        dirty = true
    }

    SettingsUiScope {
        MusicDialog("我的音乐偏好", onDismiss, footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (dirty) MusicTextAction("重置", {
                    scope.launch {
                        val loaded = groups ?: return@launch
                        val reset = loaded.map { group -> group.copy(options = group.options.map {
                            it.copy(status = if (it.id == "0") 1 else 0) }) }
                        busy = true
                        when (val result = appResult { container.qqMusic.saveRadioPreferences(reset) }) {
                            is AppResult.Success -> { groups = reset; dirty = false; onDismiss() }
                            is AppResult.Failure -> error = result.message
                        }
                        busy = false
                    }
                }, Modifier.weight(1f).testTag("qq_preference_reset"), enabled = !busy)
                MusicDialogButton(if (busy) "保存中…" else "保存", {
                    val loaded = groups ?: return@MusicDialogButton
                    scope.launch {
                        busy = true
                        when (val result = appResult { container.qqMusic.saveRadioPreferences(loaded) }) {
                            is AppResult.Success -> onDismiss()
                            is AppResult.Failure -> error = result.message
                        }
                        busy = false
                    }
                }, Modifier.weight(1f).testTag("qq_preference_save"), enabled = !busy && groups != null)
            }
        }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                .testTag("qq_preference_dialog"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("调节偏好后，猜你喜欢会按新比例推荐歌曲。", fontSize = 12.sp,
                    color = COUITheme.colorScheme.labelSecondaryVariant)
                when {
                    error != null -> SettingsHint(error.orEmpty())
                    groups == null -> Text("正在加载…", fontSize = 13.sp)
                    groups!!.isEmpty() -> SettingsHint("当前账号暂无可调偏好")
                    else -> groups!!.forEachIndexed { index, group ->
                        Text(group.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        SettingsGroup {
                            group.options.forEachIndexed { optionIndex, option ->
                                SettingsChoiceRow(option.name, option.status == 1,
                                    { select(index, option) },
                                    Modifier.testTag("qq_preference_${group.type}_${option.id}"),
                                    enabled = !busy)
                                if (optionIndex != group.options.lastIndex) SettingsDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
