package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.song.ServerAudioSource
import io.github.currencortex.music.ui.component.MusicChoiceDialog
import io.github.currencortex.music.ui.component.MusicDialogButton
import io.github.currencortex.music.ui.component.MusicDialogChoiceRow
import io.github.currencortex.music.ui.component.nativeui.SettingsHint

@Composable
internal fun ServerAudioSourceDialog(state: ServerAudioCatalogState, onDismiss: () -> Unit, onRetry: () -> Unit,
    onImport: (ServerAudioSource) -> Unit, onLogin: () -> Unit) {
    var selection by remember(state.sources) { mutableStateOf<ServerAudioSource?>(null) }
    MusicChoiceDialog("从服务器导入", onDismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicDialogButton("取消", onDismiss, Modifier.weight(1f))
            when {
                state.needsLogin -> MusicDialogButton("登录", onLogin, Modifier.weight(1f))
                state.sources.isNotEmpty() -> MusicDialogButton("导入并使用", { selection?.let(onImport) },
                    Modifier.weight(1f).testTag("server_audio_confirm"), enabled = selection != null)
                !state.busy -> MusicDialogButton("重新加载", onRetry, Modifier.weight(1f))
            }
        }
    }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            SettingsHint("提供方 · ${state.server}")
            if (state.busy) SettingsHint("正在加载音源…")
            state.message?.let { SettingsHint(it) }
            for (source in state.sources) {
                MusicDialogChoiceRow(source.name, selection == source, onClick = { selection = source },
                    modifier = Modifier.testTag("server_audio_source_${source.id}"), summary = "支持网易云曲库")
            }
        }
    }
}
