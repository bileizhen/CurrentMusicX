package io.github.currencortex.music.feature.visualizer

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.core.visualizer.VisualizerEffectConfig
import io.github.currencortex.music.data.visualizer.VisualizerQuality
import io.github.currencortex.music.feature.player.PlayerViewModel
import top.yukonga.miuix.kmp.basic.*

@Composable fun VisualizerEffectControls(vm: PlayerViewModel, config: VisualizerEffectConfig, quality: VisualizerQuality) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        VisualizerQuality.entries.forEach { value ->
            TextButton(if (value == quality) "● ${value.name}" else value.name,
                { vm.visualizerRender { it.copy(preferredQuality = value) } }, Modifier.weight(1f).testTag("visualizer_quality_${value.name}"))
        }
    }
    TextButton(if (config.reduceMotion) "减少动态效果：开" else "减少动态效果：关",
        { vm.visualizerEffects { it.copy(reduceMotion = !it.reduceMotion) } }, Modifier.testTag("visualizer_reduce_motion"))
    EffectSlider("全局强度", config.globalIntensity, 2f, "intensity") { v -> vm.visualizerEffects { it.copy(globalIntensity = v) } }
    EffectSlider("低音灵敏度", config.bassSensitivity, 3f, "bass") { v -> vm.visualizerEffects { it.copy(bassSensitivity = v) } }
    EffectSlider("频谱灵敏度", config.spectrumSensitivity, 3f, "spectrum") { v -> vm.visualizerEffects { it.copy(spectrumSensitivity = v) } }
    EffectSlider("光晕强度", config.glowIntensity, 2f, "glow") { v -> vm.visualizerEffects { it.copy(glowIntensity = v) } }
    EffectSlider("粒子密度", config.particleDensity, 1f, "particles") { v -> vm.visualizerEffects { it.copy(particleDensity = v) } }
    EffectSlider("封面缩放与震动", config.motionIntensity, 1f, "motion") { v -> vm.visualizerEffects { it.copy(motionIntensity = v) } }
    EffectSlider("径向扭曲", config.distortionIntensity, 1f, "distortion") { v -> vm.visualizerEffects { it.copy(distortionIntensity = v) } }
    EffectSlider("故障强度", config.glitchIntensity, 1f, "glitch") { v -> vm.visualizerEffects { it.copy(glitchIntensity = v) } }
}

@Composable private fun EffectSlider(label: String, saved: Float, maximum: Float, tag: String, commit: (Float) -> Unit) {
    var draft by remember(saved) { mutableFloatStateOf(saved) }
    Text("$label · %.2f".format(draft), fontSize = 12.sp)
    Slider(value = draft, onValueChange = { draft = it }, onValueChangeFinished = { commit(draft) },
        valueRange = 0f..maximum, modifier = Modifier.fillMaxWidth().testTag("visualizer_parameter_$tag").semantics {
            setProgress { value -> draft = value.coerceIn(0f, maximum); commit(draft); true }
        })
}
