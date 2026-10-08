package io.github.currencortex.music.feature.visualizer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.core.visualizer.AudioAnalysisEngine
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.ui.component.MusicDialog
import kotlinx.coroutines.awaitCancellation
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/** Temporary M0 diagnostics; the shared artwork/pager is never modified by this overlay. */
@Composable fun AudioAnalysisDebugDialog(vm: PlayerViewModel, visible: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.audioAnalysis.status.collectAsStateWithLifecycle()
    var permissionRevision by remember { mutableIntStateOf(0) }
    var denied by remember { mutableStateOf(false) }
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        permissionRevision++
        denied = !allowed
        vm.visualizerEnabled(allowed)
    }
    LaunchedEffect(lifecycle, visible, permissionRevision) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.visualizerVisible(visible, granted())
            try { awaitCancellation() } finally { vm.visualizerVisible(false, false) }
        }
    }
    DisposableEffect(vm) { onDispose { vm.visualizerVisible(false, false) } }
    MusicDialog("音乐可视化 · 音频调试", onDismiss) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("读取当前播放器的 FFT 与波形，仅在此页面可见时采集；数据只在内存中处理，不录音、不上传。")
        Text(status.label, Modifier.testTag("visualizer_status"))
        if (denied) Text("音频采集权限未授予，可在系统设置中允许后重试。")
        AudioDebugReadout(vm.audioAnalysis)
        AudioDebugCanvas(vm.audioAnalysis, Modifier.fillMaxWidth().height(160.dp).testTag("visualizer_spectrum"))
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

@Composable private fun AudioDebugReadout(engine: AudioAnalysisEngine) {
    val frame by engine.frames.collectAsStateWithLifecycle()
    Text("${frame.sampleRateHz} Hz / ${frame.captureSize} 点 · 峰值 ${frame.peakFrequencyHz.toInt()} Hz\n" +
        "RMS %.3f · Bass %.3f · Mid %.3f · Treble %.3f\n".format(frame.rms, frame.bass, frame.mid, frame.treble) +
        "Kick ${frame.kick} · Transient ${frame.transient} · Pulse %.2f".format(frame.beatPulse),
        Modifier.testTag("visualizer_metrics"))
}

@Composable internal fun AudioDebugCanvas(engine: AudioAnalysisEngine, modifier: Modifier = Modifier) {
    // Read the snapshot inside drawing: capture never recomposes the player or canvas.
    val frame = engine.frames.collectAsStateWithLifecycle()
    Canvas(modifier) {
        val data = frame.value
        val step = size.width / data.spectrum.size
        data.spectrum.forEachIndexed { i, amplitude ->
            val height = amplitude * size.height * .65f
            drawRect(Color(0xFF8A7CFF), Offset(i * step, size.height * .65f - height), Size(step * .75f, height))
        }
        data.waveform.zipWithNext().forEachIndexed { i, pair ->
            val dx = size.width / maxOf(1, data.waveform.size - 1)
            drawLine(Color(0xFF53D8E8), Offset(i * dx, size.height * (.83f - pair.first * .14f)),
                Offset((i + 1) * dx, size.height * (.83f - pair.second * .14f)), 2.dp.toPx())
        }
    }
}
