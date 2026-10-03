package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.config.ServerDefaults
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.ui.component.SettingsSwitch
import top.yukonga.miuix.kmp.basic.*

@Composable fun MusicSettingsScreen(vm: MusicSettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var server by rememberSaveable(settings.server) { mutableStateOf(settings.server) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("network_settings"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { TextButton("返回", onClick = onBack, modifier = Modifier.testTag("navigate_back")); Text("网络与播放") }
        item {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CurrentMusic Server")
                TextField(server, { server = it }, singleLine = true, modifier = Modifier.testTag("server_input"))
                Text("修改服务器会清除当前登录状态，请重新登录。")
                TextButton("保存服务器", onClick = { vm.server(server) }, enabled = !state.busy)
                TextButton("测试连接", onClick = { vm.test() }, enabled = !state.busy)
                TextButton("恢复默认", onClick = { server = ServerDefaults.URL; vm.server(server) }, enabled = !state.busy)
                state.message?.let { Text(it) }
            } }
        }
        item { Text("默认音质") }
        AudioQuality.entries.forEach { quality -> item {
            TextButton((if (settings.quality == quality) "✓ " else "") + quality.label, onClick = { vm.quality(quality) })
        } }
        item { SettingsSwitch("高规格音频提醒", settings.warnHighSpec, vm::warning) }
        item { SettingsSwitch("恢复播放队列", settings.restoreQueue, vm::restore) }
    }
}
