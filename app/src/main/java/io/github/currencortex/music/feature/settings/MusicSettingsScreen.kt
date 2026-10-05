package io.github.currencortex.music.feature.settings

import io.github.currencortex.music.ui.component.musicScrollPadding
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.github.currencortex.music.data.settings.AudioProvider

@Composable fun MusicSettingsScreen(vm: MusicSettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val cacheBytes by vm.cacheBytes.collectAsStateWithLifecycle()
    val cacheState by vm.cacheState.collectAsStateWithLifecycle()
    val provider by vm.audioProvider.collectAsStateWithLifecycle()
    val audioState by vm.audioState.collectAsStateWithLifecycle()
    // Credentials must not be saved in Bundle/saved instance state.
    var audioKey by remember { mutableStateOf("") }
    var qualityOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.refreshCache() }
    var server by rememberSaveable(settings.server) { mutableStateOf(settings.server) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("network_settings"),
        contentPadding = musicScrollPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { TextButton("返回", onClick = onBack, modifier = Modifier.testTag("navigate_back")); Text("网络与播放") }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("音乐音源")
            Text("只切换歌曲播放和投屏音源。搜索、歌词、歌单、账户、MV 与一起听仍使用 CurrentMusic。")
            AudioProvider.entries.forEach { entry ->
                TextButton((if (provider.provider == entry) "✓ " else "") + entry.label,
                    onClick = { vm.audioProvider(entry) }, enabled = !audioState.busy,
                    modifier = Modifier.testTag("audio_provider_${entry.name.lowercase()}"))
            }
            Text(if (provider.keyConfigured) "LeiZ API Key 已保存（加密）" else "LeiZ API Key 尚未设置")
            TextField(audioKey, { audioKey = it }, label = if (provider.keyConfigured) "替换 API Key" else "API Key",
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.testTag("audio_key_input"))
            TextButton("保存 Key", onClick = { vm.audioKey(audioKey); audioKey = "" },
                enabled = audioKey.isNotBlank() && !audioState.busy, modifier = Modifier.testTag("audio_key_save"))
            if (provider.keyConfigured) TextButton("移除 Key", onClick = { vm.clearAudioKey(); audioKey = "" },
                enabled = !audioState.busy, modifier = Modifier.testTag("audio_key_clear"))
            Text("LeiZ 自动音质请求超清母带，实际音质以接口返回为准。")
            audioState.message?.let { Text(it) }
        } } }
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
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("歌曲缓存与预加载")
            Text("已缓存 ${String.format(java.util.Locale.ROOT, "%.1f", cacheBytes / 1048576.0)} MB / 256 MB")
            Text("播放时自动缓存，容量满后清理较早使用的音频。下一首预加载前 2 MB，完整缓存的歌曲可在网络不可用时重播。")
            SettingsSwitch("预加载下一首", settings.preloadAudio, vm::preload,
                summary = "当前歌曲正常播放后，在非计费网络提前加载", modifier = Modifier.testTag("preload_audio"))
            SettingsSwitch("允许计费网络预加载", settings.preloadMetered, vm::preloadMetered,
                summary = "包括移动网络，会额外消耗流量", modifier = Modifier.testTag("preload_metered"))
            TextButton(if (cacheState.busy) "正在清理…" else "清理歌曲缓存", onClick = { vm.clearCache() },
                enabled = !cacheState.busy, modifier = Modifier.testTag("clear_audio_cache"))
            cacheState.message?.let { Text(it) }
        } } }
        item { Card {
            io.github.currencortex.music.ui.component.MusicDestinationRow("默认音质", { qualityOpen = true },
                Modifier.testTag("default_quality"), summary = settings.quality.label)
        } }
        item { SettingsSwitch("高规格音频提醒", settings.warnHighSpec, vm::warning) }
        item { SettingsSwitch("恢复播放队列", settings.restoreQueue, vm::restore) }
    }
    if (qualityOpen) io.github.currencortex.music.feature.player.AudioQualitySheet(settings.quality, vm::quality,
        { qualityOpen = false }, title = "默认播放音质")
}
