package io.github.currencortex.music.feature.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.dlna.DlnaDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.ui.component.MusicDialog
import top.yukonga.miuix.kmp.basic.*

data class CastDevices(val devices: List<DlnaDevice> = emptyList(), val scanning: Boolean = false, val error: String? = null)
class CastViewModel(val container: AppContainer) : ViewModel() {
    val devices = MutableStateFlow(CastDevices())
    private var scan: Job? = null
    fun scan() {
        if (devices.value.scanning) return
        scan = viewModelScope.launch {
            devices.value = CastDevices(scanning = true)
            try { devices.value = CastDevices(container.dlnaDiscovery.scan()) }
            catch (e: CancellationException) { devices.value = devices.value.copy(scanning = false); throw e }
            catch (_: Exception) { devices.value = CastDevices(error = "搜索失败，请连接 Wi-Fi 并确认设备支持 DLNA") }
        }
    }
    fun cancelScan() { scan?.cancel() }
}
@Composable fun CastScreen(vm: CastViewModel, onBack: () -> Unit, onDialogActive: (Boolean) -> Unit = {}) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val cast by vm.container.dlnaController.state.collectAsStateWithLifecycle()
    val player by vm.container.playerController.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<DlnaDevice?>(null) }
    LaunchedEffect(confirm != null) { onDialogActive(confirm != null) }
    var volume by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(vm) { onDispose { vm.cancelScan(); onDialogActive(false) } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("cast_screen"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton("返回", onClick = onBack); Text("DLNA 投屏", fontSize = 28.sp) }
        item { Text("手机与设备需要连接同一 Wi-Fi。投屏后，手机暂停发声，由设备播放音乐。") }
        cast.device?.let { device -> item {
            Text(device.name, fontSize = 23.sp); Text(player.song?.name.orEmpty()); Text(cast.quality)
            Row { TextButton(if (player.playing) "暂停" else "播放", onClick = { vm.container.dlnaController.play(!player.playing) }, enabled = !cast.busy)
                TextButton("下一首", onClick = vm.container.dlnaController::next, enabled = !cast.busy) }
            val duration = player.durationMs.coerceAtLeast(1)
            var seek by remember { mutableStateOf<Float?>(null) }
            Slider(seek ?: (player.positionMs.toFloat() / duration).coerceIn(0f, 1f), { seek = it },
                onValueChangeFinished = { seek?.let { vm.container.dlnaController.seek((it * duration).toLong()) }; seek = null })
            cast.volume?.let { current ->
                Text("音量 ${volume?.toInt() ?: current}")
                Slider(volume ?: current.toFloat(), { volume = it }, valueRange = 0f..100f,
                    onValueChangeFinished = { volume?.let { vm.container.dlnaController.volume(it.toInt()) }; volume = null })
            }
            TextButton("使用兼容音质", onClick = vm.container.dlnaController::compatible, enabled = !cast.busy)
            TextButton("结束投屏", onClick = vm.container.dlnaController::stop)
        } }
        cast.error?.let { item { Text(it) } }
        item { TextButton(if(devices.scanning) "正在搜索…" else "搜索设备", onClick = vm::scan, enabled = !devices.scanning && cast.device == null, modifier = Modifier.testTag("scan_dlna")) }
        devices.error?.let { item { Text(it) } }
        if (!devices.scanning && devices.devices.isEmpty()) item { Text("未发现设备。确认电视已开启 DLNA 媒体接收，或重新搜索。") }
        items(devices.devices, key = { it.id }) { device ->
            TextButton(device.name, onClick = { confirm = device }, enabled = cast.device == null && !cast.busy)
        }
    }
    confirm?.let { device -> MusicDialog("投屏到 ${device.name}", onDismiss = { confirm = null }) {
        Text("默认尝试无损音质；设备不兼容时回退到极高音质。")
        TextButton("开始投屏", onClick = { confirm = null; vm.container.dlnaController.start(device) })
    } }
}
