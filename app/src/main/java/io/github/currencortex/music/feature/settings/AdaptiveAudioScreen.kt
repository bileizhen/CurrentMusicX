package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.ui.component.LocalMusicPageActive
import io.github.currencortex.music.ui.component.MusicIcons
import io.github.currencortex.music.ui.component.musicScrollPadding
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable internal fun AdaptiveAudioScreen(vm: SoundEffectsViewModel, onBack: () -> Unit) {
    val settings by vm.adaptiveEffective.collectAsStateWithLifecycle()
    val runtime by vm.adaptiveRuntime.collectAsStateWithLifecycle()
    val output by vm.audioOutput.collectAsStateWithLifecycle()
    val playback by vm.presentation.collectAsStateWithLifecycle()
    var selectedBand by rememberSaveable { mutableIntStateOf(0) }
    val active = LocalMusicPageActive.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(vm, active, lifecycle) {
        if (!active) vm.clearAdaptivePreview()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.clearAdaptivePreview()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vm.clearAdaptivePreview() }
    }
    val bypass = output.mode.bypassProcessing || playback.mode == PlayerMode.CAST
    val live = settings.enabled && playback.playing && runtime.processing && !bypass
    val liveEq = live && settings.dynamicEq
    val status = when {
        !settings.enabled -> "已关闭 · 调节参数已保留"
        playback.mode == PlayerMode.CAST -> "投屏由接收设备处理，本机设置暂不生效"
        output.mode.bypassProcessing -> "${output.mode.label} 已旁路音频处理和马达驱动"
        !playback.playing -> "已开启 · 播放后生效"
        else -> runtime.message
    }
    val enabled = settings.enabled && active
    fun band(value: Float): (AdaptiveAudioSettings) -> AdaptiveAudioSettings {
        val index = selectedBand
        return { it.copy(gains = it.gains.toMutableList().apply { set(index, value) }) }
    }
    SettingsUiScope {
        SettingsScrollLayout(Modifier.fillMaxSize().testTag("adaptive_audio_screen"), topBar = {
            SettingsTopBar("音频处理", onBack = { vm.clearAdaptivePreview(); onBack() })
        }) { topInset ->
            LazyColumn(Modifier.fillMaxSize().testTag("adaptive_audio_list"), contentPadding = musicScrollPadding(PaddingValues(
                start = 16.dp, end = 16.dp, top = topInset, bottom = 24.dp)),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item("master") {
                    SettingsGroup {
                        SettingsToggle("启用音频处理", settings.enabled, { value -> vm.editAdaptive { it.copy(enabled = value) } },
                            Modifier.testTag("adaptive_enabled"), summary = "均衡器、低频增强与音乐震动")
                        SettingsHint(status, Modifier.testTag("adaptive_status"))
                    }
                }
                item("equalizer") {
                    SettingsGroup {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("十段均衡器", fontSize = 17.sp)
                                Text(if (liveEq) "实时增益 · 含动态 EQ · ±9 dB" else "手动调节 · ±6 dB",
                                    Modifier.testTag("adaptive_band_mode"), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                            GlassIconButton({ vm.editAdaptive { it.copy(gains = List(10) { 0f }) } }, "重置十段均衡器",
                                Modifier.testTag("adaptive_reset"), enabled = enabled && settings.gains.any { it != 0f }, surface = false) {
                                Icon(MusicIcons.Reset, null)
                            }
                        }
                        AdaptiveEqBands(settings.gains, selectedBand, enabled, onSelect = { selectedBand = it },
                            onPreview = { index, value -> vm.previewAdaptive { it.copy(gains = it.gains.toMutableList().apply { set(index, value) }) } },
                            onSave = { index, value -> vm.commitAdaptive { it.copy(gains = it.gains.toMutableList().apply { set(index, value) }) } },
                            onCancel = vm::clearAdaptivePreview, liveGains = runtime.gains.takeIf { liveEq })
                        AudioAdjustment("${AdaptiveAudioSettings.FREQUENCIES[selectedBand]} Hz · 手动增益", settings.gains[selectedBand], -6f..6f,
                            ::gainLabel, enabled, "adaptive_band_fine", key = selectedBand,
                            onPreview = { vm.previewAdaptive(band(it)) }, onCancel = vm::clearAdaptivePreview,
                            onSave = { vm.commitAdaptive(band(it)) })
                        SettingsHint(if (liveEq) "数字与圆点跟随动态 EQ；拖动可叠加手动调节。" else "拖动圆点调节，空白处滑动页面；滑条可精调。")
                    }
                }
                item("dynamic") {
                    SettingsGroup {
                        SettingsToggle("动态 EQ", settings.dynamicEq, { value -> vm.editAdaptive { it.copy(dynamicEq = value) } },
                            Modifier.testTag("adaptive_dynamic"), "随音乐变化微调音色，保留手动均衡设置", enabled = enabled)
                        if (settings.dynamicEq) {
                            val controls = enabled && settings.dynamicEq
                            AdaptiveEqCurve(if (live) runtime.gains else settings.gains, live)
                            Column(Modifier.padding(horizontal = 16.dp).semantics { selectableGroup() }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DynamicEqCurve.entries.chunked(2).forEach { pair ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        pair.forEach { curve -> AudioChoice(curve.label, settings.curve == curve, controls,
                                            Modifier.weight(1f).testTag("adaptive_curve_${curve.name}")) { vm.editAdaptive { it.copy(curve = curve) } } }
                                    }
                                }
                            }
                            AudioAdjustment("效果强度", settings.strength.toFloat(), 0f..4f,
                                { listOf("弱", "默认", "中", "强", "超强")[it.roundToInt().coerceIn(0, 4)] }, controls,
                                "adaptive_strength", steps = 3, onPreview = { value -> vm.previewAdaptive { it.copy(strength = value.roundToInt()) } },
                                onCancel = vm::clearAdaptivePreview, onSave = { value -> vm.commitAdaptive { it.copy(strength = value.roundToInt()) } })
                            AudioAdjustment("响应速度", settings.speed.toFloat(), 0f..4f,
                                { listOf("舒缓 · 0.5×", "标准 · 1×", "灵敏 · 1.5×", "快速 · 2×", "极快 · 3×")[it.roundToInt().coerceIn(0, 4)] }, controls,
                                "adaptive_speed", steps = 3, onPreview = { value -> vm.previewAdaptive { it.copy(speed = value.roundToInt()) } },
                                onCancel = vm::clearAdaptivePreview, onSave = { value -> vm.commitAdaptive { it.copy(speed = value.roundToInt()) } })
                            SettingsHint("响应越快，耗电可能越高。")
                        }
                    }
                }
                item("bass") {
                    SettingsGroup {
                        SettingsToggle("虚拟低频", settings.virtualBass, { value -> vm.editAdaptive { it.copy(virtualBass = value) } },
                            Modifier.testTag("adaptive_bass"), "增强小型扬声器的低音感知", enabled = enabled)
                        if (settings.virtualBass) AudioAdjustment("低频力度", settings.bassStrength.toFloat(), 0f..100f, { "${it.roundToInt()}%" },
                            enabled, "adaptive_bass_strength", onPreview = { value -> vm.previewAdaptive { it.copy(bassStrength = value.roundToInt()) } },
                            onCancel = vm::clearAdaptivePreview, onSave = { value -> vm.commitAdaptive { it.copy(bassStrength = value.roundToInt()) } })
                    }
                }
                item("haptics") {
                    SettingsGroup {
                        SettingsToggle("马达驱动", settings.haptics, { value -> vm.editAdaptive { it.copy(haptics = value) } },
                            Modifier.testTag("adaptive_haptics"), if (vm.hapticsSupported) "随音乐震动，暂停即停止" else "此设备没有可用马达",
                            enabled = enabled && vm.hapticsSupported)
                        if (settings.haptics && vm.hapticsSupported) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { selectableGroup() }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MusicHapticMode.entries.forEach { mode -> AudioChoice(mode.label, settings.hapticMode == mode, enabled,
                                    Modifier.weight(1f).testTag("adaptive_haptic_mode_${mode.name}")) { vm.editAdaptive { it.copy(hapticMode = mode) } } }
                            }
                            AudioAdjustment("震动力度", settings.hapticStrength.toFloat(), 0f..100f, { "${it.roundToInt()}%" },
                                enabled, "adaptive_haptic_strength", onPreview = { value -> vm.previewAdaptive { it.copy(hapticStrength = value.roundToInt()) } },
                                onCancel = vm::clearAdaptivePreview, onSave = { value -> vm.commitAdaptive { it.copy(hapticStrength = value.roundToInt()) } })
                            if (!vm.hapticsAmplitudeControl) SettingsHint("此设备采用短脉冲模式，通过时长与间隔调节震感。")
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun AudioChoice(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    GlassActionButton(label, onClick, modifier.semantics { this.selected = selected; role = Role.RadioButton }, primary = selected, enabled = enabled)
}

@Composable private fun AudioAdjustment(title: String, value: Float, range: ClosedFloatingPointRange<Float>,
    label: (Float) -> String, enabled: Boolean, tag: String, steps: Int = 0, key: Any = tag,
    onPreview: (Float) -> Unit, onCancel: () -> Unit, onSave: (Float) -> Unit) {
    // A stable key prevents switching bands midway through an accessibility/keyboard adjustment.
    key(key) {
        var preview by remember(value) { mutableFloatStateOf(value) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontSize = 14.sp)
                Text(label(preview), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            GlassSlider(preview, { preview = if (steps == 0 && range.start < 0) (it * 10).roundToInt() / 10f else it; onPreview(preview) },
                Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = title }, enabled = enabled,
                valueRange = range, steps = steps, showKeyPoints = true,
                keyPoints = if (range.start < 0) listOf(-6f, 0f, 6f) else null,
                onValueChangeFinished = { onSave(preview) }, onValueChangeCancelled = onCancel)
        }
    }
}
