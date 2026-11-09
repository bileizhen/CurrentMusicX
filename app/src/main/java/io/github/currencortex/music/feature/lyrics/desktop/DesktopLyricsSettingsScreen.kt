package io.github.currencortex.music.feature.lyrics.desktop

import io.github.currencortex.music.ui.component.nativeui.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

import io.github.currencortex.music.ui.component.nativeui.GlassSlider as Slider


import io.github.currencortex.music.ui.component.nativeui.SettingsPage
import io.github.currencortex.music.ui.component.nativeui.SettingsUiScope
import io.github.currencortex.music.ui.component.nativeui.SettingsGroup as Card

import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
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
    fun saveFont(value: Float) { edit { it.copy(islandFontSize = value) } }
    fun saveOpacity(value: Float) { edit { it.copy(islandOpacity = value) } }
    fun saveOffset(value: Float) { edit { it.copy(offsetMs = value.toLong()) } }
    fun saveGlowStrength(value: Float) { edit { it.copy(glowStrength = value) } }
    fun saveGlowSpread(value: Float) { edit { it.copy(glowSpread = value) } }
    var font by remember(options.displayFontSize) { mutableFloatStateOf(options.displayFontSize) }
    var islandHeight by remember(options.islandY) { mutableFloatStateOf(options.islandY) }
    fun saveIslandHeight(value: Float) { edit { it.copy(islandY = value) } }
    var opacity by remember(options.displayOpacity) { mutableFloatStateOf(options.displayOpacity) }
    var offset by remember(options.offsetMs) { mutableFloatStateOf(options.offsetMs.toFloat()) }
    var glowStrength by remember(options.glowStrength) { mutableFloatStateOf(options.glowStrength) }
    var glowSpread by remember(options.glowSpread) { mutableFloatStateOf(options.glowSpread) }
    val preview = options.copy(islandFontSize = font, islandOpacity = opacity, offsetMs = offset.toLong(), glowStrength = glowStrength, glowSpread = glowSpread)
    val summary = when {
        !ready -> "正在读取设置"
        pending -> "等待系统授权，返回后开启"
        action.connecting -> "正在连接播放服务，可关闭以取消"
        !permitted -> "首次开启需要授予悬浮窗权限"
        !options.enabled -> "已关闭，样式与位置会保留"
        status.visible -> "点击切换歌词与帧率 · 左右滑动切歌"
        playback.song == null -> "已开启 · 播放音乐后同步显示"
        else -> "已开启 · 返回桌面显示居中灵动岛"
    }

    var glowOpen by rememberSaveable { mutableStateOf(false) }
    val glowArrow by animateFloatAsState(if (glowOpen) 90f else 0f, tween(200), label = "glow details")
    SettingsUiScope {
        SettingsPage("桌面悬浮歌词", "desktop_lyrics_settings") {
            item("enable") { Card {
                SettingsSwitch("开启桌面歌词", options.enabled || pending || action.connecting, { value ->
                    permissionError = null
                    if (!value) { pending = false; activation.request(false) }
                    else if (Settings.canDrawOverlays(context)) { permitted = true; activation.request(true) }
                    else { pending = true; try {
                        permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
                    } catch (_: RuntimeException) { pending = false; permissionError = "无法打开权限页面，请在系统设置中授予悬浮窗权限" } }
                }, Modifier.testTag("desktop_enable"), summary = summary, enabled = ready)
            } }
            (permissionError ?: action.error ?: status.error)?.let { message -> item("error") {
                SettingsHint(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, error = true)
            } }
            item("preview") { DesktopLyricsPreview(container, preview) }
            item("style") { SettingsSection("显示样式", MusicIcons.Font) {
                SettingsOptionsRow("动画效果", options.effect.label, DesktopEffect.entries.map { mode ->
                    SettingsOption("desktop_effect_${mode.name}", mode.label, options.effect == mode,
                        summary = if (mode == DesktopEffect.PRECISE) "轻柔切换 · 细腻辉光" else "延音辉光 · 微幅上提",
                        onSelect = { edit { it.copy(effect = mode) } })
                }, Modifier.testTag("desktop_effect"))
                SettingsDivider()
                SettingsSwitch("显示翻译", options.translation, { edit { s -> s.copy(translation = it) } },
                    Modifier.testTag("desktop_translation"), summary = "显示在当前歌词下方")
                SettingsDivider()
                DesktopLyricsColorControl("文字颜色", options.textColor, "desktop_text_color") { value -> edit { it.copy(textColor = value) } }
                SettingsDivider()
                DesktopSlider("字号", "${font.roundToInt()} sp", font, 12f..24f, "desktop_font", { font = it.roundToInt().toFloat() },
                    { saveFont(font) }, { value -> font = value.roundToInt().toFloat(); saveFont(font) },
                    reset = { font = DESKTOP_ISLAND_FONT_SIZE; saveFont(font) },
                    resetVisible = font != DESKTOP_ISLAND_FONT_SIZE, resetTag = "desktop_font_reset")
                SettingsDivider()
                DesktopSlider("不透明度", "${(opacity * 100).roundToInt()}%", opacity, .35f..1f, "desktop_opacity",
                    { opacity = (it * 100).roundToInt() / 100f }, { saveOpacity(opacity) },
                    { value -> opacity = value; saveOpacity(value) })
                if (options.enabled && status.alpha > 0 && options.displayOpacity > status.alpha + .005f)
                    SettingsHint("触摸穿透时，实际不透明度上限为 ${(status.alpha * 100).roundToInt()}%。")
            } }
            item("glow") { SettingsSection("辉光", MusicIcons.Sun) {
                SettingsSwitch("歌词辉光", options.glowEnabled, { value -> edit { it.copy(glowEnabled = value) } },
                    Modifier.testTag("desktop_glow_enabled"), summary = "整句柔光，逐字延音与强调处增强")
                AnimatedVisibility(options.glowEnabled) { Column {
                    SettingsDivider()
                    SettingsPreference("调节辉光", "强度 ${(glowStrength * 100).roundToInt()}% · 范围 ${(glowSpread * 100).roundToInt()}%",
                        Modifier.testTag("desktop_glow_details").semantics { stateDescription = if (glowOpen) "已展开" else "已收起" },
                        onClick = { glowOpen = !glowOpen }, trailing = {
                            Icon(MusicIcons.Forward, null, Modifier.size(18.dp).rotate(glowArrow))
                        })
                    AnimatedVisibility(glowOpen) { Column {
                        DesktopSlider("强度", "${(glowStrength * 100).roundToInt()}%", glowStrength, 0f..2f, "desktop_glow_strength",
                            { glowStrength = (it * 100).roundToInt() / 100f }, { saveGlowStrength(glowStrength) },
                            { value -> glowStrength = value; saveGlowStrength(value) })
                        DesktopSlider("范围", "${(glowSpread * 100).roundToInt()}%", glowSpread, .5f..2f, "desktop_glow_spread",
                            { glowSpread = (it * 10).roundToInt() / 10f }, { saveGlowSpread(glowSpread) },
                            { value -> glowSpread = (value * 10).roundToInt() / 10f; saveGlowSpread(glowSpread) })
                        DesktopLyricsColorControl("辉光颜色", options.glowColor, "desktop_glow_color",
                            defaultColor = DESKTOP_DEFAULT_GLOW_COLOR) { value -> edit { it.copy(glowColor = value) } }
                    } }
                } }
            } }
            item("window") { SettingsSection("窗口位置", MusicIcons.BottomBar) {
                DesktopSlider("高度", "${(islandHeight * 100).roundToInt()}%", islandHeight, 0f..1f, "desktop_island_height",
                    { islandHeight = it }, { saveIslandHeight(islandHeight) }, { value -> islandHeight = value; saveIslandHeight(value) },
                    reset = { islandHeight = DESKTOP_ISLAND_HEIGHT; saveIslandHeight(islandHeight) },
                    resetVisible = islandHeight != DESKTOP_ISLAND_HEIGHT, resetTag = "desktop_island_height_reset")
                SettingsHint("0% 屏幕顶部 · 100% 屏幕底部")
            } }
            item("sync") { SettingsSection("歌词同步", MusicIcons.Update) {
                val range = maxOf(3000f, abs(offset))
                DesktopSlider("歌词偏移", if (offset == 0f) "同步" else "${if (offset > 0) "+" else ""}${offset.toInt()} ms",
                    offset, -range..range, "desktop_offset", { offset = (it / 100).roundToInt() * 100f }, { saveOffset(offset) },
                    { value -> offset = (value / 100).roundToInt() * 100f; saveOffset(offset) })
                FlowRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MusicTextAction("延后 100ms", { offset = (offset - 100).coerceAtLeast(-60000f); saveOffset(offset) }, Modifier.testTag("desktop_offset_later"))
                    MusicTextAction("归零", { offset = 0f; saveOffset(0f) }, Modifier.testTag("desktop_offset_zero"), enabled = offset != 0f)
                    MusicTextAction("提前 100ms", { offset = (offset + 100).coerceAtMost(60000f); saveOffset(offset) }, Modifier.testTag("desktop_offset_earlier"))
                }
            } }
        }
    }
}

@Composable private fun DesktopSlider(title: String, label: String, value: Float, range: ClosedFloatingPointRange<Float>, tag: String,
    change: (Float) -> Unit, finish: () -> Unit, accessible: (Float) -> Unit,
    reset: (() -> Unit)? = null, resetVisible: Boolean = false, resetTag: String = "") {
    SettingsPreference(title, trailing = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontSize = 13.sp, color = MiuixTheme.colorScheme.primary, modifier = Modifier.testTag("${tag}_value"))
            if (reset != null) SettingsFadingReset(resetVisible, reset, "恢复默认$title", Modifier.testTag(resetTag))
        }
    }, footer = {
        Slider(value = value, onValueChange = change, modifier = Modifier.fillMaxWidth().testTag(tag).semantics {
            contentDescription = title
            setProgress { target -> if (!target.isFinite()) false else { accessible(target.coerceIn(range)); true } }
        }, valueRange = range, onValueChangeFinished = finish)
    })
}
