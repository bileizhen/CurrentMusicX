package io.github.currencortex.music.feature.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.ui.component.MusicDialog
import io.github.currencortex.music.ui.component.MusicDialogActions
import io.github.currencortex.music.ui.component.MusicDialogButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun PlayerBackgroundDialog(container: AppContainer, onDismiss: () -> Unit) {
    val config by container.settings.state.collectAsStateWithLifecycle()
    val queue by container.playbackQueue.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    // Miuix retains drag callbacks. Keep the state object stable across persisted updates.
    var blur by remember { mutableFloatStateOf(config.playerBackgroundBlur) }
    LaunchedEffect(config.playerBackgroundBlur) { blur = config.playerBackgroundBlur }
    val file = remember(config.playerBackground) { container.playerBackgrounds.file(config.playerBackground) }
    val canAdjustBlur = !busy && (file != null || !config.playerDynamicBackground)
    fun operation(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { container.errorNotices.show(
                if (failure is IllegalArgumentException || failure is IllegalStateException)
                    failure.message ?: "背景图片保存失败，请重试" else "无法读取背景图片，请重新选择") }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) operation { container.playerBackgrounds.import(uri) }
    }
    fun saveBlur() {
        val value = blur
        scope.launch { container.settings.edit { it.copy(playerBackgroundBlur = value) } }
    }
    MusicDialog("播放背景", { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())
            .testTag("player_background_settings"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PlayerBackdrop(queue.current?.cover.orEmpty(), Modifier.fillMaxWidth().height(140.dp)
                .clip(RoundedCornerShape(18.dp)).testTag("player_background_preview"), file, blur,
                dynamicBackground = config.playerDynamicBackground)
            Text(if (busy) "正在保存图片…" else if (file != null) "自定义背景 · 图片仅保存在本机"
                else if (config.playerBackground.isNotBlank()) "图片不可用，请重新选择"
                else if (config.playerDynamicBackground) "流动背景 · 跟随歌曲封面取色"
                else "封面毛玻璃 · 播放与歌词共用柔和背景", fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            MusicDialogActions(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(false to "封面毛玻璃", true to "流动背景").forEach { (dynamic, label) ->
                    val chosen = file == null && config.playerDynamicBackground == dynamic
                    MusicDialogButton(if (chosen) "✓ $label" else label, enabled = !busy && file == null,
                        colors = if (chosen) ButtonDefaults.textButtonColors(color = MiuixTheme.colorScheme.primary,
                            textColor = MiuixTheme.colorScheme.onPrimary) else ButtonDefaults.textButtonColors(),
                        modifier = Modifier.weight(1f).testTag(if (dynamic) "player_dynamic_background" else "player_frosted_background")
                            .semantics { selected = chosen },
                        onClick = { scope.launch { container.settings.edit { it.copy(playerDynamicBackground = dynamic) } } })
                }
            }
            if (file != null) Text("恢复默认背景后可选择封面效果", fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            MusicDialogActions(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MusicDialogButton(if (file == null) "选择图片" else "更换图片",
                    colors = ButtonDefaults.textButtonColors(color = MiuixTheme.colorScheme.primary, textColor = MiuixTheme.colorScheme.onPrimary),
                    enabled = !busy, modifier = Modifier.weight(1f).testTag("player_background_choose"),
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                MusicDialogButton("恢复默认", enabled = config.playerBackground.isNotBlank() && !busy,
                    modifier = Modifier.weight(1f).testTag("player_background_reset"),
                    onClick = { operation { container.playerBackgrounds.clear() } })
            }
            Text("模糊度 ${blur.roundToInt()}%", fontSize = 15.sp)
            Slider(value = blur, onValueChange = { blur = it }, onValueChangeFinished = ::saveBlur,
                valueRange = 0f..100f, enabled = canAdjustBlur,
                modifier = Modifier.fillMaxWidth().testTag("player_background_blur").semantics {
                    if (!canAdjustBlur) disabled()
                    setProgress { target ->
                        if (!canAdjustBlur || !target.isFinite()) false else {
                            blur = target.coerceIn(0f, 100f); saveBlur(); true
                        }
                    }
                })
            MusicDialogButton("完成", enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                val value = blur
                operation { container.settings.edit { it.copy(playerBackgroundBlur = value) }; onDismiss() }
            })
        }
    }
}
