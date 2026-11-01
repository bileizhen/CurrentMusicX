package io.github.currencortex.music.feature.settings

import io.github.currencortex.music.ui.component.nativeui.*

import io.github.currencortex.music.ui.component.nativeui.SettingsPage
import io.github.currencortex.music.ui.component.nativeui.SettingsUiScope
import io.github.currencortex.music.ui.component.nativeui.SettingsGroup as Card
import io.github.currencortex.music.ui.component.nativeui.SettingsChoiceRow
import io.github.currencortex.music.ui.component.nativeui.SettingsOptionsRow
import io.github.currencortex.music.ui.component.nativeui.SettingsOption

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
import androidx.compose.ui.draw.rotate
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
    SettingsUiScope {
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

    val modeDescriptions = mapOf(
        AudioOutputMode.AUDIO_TRACK to "系统音轨，支持系统音效",
        AudioOutputMode.OPENSL_ES to "固定 OpenSL ES，设备路由跟随系统",
        AudioOutputMode.AAUDIO to "固定 AAudio，原生低延迟输出",
        AudioOutputMode.OBOE to "自动选择 AAudio 或 OpenSL ES",
        AudioOutputMode.DIRECT to "PCM 16 bit，按音源采样率请求 AAudio 独占，跳过音效与重采样",
        AudioOutputMode.BIT_PERFECT to "Android 14+ 与兼容 USB DAC，保留解码后的 PCM 位深、采样率和声道",
        AudioOutputMode.NETEASE_USB to "内置 USB Host 驱动，需授权独占设备；实验性输出"
    )
    var capabilitiesOpen by remember { mutableStateOf(false) }
    SettingsPage("音频输出", "audio_output_settings") {
        item("mode") { SettingsSection("输出方式", MusicIcons.Headphones,
            footer = when (settings.mode) {
                AudioOutputMode.BIT_PERFECT -> "逐位输出关闭音效、变速、静音跳过与淡入淡出。使用 DAC 调节音量；设备或格式不支持时停止，不自动回退。仅用于主播放器。"
                AudioOutputMode.NETEASE_USB -> "USB Host 跳过应用音效、变速、重采样与淡入淡出。原驱动自身的音量与启动淡入仍存在，不保证 Bit-perfect；设备不可用时停止。"
                else -> "原生接口不可用时回退 AudioTrack；Direct 暂停变速与淡入淡出，独占结果以实际输出状态为准。"
            }) {
            SettingsOptionsRow("输出模式", settings.mode.label, AudioOutputMode.entries.map { mode ->
                SettingsOption("output_mode_${mode.name}", mode.label, settings.mode == mode,
                    summary = modeDescriptions.getValue(mode), onSelect = { save { container.audioOutput.setMode(mode) } })
            }, Modifier.testTag("output_mode"))
            SettingsDivider()
            SettingsPreference("运行状态", outputStatus, Modifier.testTag("output_status"))
            if (settings.mode == AudioOutputMode.BIT_PERFECT)
                SettingsHint(bitPerfectDevices, Modifier.testTag("bit_perfect_capability"))
        } }
        item("format") { SettingsSection("音频格式", MusicIcons.File,
            footer = "设置解码后送入接口的采样率。更改会在原进度重新准备播放；共享输出时系统仍可能重采样。") {
            SettingsOptionsRow("输出采样率", if (settings.mode.bypassProcessing || settings.sampleRate == 0) "跟随音源" else "${settings.sampleRate} Hz",
                supportedRates.map { rate -> SettingsOption("output_rate_$rate", if (rate == 0) "跟随音源" else "$rate Hz",
                    settings.sampleRate == rate, onSelect = { save { container.audioOutput.setSampleRate(rate) } }) },
                Modifier.testTag("output_sample_rate"),
                summary = if (settings.mode.bypassProcessing) "${settings.mode.label} 使用音源采样率" else null,
                enabled = !settings.mode.bypassProcessing)
        } }
        if (settings.mode == AudioOutputMode.NETEASE_USB) item("usb") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsInfoHeader("输出设备", MusicIcons.Device, horizontalInset = 0.dp)
                NeteaseUsbOutputSettings(settings) { key -> save { container.audioOutput.setUsbDevice(key) } }
            }
        } else item("device") { SettingsSection("输出设备", MusicIcons.Device,
            footer = if (settings.mode == AudioOutputMode.BIT_PERFECT) "逐位输出必须确认实际 USB 路由；设备断开或路由不符时停止播放。"
                else "实际路由由系统决定。设备断开后回到系统默认输出。") {
            SettingsOptionsRow("首选设备", selectedDevice?.outputLabel() ?: if (settings.deviceKey.isEmpty()) "跟随系统" else "设备未连接",
                listOf(SettingsOption("output_device_system", "跟随系统", settings.deviceKey.isEmpty(),
                    onSelect = { save { container.audioOutput.setDevice("") } })) + devices.map { device ->
                    SettingsOption("output_device_${device.id}", device.outputLabel(), settings.deviceKey == device.outputKey(),
                        onSelect = { save { container.audioOutput.setDevice(device.outputKey()) } })
                }, Modifier.testTag("output_device"))
        } }
        item("diagnostics") { SettingsSection("设备与诊断", MusicIcons.Info,
            footer = "设备报告值不代表当前实际格式。蓝牙编码与硬件独占由系统和设备决定。") {
            SettingsPreference("设备报告的能力", if (capabilitiesOpen) "收起设备信息" else "采样率与系统首选配置",
                Modifier.testTag("output_capabilities"), onClick = { capabilitiesOpen = !capabilitiesOpen },
                trailing = { Icon(MusicIcons.Forward, null, Modifier.size(18.dp).rotate(if (capabilitiesOpen) 90f else 0f)) })
            androidx.compose.animation.AnimatedVisibility(capabilitiesOpen) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("系统首选采样率：${manager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.plus(" Hz") ?: "未报告"}")
                    Text("系统默认值不代表 AudioTrack 或 DAC 的实际采样率。", fontSize = 13.sp, color = hint)
                    (selectedDevice?.let(::listOf) ?: devices).forEach { device ->
                        Text(device.outputLabel())
                        Text(if (device.sampleRates.isEmpty()) "未限定采样率" else device.sampleRates.sorted().joinToString(" / ") { "$it Hz" },
                            fontSize = 13.sp, color = hint)
                    }
                }
            }
            if (settings.mode.rawPcm) {
                SettingsDivider()
                SettingsPreference("复制最近一次音频诊断", "本次解码格式、支持配置、路由与结果",
                    Modifier.testTag("copy_audio_diagnostics"), onClick = {
                        val recent = CurrentMusicAudioDiagnostics.latestAttempt()
                        clipboard.setText(AnnotatedString(if (CurrentMusicAudioDiagnostics.text.value.isEmpty()) "$recent\n$bitPerfectDevices" else recent))
                        diagnosticMessage = "已复制最近一次诊断"
                    })
                SettingsDivider()
                SettingsPreference("导出完整音频诊断", "保存完整记录，便于分享与排查",
                    Modifier.testTag("export_audio_diagnostics"), onClick = { exportDiagnostics.launch("CurrentMusicAudio.txt") })
            }
            diagnosticMessage?.let { SettingsHint(it) }
        } }
        error?.let { message -> item { SettingsHint(message, error = true) } }
    }
    }
}
