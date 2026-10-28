package io.github.currencortex.music.core.media

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object NeteaseUsbDevices {
    private val changed = MutableStateFlow(0)
    val revision = changed.asStateFlow()
    fun refresh() { changed.value += 1 }
    fun devices(context: Context) = context.getSystemService(UsbManager::class.java).deviceList.values
        .filter { device -> (0 until device.interfaceCount).any {
            val face = device.getInterface(it)
            face.interfaceClass == UsbConstants.USB_CLASS_AUDIO && face.interfaceSubclass == 2 &&
                (0 until face.endpointCount).any { endpoint -> face.getEndpoint(endpoint).direction == UsbConstants.USB_DIR_OUT }
        } }.sortedBy { it.deviceId }
    fun key(device: UsbDevice) = "${device.vendorId}:${device.productId}:${device.deviceName}"
    fun label(device: UsbDevice) = device.productName ?: "USB Audio ${device.vendorId.toString(16)}:${device.productId.toString(16)}"
    fun select(devices: List<UsbDevice>, selected: String): UsbDevice {
        if (selected.isNotEmpty()) return devices.singleOrNull { key(it) == selected }
            ?: error("[USB_MISSING] 所选 USB Host 设备未连接，请重新选择")
        check(devices.isNotEmpty()) { "[USB_MISSING] 未连接 USB Audio 输出设备" }
        check(devices.size == 1) { "[USB_AMBIGUOUS] 已连接多个 USB 音频设备，请选择目标 DAC" }
        return devices.single()
    }
    fun requestPermission(context: Context, device: UsbDevice) {
        val intent = Intent(context, NeteaseUsbPermissionReceiver::class.java).setAction("${context.packageName}.USB_AUDIO_PERMISSION")
        val pending = PendingIntent.getBroadcast(context, device.deviceId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        context.getSystemService(UsbManager::class.java).requestPermission(device, pending)
    }
}

/** Explicit, non-exported PendingIntent recipient; no automatic playback on authorization. */
class NeteaseUsbPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "${context.packageName}.USB_AUDIO_PERMISSION") return
        val manager = context.getSystemService(UsbManager::class.java)
        @Suppress("DEPRECATION") val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        val authorized = device != null && manager.deviceList.values.any { it.deviceId == device.deviceId } && manager.hasPermission(device)
        CurrentMusicAudioDiagnostics.record("usbHost.permissionGranted=$authorized")
        NeteaseUsbDevices.refresh()
    }
}
