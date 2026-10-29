package io.github.currencortex.music.feature.settings

import io.github.currencortex.music.ui.component.MusicChoiceDialog
import io.github.currencortex.music.ui.component.MusicDialogChoiceRow

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
import io.github.currencortex.music.data.settings.MusicTransitionMode
import io.github.currencortex.music.data.settings.LyricsSource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import kotlin.math.roundToInt

@Composable fun MusicSettingsScreen(vm: MusicSettingsViewModel, onEqualizer: () -> Unit = {}, onNavidrome: () -> Unit = {}, onDesktopLyrics: () -> Unit = {}, onStorage: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val cacheBytes by vm.cacheBytes.collectAsStateWithLifecycle()
    val provider by vm.audioProvider.collectAsStateWithLifecycle()
    val audioState by vm.audioState.collectAsStateWithLifecycle()
    // Credentials must not be saved in Bundle/saved instance state.
    var audioKey by remember { mutableStateOf("") }
    var qualityOpen by rememberSaveable { mutableStateOf(false) }
    var lyricsSourceOpen by rememberSaveable { mutableStateOf(false) }
    var crossfadeSeconds by remember(settings.crossfadeSeconds) { mutableFloatStateOf(settings.crossfadeSeconds.toFloat()) }
    LaunchedEffect(vm) { vm.refreshCache() }
    var server by rememberSaveable(settings.server) { mutableStateOf(settings.server) }
    val colors = MiuixTheme.colorScheme
    val hint = colors.onSurfaceVariantSummary
    // No back button: the navigation layer owns system/predictive back for pushed routes.
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("network_settings"),
        contentPadding = musicScrollPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("网络与播放", fontSize = 28.sp) }

        item { Card { MusicDestinationRow("Navidrome 自建曲库", onNavidrome, Modifier.testTag("open_navidrome"),
            summary = "连接自己的音乐服务器 · Subsonic") } }
        item { MusicSectionHeader("音乐音源") }
        item { Card { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("网易云原生：歌曲播放、下载和 MV 由手机直连网易云；音质与可播放范围由本机网易云账号权限决定。歌词以网易云歌词为参考，先校验内容与原始时间轴，再从合格候选中优先采用内容匹配度 ≥75% 的真实逐字，符合条件的 AMLL 优先。列表百分比仅代表歌曲信息匹配，也可手动指定来源。CurrentMusic 账号、公共聊天与本站房间仍使用原服务。",
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

        item { MusicSectionHeader("歌词") }
        item { Card { MusicDestinationRow("歌词来源", { lyricsSourceOpen = true },
            Modifier.testTag("lyrics_source"), summary = settings.lyricsSource.label) } }

        item { MusicSectionHeader("CurrentMusic 服务器") }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(server, { server = it }, singleLine = true, label = "服务器地址", modifier = Modifier.testTag("server_input"))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MusicTextAction("保存", { vm.server(server) }, Modifier.testTag("save_server"), enabled = !state.busy)
                MusicTextAction("测试连接", { vm.test() }, enabled = !state.busy)
                MusicTextAction("恢复默认", { server = ServerDefaults.URL; vm.server(server) }, enabled = !state.busy)
            }
            Text(if (provider.provider == AudioProvider.NETEASE) "网易云原生音源不使用此地址；CurrentMusic 歌词及本站功能仍使用它。修改地址会清除站内登录状态。"
                else "修改服务器会清除当前登录状态，需要重新登录。", fontSize = 12.sp, color = hint)
            state.message?.let { Text(it, fontSize = 13.sp, color = if (state.busy) hint else colors.error) }
        } } }

        item { MusicSectionHeader("红心与点赞") }
        item { Card { Column(Modifier.padding(vertical = 8.dp)) {
            Text("默认红心位置", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 16.sp)
            io.github.currencortex.music.data.settings.LikeMode.entries.forEach { mode ->
                MusicDestinationRow((if (mode == settings.likeMode) "✓ " else "") + mode.label, { vm.likeMode(mode) },
                    Modifier.testTag("like_mode_${mode.name}"), chevron = false, summary = when (mode) {
                        io.github.currencortex.music.data.settings.LikeMode.AUTO -> "已绑定网易云时同步到网易云，否则存入 CurrentMusic"
                        io.github.currencortex.music.data.settings.LikeMode.NETEASE -> "未绑定网易云时回退到 CurrentMusic"
                        io.github.currencortex.music.data.settings.LikeMode.CURRENT_MUSIC -> "仅存入本站，不影响网易云账号"
                    })
            }
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
        item { Card {
            SettingsSwitch("智能过渡", settings.transitionMode == MusicTransitionMode.SMART,
                { vm.transition(if (it) MusicTransitionMode.SMART else MusicTransitionMode.OFF) },
                summary = "智能分析音乐，让歌曲丝滑衔接，让听感更流畅",
                modifier = Modifier.testTag("smart_transition"))
            SettingsSwitch("淡入淡出", settings.transitionMode == MusicTransitionMode.CROSSFADE,
                { vm.transition(if (it) MusicTransitionMode.CROSSFADE else MusicTransitionMode.OFF) },
                summary = "上一首声音渐弱，下一首声音渐强，交叉过渡",
                modifier = Modifier.testTag("crossfade"))
            if (settings.transitionMode == MusicTransitionMode.CROSSFADE) Column(Modifier.padding(horizontal = 16.dp)) {
                Text("过渡时长 · ${crossfadeSeconds.roundToInt()} 秒", fontSize = 13.sp, color = hint)
                Slider(value = crossfadeSeconds, valueRange = 1f..12f,
                    onValueChange = { crossfadeSeconds = it.roundToInt().toFloat() },
                    onValueChangeFinished = { vm.crossfadeSeconds(crossfadeSeconds.roundToInt()) },
                    modifier = Modifier.fillMaxWidth().testTag("crossfade_duration").semantics {
                        setProgress { value ->
                            crossfadeSeconds = value.roundToInt().coerceIn(1, 12).toFloat()
                            vm.crossfadeSeconds(crossfadeSeconds.roundToInt()); true
                        }
                    })
            }
            Text("两种过渡择一开启，自动切歌时生效。", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp, color = hint)
        } }
        item { Card {
            SettingsSwitch("允许与其他应用同时播放", settings.allowConcurrentPlayback, vm::concurrentPlayback,
                summary = "允许音乐与其他应用的声音同时播放，立即生效",
                modifier = Modifier.testTag("allow_concurrent_playback"))
        } }
        item { Card { MusicDestinationRow("桌面悬浮歌词", onDesktopLyrics, Modifier.testTag("open_desktop_lyrics"), summary = "后台同步 · 精致与舞台效果 · 触摸穿透") } }
        item { Card { MusicDestinationRow("音效", onEqualizer, Modifier.testTag("open_equalizer"),
            summary = "网易云分析 · 智能调音 · 均衡器") } }
        item { Card { MusicDestinationRow("默认音质", { qualityOpen = true },
            Modifier.testTag("default_quality"), summary = settings.quality.label) } }
        item { Card {
            SettingsSwitch("恢复播放队列", settings.restoreQueue, vm::restore,
                summary = "重新打开应用时回到上次的播放位置")
        } }
    }
    if (qualityOpen) io.github.currencortex.music.feature.player.AudioQualitySheet(settings.quality, vm::quality,
        { qualityOpen = false }, title = "默认播放音质")
    if (lyricsSourceOpen) io.github.currencortex.music.ui.component.MusicChoiceDialog("歌词来源", { lyricsSourceOpen = false },
        footer = { io.github.currencortex.music.ui.component.MusicDialogButton("关闭", { lyricsSourceOpen = false }) }) {
        LyricsSource.entries.forEach { source ->
            MusicDialogChoiceRow(source.label, source == settings.lyricsSource,
                { vm.lyricsSource(source); lyricsSourceOpen = false },
                Modifier.testTag("lyrics_source_${source.name.lowercase()}"),
                summary = when (source) {
                    LyricsSource.AUTO -> "通过内容与原始时间轴校验后才自动替换；合格逐字优先，AMLL 优先"
                    LyricsSource.CURRENT_MUSIC -> "由 CurrentMusic 服务提供，支持 LDDC 真实逐字；过滤估算时间轴"
                    LyricsSource.NETEASE -> "手机直连网易云，使用原生逐字或逐行歌词"
                    LyricsSource.LOCAL -> "离线读取本地音频文件内的歌词；独立 LRC / TTML 文件可在歌词管理中导入"
                })
        }
        Text("独立于音乐音源。单曲手动导入或匹配优先，本地歌曲优先读取内嵌歌词。切换默认策略后重新获取歌词。", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontSize = 12.sp, color = hint)
    }
}
