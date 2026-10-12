package io.github.currencortex.music.feature.lyrics.desktop

import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable fun DesktopLyricsSettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val options by container.desktopLyrics.state.collectAsStateWithLifecycle()
    val status by container.desktopLyricsStatus.collectAsStateWithLifecycle()
    val playback by container.playerController.state.collectAsStateWithLifecycle()
    val ready = produceState(false, container) { container.desktopLyrics.ready.await(); value = true }.value
    val scope = rememberCoroutineScope()
    val activation = remember(container) { DesktopLyricsActivation(scope, { Settings.canDrawOverlays(context) },
        { container.playerController.connect() }, { enabled -> container.desktopLyrics.edit { it.copy(enabled = enabled) } }) }
    val action by activation.state.collectAsStateWithLifecycle()
    DisposableEffect(activation) { onDispose { activation.close() } }
    var permitted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var pending by rememberSaveable { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permitted = Settings.canDrawOverlays(context)
        val requested = pending; pending = false
        if (requested && permitted) activation.request(true)
        else if (requested) permissionError = "尚未授权。可以先调整样式，需要时再开启。"
    }
    val currentOptions by rememberUpdatedState(options)
    val life = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(life) {
        fun visibility() { container.desktopLyricsSettingsVisible.value = life.currentState.isAtLeast(Lifecycle.State.STARTED) }
        val observer = LifecycleEventObserver { _, event ->
            visibility()
            if (event == Lifecycle.Event.ON_RESUME) {
                permitted = Settings.canDrawOverlays(context)
                if (!permitted && currentOptions.enabled) activation.request(false)
            }
        }
        life.addObserver(observer); visibility()
        onDispose { life.removeObserver(observer); container.desktopLyricsSettingsVisible.value = false }
    }
    fun edit(change: (DesktopLyricsSettings) -> DesktopLyricsSettings) { scope.launch { container.desktopLyrics.edit(change) } }
    fun saveFont(value: Float) { edit { it.copy(fontSize = value) } }
    fun saveOpacity(value: Float) { edit { it.copy(opacity = value) } }
    fun saveOffset(value: Float) { edit { it.copy(offsetMs = value.toLong()) } }
    fun saveGlowStrength(value: Float) { edit { it.copy(glowStrength = value) } }
    fun saveGlowSpread(value: Float) { edit { it.copy(glowSpread = value) } }
    var font by remember(options.fontSize) { mutableFloatStateOf(options.fontSize) }
    var opacity by remember(options.opacity) { mutableFloatStateOf(options.opacity) }
    var offset by remember(options.offsetMs) { mutableFloatStateOf(options.offsetMs.toFloat()) }
    var glowStrength by remember(options.glowStrength) { mutableFloatStateOf(options.glowStrength) }
    var glowSpread by remember(options.glowSpread) { mutableFloatStateOf(options.glowSpread) }
    val preview = options.copy(fontSize = font, opacity = opacity, offsetMs = offset.toLong(), glowStrength = glowStrength, glowSpread = glowSpread)
    val hint = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val summary = when {
        !ready -> "正在读取设置"
        pending -> "等待系统授权，返回后开启"
        action.connecting -> "正在连接播放服务，可关闭以取消"
        !permitted -> "首次开启需要授予悬浮窗权限"
        !options.enabled -> "已关闭，样式与位置会保留"
        status.visible && options.locked -> "已锁定 · 触摸穿透，可在下方解锁"
        status.visible -> "编辑中 · 拖动窗口顶部调整位置"
        playback.song == null -> "已开启 · 播放音乐后同步显示"
        options.locked -> "已开启 · 返回桌面显示，窗口已锁定"
        else -> "已开启 · 返回桌面后可拖动窗口"
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxWidth().testTag("desktop_lyrics_settings"),
            contentPadding = musicScrollPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("桌面悬浮歌词", fontSize = 28.sp)
                Text("离开应用，也能跟着音乐唱。", fontSize = 13.sp, color = hint)
            } }
            item { Card(Modifier.fillMaxWidth()) {
                SettingsSwitch("开启桌面歌词", options.enabled || pending || action.connecting, { value ->
                    permissionError = null
                    if (!value) { pending = false; activation.request(false) }
                    else if (Settings.canDrawOverlays(context)) { permitted = true; activation.request(true) }
                    else { pending = true; try {
                        permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
                    } catch (_: RuntimeException) { pending = false; permissionError = "无法打开权限页面，请在系统设置中授予悬浮窗权限" } }
                }, Modifier.testTag("desktop_enable"), summary = summary, enabled = ready)
                if (options.enabled) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (options.locked) "窗口已锁定" else "窗口可拖动", Modifier.weight(1f), fontSize = 13.sp, color = hint)
                    MusicTextAction(if (options.locked) "解锁并调整" else "锁定窗口", { edit { it.copy(locked = !it.locked) } },
                        Modifier.testTag("desktop_lock").semantics { toggleableState = if (options.locked)
                            androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off })
                }
            } }
            (permissionError ?: action.error ?: status.error)?.let { message -> item {
                Text(message, color = MiuixTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } }
            item { DesktopLyricsPreview(container, preview) }
            item { MusicSectionHeader("外观与动效") }
            item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("背景外观", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { DesktopAppearance.entries.forEach { mode ->
                    DesktopChoice(mode.label, if (mode == DesktopAppearance.CAPTION) "只留下文字" else "渐变底色，清晰易读",
                        options.appearance == mode, { edit { it.copy(appearance = mode) } }, Modifier.weight(1f).testTag("desktop_appearance_${mode.name}"))
                } }
                Text("动画强度", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { DesktopEffect.entries.forEach { mode ->
                    DesktopChoice(mode.label, if (mode == DesktopEffect.PRECISE) "轻柔切换 · 细腻辉光" else "延音辉光 · 微幅上提",
                        options.effect == mode, { edit { it.copy(effect = mode) } }, Modifier.weight(1f).testTag("desktop_effect_${mode.name}"))
                } }
                SettingsSwitch("显示翻译", options.translation, { edit { s -> s.copy(translation = it) } }, Modifier.testTag("desktop_translation"), summary = "当前歌词下方显示翻译")
            } } }
            item { MusicSectionHeader("颜色与辉光") }
            item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DesktopLyricsColorControl("文字颜色", options.textColor, "desktop_text_color") { value -> edit { it.copy(textColor = value) } }
                SettingsSwitch("歌词辉光", options.glowEnabled, { value -> edit { it.copy(glowEnabled = value) } },
                    Modifier.testTag("desktop_glow_enabled"), summary = "整句歌词显示柔光，逐字歌词在延音与强调处亮起")
                if (options.glowEnabled) {
                    DesktopSlider("辉光强度", "${(glowStrength * 100).roundToInt()}%", glowStrength, 0f..2f, "desktop_glow_strength",
                        { glowStrength = (it * 100).roundToInt() / 100f }, { saveGlowStrength(glowStrength) },
                        { value -> glowStrength = value; saveGlowStrength(value) })
                    DesktopSlider("辉光范围", "${(glowSpread * 100).roundToInt()}%", glowSpread, .5f..2f, "desktop_glow_spread",
                        { glowSpread = (it * 10).roundToInt() / 10f }, { saveGlowSpread(glowSpread) },
                        { value -> glowSpread = (value * 10).roundToInt() / 10f; saveGlowSpread(glowSpread) })
                    DesktopLyricsColorControl("辉光颜色", options.glowColor, "desktop_glow_color", defaultColor = DESKTOP_DEFAULT_GLOW_COLOR) { value -> edit { it.copy(glowColor = value) } }
                }
                Text("整句柔光不模拟逐字扫光；精致模式更轻柔，舞台模式更明显。", fontSize = 11.sp, color = hint)
            } } }
            item { MusicSectionHeader("文字与同步") }
            item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DesktopSlider("字号", "${font.roundToInt()} sp", font, 18f..38f, "desktop_font", { font = it.roundToInt().toFloat() },
                    { saveFont(font) }, { value -> font = value.roundToInt().toFloat(); saveFont(font) })
                DesktopSlider("不透明度", "${(opacity * 100).roundToInt()}%", opacity, 0.35f..0.95f, "desktop_opacity", { opacity = (it * 100).roundToInt() / 100f },
                    { saveOpacity(opacity) }, { value -> opacity = value; saveOpacity(value) })
                if (options.enabled && options.locked && status.alpha > 0 && options.opacity > status.alpha + .005f)
                    Text("触摸穿透时，系统将实际不透明度限制为 ${(status.alpha * 100).roundToInt()}%。", fontSize = 12.sp, color = hint)
                val range = maxOf(3000f, abs(offset))
                DesktopSlider("歌词偏移", if (offset == 0f) "同步" else "${if (offset > 0) "+" else ""}${offset.toInt()} ms", offset, -range..range, "desktop_offset",
                    { offset = (it / 100).roundToInt() * 100f }, { saveOffset(offset) },
                    { value -> offset = (value / 100).roundToInt() * 100f; saveOffset(offset) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MusicTextAction("延后 100ms", { offset = (offset - 100).coerceAtLeast(-60000f); edit { it.copy(offsetMs = it.offsetMs - 100) } }, Modifier.testTag("desktop_offset_later"))
                    MusicTextAction("归零", { offset = 0f; saveOffset(0f) }, Modifier.testTag("desktop_offset_zero"), enabled = offset != 0f)
                    MusicTextAction("提前 100ms", { offset = (offset + 100).coerceAtMost(60000f); edit { it.copy(offsetMs = it.offsetMs + 100) } }, Modifier.testTag("desktop_offset_earlier"))
                }
                Text("正值提前，负值延后。拖动时预览，松手保存。", fontSize = 12.sp, color = hint)
            } } }
            item { MusicSectionHeader("窗口位置") }
            item { Card {
                SettingsSwitch("松手自动贴边", options.snapEdges, { edit { s -> s.copy(snapEdges = it) } }, Modifier.testTag("desktop_snap"), summary = "关闭后也会在接近边缘时吸附")
                MusicDestinationRow("重置窗口位置", { edit { it.copy(x = .5f, y = .2f) } }, Modifier.testTag("desktop_reset_position"), summary = "回到屏幕上方，不改变字号和效果", chevron = false)
            } }
            item { Text("普通歌词按句切换；逐字歌词支持扫光与延音辉光。桌面设置单独保存，不影响应用内歌词。锁定后可从通知或这里解锁。", fontSize = 12.sp, color = hint) }
        }
    }
}

