package io.github.currencortex.music.feature.settings

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.logging.LogRedactor
import io.github.currencortex.music.core.media.CurrentMusicAudioDiagnostics
import io.github.currencortex.music.core.media.outputKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun audioOutputDiagnosticReport(container: AppContainer): String =
    audioOutputDiagnosticReport(container, usbCapabilities = null)

internal suspend fun audioOutputDiagnosticReport(container: AppContainer, usbCapabilities: String?): String =
    withContext(Dispatchers.IO) {
        val structured = container.audioDiagnostics.text()
        val capability = usbCapabilities ?: audioOutputUsbCapabilities(
            manager = container.originalEffectsPageAudioManager(),
            deviceKey = container.audioOutput.state.value.deviceKey,
        )
        val nativeSnapshot = CurrentMusicAudioDiagnostics.fullSnapshot()
        LogRedactor.redact("$structured\nUSB 系统能力查询:\n$capability\n\n完整原生诊断:\n$nativeSnapshot")
    }

internal fun audioOutputUsbCapabilities(
    manager: AudioManager?,
    deviceKey: String,
    devices: List<AudioDeviceInfo>? = null,
): String {
    if (Build.VERSION.SDK_INT < 34) return "当前系统低于 Android 14，无法启用 Bit-perfect"
    if (manager == null) return "系统未提供 AudioManager，无法查询 USB DAC 能力"

    val usbDevices = (devices ?: manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()).filter { device ->
        (device.type == AudioDeviceInfo.TYPE_USB_DEVICE || device.type == AudioDeviceInfo.TYPE_USB_HEADSET) &&
            (deviceKey.isEmpty() || device.outputKey() == deviceKey)
    }
    if (usbDevices.isEmpty()) return "当前未连接或未选择 USB DAC"
    return usbDevices.joinToString("\n") { CurrentMusicAudioDiagnostics.capabilities(manager, it) }
}
