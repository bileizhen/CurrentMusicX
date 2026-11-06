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
import io.github.currencortex.music.data.settings.LikeMode
import io.github.currencortex.music.data.settings.MusicTransitionMode
import io.github.currencortex.music.ui.component.MusicDestinationRow
import io.github.currencortex.music.ui.component.LyricsStyleSelector
import io.github.currencortex.music.ui.component.AppleMusicLyricsControls
import io.github.currencortex.music.ui.component.LyricsSizeSlider
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.data.settings.LyricsStyle
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.SettingsSwitch
import io.github.currencortex.music.ui.component.nativeui.*
import kotlin.math.roundToInt

@Composable fun PlaybackSettingsScreen(vm: MusicSettingsViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var qualityOpen by rememberSaveable { mutableStateOf(false) }
    var appleLyricsOpen by rememberSaveable { mutableStateOf(false) }
    var seconds by remember(settings.crossfadeSeconds) { mutableFloatStateOf(settings.crossfadeSeconds.toFloat()) }
    val modes = mapOf(MusicTransitionMode.OFF to "关闭", MusicTransitionMode.SMART to "智能过渡", MusicTransitionMode.CROSSFADE to "淡入淡出")
    SettingsPage("播放", "playback_settings") {
        item("quality") { SettingsSection("音质", MusicIcons.Headphones) {
            MusicDestinationRow("默认音质", { qualityOpen = true }, Modifier.testTag("default_quality"), summary = settings.quality.label)
        } }
        item("lyrics") { SettingsSection("歌词样式", MusicIcons.File,
            footer = "字体、翻译、罗马音及歌词效果可在播放器的歌词显示选项中调整。") {
            LyricsStyleSelector(settings.lyricsDisplay.style, vm::lyricsStyle)
            if (settings.lyricsDisplay.style == LyricsStyle.APPLE_MUSIC) {
                SettingsDivider()
                MusicDestinationRow("Apple Music 自定义", { appleLyricsOpen = true },
                    Modifier.testTag("open_apple_lyrics_settings"), summary = "字号、间距、动画速度与光效")
            }
        } }
        item("transition") { SettingsSection("歌曲衔接", MusicIcons.Update, footer = "自动切歌时生效。智能过渡和淡入淡出仅能选择一种。") {
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
                    GlassSlider(seconds, { seconds = it.roundToInt().toFloat() }, valueRange = 1f..12f,
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
        item("background") { SettingsSection("播放行为", MusicIcons.Play) {
            SettingsSwitch("允许与其他应用同时播放", settings.allowConcurrentPlayback, vm::concurrentPlayback,
                summary = "允许音乐与其他应用的声音同时播放", modifier = Modifier.testTag("allow_concurrent_playback"))
            SettingsDivider()
            SettingsSwitch("恢复播放队列", settings.restoreQueue, vm::restore,
                summary = "重新打开应用时回到上次的播放位置", modifier = Modifier.testTag("restore_queue"))
        } }
        item("preload") { SettingsSection("预加载", MusicIcons.Download,
            footer = "正常播放后提前加载下一首。播放缓存默认最多 1 GiB，按最近使用情况自动回收；完整缓存可离线重播，下载文件不受影响。") {
            SettingsSwitch("预加载下一首", settings.preloadAudio, vm::preload, modifier = Modifier.testTag("preload_audio"))
            SettingsDivider()
            SettingsSwitch("允许计费网络预加载", settings.preloadMetered, vm::preloadMetered,
                summary = "包括移动网络，会额外消耗流量", enabled = settings.preloadAudio, modifier = Modifier.testTag("preload_metered"))
        } }
        item("likes") { SettingsSection("红心与点赞", MusicIcons.Heart,
            footer = "自动模式下，已绑定网易云则同步到网易云，否则存入 CurrentMusic。") {
            SettingsOptionsRow("默认红心位置", settings.likeMode.label, LikeMode.entries.map { mode ->
                SettingsOption("like_mode_${mode.name}", mode.label, mode == settings.likeMode,
                    summary = when (mode) {
                        LikeMode.AUTO -> "已绑定网易云则同步，否则使用 CurrentMusic"
                        LikeMode.NETEASE -> "未绑定网易云时使用 CurrentMusic"
                        LikeMode.CURRENT_MUSIC -> "仅存入本站，不影响网易云账号"
                    },
                    onSelect = { vm.likeMode(mode) })
            }, Modifier.testTag("like_mode"))
        } }
    }
    if (qualityOpen) io.github.currencortex.music.feature.player.AudioQualitySheet(settings.quality, vm::quality,
        { qualityOpen = false }, title = "默认播放音质")
    if (appleLyricsOpen) MusicDialog("Apple Music 歌词", { appleLyricsOpen = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            .testTag("apple_lyrics_settings")) {
            LyricsSizeSlider(settings.lyricsFontSize, vm::lyricsFontSize)
            AppleMusicLyricsControls(settings.lyricsDisplay, vm::lyricsDisplay)
        }
    }
}
