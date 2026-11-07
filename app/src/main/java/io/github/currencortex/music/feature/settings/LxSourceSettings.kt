package io.github.currencortex.music.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.TextField

/** Uses the same grouped lists and anchored choice menu as the other source settings. */
@Composable internal fun LxSourceSettings(vm: MusicSettingsViewModel) {
    val sources by vm.lxSources.collectAsStateWithLifecycle()
    val provider by vm.audioProvider.collectAsStateWithLifecycle()
    val state by vm.audioState.collectAsStateWithLifecycle()
    val health by vm.lxHealth.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(sources.map { it.id }, provider.lxSourceId, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.checkLxSources() }
    }
    var urlOpen by rememberSaveable { mutableStateOf(false) }
    var removal by rememberSaveable { mutableStateOf<String?>(null) }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importLxFile)
    }
    val selected = sources.firstOrNull { it.id == provider.lxSourceId }
    SettingsSection("落雪音源", MusicIcons.File) {
        if (sources.isNotEmpty()) {
            SettingsOptionsRow("当前音源", selected?.name ?: "请选择", sources.map { source ->
                SettingsOption("lx_source_${source.id}", source.name, source.id == provider.lxSourceId,
                    summary = source.summary, leadingContent = { LxHealthBadge(vm, source.id) }, onSelect = { vm.selectLxSource(source.id) })
            }, Modifier.testTag("lx_source_select"), enabled = !state.busy,
                valueLeading = selected?.let { source -> { LxHealthBadge(vm, source.id) } })
            selected?.let { source ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (source.description.isNotBlank()) SettingsHint(source.description.take(500))
                    if (source.author.isNotBlank()) SettingsHint("作者 · ${source.author.take(100)}")
                    SettingsHint(source.capabilities.joinToString("；") { capability ->
                        "网易云 · ${capability.qualities.joinToString(" / ")}"
                    })
                    health[source.id]?.message?.let { SettingsHint(it) }
                }
            }
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
        if (!vm.lxSupported()) SettingsHint("当前系统缺少脚本运行环境，请更新 Android System WebView。", error = true)
        state.message?.let { SettingsHint(it) }
    }
    if (urlOpen) LxSourceUrlDialog(onDismiss = { urlOpen = false }, onImport = vm::importLxUrl)
    removal?.let { id ->
        MusicChoiceDialog("移除音源？", { removal = null }, footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MusicDialogButton("取消", { removal = null }, Modifier.weight(1f))
                MusicDialogButton("移除", { vm.removeLxSource(id); removal = null }, Modifier.weight(1f).testTag("lx_remove_confirm"))
            }
        }) { SettingsHint("移除后请重新选择或导入落雪音源。") }
    }
}

@Composable private fun LxHealthBadge(vm: MusicSettingsViewModel, id: String) {
    val health by vm.lxHealth.collectAsStateWithLifecycle()
    val status = health[id]?.status ?: io.github.currencortex.music.core.network.SourceAvailabilityStatus.UNKNOWN
    SourceAvailabilityBadge(status, Modifier.testTag("lx_source_badge_$id"), "音源：${status.label}")
}

@Composable private fun LxSourceUrlDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    // The link may include a private source key. Never put it in saved instance state.
    var value by remember { mutableStateOf("") }
    MusicChoiceDialog("链接导入音源", onDismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicDialogButton("取消", onDismiss, Modifier.weight(1f))
            MusicDialogButton("导入", { onImport(value); onDismiss() }, Modifier.weight(1f).testTag("lx_url_confirm"),
                enabled = value.isNotBlank())
        }
    }) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(value, { value = it }, singleLine = true, label = "脚本下载链接",
                modifier = Modifier.fillMaxWidth().testTag("lx_url_input"))
            SettingsHint("填写返回 .js 脚本内容的 HTTP 或 HTTPS 链接。导入时会检查音源声明的接口。")
        }
    }
}
