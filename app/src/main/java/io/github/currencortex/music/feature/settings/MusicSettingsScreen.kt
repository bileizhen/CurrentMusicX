package io.github.currencortex.music.feature.settings

import io.github.currencortex.music.ui.component.musicScrollPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.config.ServerDefaults
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.ui.component.MusicDestinationRow
import io.github.currencortex.music.ui.component.MusicSectionHeader
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.SettingsSwitch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.github.currencortex.music.data.settings.AudioProvider

@Composable fun MusicSettingsScreen(vm: MusicSettingsViewModel, onStorage: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val cacheBytes by vm.cacheBytes.collectAsStateWithLifecycle()
    val provider by vm.audioProvider.collectAsStateWithLifecycle()
    val audioState by vm.audioState.collectAsStateWithLifecycle()
    // Credentials must not be saved in Bundle/saved instance state.
    var audioKey by remember { mutableStateOf("") }
    var qualityOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.refreshCache() }
    var server by rememberSaveable(settings.server) { mutableStateOf(settings.server) }
    val colors = MiuixTheme.colorScheme
    val hint = colors.onSurfaceVariantSummary
    // No back button: the navigation layer owns system/predictive back for pushed routes.
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("network_settings"),
        contentPadding = musicScrollPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("网络与播放", fontSize = 28.sp) }

        item { MusicSectionHeader("音乐音源") }
        item { Card { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("搜索、歌词、歌单与 MV 直接连接网易云；歌曲音源继续使用 CurrentMusic，用于播放、下载和投屏。CurrentMusic 账户与房间保留原服务。",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp, color = hint)
            AudioProvider.entries.forEach { entry ->
                MusicDestinationRow((if (provider.provider == entry) "✓ " else "") + entry.label,
                    { vm.audioProvider(entry) }, Modifier.testTag("audio_provider_${entry.name.lowercase()}"),
                    chevron = false, enabled = !audioState.busy)
            }
        } } }
        // The key only matters for LeiZ, so the card appears with that source and hides with the others.
        if (provider.provider == AudioProvider.LEIZ) item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("LeiZ API Key")
            Text(if (provider.keyConfigured) "已保存，加密存放于本机" else "尚未设置，需要填入后才能使用 LeiZ 音源",
                fontSize = 12.sp, color = hint)
            // Field and actions share one row so the server card below stays clear of the mini player.
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextField(audioKey, { audioKey = it }, label = if (provider.keyConfigured) "替换 API Key" else "API Key",
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.weight(1f).testTag("audio_key_input"))
                MusicTextAction("保存", { vm.audioKey(audioKey); audioKey = "" },
                    Modifier.testTag("audio_key_save"), enabled = audioKey.isNotBlank() && !audioState.busy)
                if (provider.keyConfigured) MusicTextAction("移除", { vm.clearAudioKey(); audioKey = "" },
                    Modifier.testTag("audio_key_clear"), enabled = !audioState.busy, destructive = true)
            }
            Text("LeiZ 自动请求超清母带，实际音质以接口返回为准。", fontSize = 12.sp, color = hint)
            audioState.message?.let { Text(it, fontSize = 13.sp, color = if (audioState.busy) hint else colors.error) }
        } } }

        item { MusicSectionHeader("CurrentMusic 服务器") }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(server, { server = it }, singleLine = true, label = "服务器地址", modifier = Modifier.testTag("server_input"))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MusicTextAction("保存", { vm.server(server) }, Modifier.testTag("save_server"), enabled = !state.busy)
                MusicTextAction("测试连接", { vm.test() }, enabled = !state.busy)
                MusicTextAction("恢复默认", { server = ServerDefaults.URL; vm.server(server) }, enabled = !state.busy)
            }
            Text("修改服务器会清除当前登录状态，需要重新登录。", fontSize = 12.sp, color = hint)
            state.message?.let { Text(it, fontSize = 13.sp, color = if (state.busy) hint else colors.error) }
        } } }

        item { MusicSectionHeader("预加载") }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsSwitch("预加载下一首", settings.preloadAudio, vm::preload,
                summary = "当前歌曲正常播放后，在非计费网络提前加载", modifier = Modifier.testTag("preload_audio"))
            SettingsSwitch("允许计费网络预加载", settings.preloadMetered, vm::preloadMetered,
                summary = "包括移动网络，会额外消耗流量", modifier = Modifier.testTag("preload_metered"))
            Text("播放时自动缓存，不设上限；完整缓存的歌曲在断网时仍可重播。",
                fontSize = 12.sp, color = hint)
        } } }
        // Cache sizes and clearing live on the storage page so the destructive action exists once.
        item { Card { MusicDestinationRow("存储空间", onStorage, Modifier.testTag("open_storage"),
            summary = "已缓存 ${String.format(java.util.Locale.ROOT, "%.1f", cacheBytes / 1048576.0)} MB") } }

        item { MusicSectionHeader("播放") }
        item { Card { MusicDestinationRow("默认音质", { qualityOpen = true },
            Modifier.testTag("default_quality"), summary = settings.quality.label) } }
        item { Card {
            SettingsSwitch("高规格音频提醒", settings.warnHighSpec, vm::warning,
                summary = "音源超出当前音质档位时先询问")
            SettingsSwitch("恢复播放队列", settings.restoreQueue, vm::restore,
                summary = "重新打开应用时回到上次的播放位置")
        } }
    }
    if (qualityOpen) io.github.currencortex.music.feature.player.AudioQualitySheet(settings.quality, vm::quality,
        { qualityOpen = false }, title = "默认播放音质")
}
