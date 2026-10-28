package io.github.currencortex.music.feature.settings

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.R
import io.github.currencortex.music.core.media.outputKey
import io.github.currencortex.music.core.media.outputLabel
import io.github.currencortex.music.core.media.CurrentMusicAudioDiagnostics
import io.github.currencortex.music.data.settings.AudioOutputSettings
import io.github.currencortex.music.data.settings.AudioOutputMode
import io.github.currencortex.music.ui.component.*
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun AudioOutputScreen(container: AppContainer) {
    val settings by container.audioOutput.state.collectAsStateWithLifecycle()
    val outputStatus by container.audioOutput.status.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AudioManager::class.java) }
    var devices by remember { mutableStateOf(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()) }
    DisposableEffect(manager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { refresh() }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { refresh() }
            fun refresh() { devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }
        }
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterAudioDeviceCallback(callback) }
    }
    val supportedRates = remember { AudioOutputSettings.rates.filter { it == 0 ||
        AudioTrack.getMinBufferSize(it, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT) > 0 } }
    val selectedDevice = devices.firstOrNull { it.outputKey() == settings.deviceKey }
    var bitPerfectDevices by remember { mutableStateOf("正在读取 USB 逐位输出能力…") }
    LaunchedEffect(devices, settings.deviceKey) {
        bitPerfectDevices = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (android.os.Build.VERSION.SDK_INT < 34) "当前系统低于 Android 14，无法启用 Bit-perfect"
            else {
                val usb = devices.filter { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
                    .filter { settings.deviceKey.isEmpty() || it.outputKey() == settings.deviceKey }
                if (usb.isEmpty()) "当前未连接或未选择 USB DAC"
                else usb.joinToString("\n") { device ->
                    CurrentMusicAudioDiagnostics.capabilities(manager, device)
                }
            }
        }
    }
    var picker by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var diagnosticMessage by remember { mutableStateOf<String?>(null) }
    val exportDiagnostics = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            try {
                val report = CurrentMusicAudioDiagnostics.fullSnapshot()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri)) { "无法打开文件" }.bufferedWriter(Charsets.UTF_8).use { it.write(report) }
                }
                diagnosticMessage = "完整诊断已导出"
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { diagnosticMessage = "导出失败，请重试" }
        }
    }
    fun save(action: suspend () -> Unit) { scope.launch {
        try { action(); error = null } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = "保存失败，请重试" }
    } }
    val hint = MiuixTheme.colorScheme.onSurfaceVariantSummary
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("audio_output_settings"),
        contentPadding = musicScrollPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("音频输出", fontSize = 28.sp) }
        item { MusicSectionHeader("音频输出模式") }
        item { Card { Column(Modifier.padding(vertical = 8.dp)) {
            AudioOutputMode.entries.forEach { mode ->
                MusicDialogChoiceRow(mode.label, settings.mode == mode, { save { container.audioOutput.setMode(mode) } },
                    Modifier.testTag("output_mode_${mode.name}"), summary = when (mode) {
                        AudioOutputMode.AUDIO_TRACK -> "系统音轨，支持系统音效"
                        AudioOutputMode.OPENSL_ES -> "固定 OpenSL ES，设备路由跟随系统"
                        AudioOutputMode.AAUDIO -> "固定 AAudio，原生低延迟输出"
                        AudioOutputMode.OBOE -> "自动选择 AAudio 或 OpenSL ES"
                        AudioOutputMode.DIRECT -> "PCM 16 bit，按音源采样率请求 AAudio 独占，跳过音效与重采样"
                        AudioOutputMode.BIT_PERFECT -> "Android 14+ 与兼容 USB DAC；保留解码后的 PCM 位深、采样率和声道"
                        AudioOutputMode.NETEASE_USB -> "内置网易云 USB 驱动，通过 USB Host 授权独占；实验性，非 Bit-perfect 保证"
                    }, showDivider = mode != AudioOutputMode.entries.last(), leadingContent = {
                        Icon(
                            painter = painterResource(when (mode) {
                                AudioOutputMode.AUDIO_TRACK -> R.drawable.ic_lucide_audio_lines
                                AudioOutputMode.OPENSL_ES -> R.drawable.ic_lucide_waves
                                AudioOutputMode.AAUDIO -> R.drawable.ic_lucide_activity
                                AudioOutputMode.DIRECT -> R.drawable.ic_lucide_cable
                                AudioOutputMode.OBOE -> R.drawable.ic_lucide_guitar
                                AudioOutputMode.BIT_PERFECT -> R.drawable.ic_lucide_binary
                                AudioOutputMode.NETEASE_USB -> R.drawable.ic_lucide_cable
                            }),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (settings.mode == mode) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    })
            }
        } } }
        item { Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(outputStatus, Modifier.testTag("output_status"), fontSize = 13.sp, color = hint)
            if (settings.mode == AudioOutputMode.BIT_PERFECT)
                Text(bitPerfectDevices, Modifier.testTag("bit_perfect_capability"), fontSize = 13.sp, color = hint)
            if (settings.mode.rawPcm)
                MusicDestinationRow("复制最近一次音频诊断", {
                    val recent = CurrentMusicAudioDiagnostics.latestAttempt()
                    clipboard.setText(AnnotatedString(if (CurrentMusicAudioDiagnostics.text.value.isEmpty()) "$recent\n$bitPerfectDevices" else recent))
                    diagnosticMessage = "已复制最近一次诊断"
                }, Modifier.testTag("copy_audio_diagnostics"), summary = "包含本次解码格式、支持配置、路由和结果")
            if (settings.mode.rawPcm)
                MusicDestinationRow("导出完整音频诊断", { exportDiagnostics.launch("CurrentMusicAudio.txt") },
                    Modifier.testTag("export_audio_diagnostics"), summary = "保存完整记录，便于分享和排查")
            diagnosticMessage?.let { Text(it, fontSize = 12.sp, color = hint) }
            Text(if (settings.mode == AudioOutputMode.BIT_PERFECT)
                "逐位输出关闭音效、变速、静音跳过和淡入淡出，使用 DAC 调节音量。设备或格式不支持时停止播放，不自动回退。仅用于主播放器，暂停唱片架及音效试听。"
                else if (settings.mode == AudioOutputMode.NETEASE_USB)
                    "USB Host 输出跳过应用音效、变速、重采样与淡入淡出；原驱动自身的音量和启动淡入仍然存在。不使用 Android MixerAttributes，设备不可用时停止播放。"
                else "原生输出不使用系统音效会话。接口或格式不可用时回退 AudioTrack；Direct 暂停变速与淡入淡出，是否获得独占以实际输出状态为准。",
                fontSize = 12.sp, color = hint)
        } }
        item { MusicSectionHeader("采样率") }
        item { Card {
            MusicDestinationRow("输出采样率", { picker = "rate" }, Modifier.testTag("output_sample_rate"),
                summary = if (settings.mode.bypassProcessing) "${settings.mode.label} 使用音源采样率" else if (settings.sampleRate == 0) "跟随音源" else "${settings.sampleRate} Hz",
                enabled = !settings.mode.bypassProcessing)
            Text("设置解码后送入输出接口的采样率。更改会在原进度重新准备本机播放，可能短暂停顿；共享输出时系统混音器仍可能重新采样。",
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp), fontSize = 13.sp, color = hint)
        } }
        item { MusicSectionHeader("输出设备") }
        if (settings.mode == AudioOutputMode.NETEASE_USB) item {
            NeteaseUsbOutputSettings(settings) { key -> save { container.audioOutput.setUsbDevice(key) } }
        }
        else item { Card {
            MusicDestinationRow("首选输出设备", { picker = "device" }, Modifier.testTag("output_device"),
                summary = selectedDevice?.outputLabel() ?: if (settings.deviceKey.isEmpty()) "跟随系统" else
                    if (settings.mode == AudioOutputMode.BIT_PERFECT) "设备未连接 · 逐位输出停止" else "设备未连接 · 跟随系统")
            Text(if (settings.mode == AudioOutputMode.BIT_PERFECT) "逐位输出必须确认实际 USB 路由；设备断开或路由不符时停止播放。"
                else "向系统请求使用所选设备；实际路由由系统决定。设备断开后回到系统默认输出。",
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp), fontSize = 13.sp, color = hint)
        } }
        item { MusicSectionHeader("设备报告的能力") }
        item { Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("系统首选采样率：${manager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.plus(" Hz") ?: "未报告"}", fontSize = 14.sp)
            Text("这是系统默认值，不代表当前 AudioTrack 或 DAC 的实际采样率。", fontSize = 12.sp, color = hint)
            (selectedDevice?.let(::listOf) ?: devices).forEach { device ->
                Text(device.outputLabel(), fontSize = 15.sp)
                Text(if (device.sampleRates.isEmpty()) "未限定采样率" else device.sampleRates.sorted().joinToString(" / ") { "$it Hz" },
                    fontSize = 12.sp, color = hint)
            }
            Text("设备报告值不代表当前实际输出格式。蓝牙编码与硬件独占输出由系统和设备决定。", fontSize = 12.sp, color = hint)
        } } }
        error?.let { message -> item { Text(message, color = MiuixTheme.colorScheme.error) } }
    }
    if (picker == "rate") MusicChoiceDialog("输出采样率", { picker = "" }) {
        supportedRates.forEach { rate -> MusicDialogChoiceRow(if (rate == 0) "跟随音源" else "$rate Hz",
            settings.sampleRate == rate, { save { container.audioOutput.setSampleRate(rate) }; picker = "" },
            Modifier.testTag("output_rate_$rate")) }
    }
    if (picker == "device") MusicChoiceDialog("首选输出设备", { picker = "" }) {
        MusicDialogChoiceRow("跟随系统", settings.deviceKey.isEmpty(), { save { container.audioOutput.setDevice("") }; picker = "" })
        devices.forEach { device -> MusicDialogChoiceRow(device.outputLabel(), settings.deviceKey == device.outputKey(),
            { save { container.audioOutput.setDevice(device.outputKey()) }; picker = "" }) }
    }
}
