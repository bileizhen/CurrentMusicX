package io.github.currencortex.music.feature.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.NeteaseUsbDevices
import io.github.currencortex.music.data.settings.AudioOutputSettings
import io.github.currencortex.music.ui.component.MusicDestinationRow
import io.github.currencortex.music.ui.component.MusicDialogChoiceRow
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun NeteaseUsbOutputSettings(settings: AudioOutputSettings, select: (String) -> Unit) {
    val context = LocalContext.current
    val revision by NeteaseUsbDevices.revision.collectAsStateWithLifecycle()
    val devices = remember(revision) { NeteaseUsbDevices.devices(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { NeteaseUsbDevices.refresh() }
        }
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED).apply { addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) NeteaseUsbDevices.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) }
    }
    val manager = remember(context) { context.getSystemService(UsbManager::class.java) }
    var message by remember { mutableStateOf<String?>(null) }
    val hint = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Card { Column(Modifier.padding(vertical = 8.dp).testTag("netease_usb_settings")) {
        Text("USB Host 独占输出", Modifier.padding(horizontal = 20.dp, vertical = 10.dp), fontSize = 18.sp)
        Text("内置网易云 9.6.15 arm64 USB 驱动，直接访问授权设备。支持 8–192 kHz、单声道或立体声的 PCM 16/24/32-bit 整数；24-bit packed 保留全部样本位扩展至 32-bit。原驱动包含音量与淡入处理，此模式不标为 Bit-perfect。",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 13.sp, color = hint)
        if (devices.isEmpty()) Text("未连接 USB Audio 设备；连接后在此授权。", Modifier.padding(20.dp), fontSize = 14.sp)
        else devices.forEach { device ->
            val selected = settings.usbDeviceKey == NeteaseUsbDevices.key(device) || (settings.usbDeviceKey.isEmpty() && devices.size == 1)
            MusicDialogChoiceRow(NeteaseUsbDevices.label(device), selected, {
                select(NeteaseUsbDevices.key(device))
                if (!manager.hasPermission(device)) try { NeteaseUsbDevices.requestPermission(context, device) }
                catch (_: Exception) { message = "USB 授权请求失败，请重新连接设备" }
            }, summary = if (manager.hasPermission(device)) "已获得 USB 访问授权" else "点按选择并授权 USB 访问")
            if (selected && !manager.hasPermission(device)) MusicDestinationRow("授权访问 USB DAC", {
                try { NeteaseUsbDevices.requestPermission(context, device) }
                catch (_: Exception) { message = "USB 授权请求失败，请重新连接设备" }
            }, Modifier.testTag("request_usb_audio_permission"), summary = "系统授权成功后，再手动开始播放")
        }
        message?.let { Text(it, Modifier.padding(20.dp), fontSize = 13.sp, color = hint) }
        Text("拒绝授权、设备断开或实际输出格式不支持时停止。24-bit 音源需要同采样率、4 字节容器且至少 24 位有效数据的配置；不改变采样率，浮点暂不支持。可导出包含全部候选的音频诊断。",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 12.sp, color = hint)
    } }
}
