package io.github.currencortex.music.core.media.engine

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/** One shared callback while a service/settings consumer is active; no lifetime-long background poll. */
class AudioDeviceMonitor(context: Context, scope: CoroutineScope) {
    private val manager = context.applicationContext.getSystemService(AudioManager::class.java)
    val devices: StateFlow<List<AudioDeviceInfo>> = callbackFlow {
        fun snapshot() { trySend(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()) }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = snapshot()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = snapshot()
        }
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        snapshot()
        awaitClose { manager.unregisterAudioDeviceCallback(callback) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList())
    fun capabilities() = devices.value.map { device -> device.productName.toString() to AudioDeviceCapabilities(
        device.sampleRates.toSet(), device.encodings.toSet(), device.channelCounts.toSet(),
        device.type == AudioDeviceInfo.TYPE_USB_DEVICE || device.type == AudioDeviceInfo.TYPE_USB_HEADSET) }
}
