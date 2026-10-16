package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.settings.RefreshRatePreference
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun refreshRateSliderOptions(supported: List<Int>): List<RefreshRatePreference> =
    listOf(RefreshRatePreference.SYSTEM) + supported.filter { it in 1..1000 }.distinct().sorted().map(::RefreshRatePreference)

/** Drag previews a supported step; releasing commits once, avoiding display-mode thrash. */
@Composable internal fun RefreshRateSlider(selected: RefreshRatePreference, supported: List<Int>,
    onSelected: (RefreshRatePreference) -> Unit) {
    val options = remember(supported) { refreshRateSliderOptions(supported) }
    val selectedIndex = options.indices.minBy { abs(options[it].hz - selected.hz) }
    var position by remember(selected, options) { mutableFloatStateOf(selectedIndex.toFloat()) }
    val index = position.roundToInt().coerceIn(options.indices)
    val label = options[index].label
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.testTag("refresh_rate_selection"), color = MiuixTheme.colorScheme.primary, fontSize = 16.sp)
        if (options.size > 1) {
            Slider(value = position, onValueChange = { position = it },
                onValueChangeFinished = {
                    position = index.toFloat()
                    onSelected(options[index])
                }, valueRange = 0f..options.lastIndex.toFloat(), showKeyPoints = true,
                keyPoints = options.indices.map(Int::toFloat), magnetThreshold = .2f,
                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                modifier = Modifier.testTag("refresh_rate_slider").semantics {
                    stateDescription = label
                    // Accessibility adjustments must persist without waiting for touch-up.
                    setProgress { target ->
                        val step = target.roundToInt().coerceIn(options.indices)
                        position = step.toFloat(); onSelected(options[step]); true
                    }
                })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("跟随系统", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(options.last().label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        } else Text("正在读取屏幕刷新率…", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
