package io.github.currencortex.music.feature.lyrics.desktop

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.ui.component.nativeui.SettingsDialog
import kotlinx.coroutines.launch

/** A one-time, optional shortcut using the same activation guard as desktop settings. */
@Composable internal fun DesktopLyricsRecommendation(container: AppContainer, eligible: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offered by produceState(false, container) { value = container.desktopLyrics.shouldRecommend() }
    val options by container.desktopLyrics.state.collectAsStateWithLifecycle()
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val activation = remember(container) { DesktopLyricsActivation(scope, { Settings.canDrawOverlays(context) },
        { container.playerController.connect() }, { enabled -> container.desktopLyrics.edit { it.copy(enabled = enabled) } }) }
    val action by activation.state.collectAsStateWithLifecycle()
    DisposableEffect(activation) { onDispose { activation.close() } }
    LaunchedEffect(options.enabled) { if (options.enabled) dismissed = true }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val requested = pending; pending = false
        if (requested && Settings.canDrawOverlays(context)) activation.request(true)
        else if (requested) error = "尚未授予悬浮窗权限，可以稍后在设置中开启。"
    }
    fun markSeen() { scope.launch { container.desktopLyrics.markRecommendationSeen() } }
    fun dismiss() { dismissed = true; pending = false; activation.request(false); markSeen() }
    SettingsDialog(
        show = eligible && offered && !dismissed && !options.enabled,
        title = "试试桌面歌词灵动岛",
        onDismissRequest = ::dismiss,
        summary = buildString {
            append("悬浮在屏幕顶部，离开应用也能看歌词。")
            append("\n\n应用内默认显示 FPS，点击灵动岛可切换歌词与帧率。")
            (error ?: action.error)?.let { append("\n\n$it") }
        },
        positiveText = if (action.connecting) "正在开启…" else "快捷开启",
        onPositive = {
            error = null; markSeen()
            if (Settings.canDrawOverlays(context)) activation.request(true)
            else {
                pending = true
                try { permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri())) }
                catch (_: RuntimeException) { pending = false; error = "无法打开授权页，请在系统设置中允许悬浮窗。" }
            }
        },
        positiveEnabled = !action.connecting && !pending,
        negativeText = "暂不开启",
        onNegative = ::dismiss,
    )
}
