package io.github.currencortex.music.feature.roaming

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Uses the existing full player. Browsing a mode never replaces the playing queue. */
@Composable internal fun PersonalRoamingDialog(container: AppContainer, onDismiss: () -> Unit, onBinding: () -> Unit) {
    val station = container.personalRoaming
    val state by station.state.collectAsStateWithLifecycle()
    val account by container.neteaseSessions.state.collectAsStateWithLifecycle()
    var draftKey by rememberSaveable { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }
    val choice = state.catalog.choices.find { it.key == draftKey } ?: state.choice
    val loggedIn = account.loggedIn && !account.stale
    val changed = !state.active || choice.key != state.choice.key
    val colors = MiuixTheme.colorScheme
    val bodyHeight = (LocalConfiguration.current.screenHeightDp * .48f).dp
    LaunchedEffect(Unit) { station.loadCatalog() }
    LaunchedEffect(account.revision) { draftKey = null; submitted = null }
    LaunchedEffect(state.active, state.choice.key, state.loading, state.error) {
        if (submitted != null && state.active && !state.loading && state.error == null && submitted == state.choice.key) onDismiss()
        state.error?.let(container.errorNotices::show)
    }
    if (settings) MusicDialog("漫游设置", { settings = false }, footer = {
        MusicDialogButton("返回模式选择", { settings = false }, Modifier.testTag("roaming_settings_done"))
    }) {
        SettingsSwitch("提前补充推荐", state.settings.preload,
            { station.updateSettings(state.settings.copy(preload = it)) }, Modifier.testTag("roaming_preload"))
        SettingsSwitch("结束时恢复原队列", state.settings.restoreQueue,
            { station.updateSettings(state.settings.copy(restoreQueue = it)) }, Modifier.testTag("roaming_restore_queue"))
        Text(if (state.settings.restoreQueue) "结束后恢复之前的队列与进度，保持暂停。" else "结束后保留当前歌曲与队列。",
            fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
    } else MusicDialog("私人漫游", onDismiss, footer = {
        val primary: @Composable (Modifier) -> Unit = { modifier ->
        MusicDialogButton(if (!loggedIn) "登录网易云" else if (state.loading) "正在寻找推荐…"
            else if (changed) if (state.active) "切换到${choice.title}" else "开始漫游"
            else if (state.error != null) "重试推荐" else "继续聆听",
            onClick = {
                when {
                    !loggedIn -> { onDismiss(); onBinding() }
                    changed -> { submitted = choice.key; station.start(choice) }
                    state.error != null -> { submitted = choice.key; station.retry() }
                    else -> onDismiss()
                }
            }, modifier.testTag(if (loggedIn) "roaming_start" else "roaming_login"),
            enabled = state.ready && !state.loading, cornerRadius = 14.dp,
            colors = ButtonDefaults.textButtonColors(color = colors.primary, textColor = colors.onPrimary))
        }
        if (state.active) MusicDialogActions {
            MusicDialogButton("结束漫游", { station.stop(); onDismiss() },
                Modifier.weight(1f).testTag("roaming_stop"), cornerRadius = 14.dp,
                colors = ButtonDefaults.textButtonColors(textColor = colors.error))
            primary(Modifier.weight(1f))
        } else primary(Modifier)
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.active) "正在聆听 · ${state.choice.title}" else "选择模式或场景，开始连续推荐",
                Modifier.weight(1f), fontSize = 12.sp, color = colors.onSurfaceVariantSummary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            MusicTextAction("设置", { settings = true }, Modifier.testTag("roaming_settings"))
        }
        Column(Modifier.fillMaxWidth().heightIn(max = bodyHeight).verticalScroll(rememberScrollState())
            .testTag("roaming_choices"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.catalog.modes.forEach { mode ->
                val selected = choice.key == mode.key
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (selected) colors.primary.copy(alpha = .1f) else colors.surfaceContainer)
                    .selectable(selected, enabled = state.ready && !state.loading, role = Role.RadioButton,
                        onClick = { draftKey = mode.key }).testTag("roaming_mode_${mode.code}")
                    .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(mode.title, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            color = if (selected) colors.primary else colors.onSurface)
                        if (mode.subtitle.isNotBlank()) Text(mode.subtitle, fontSize = 11.sp,
                            color = colors.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (selected) Icon(MusicIcons.Check, "已选择", Modifier.size(20.dp), tint = colors.primary)
                }
            }
            Text("场景模式", Modifier.padding(top = 8.dp, bottom = 4.dp), fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
            state.catalog.scenes.chunked(4).forEach { group ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    group.forEach { scene ->
                        val selected = choice.key == scene.key
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (selected) colors.primary.copy(alpha = .1f) else Color.Transparent)
                            .selectable(selected, enabled = state.ready && !state.loading, role = Role.RadioButton,
                                onClick = { draftKey = scene.key }).testTag("roaming_scene_${scene.code}")
                            .padding(horizontal = 2.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (scene.icon.isNotBlank()) AsyncImage(scene.icon, null, Modifier.size(24.dp),
                                colorFilter = ColorFilter.tint(if (selected) colors.primary else colors.onSurfaceVariantSummary))
                            else Icon(MusicIcons.Headphones, null, Modifier.size(24.dp), tint = colors.onSurfaceVariantSummary)
                            Text(scene.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (selected) colors.primary else colors.onSurface)
                        }
                    }
                    repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (state.catalogLoading) Text("正在刷新场景…", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
            if (state.catalogError != null) MusicTextAction("重新加载场景", station::loadCatalog, Modifier.testTag("roaming_reload_catalog"))
        }
    }
}
