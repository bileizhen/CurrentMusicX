package io.github.currencortex.music.feature.visualizer

import android.content.*
import android.hardware.display.DisplayManager
import android.os.*
import android.view.View
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import io.github.currencortex.music.data.visualizer.VisualizerDisplayState

/** Read only: no preferredDisplayModeId, global settings, or competing refresh-rate requests. */
@Composable internal fun rememberVisualizerDisplay(view: View): State<VisualizerDisplayState> {
    val context = view.context.applicationContext
    val result = remember(view) { mutableStateOf(VisualizerDisplayState()) }
    DisposableEffect(view) {
        val manager = context.getSystemService(DisplayManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        fun read() {
            val display = view.display ?: manager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val mode = display?.mode
            val rates = display?.supportedModes?.filter { it.physicalWidth == mode?.physicalWidth && it.physicalHeight == mode?.physicalHeight }
                ?.map { it.refreshRate }?.distinct().orEmpty()
            result.value = VisualizerDisplayState(display?.refreshRate ?: 60f, rates.ifEmpty { listOf(60f) },
                power.isPowerSaveMode, Build.VERSION.SDK_INT >= 29 && power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE)
        }
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = read()
            override fun onDisplayRemoved(id: Int) = read()
            override fun onDisplayChanged(id: Int) = read()
        }
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context, intent: Intent) = read() }
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        // This is a protected system broadcast, supported without exported receiver flags on API 26+.
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        val thermal = if (Build.VERSION.SDK_INT >= 29) PowerManager.OnThermalStatusChangedListener { read() } else null
        if (Build.VERSION.SDK_INT >= 29 && thermal != null) power.addThermalStatusListener(context.mainExecutor, thermal)
        read()
        onDispose {
            manager.unregisterDisplayListener(listener); context.unregisterReceiver(receiver)
            if (Build.VERSION.SDK_INT >= 29 && thermal != null) power.removeThermalStatusListener(thermal)
        }
    }
    return result
}
