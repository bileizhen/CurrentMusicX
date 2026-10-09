package io.github.currencortex.music.feature.visualizer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.data.visualizer.VisualizerFrameRate
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.ui.component.MusicDialog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

@Composable fun VisualizerSettingsDialog(vm: PlayerViewModel, visible: Boolean, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        denied = !allowed; vm.visualizerEnabled(allowed)
    }
    if (preview) {
        AudioAnalysisDebugDialog(vm, visible, onDismiss, initialPreview = true)
        return
    }
    MusicDialog("音乐可视化", onDismiss) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
            .testTag("visualizer_settings"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("开启后，特效会显示在播放页的圆形封面周围。")
            if (denied) Text("未获得音频采集权限，可再次开启或在系统设置中允许。")
            TextButton(if (settings.visualizerEnabled) "关闭特效" else "开启特效", {
                if (settings.visualizerEnabled) vm.visualizerEnabled(false)
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                    vm.visualizerEnabled(true)
                else launcher.launch(Manifest.permission.RECORD_AUDIO)
            }, Modifier.testTag("visualizer_toggle"))
            VisualizerPresetSelector(settings.visualizerEffects) { selected -> vm.visualizerEffects { it.copy(presetId = selected) } }
            TextButton(if (details) "收起参数" else "画质与动态参数", { details = !details }, Modifier.testTag("visualizer_details"))
            if (details) {
                Row { VisualizerFrameRate.entries.forEach { rate ->
                    TextButton(rate.label, { vm.visualizerFrameRate(rate) }, Modifier.weight(1f).testTag("visualizer_fps_${rate.name}"))
                } }
                VisualizerEffectControls(vm, settings.visualizerEffects, settings.visualizerRender.preferredQuality)
                TextButton(if (settings.visualizerRender.automaticOptimization) "自动优化：开" else "自动优化：关",
                    { vm.visualizerRender { it.copy(automaticOptimization = !it.automaticOptimization) } })
            }
            TextButton("打开沉浸预览", { preview = true }, Modifier.testTag("visualizer_preview_toggle"))
            TextButton("返回播放页", onDismiss, Modifier.testTag("visualizer_settings_close"))
        }
    }
}
