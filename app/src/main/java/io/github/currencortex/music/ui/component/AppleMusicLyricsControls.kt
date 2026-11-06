package io.github.currencortex.music.ui.component

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.settings.AppleMusicLyricsOptions
import io.github.currencortex.music.data.settings.LyricsDisplayOptions
import io.github.currencortex.music.data.settings.LyricsTypography
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.roundToInt

/** Each completed gesture commits only its own field, through the shared settings repository. */
@Composable internal fun AppleMusicLyricsControls(display: LyricsDisplayOptions,
    onDisplay: ((LyricsDisplayOptions) -> LyricsDisplayOptions) -> Unit, modifier: Modifier = Modifier) {
    val options = display.appleMusic.normalized()
    val ink = MiuixTheme.colorScheme.onSurface
    Column(modifier.fillMaxWidth().testTag("apple_lyrics_controls")) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Apple Music 效果", fontSize = 16.sp, color = ink, modifier = Modifier.weight(1f))
            Text("重置参数", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.testTag("apple_lyrics_reset").clickable(role = Role.Button) {
                    onDisplay { it.copy(appleMusic = AppleMusicLyricsOptions()) }
                }.padding(start = 12.dp, top = 14.dp, bottom = 14.dp))
        }
        LyricsParameterSlider("歌词间距", options.lineSpacing, AppleMusicLyricsOptions.LINE_SPACING_RANGE,
            "apple_lyrics_spacing", "调整两句歌词之间的留白", { "${(it * 100).roundToInt()}%" }) { value ->
            onDisplay { it.copy(appleMusic = it.appleMusic.copy(lineSpacing = value)) }
        }
        LyricsParameterSlider("换行行高", options.lineHeight, AppleMusicLyricsOptions.LINE_HEIGHT_RANGE,
            "apple_lyrics_line_height", "调整同一句歌词换行后的距离", ::multiplier) { value ->
            onDisplay { it.copy(appleMusic = it.appleMusic.copy(lineHeight = value)) }
        }
        LyricsParameterSlider("动画速度", options.animationSpeed, AppleMusicLyricsOptions.ANIMATION_SPEED_RANGE,
            "apple_lyrics_speed", "数值越大越快；歌曲速度和逐字同步保持不变", ::multiplier) { value ->
            onDisplay { it.copy(appleMusic = it.appleMusic.copy(animationSpeed = value)) }
        }
        AppleEffectToggle("歌词辉光", display.glow, "lyrics_glow") { enabled ->
            onDisplay { it.copy(glow = enabled) }
        }
        LyricsParameterSlider("辉光亮度", options.glowIntensity, AppleMusicLyricsOptions.GLOW_INTENSITY_RANGE,
            "apple_lyrics_glow", "调整中长音与强调处的光晕，不改变文字亮度", { "${(it * 100).roundToInt()}%" },
            enabled = display.glow) { value -> onDisplay { it.copy(appleMusic = it.appleMusic.copy(glowIntensity = value)) } }
        val blurSupported = Build.VERSION.SDK_INT >= 31
        AppleEffectToggle("歌词视图模糊", display.blur, "lyrics_blur", enabled = blurSupported) { enabled ->
            onDisplay { it.copy(blur = enabled) }
        }
        LyricsParameterSlider("模糊强度", options.blurStrength, AppleMusicLyricsOptions.BLUR_STRENGTH_RANGE,
            "apple_lyrics_blur", if (blurSupported) "离当前句越远越模糊，0% 为清晰显示" else "需要 Android 12 或以上版本",
            { "${(it * 100).roundToInt()}%" }, enabled = display.blur && blurSupported) { value ->
            onDisplay { it.copy(appleMusic = it.appleMusic.copy(blurStrength = value)) }
        }
    }
}

@Composable internal fun LyricsSizeSlider(value: Float, onChange: (Float) -> Unit) {
    LyricsParameterSlider("字号", LyricsTypography.normalize(value), LyricsTypography.MIN_SIZE..LyricsTypography.MAX_SIZE,
        "lyrics_font_size", "与播放器中的歌词字号同步", { it.roundToInt().toString() }, step = 1f, onCommit = onChange)
}

@Composable private fun LyricsParameterSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>,
    tag: String, summary: String, format: (Float) -> String, enabled: Boolean = true, step: Float = .05f,
    onCommit: (Float) -> Unit) {
    var preview by remember(value) { mutableFloatStateOf(value) }
    val ink = MiuixTheme.colorScheme.onSurface
    fun rounded(target: Float) = ((target / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 16.sp, color = ink.copy(alpha = if (enabled) 1f else .45f), modifier = Modifier.weight(1f))
            Text(format(preview), fontSize = 14.sp, color = ink.copy(alpha = if (enabled) .75f else .4f))
        }
        Slider(value = preview, onValueChange = { if (it.isFinite()) preview = rounded(it) },
            onValueChangeFinished = { onCommit(preview) }, valueRange = range, enabled = enabled,
            modifier = Modifier.testTag(tag).semantics {
                contentDescription = title
                stateDescription = format(preview)
                if (!enabled) disabled()
                setProgress { target ->
                    if (!enabled || !target.isFinite()) false else {
                        preview = rounded(target); onCommit(preview); true
                    }
                }
            })
        Text(summary, fontSize = 12.sp, lineHeight = 18.sp, color = ink.copy(alpha = .6f))
    }
}

@Composable private fun AppleEffectToggle(title: String, checked: Boolean, tag: String, enabled: Boolean = true,
    onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)
        .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
        .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off; if (!enabled) disabled() }
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

private fun multiplier(value: Float) = String.format(Locale.ROOT, "%.2f×", value)
