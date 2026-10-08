package io.github.currencortex.music.feature.visualizer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.core.visualizer.AudioAnalysisEngine
import io.github.currencortex.music.core.visualizer.AudioAnalysisFrame
import io.github.currencortex.music.data.visualizer.*
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.ui.component.MusicDialog
import kotlinx.coroutines.awaitCancellation
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Real audio diagnostics share the M0 engine and preserve the existing artwork/pager. */
@Composable fun AudioAnalysisDebugDialog(vm: PlayerViewModel, visible: Boolean, onDismiss: () -> Unit,
    renderState: VisualizerRenderState? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.audioAnalysis.status.collectAsStateWithLifecycle()
    val render = renderState ?: remember(vm.audioAnalysis) { VisualizerRenderState() }
    var permissionRevision by remember { mutableIntStateOf(0) }
    var denied by remember { mutableStateOf(false) }
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        permissionRevision++
        denied = !allowed
        vm.visualizerEnabled(allowed)
    }
    LaunchedEffect(lifecycle, visible, permissionRevision) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.visualizerVisible(visible, granted())
            try { awaitCancellation() } finally { vm.visualizerVisible(false, false) }
        }
    }
    DisposableEffect(vm) { onDispose { vm.visualizerVisible(false, false) } }
    MusicDialog("音乐可视化 · 音频调试", onDismiss) {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("读取当前播放器的 FFT 与波形，仅在此页面可见时采集；数据只在内存中处理，不录音、不上传。")
        Text(status.label, Modifier.testTag("visualizer_status"))
        if (denied) Text("音频采集权限未授予，可在系统设置中允许后重试。")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            VisualizerFrameRate.entries.forEach { rate ->
                Box(Modifier.weight(1f).heightIn(min = 48.dp).testTag("visualizer_fps_${rate.name}")
                    .semantics { selected = settings.visualizerRender.frameRate == rate }
                    .clickable { vm.visualizerFrameRate(rate) }, contentAlignment = Alignment.Center) {
                    Text(rate.label, fontSize = 14.sp, maxLines = 1,
                        color = if (settings.visualizerRender.frameRate == rate) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                }
            }
        }
        VisualizerRenderLayer(vm.audioAnalysis, Modifier.fillMaxWidth().height(160.dp).testTag("visualizer_spectrum"),
            settings.visualizerRender, visible, render)
        AudioDebugReadout(render)
        VisualizerPerformanceReadout(render)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(if (settings.visualizerRender.rawSpectrum) "原始频谱" else "平滑频谱", {
                vm.visualizerRender { it.copy(rawSpectrum = !it.rawSpectrum) }
            }, Modifier.testTag("visualizer_raw"))
            TextButton(if (settings.visualizerRender.automaticOptimization) "自动优化：开" else "自动优化：关", {
                vm.visualizerRender { it.copy(automaticOptimization = !it.automaticOptimization) }
            }, Modifier.testTag("visualizer_auto_performance"))
        }
        Text("频谱 30 Hz–16 kHz · 48 个对数频带。RMS 是 8 位可视化波形的相对幅度。")
        TextButton(if (settings.visualizerEnabled) "关闭音频采集" else "开启音频采集", onClick = {
            if (settings.visualizerEnabled) vm.visualizerEnabled(false)
            else if (granted()) { permissionRevision++; denied = false; vm.visualizerEnabled(true) }
            else launcher.launch(Manifest.permission.RECORD_AUDIO)
        }, modifier = Modifier.testTag("visualizer_toggle"))
        TextButton("关闭", onClick = onDismiss)
        }
    }
}

@Composable private fun AudioDebugReadout(render: VisualizerRenderState) {
    val frame by render.audioReadout
    Text("${frame.sampleRateHz} Hz / ${frame.captureSize} 点 · 峰值 ${frame.peakFrequencyHz.toInt()} Hz\n" +
        "RMS %.3f · Bass %.3f · Mid %.3f · Treble %.3f\n".format(frame.rms, frame.bass, frame.mid, frame.treble) +
        "Kick ${frame.kick} · Transient ${frame.transient} · Pulse %.2f".format(frame.beatPulse),
        Modifier.testTag("visualizer_metrics"))
}

@Composable internal fun AudioDebugCanvas(engine: AudioAnalysisEngine, modifier: Modifier = Modifier) {
    VisualizerRenderLayer(engine, modifier)
}
