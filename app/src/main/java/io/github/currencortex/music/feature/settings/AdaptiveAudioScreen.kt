package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.ui.component.musicScrollPadding
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable internal fun AdaptiveAudioScreen(vm: SoundEffectsViewModel, onBack: () -> Unit) {
    val settings by vm.adaptive.collectAsStateWithLifecycle()
    val runtime by vm.adaptiveRuntime.collectAsStateWithLifecycle()
    val output by vm.audioOutput.collectAsStateWithLifecycle()
    val playback by vm.presentation.collectAsStateWithLifecycle()
    val color = MiuixTheme.colorScheme.primary
    val bypass = output.mode.bypassProcessing || playback.mode == PlayerMode.CAST
    val status = when {
        !settings.enabled -> "已关闭，保留调节参数"
        playback.mode == PlayerMode.CAST -> "投屏由接收设备处理"
        output.mode.bypassProcessing -> "${output.mode.label} 已旁路音频处理和马达驱动"
        !playback.playing -> "播放后生效"
        else -> runtime.message
    }
    SettingsUiScope {
        SettingsScrollLayout(Modifier.fillMaxSize().testTag("adaptive_audio_screen"), topBar = {
            SettingsTopBar("音频处理系统", onBack = onBack)
        }) { topInset ->
            LazyColumn(Modifier.fillMaxSize().testTag("adaptive_audio_list"), contentPadding = musicScrollPadding(PaddingValues(
                start = 16.dp, end = 16.dp, top = topInset, bottom = 24.dp)),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SettingsGroup {
                        SettingsToggle("音频处理系统", settings.enabled, { value -> vm.editAdaptive { it.copy(enabled = value) } },
                            Modifier.testTag("adaptive_enabled"), summary = "本地实时处理 · 可叠加现有音效")
                        SettingsHint(status, Modifier.testTag("adaptive_status"))
                    }
                }
                item {
                    SettingsGroup {
                        SettingsSectionHeader("EQ 控制台", "重置") { vm.editAdaptive { it.copy(gains = List(10) { 0f }) } }
                        Canvas(Modifier.fillMaxWidth().height(100.dp).padding(16.dp).testTag("adaptive_eq_curve")) {
                            val values = if (settings.enabled && runtime.processing && !bypass) runtime.gains else settings.gains
                            drawLine(color.copy(alpha = .2f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
                            val path = Path()
                            values.forEachIndexed { i, value ->
                                val point = Offset(size.width * i / 9, size.height * (.5f - value.coerceIn(-9f, 9f) / 18))
                                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                            }
                            drawPath(path, color, style = Stroke(2.dp.toPx()))
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.testTag("adaptive_bands")) {
                            itemsIndexed(AdaptiveAudioSettings.FREQUENCIES) { index, frequency ->
                                var preview by remember(settings.gains[index]) { mutableFloatStateOf(settings.gains[index]) }
                                Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(String.format(Locale.ROOT, "%+.1f", preview), fontSize = 13.sp)
                                    Box(Modifier.size(48.dp, 180.dp), contentAlignment = Alignment.Center) {
                                        GlassSlider(preview, { preview = it },
                                            Modifier.requiredSize(180.dp, 48.dp).graphicsLayer { rotationZ = -90f }
                                                .testTag("adaptive_band_$index").semantics { contentDescription = "$frequency Hz" },
                                            enabled = settings.enabled, valueRange = -6f..6f,
                                            onValueChangeFinished = { vm.editAdaptive { it.copy(gains = it.gains.toMutableList().apply {
                                                set(index, (preview * 10).roundToInt() / 10f)
                                            }) } })
                                    }
                                    Text(if (frequency < 1000) "$frequency" else "${frequency / 1000}k", fontSize = 13.sp)
                                }
                            }
                        }
                        SettingsHint("左右滑动调整 10 段频率 · 动态曲线叠加显示 · 预留 6 dB 处理空间")
                    }
                }
                item {
                    SettingsGroup {
                        SettingsToggle("虚拟低频", settings.virtualBass, { value -> vm.editAdaptive { it.copy(virtualBass = value) } },
                            Modifier.testTag("adaptive_bass"), "用低频谐波增强小型扬声器的低音感知", enabled = settings.enabled)
                        AudioAdjustment("虚拟低频力度", settings.bassStrength.toFloat(), 0f..100f, { "${it.roundToInt()}%" },
                            settings.enabled && settings.virtualBass, "adaptive_bass_strength") { value -> vm.editAdaptive { it.copy(bassStrength = value.roundToInt()) } }
                    }
                }
                item {
                    SettingsGroup {
                        SettingsToggle("马达驱动", settings.haptics, { value -> vm.editAdaptive { it.copy(haptics = value) } },
                            Modifier.testTag("adaptive_haptics"), when {
                                !vm.hapticsSupported -> "此设备没有可用马达"
                                !vm.hapticsAmplitudeControl -> "系统未开放振幅控制，以短脉冲时长与间隔调节力度"
                                else -> "跟随本机播放位置，暂停时停止震动"
                            },
                            enabled = settings.enabled && vm.hapticsSupported)
                        AudioAdjustment("马达力度", settings.hapticStrength.toFloat(), 0f..100f, { "${it.roundToInt()}%" },
                            settings.enabled && settings.haptics && vm.hapticsSupported, "adaptive_haptic_strength") { value -> vm.editAdaptive { it.copy(hapticStrength = value.roundToInt()) } }
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MusicHapticMode.entries.forEach { mode ->
                                SettingsChoiceButton(mode.label, settings.hapticMode == mode,
                                    { vm.editAdaptive { it.copy(hapticMode = mode) } }, Modifier.weight(1f))
                            }
                        }
                    }
                }
                item {
                    SettingsGroup {
                        SettingsToggle("动态 EQ", settings.dynamicEq, { value -> vm.editAdaptive { it.copy(dynamicEq = value) } },
                            Modifier.testTag("adaptive_dynamic"), "分析频段能量，平滑调整音色；静音时不补偿增益", enabled = settings.enabled)
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DynamicEqCurve.entries.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { curve -> SettingsChoiceButton(curve.label, settings.curve == curve,
                                        { vm.editAdaptive { it.copy(curve = curve) } }, Modifier.weight(1f)) }
                                }
                            }
                        }
                        AudioAdjustment("效果强度", settings.strength.toFloat(), 0f..4f,
                            { listOf("弱", "默认", "中", "强", "超强")[it.roundToInt().coerceIn(0, 4)] }, settings.enabled && settings.dynamicEq,
                            "adaptive_strength", steps = 3) { value -> vm.editAdaptive { it.copy(strength = value.roundToInt()) } }
                        AudioAdjustment("分析频率", settings.speed.toFloat(), 0f..4f, { "${AdaptiveAudioSettings.SPEEDS[it.roundToInt().coerceIn(0, 4)]}×" },
                            settings.enabled && settings.dynamicEq, "adaptive_speed", steps = 3) { value -> vm.editAdaptive { it.copy(speed = value.roundToInt()) } }
                        SettingsHint("提高分析频率会增加运算量，滤波与播放采样率保持不变。")
                    }
                }
            }
        }
    }
}

@Composable private fun AudioAdjustment(title: String, value: Float, range: ClosedFloatingPointRange<Float>,
    label: (Float) -> String, enabled: Boolean, tag: String, steps: Int = 0, onSave: (Float) -> Unit) {
    var preview by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 14.sp)
            Text(label(preview), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        GlassSlider(preview, { preview = it }, Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = title },
            enabled = enabled, valueRange = range, steps = steps, onValueChangeFinished = { onSave(preview) })
    }
}
