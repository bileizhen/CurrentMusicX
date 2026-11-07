package io.github.currencortex.music.feature.settings

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.network.CurrentMusicServer
import io.github.currencortex.music.data.settings.AudioProvider
import io.github.currencortex.music.data.settings.LyricsSource
import io.github.currencortex.music.ui.component.MusicDestinationRow
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.TextField

/** Sources and connections only; playback and other settings each have one owner. */
@Composable fun MusicSettingsScreen(vm: MusicSettingsViewModel, onNavidrome: () -> Unit = {}, onEmby: () -> Unit = {}) {
    SettingsUiScope {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val provider by vm.audioProvider.collectAsStateWithLifecycle()
    val audioState by vm.audioState.collectAsStateWithLifecycle()
    val automatic by vm.automaticServer.collectAsStateWithLifecycle()
    // Never save a credential in a Bundle.
    var audioKey by remember { mutableStateOf("") }
    var serverDialogOpen by rememberSaveable { mutableStateOf(false) }
    var customServerOpen by rememberSaveable { mutableStateOf(false) }
    SettingsPage("音源与网络", "network_settings") {
        item("source") { SettingsSection("音乐音源", MusicIcons.Headphones) {
            SettingsOptionsRow("音源", provider.provider.label, AudioProvider.entries.map { entry ->
                SettingsOption("audio_provider_${entry.name.lowercase()}", entry.label, provider.provider == entry,
                    onSelect = { audioKey = ""; vm.audioProvider(entry) })
            }, Modifier.testTag("audio_provider"), enabled = !audioState.busy)
            AnimatedVisibility(provider.provider == AudioProvider.LEIZ,
                enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsHint(if (provider.keyConfigured) "API Key 已加密保存在本机" else "填入 API Key 后使用 LeiZ 音源")
                    TextField(audioKey, { audioKey = it }, label = if (provider.keyConfigured) "替换 API Key" else "API Key",
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("audio_key_input"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MusicTextAction("保存", { vm.audioKey(audioKey); audioKey = "" }, Modifier.testTag("audio_key_save"),
                            enabled = audioKey.isNotBlank() && !audioState.busy)
                        if (provider.keyConfigured) MusicTextAction("移除", { vm.clearAudioKey(); audioKey = "" },
                            Modifier.testTag("audio_key_clear"), enabled = !audioState.busy, destructive = true)
                    }
                }
            }
            if (provider.provider != AudioProvider.LX) audioState.message?.let { SettingsHint(it, error = !audioState.busy) }
        } }
        if (provider.provider == AudioProvider.LX) item("lx_sources") { LxSourceSettings(vm) }
        item("lyrics") { SettingsSection("歌词来源", MusicIcons.File) {
            SettingsOptionsRow("默认来源", when (settings.lyricsSource) {
                LyricsSource.AUTO -> "自动择优"
                LyricsSource.CURRENT_MUSIC -> "CurrentMusic"
                LyricsSource.NETEASE -> "网易云"
                LyricsSource.LOCAL -> "本地内嵌"
            }, LyricsSource.entries.map { source ->
                SettingsOption("lyrics_source_${source.name.lowercase()}", source.label, source == settings.lyricsSource,
                    summary = when (source) {
                        LyricsSource.AUTO -> "信息匹配 ≥75% · QQ 优先查找真实逐字"
                        LyricsSource.CURRENT_MUSIC -> "CurrentMusic · LDDC 真实逐字"
                        LyricsSource.NETEASE -> "手机直连网易云逐字或逐行歌词"
                        LyricsSource.LOCAL -> "离线读取本地音频文件内的歌词"
                    }, onSelect = { vm.lyricsSource(source) })
            }, Modifier.testTag("lyrics_source"))
        } }
        item("server") { SettingsSection("CurrentMusic 服务", MusicIcons.Storage) {
            MusicDestinationRow("服务器线路", { serverDialogOpen = true }, Modifier.testTag("server_source"),
                summary = when (settings.serverMode) {
                    CurrentMusicServer.AUTO -> "自动 · ${CurrentMusicServer.presets.firstOrNull { it.url == automatic }?.label.orEmpty()}"
                    CurrentMusicServer.CUSTOM -> "自定义 · ${settings.customServer}"
                    else -> settings.serverMode.label
                }, enabled = !state.busy)
            SettingsDivider()
            SettingsPreference(if (state.busy) "正在测试…" else "测试连接", state.message,
                Modifier.testTag("server_test"), enabled = !state.busy, onClick = { vm.test() })
        } }
        item("library") { SettingsSection("自建曲库", MusicIcons.List) {
            MusicDestinationRow("Navidrome", onNavidrome, Modifier.testTag("open_navidrome"),
                summary = "连接自己的音乐服务器 · Subsonic")
            SettingsDivider()
            MusicDestinationRow("Emby", onEmby, Modifier.testTag("open_emby"), summary = "专辑、歌曲、艺术家与歌单 · 原音质播放")
        } }
    }
    if (serverDialogOpen) CurrentMusicServerDialog(vm, onDismiss = { serverDialogOpen = false }, onCustom = {
        serverDialogOpen = false; customServerOpen = true
    })
    if (customServerOpen) CustomMusicServerDialog(settings.customServer, state, onSave = { vm.server(it) },
        onDismiss = { customServerOpen = false })
    }
}
