package io.github.currencortex.music.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.core.network.SourceAvailabilityStatus
import io.github.currencortex.music.data.lx.LxPlatform
import io.github.currencortex.music.data.lx.lxQualityLabel
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import io.github.suqi8.coui.kmp.basic.TextField

/** Uses the same grouped lists and anchored choice menu as the other source settings. */
@Composable internal fun LxSourceSettings(vm: MusicSettingsViewModel, platform: LxPlatform) {
    val sources by vm.lxSources.collectAsStateWithLifecycle()
    val provider by vm.audioProvider.collectAsStateWithLifecycle()
    val state by vm.audioState.collectAsStateWithLifecycle()
    val health by vm.lxHealth.collectAsStateWithLifecycle()
    val resolveTimes by vm.lxResolveTimes.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(sources.map { it.id }, provider.lxSourceId, platform, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.checkLxSources(platform) }
    }
    var urlOpen by rememberSaveable { mutableStateOf(false) }
    var removal by rememberSaveable { mutableStateOf<String?>(null) }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importLxFile)
    }
    val selected = sources.firstOrNull { it.id == provider.lxSourceId }
    fun timeSuffix(id: String): String = resolveTimes[platform to id]?.let { "，取链 $it ms" } ?: ""
    SettingsSection("落雪音源") {
        if (sources.isNotEmpty()) {
            SettingsOptionsRow(if (provider.lxSmartSwitch) "默认音源" else "当前音源", selected?.name ?: "请选择", sources.map { source ->
                SettingsOption("lx_source_${source.id}", source.name, source.id == provider.lxSourceId,
                    summary = source.summary(platform) + timeSuffix(source.id),
                    leadingContent = { LxHealthBadge(source.id, platform, health[platform to source.id]?.status) },
                    onSelect = { vm.selectLxSource(source.id) })
            }, Modifier.testTag("lx_source_select"), enabled = !state.busy,
                valueLeading = selected?.let { source ->
                    { LxHealthBadge(source.id, platform, health[platform to source.id]?.status) }
                })
            selected?.let { source ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (source.description.isNotBlank()) SettingsHint(source.description.take(500))
                    if (source.author.isNotBlank()) SettingsHint("作者 · ${source.author.take(100)}")
                    val qualities = source.qualities(platform)
                    SettingsHint(if (qualities.isEmpty()) platform.label else
                        "${platform.label} · ${qualities.joinToString(" / ") { lxQualityLabel(platform, it) }}")
                    resolveTimes[platform to source.id]?.let { SettingsHint("最近成功取链 · $it ms") }
                    health[platform to source.id]?.message?.let { SettingsHint(it) }
                }
            }
            SettingsDivider()
            SettingsSwitch("多音源智能切换", provider.lxSmartSwitch, vm::lxSmartSwitch,
                Modifier.testTag("lx_smart_switch"), summary = "优先使用取链更快的插件，取链或播放失败时自动尝试其他已导入音源。",
                enabled = !state.busy)
            SettingsDivider()
        } else SettingsHint("尚未导入音源")
        SettingsPreference(if (state.busy) "正在处理音源…" else "从文件导入", "选择落雪自定义音源 .js 文件",
            Modifier.testTag("lx_import_file"), enabled = !state.busy, onClick = { file.launch(arrayOf("*/*")) })
        SettingsDivider()
        SettingsPreference("从链接导入", "填写脚本文件的下载链接", Modifier.testTag("lx_import_url"),
            enabled = !state.busy, onClick = { urlOpen = true })
        if (selected != null) {
            SettingsDivider()
            SettingsPreference("移除此音源", modifier = Modifier.testTag("lx_remove"), enabled = !state.busy,
                onClick = { removal = selected.id })
        }
        state.message?.let { SettingsHint(it) }
    }
    LxSourceUrlDialog(urlOpen, onDismiss = { urlOpen = false }, onImport = vm::importLxUrl)
    SettingsDialog(
        show = removal != null,
        title = "移除音源？",
        onDismissRequest = { removal = null },
        summary = "移除后请重新选择或导入落雪音源。",
        positiveText = "移除",
        onPositive = { removal?.let(vm::removeLxSource); removal = null },
        negativeText = "取消",
        onNegative = { removal = null },
        destructive = true,
    )
}

@Composable private fun LxHealthBadge(id: String, platform: LxPlatform, status: SourceAvailabilityStatus?) {
    val availability = status ?: SourceAvailabilityStatus.UNKNOWN
    SourceAvailabilityBadge(availability, Modifier.testTag("lx_source_badge_$id"),
        "${platform.label}音源：${availability.label}")
}

@Composable private fun LxSourceUrlDialog(show: Boolean, onDismiss: () -> Unit, onImport: (String) -> Unit) {
    // The link may include a private source key. Never put it in saved instance state; clear
    // it on every open so a cancelled link does not linger in the field.
    var value by remember { mutableStateOf("") }
    LaunchedEffect(show) { if (show) value = "" }
    SettingsDialog(
        show = show,
        title = "链接导入音源",
        onDismissRequest = onDismiss,
        positiveText = "导入",
        onPositive = { onImport(value); onDismiss() },
        positiveEnabled = value.isNotBlank(),
        negativeText = "取消",
        onNegative = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(value, { value = it }, singleLine = true, label = "脚本下载链接",
                modifier = Modifier.fillMaxWidth().testTag("lx_url_input"))
            SettingsHint("填写返回 .js 脚本内容的 HTTP 或 HTTPS 链接。导入时会检查音源声明的接口。")
        }
    }
}
