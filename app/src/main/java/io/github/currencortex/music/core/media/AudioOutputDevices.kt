package io.github.currencortex.music.core.media

import android.media.AudioDeviceInfo
import android.media.AudioManager

internal fun AudioDeviceInfo.outputKey() = "$type:$address:$productName"
internal fun AudioDeviceInfo.outputLabel(): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "设备扬声器"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "听筒"
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机 · $productName"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙 · $productName"
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB · $productName"
    else -> productName.toString()
}
internal fun AudioManager.preferredOutput(key: String): AudioDeviceInfo? =
    getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.outputKey() == key }
