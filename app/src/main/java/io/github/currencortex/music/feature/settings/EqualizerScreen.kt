package io.github.currencortex.music.feature.settings

import io.github.currencortex.music.ui.component.nativeui.*

import io.github.currencortex.music.ui.component.nativeui.GlassSlider as Slider

import io.github.currencortex.music.ui.component.nativeui.SettingsChoiceButton

import io.github.currencortex.music.ui.component.nativeui.SettingsPage
import io.github.currencortex.music.ui.component.nativeui.SettingsUiScope
import io.github.currencortex.music.ui.component.nativeui.SettingsGroup as Card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import java.util.Locale

@Composable fun EqualizerScreen(vm: EqualizerViewModel, onBack: (() -> Unit)? = null) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val runtime by vm.runtime.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val soundPreferences by vm.soundPreferences.collectAsStateWithLifecycle()
    val hint = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val accent = MiuixTheme.colorScheme.primary
    var preview by remember(settings.gains) { mutableStateOf(settings.gains) }
    val status = when {
        !runtime.supported -> "此设备未提供均衡器"
        runtime.error != null -> runtime.error!!
        playback.mode == PlayerMode.CAST -> "投屏音效由接收设备处理"
        soundPreferences.smartEnabled -> "智能调音正在使用；以下为手动参数，调整后切回手动"
        !settings.enabled -> "已关闭，保留你的调节参数"
        runtime.active -> "已连接本机播放器"
        else -> "开始本机播放后自动生效"
    }
    SettingsUiScope {
        SettingsPage("均衡器","equalizer_screen", onBack = onBack) {
            item { Card {
                SettingsSwitch("开启均衡器", settings.enabled, vm::enabled, summary = status,
                    enabled = runtime.supported, modifier = Modifier.testTag("equalizer_enabled"))
            } }
            item { SettingsSection("预设与曲线", MusicIcons.Headphones) {
                SettingsOptionsRow("预设", settings.preset.label,
                    EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM }.map { preset ->
                        SettingsOption("equalizer_preset_${preset.name.lowercase()}", preset.label, settings.preset == preset,
                            onSelect = { vm.preset(preset) })
                    }, Modifier.testTag("equalizer_selected"))
                SettingsDivider()
                SettingsPreference("恢复平直", "将各频段恢复为 0 dB", Modifier.testTag("equalizer_reset"),
                    onClick = { vm.preset(EqualizerPreset.FLAT) }, enabled = settings.preset != EqualizerPreset.FLAT)
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Canvas(Modifier.fillMaxWidth().height(96.dp).testTag("equalizer_curve")) {
                    val inset = 10.dp.toPx()
                    val width = size.width - inset * 2
                    val height = size.height - inset * 2
                    listOf(0f, .5f, 1f).forEach { fraction ->
                        val y = inset + height * fraction
                        drawLine(hint.copy(alpha = .18f), Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                    }
                    val path = Path()
                    preview.forEachIndexed { index, gain ->
                        val point = Offset(inset + width * index / 4f, inset + height * (1200 - gain) / 2400f)
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                        drawCircle(accent, 3.dp.toPx(), point)
                    }
                    drawPath(path, accent, style = Stroke(2.dp.toPx()))
                }
                }
            } }
            item { SettingsSection("频段调节", MusicIcons.Scale,
                footer = "−12 dB 至 +12 dB · 1 dB 步进 · 自动保存") { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                EqualizerSettings.FREQUENCIES.forEachIndexed { index, frequency ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (frequency >= 1000) String.format(Locale.ROOT, "%.1f kHz", frequency / 1000f) else "$frequency Hz",
                            Modifier.weight(1f), fontSize = 14.sp)
                        Text(String.format(Locale.ROOT, "%+.1f dB", preview[index] / 100f), color = hint, fontSize = 13.sp)
                    }
                    Slider(value = preview[index] / 100f, valueRange = -12f..12f,
                        enabled = settings.enabled && runtime.supported,
                        onValueChange = { value ->
                            val gain = value.roundToInt() * 100
                            preview = preview.toMutableList().also { it[index] = gain }
                            vm.previewBand(index, gain)
                        },
                        onValueChangeFinished = { vm.saveBand(index, preview[index]) },
                        modifier = Modifier.fillMaxWidth().testTag("equalizer_band_$index").semantics {
                            contentDescription = "$frequency Hz 均衡器"
                            setProgress { value ->
                                if (!settings.enabled || !runtime.supported) false else {
                                    val gain = value.coerceIn(-12f, 12f).roundToInt() * 100
                                    preview = preview.toMutableList().also { it[index] = gain }
                                    vm.saveBand(index, gain); true
                                }
                            }
                        })
                }
            } } }
        }
    }
}
