package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
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
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item("master") {
                    SettingsGroup {
                        SettingsToggle("启用音频处理", settings.enabled, { value -> vm.editAdaptive { it.copy(enabled = value) } },
                            Modifier.testTag("adaptive_enabled"))
                        Row(Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.primary.copy(alpha = if (live) .08f else .03f))
                            .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(
                                if (live) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary))
                            Text(status, Modifier.weight(1f).testTag("adaptive_status"), fontSize = 12.sp, lineHeight = 17.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
                item("equalizer") {
                    SettingsGroup {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("十段均衡器", fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
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
                        Column(Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(14.dp))
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = if (enabled) .07f else .025f))) {
                            AudioAdjustment("${AdaptiveAudioSettings.FREQUENCIES[selectedBand]} Hz · 手动增益", settings.gains[selectedBand], -6f..6f,
                                ::gainLabel, enabled, "adaptive_band_fine", key = selectedBand,
                                onPreview = { vm.previewAdaptive(band(it)) }, onCancel = vm::clearAdaptivePreview,
                                onSave = { vm.commitAdaptive(band(it)) })
                        }
                    }
                }
                item("dynamic") {
                    SettingsGroup {
                        SettingsToggle("动态 EQ", settings.dynamicEq, { value -> vm.editAdaptive { it.copy(dynamicEq = value) } },
                            Modifier.testTag("adaptive_dynamic"), "跟随音乐调整，叠加在手动曲线上", enabled = enabled)
                        if (settings.dynamicEq) {
                            val controls = enabled && settings.dynamicEq
                            AdaptiveEqCurve(if (live) runtime.gains else settings.gains, live)
                            AudioSectionLabel("音色方向")
                            BoxWithConstraints(Modifier.padding(horizontal = 16.dp).semantics { selectableGroup() }) {
                                val columns = if (maxWidth < 240.dp * LocalDensity.current.fontScale) 1 else 2
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DynamicEqCurve.entries.chunked(columns).forEach { pair ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            pair.forEach { curve ->
                                                AudioCurveChoice(curve, settings.curve == curve, controls,
                                                    Modifier.weight(1f).testTag("adaptive_curve_${curve.name}")) {
                                                    vm.editAdaptive { it.copy(curve = curve) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            SettingsDivider()
                            AudioAdjustment("效果强度", settings.strength.toFloat(), 0f..4f,
                                { listOf("弱", "默认", "中", "强", "超强")[it.roundToInt().coerceIn(0, 4)] }, controls,
                                "adaptive_strength", steps = 3, onPreview = { value -> vm.previewAdaptive { it.copy(strength = value.roundToInt()) } },
                                onCancel = vm::clearAdaptivePreview, onSave = { value -> vm.commitAdaptive { it.copy(strength = value.roundToInt()) } })
                            AudioAdjustment("响应速度", settings.speed.toFloat(), 0f..4f,
                                { listOf("舒缓", "标准", "灵敏", "快速", "极快")[it.roundToInt().coerceIn(0, 4)] }, controls,
                                "adaptive_speed", steps = 3, onPreview = { value -> vm.previewAdaptive { it.copy(speed = value.roundToInt()) } },
                                onCancel = vm::clearAdaptivePreview, onSave = { value -> vm.commitAdaptive { it.copy(speed = value.roundToInt()) } })
                        }
                    }
                }
                item("bass") {
                    SettingsGroup {
                        SettingsToggle("虚拟低频", settings.virtualBass, { value -> vm.editAdaptive { it.copy(virtualBass = value) } },
                            Modifier.testTag("adaptive_bass"), "用低频谐波补充厚度，适合小型扬声器", enabled = enabled)
                        if (settings.virtualBass) {
                            AudioAdjustment("低频力度", settings.bassStrength.toFloat(), 0f..100f, { "${it.roundToInt()}%" },
                                enabled, "adaptive_bass_strength", onPreview = { value -> vm.previewAdaptive { it.copy(bassStrength = value.roundToInt()) } },
                                onCancel = vm::clearAdaptivePreview, onSave = { value -> vm.commitAdaptive { it.copy(bassStrength = value.roundToInt()) } })
                        }
                    }
                }
                item("haptics") {
                    SettingsGroup {
                        SettingsToggle("马达驱动", settings.haptics, { value -> vm.editAdaptive { it.copy(haptics = value) } },
                            Modifier.testTag("adaptive_haptics"), if (vm.hapticsSupported) "随音乐震动，暂停即停止" else "此设备没有可用马达",
                            enabled = enabled && vm.hapticsSupported)
                        if (settings.haptics && vm.hapticsSupported) {
                            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { selectableGroup() }) {
                                val columns = if (maxWidth < 220.dp * LocalDensity.current.fontScale) 1 else 2
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MusicHapticMode.entries.chunked(columns).forEach { modes ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            modes.forEach { mode ->
                                                AudioChoice(mode.label, settings.hapticMode == mode, enabled,
                                                    Modifier.weight(1f).testTag("adaptive_haptic_mode_${mode.name}")) {
                                                    vm.editAdaptive { it.copy(hapticMode = mode) }
                                                }
                                            }
                                        }
                                    }
                                }
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

@Composable private fun AudioSectionLabel(title: String) {
    Text(title, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp,
        fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable private fun AudioCurveChoice(curve: DynamicEqCurve, selected: Boolean, enabled: Boolean,
    modifier: Modifier, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val summary = when (curve) {
        DynamicEqCurve.WARM -> "柔和、厚实"
        DynamicEqCurve.V_SHAPE -> "低音与高音更突出"
        DynamicEqCurve.VOCAL -> "突出人声层次"
        DynamicEqCurve.BRIGHT -> "清晰、明亮"
    }
    ElasticPress(modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick), enabled) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.primary.copy(alpha = .12f) else colors.onSurface.copy(alpha = .035f))
            .padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(curve.label, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                    color = (if (selected) colors.primary else colors.onSurface).copy(alpha = if (enabled) 1f else .38f))
                if (selected) Icon(MusicIcons.Check, null, Modifier.size(16.dp), tint = colors.primary.copy(alpha = if (enabled) 1f else .38f))
            }
            Text(summary, fontSize = 11.sp, lineHeight = 16.sp,
                color = colors.onSurfaceVariantSummary.copy(alpha = if (enabled) 1f else .45f))
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
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, Modifier.padding(end = 12.dp), fontSize = 13.sp, lineHeight = 19.sp)
                Text(label(preview), fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            GlassSlider(preview, { preview = if (steps == 0 && range.start < 0) (it * 10).roundToInt() / 10f else it; onPreview(preview) },
                Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = title }, enabled = enabled,
                valueRange = range, steps = steps, showKeyPoints = true,
                keyPoints = if (range.start < 0) listOf(-6f, 0f, 6f) else null,
                onValueChangeFinished = { onSave(preview) }, onValueChangeCancelled = onCancel)
        }
    }
}