@Composable private fun DesktopChoice(title: String, summary: String, chosen: Boolean, click: () -> Unit, modifier: Modifier) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(if (chosen) colors.primary.copy(alpha = .09f) else colors.onSurface.copy(alpha = .04f), shape)
        .border(1.dp, if (chosen) colors.primary.copy(alpha = .6f) else Color.Transparent, shape)
        .selectable(chosen, role = Role.RadioButton, onClick = click).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (chosen) colors.primary else colors.onSurface)
            if (chosen) Text("✓", fontSize = 14.sp, color = colors.primary)
        }
        Text(summary, fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
    }
}

@Composable private fun DesktopSlider(title: String, label: String, value: Float, range: ClosedFloatingPointRange<Float>, tag: String,
    change: (Float) -> Unit, finish: () -> Unit, accessible: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 15.sp)
            Text(label, fontSize = 13.sp, color = MiuixTheme.colorScheme.primary, modifier = Modifier.testTag("${tag}_value"))
        }
        Slider(value = value, onValueChange = change, modifier = Modifier.fillMaxWidth().testTag(tag).semantics {
            contentDescription = title
            setProgress { target -> if (!target.isFinite()) false else { accessible(target.coerceIn(range)); true } }
        }, valueRange = range, onValueChangeFinished = finish)
    }
}
