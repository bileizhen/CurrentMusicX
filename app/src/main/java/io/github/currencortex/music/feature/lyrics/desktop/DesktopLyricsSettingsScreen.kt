package io.github.currencortex.music.feature.lyrics.desktop

import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.ui.component.*
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun DesktopLyricsSettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val options by container.desktopLyrics.state.collectAsStateWithLifecycle()
    val status by container.desktopLyricsStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var permitted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var pending by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun enable() { scope.launch {
        if (!Settings.canDrawOverlays(context)) return@launch
        container.desktopLyrics.edit { it.copy(enabled = true, locked = false) }
        try { container.playerController.connect() } catch (_: Exception) {
            container.desktopLyrics.edit { it.copy(enabled = false) }; error = "无法连接播放服务，请重试"
        }
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permitted = Settings.canDrawOverlays(context)
        val requested = pending; pending = false
        if (requested && permitted) enable()
        else if (requested) error = "尚未授予悬浮窗权限，需要时再主动开启"
    }
    val currentOptions by rememberUpdatedState(options)
    val life = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(life) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            permitted = Settings.canDrawOverlays(context)
            if (!permitted && currentOptions.enabled) scope.launch { container.desktopLyrics.edit { it.copy(enabled = false) } }
        } }
        life.addObserver(observer); onDispose { life.removeObserver(observer) }
    }
    fun edit(change: (DesktopLyricsSettings) -> DesktopLyricsSettings) { scope.launch { container.desktopLyrics.edit(change) } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("desktop_lyrics_settings"),
        contentPadding = musicScrollPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("桌面悬浮歌词", fontSize = 28.sp) }
        item { Text("离开应用后继续跟随当前音乐。设置仅影响桌面歌词。", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        item { Card(Modifier.fillMaxWidth()) {
            SettingsSwitch("开启悬浮歌词", options.enabled, { value ->
                error = null
                if (!value) { pending = false; edit { it.copy(enabled = false) } }
                else if (permitted) enable()
                else { pending = true; try { permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())) }
                    catch (_: RuntimeException) { pending = false; error = "无法打开权限页面，请在系统设置中授予悬浮窗权限" } }
            }, Modifier.testTag("desktop_enable"), summary = if (!permitted) "需要在系统页面授予悬浮窗权限" else if (status.visible) "正在显示" else if (options.enabled) "已开启，等待播放服务或解锁屏幕" else "当前关闭")
            if (options.enabled) SettingsSwitch("锁定并穿透触摸", options.locked, { edit { s -> s.copy(locked = it) } },
                Modifier.testTag("desktop_lock"), summary = "锁定后可在这里或通知中解锁；系统会限制窗口的不透明度")
        } }
        (error ?: status.error)?.let { message -> item { Text(message, color = MiuixTheme.colorScheme.error, fontSize = 13.sp) } }
        item { MusicSectionHeader("外观与效果") }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("动画档位")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { DesktopEffect.entries.forEach { mode ->
                TextButton((if (options.effect == mode) "✓ " else "") + mode.label, { edit { it.copy(effect = mode) } }, Modifier.weight(1f).testTag("desktop_effect_${mode.name}"))
            } }
            Text("背景外观")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { DesktopAppearance.entries.forEach { appearance ->
                TextButton((if (options.appearance == appearance) "✓ " else "") + appearance.label, { edit { it.copy(appearance = appearance) } }, Modifier.weight(1f))
            } }
            SettingsSwitch("显示翻译", options.translation, { edit { s -> s.copy(translation = it) } }, summary = "仅显示当前句的翻译")
            Text("胶囊使用自身渐变与光泽，不读取其他应用的画面。", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DesktopAdjustment("字号", "${options.fontSize.toInt()} sp", { edit { it.copy(fontSize = it.fontSize - 2) } }, { edit { it.copy(fontSize = it.fontSize + 2) } })
            DesktopAdjustment("不透明度", "${(options.opacity * 100).toInt()}%", { edit { it.copy(opacity = it.opacity - .05f) } }, { edit { it.copy(opacity = it.opacity + .05f) } })
            DesktopAdjustment("歌词偏移", "${options.offsetMs} ms", { edit { it.copy(offsetMs = it.offsetMs - 100) } }, { edit { it.copy(offsetMs = it.offsetMs + 100) } })
            Text("正偏移让歌词提前，负偏移让歌词延后。", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            SettingsSwitch("松手自动贴边", options.snapEdges, { edit { s -> s.copy(snapEdges = it) } }, summary = "关闭时也会在接近边缘时贴边")
            MusicTextAction("重置字号、偏移与位置", { edit { it.copy(fontSize = 26f, offsetMs = 0, x = .5f, y = .2f) } })
        } } }
        item { Text("没有真实逐字时间戳时，仅切换整句；暂停或缓冲时停止扫光。窗口会在锁屏、熄屏和播放服务结束时隐藏。", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
    }
}
@Composable private fun DesktopAdjustment(title: String, value: String, less: () -> Unit, more: () -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(value, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        MusicTextAction("−", less); MusicTextAction("＋", more)
    }
}
