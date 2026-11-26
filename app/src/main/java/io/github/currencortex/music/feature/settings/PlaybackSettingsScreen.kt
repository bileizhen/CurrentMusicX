package io.github.currencortex.music.feature.settings

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.PlaybackNetworkKind
import io.github.currencortex.music.data.settings.ListeningSource
import io.github.currencortex.music.data.settings.LyricsStyle
import io.github.currencortex.music.data.settings.MusicPlatform
import io.github.currencortex.music.data.settings.MusicTransitionMode
import io.github.currencortex.music.data.settings.VolumeNormalizationTarget
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.feature.player.AudioQualitySheet
import io.github.currencortex.music.feature.player.qqAudioQualities
import io.github.currencortex.music.feature.player.qualityLabel
import io.github.currencortex.music.ui.component.AppleMusicLyricsControls
import io.github.currencortex.music.ui.component.LyricsSizeSlider
import io.github.currencortex.music.ui.component.LyricsStyleSelector
import io.github.currencortex.music.ui.component.MusicDestinationRow
import io.github.currencortex.music.ui.component.SettingsSwitch
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.currencortex.music.ui.component.nativeui.SettingsDialog
import io.github.suqi8.coui.kmp.basic.Slider
import kotlin.math.roundToInt

@Composable fun PlaybackSettingsScreen(vm: MusicSettingsViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val audioOutput by vm.audioOutput.collectAsStateWithLifecycle()
    val normalizationRuntime by vm.volumeNormalizationRuntime.collectAsStateWithLifecycle()
    val isQq = settings.musicPlatform == MusicPlatform.QQ_MUSIC
    val qualitySource = if (isQq) MusicSource.QQ_MUSIC else null
    var mobileQualityOpen by rememberSaveable { mutableStateOf(false) }
    var wirelessQualityOpen by rememberSaveable { mutableStateOf(false) }
    var downloadQualityOpen by rememberSaveable { mutableStateOf(false) }
    var appleLyricsOpen by rememberSaveable { mutableStateOf(false) }
    var seconds by remember(settings.crossfadeSeconds) { mutableFloatStateOf(settings.crossfadeSeconds.toFloat()) }
    val modes = mapOf(MusicTransitionMode.OFF to "关闭", MusicTransitionMode.SMART to "智能过渡", MusicTransitionMode.CROSSFADE to "淡入淡出")
    SettingsPage("播放", "playback_settings") {
        item("quality") { SettingsSection("音质") {
            Column {
                MusicDestinationRow("移动网络播放", { mobileQualityOpen = true },
                    Modifier.testTag("mobile_playback_quality"),
                    summary = settings.qualityFor(qualitySource).qualityLabel(isQq))
                SettingsDivider()
                MusicDestinationRow("无线网络播放", { wirelessQualityOpen = true },
                    Modifier.testTag("wireless_playback_quality"),
                    summary = settings.qualityFor(qualitySource, PlaybackNetworkKind.WIRELESS).qualityLabel(isQq))
                SettingsDivider()
                MusicDestinationRow("下载", { downloadQualityOpen = true },
                    Modifier.testTag("download_quality"),
                    summary = settings.downloadQualityFor(qualitySource).qualityLabel(isQq))
            }
        } }
        item("volume_normalization") { SettingsSection("音量均衡",
            footer = "平衡不同歌曲间的响度差异；支持内嵌响度标签、网易云/自建曲库元数据以及无标签歌曲实时自适应均衡。") {
            SettingsSwitch("开启音量均衡", settings.volumeNormalizationEnabled, vm::volumeNormalizationEnabled,
                summary = "平衡不同歌曲响度，避免切歌时声音忽大忽小",
                modifier = Modifier.testTag("volume_normalization_enabled"))
            AnimatedVisibility(settings.volumeNormalizationEnabled,
                enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    if (audioOutput.mode.bypassProcessing) {
                        SettingsHint("当前音频输出模式（${audioOutput.mode.label}）已跳过音频处理，音量均衡暂不生效",
                            modifier = Modifier.testTag("volume_normalization_bypass"))
                    }
                    SettingsDivider()
                    SettingsOptionsRow("目标响度", settings.volumeNormalizationTarget.label,
                        VolumeNormalizationTarget.entries.map { target ->
                            SettingsOption("volume_norm_target_${target.name.lowercase()}", target.label,
                                settings.volumeNormalizationTarget == target, summary = target.summary,
                                onSelect = { vm.volumeNormalizationTarget(target) })
                        }, Modifier.testTag("volume_normalization_target"))
                    SettingsDivider()
                    SettingsSwitch("无标签歌曲自适应均衡", settings.volumeNormalizationAdaptive,
                        vm::volumeNormalizationAdaptive,
                        summary = "缺少响度标签时实时测算并缓存，覆盖全部音源",
                        modifier = Modifier.testTag("volume_normalization_adaptive"))
                    SettingsDivider()
                    SettingsSwitch("优先使用专辑响度", settings.volumeNormalizationAlbumGain,
                        vm::volumeNormalizationAlbumGain,
                        summary = "支持的内嵌标签或 Navidrome 曲库保留整张专辑的原始强弱对比",
                        modifier = Modifier.testTag("volume_normalization_album_gain"))
                    if (!audioOutput.mode.bypassProcessing &&
                        (normalizationRuntime.active || normalizationRuntime.message != "未开启")) {
                        SettingsDivider()
                        SettingsPreference("当前状态", normalizationRuntime.message,
                            modifier = Modifier.testTag("volume_normalization_status"))
                    }
                }
            }
        } }
        item("download_files") { SettingsSection("下载文件",
            footer = "歌词与封面始终写入歌曲文件；关闭后不再生成同名的独立文件，下载时也可临时调整。") {
            SettingsSwitch("保存歌词文件", settings.downloadLyricsFile, vm::downloadLyricsFile,
                summary = "另存同名 LRC 文件", modifier = Modifier.testTag("download_lyrics_file"))
            SettingsDivider()
            SettingsSwitch("保存封面文件", settings.downloadCoverFile, vm::downloadCoverFile,
                summary = "另存同名封面图片", modifier = Modifier.testTag("download_cover_file"))
        } }
        item("listening") { SettingsSection("听歌足迹") {
            Column(Modifier.testTag("listening_source_setting").semantics { selectableGroup() }) {
                ListeningSource.entries.forEachIndexed { index, source ->
                    if(index > 0) SettingsDivider()
                    SettingsChoiceRow(source.label, settings.listeningSource == source,
                        { vm.listeningSource(source) }, Modifier.testTag("listening_source_${source.name.lowercase()}"),
                        summary = if(source == ListeningSource.LOCAL) "统计这台设备在 CurrentMusicX 中的播放记录"
                            else "查看已绑定网易云账号的云端听歌足迹")
                }
            }
        } }
        item("lyrics") { SettingsSection("歌词样式") {
            LyricsStyleSelector(settings.lyricsDisplay.style, vm::lyricsStyle)
            if (settings.lyricsDisplay.style == LyricsStyle.APPLE_MUSIC) {
                SettingsDivider()
                MusicDestinationRow("Apple Music 自定义", { appleLyricsOpen = true },
                    Modifier.testTag("open_apple_lyrics_settings"), summary = "字号、间距、动画速度与光效")
            }
        } }
        item("bluetooth_lyrics") { SettingsSection("蓝牙歌词") {
            SettingsSwitch("显示蓝牙歌词", settings.bluetoothLyrics, vm::bluetoothLyrics,
                summary = "在系统媒体卡片、锁屏和蓝牙车机显示当前歌词，关闭后显示歌名",
                modifier = Modifier.testTag("bluetooth_lyrics"))
        } }
        item("lyricon_lyrics") { SettingsSection("词幕状态栏歌词") {
            SettingsSwitch("向词幕推送歌词", settings.lyriconStatusBar, vm::lyriconStatusBar,
                summary = "配合词幕（Lyricon）在状态栏显示逐字歌词；需安装词幕并启用模块",
                modifier = Modifier.testTag("lyricon_status_bar_lyrics"))
        } }
        item("transition") { SettingsSection("歌曲衔接") {
            Column(Modifier.testTag("playback_transition").semantics { selectableGroup() }) {
                modes.entries.forEachIndexed { index, (mode, title) ->
                    if (index > 0) SettingsDivider()
                    SettingsChoiceRow(title, settings.transitionMode == mode, { vm.transition(mode) },
                        Modifier.testTag(when (mode) {
                            MusicTransitionMode.OFF -> "transition_off"
                            MusicTransitionMode.SMART -> "smart_transition"
                            MusicTransitionMode.CROSSFADE -> "crossfade"
                        }))
                }
            }
            AnimatedVisibility(settings.transitionMode == MusicTransitionMode.CROSSFADE,
                enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                SettingsPreference("过渡时长", "${seconds.roundToInt()} 秒", footer = {
                    Slider(seconds, { seconds = it.roundToInt().toFloat() }, valueRange = 1f..12f,
                        onValueChangeFinished = { vm.crossfadeSeconds(seconds.roundToInt()) },
                        modifier = Modifier.fillMaxWidth().testTag("crossfade_duration").semantics {
                            setProgress { value ->
                                if (!value.isFinite()) false else {
                                    seconds = value.roundToInt().coerceIn(1, 12).toFloat()
                                    vm.crossfadeSeconds(seconds.roundToInt()); true
                                }
                            }
                        })
                })
            }
        } }
        item("background") { SettingsSection("播放行为") {
            SettingsSwitch("允许与其他应用同时播放", settings.allowConcurrentPlayback, vm::concurrentPlayback,
                summary = "允许音乐与其他应用的声音同时播放", modifier = Modifier.testTag("allow_concurrent_playback"))
            SettingsDivider()
            SettingsSwitch("恢复播放队列", settings.restoreQueue, vm::restore,
                summary = "重新打开应用时回到上次的播放位置", modifier = Modifier.testTag("restore_queue"))
        } }
        item("preload") { SettingsSection("预加载") {
            SettingsSwitch("预加载下一首", settings.preloadAudio, vm::preload, modifier = Modifier.testTag("preload_audio"))
            SettingsDivider()
            SettingsSwitch("允许计费网络预加载", settings.preloadMetered, vm::preloadMetered,
                summary = "包括移动网络，会额外消耗流量", enabled = settings.preloadAudio, modifier = Modifier.testTag("preload_metered"))
        } }
    }
    val available = if (isQq) {
        val provider by vm.audioProvider.collectAsStateWithLifecycle()
        val sources by vm.lxSources.collectAsStateWithLifecycle()
        qqAudioQualities(provider, sources)
    } else null
    if (mobileQualityOpen) AudioQualitySheet(settings.qualityFor(qualitySource), vm::mobileQuality,
        { mobileQualityOpen = false }, title = "移动网络播放音质", isQq = isQq, available = available)
    if (wirelessQualityOpen) AudioQualitySheet(settings.qualityFor(qualitySource, PlaybackNetworkKind.WIRELESS),
        vm::wirelessQuality, { wirelessQualityOpen = false }, title = "无线网络播放音质", isQq = isQq, available = available)
    if (downloadQualityOpen) AudioQualitySheet(settings.downloadQualityFor(qualitySource), vm::downloadQuality,
        { downloadQualityOpen = false }, title = "下载音质", isQq = isQq, available = available)
    SettingsDialog(
        show = appleLyricsOpen,
        title = "Apple Music 歌词",
        onDismissRequest = { appleLyricsOpen = false },
        positiveText = "完成",
        onPositive = { appleLyricsOpen = false },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            .testTag("apple_lyrics_settings")) {
            LyricsSizeSlider(settings.lyricsFontSize, vm::lyricsFontSize)
            AppleMusicLyricsControls(settings.lyricsDisplay, vm::lyricsDisplay)
        }
    }
}
