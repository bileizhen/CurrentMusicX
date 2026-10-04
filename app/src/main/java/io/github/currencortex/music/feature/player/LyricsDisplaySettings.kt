package io.github.currencortex.music.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.settings.LyricsTypography
import io.github.currencortex.music.data.settings.LyricsWeight
import io.github.currencortex.music.data.settings.LyricsDisplayOptions
import io.github.currencortex.music.feature.lyrics.ui.lyricsFontWeight
import io.github.currencortex.music.ui.component.MusicDestinationRow
import io.github.currencortex.music.feature.lyrics.ui.LyricsFontFamily
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable internal fun LyricsDisplaySettings(fontSize: Float, onFontSize: (Float) -> Unit, weightMode: LyricsWeight, onWeight: () -> Unit,
    display: LyricsDisplayOptions, onDisplay: ((LyricsDisplayOptions) -> LyricsDisplayOptions) -> Unit,
    onKaraokeScope: () -> Unit) {
    val ink = MiuixTheme.colorScheme.onSurface
    var preview by remember(fontSize) { mutableFloatStateOf(fontSize) }
    var strength by remember(display.fontWeight) { mutableIntStateOf(display.fontWeight) }
    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
        .testTag("lyrics_display_settings")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("让音乐说话", fontFamily = LyricsFontFamily, fontSize = preview.sp, fontWeight = lyricsFontWeight(weightMode, true, strength),
                color = ink, modifier = Modifier.testTag("lyrics_font_preview"))
            Text("霞鹜文楷", fontSize = 12.sp, color = ink.copy(alpha = .5f), modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("字号 ${preview.roundToInt()}", fontSize = 14.sp, color = ink, modifier = Modifier.weight(1f))
                Text("恢复默认", fontSize = 13.sp, color = ink.copy(alpha = .65f),
                    modifier = Modifier.testTag("lyrics_font_reset").clickable(role = Role.Button) {
                        preview = LyricsTypography.DEFAULT_SIZE; onFontSize(preview)
                    }.padding(horizontal = 8.dp, vertical = 12.dp))
            }
            Slider(value = preview, onValueChange = { preview = it.roundToInt().toFloat() },
                onValueChangeFinished = { onFontSize(preview) },
                valueRange = LyricsTypography.MIN_SIZE..LyricsTypography.MAX_SIZE,
                modifier = Modifier.testTag("lyrics_font_size").semantics {
                    contentDescription = "歌词字号"
                    // Miuix's SetProgress callback does not call onValueChangeFinished.
                    setProgress { target ->
                        preview = LyricsTypography.normalize(target).roundToInt().toFloat()
                        onFontSize(preview); true
                    }
                })
        }
        LyricsToggle("歌词居中对齐", display.centered, { value -> onDisplay { it.copy(centered = value) } }, "lyrics_centered")
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("字体粗细 $strength", fontSize = 14.sp, color = ink)
            Slider(value = strength.toFloat(), onValueChange = { strength = ((it / 100).roundToInt() * 100).coerceIn(400, 900) },
                onValueChangeFinished = { onDisplay { it.copy(fontWeight = strength) } }, valueRange = 400f..900f,
                modifier = Modifier.testTag("lyrics_font_strength").semantics {
                    contentDescription = "歌词字体粗细"
                    setProgress { target ->
                        if (!target.isFinite()) false else {
                            strength = ((target / 100).roundToInt() * 100).coerceIn(400, 900)
                            onDisplay { it.copy(fontWeight = strength) }; true
                        }
                    }
                })
            Text("400 常规 · 500 中等 · 600–900 合成加粗", fontSize = 11.sp, color = ink.copy(alpha = .5f))
        }
        MusicDestinationRow("歌词字重", summary = weightMode.label, onClick = onWeight, modifier = Modifier.testTag("open_lyrics_weight"))
        LyricsToggle("歌词视图模糊", display.blur, { value -> onDisplay { it.copy(blur = value) } }, "lyrics_blur",
            "仅 Android 12 及以上支持", android.os.Build.VERSION.SDK_INT >= 31)
        LyricsToggle("交错滚动效果", display.stagger, { value -> onDisplay { it.copy(stagger = value) } }, "lyrics_stagger")
        LyricsToggle("翻译歌词", display.translation, { value -> onDisplay { it.copy(translation = value) } }, "lyrics_translation")
        LyricsToggle("罗马音", display.romanization, { value -> onDisplay { it.copy(romanization = value) } }, "lyrics_romanization")
        LyricsToggle("逐字动画", display.wordAnimation, { value -> onDisplay { it.copy(wordAnimation = value) } }, "lyrics_word_animation")
        MusicDestinationRow("卡拉OK（逐字）歌词动画兼容策略", summary = display.karaokeScope.label, onClick = onKaraokeScope,
            modifier = Modifier.testTag("open_karaoke_scope"))
        LyricsToggle("隐藏歌词界面控制面板", display.hideControls, { value -> onDisplay { it.copy(hideControls = value) } },
            "lyrics_hide_controls", "歌词页底部可随时恢复控制")
    }
}

@Composable private fun LyricsToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String,
    summary: String? = null, enabled: Boolean = true) {
    val ink = MiuixTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag).clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
        .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off; if (!enabled) disabled() }
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontSize = 16.sp, color = ink)
            if (summary != null) Text(summary, fontSize = 11.sp, color = ink.copy(alpha = .5f))
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
