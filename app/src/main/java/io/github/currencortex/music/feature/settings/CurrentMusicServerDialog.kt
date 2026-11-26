package io.github.currencortex.music.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.SettingsDialog
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.preference.RadioButtonPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable internal fun CurrentMusicServerDialog(show: Boolean, vm: MusicSettingsViewModel,
    onDismiss: () -> Unit, onCustom: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val health by vm.serverHealth.collectAsStateWithLifecycle()
    val automatic by vm.automaticServer.collectAsStateWithLifecycle()
    val checking by vm.serverProbeBusy.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.refreshServers() }
    SettingsDialog(
        show = show,
        title = "CurrentMusic 服务器",
        onDismissRequest = onDismiss,
        summary = "自动在启动时选择较快的可用线路。延迟为公告接口响应耗时，列表顺序固定。",
        negativeText = if (checking) "检测中" else "重新检测",
        onNegative = { vm.refreshServers() },
        negativeEnabled = !checking,
        positiveText = "关闭",
        onPositive = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            CurrentMusicServer.entries.forEach { candidate ->
                val address = when (candidate) {
                    CurrentMusicServer.AUTO -> automatic
                    CurrentMusicServer.CUSTOM -> settings.customServer
                    else -> requireNotNull(candidate.url)
                }
                val label = when (candidate) {
                    CurrentMusicServer.MAIN -> "music.20110208.xyz"
                    CurrentMusicServer.CDN -> "cm.bileizhen.top"
                    CurrentMusicServer.CDN_GEEKERTAO -> "currentmusic.geekertao.com"
                    CurrentMusicServer.CDN_CTCEHS -> "music.ctcehs.com"
                    else -> candidate.label
                }
                RadioButtonPreference(
                    title = label,
                    selected = settings.serverMode == candidate,
                    onClick = {
                        if (candidate == CurrentMusicServer.CUSTOM) onCustom()
                        else { vm.selectServer(candidate); onDismiss() }
                    },
                    modifier = Modifier.testTag("server_source_${candidate.name}"),
                    summary = when (candidate) {
                        CurrentMusicServer.AUTO -> "优选：${CurrentMusicServer.presets.first { it.url == automatic }.label}"
                        CurrentMusicServer.CUSTOM -> settings.customServer.ifBlank { "填写自己的服务器地址" }
                        CurrentMusicServer.MAIN -> "主站"
                        CurrentMusicServer.CDN, CurrentMusicServer.CDN_GEEKERTAO, CurrentMusicServer.CDN_CTCEHS -> "CDN"
                    },
                    endActions = { ServerHealthBadge(candidate, health[address]) },
                    enabled = !state.busy,
                )
            }
        }
    }
}

@Composable private fun ServerHealthBadge(source: CurrentMusicServer, health: ServerHealth?) {
    val label = when (health?.status) {
        ServerStatus.CHECKING -> "检测中"
        ServerStatus.AVAILABLE -> "可用"
        ServerStatus.TIMEOUT -> "超时"
        ServerStatus.UNAVAILABLE -> "不可用"
        null -> "未检测"
    }
    val dark = COUITheme.colorScheme.labelPrimary.luminance() > .5f
    val color = when (health?.status) {
        ServerStatus.AVAILABLE -> if (dark) Color(0xFF73D4A4) else Color(0xFF24865B)
        ServerStatus.TIMEOUT -> if (dark) Color(0xFFE8BA69) else Color(0xFFAD731B)
        ServerStatus.UNAVAILABLE -> if (dark) Color(0xFFF28F99) else Color(0xFFCD5058)
        else -> COUITheme.colorScheme.labelSecondaryVariant
    }
    Column(Modifier.width(72.dp).background(color.copy(alpha = .12f), RoundedCornerShape(10.dp))
        .padding(horizontal = 4.dp, vertical = 5.dp).testTag("server_source_badge_${source.name}")
        .semantics { contentDescription = "${source.label}：$label，${health?.latencyMs?.let { "${it}毫秒" }.orEmpty()}" },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = color, fontSize = 11.sp, lineHeight = 14.sp)
        Text(health?.latencyMs?.let { "$it ms" } ?: "— ms", color = color, fontSize = 10.sp, lineHeight = 13.sp)
    }
}

@Composable internal fun CustomMusicServerDialog(show: Boolean, saved: String, state: NetworkSettingsState,
    onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var address by rememberSaveable { mutableStateOf(saved) }
    var error by remember { mutableStateOf<String?>(null) }
    SettingsDialog(
        show = show,
        title = "自定义服务器",
        onDismissRequest = onDismiss,
        positiveText = "保存",
        onPositive = {
            val normalized = try { ServerUrl.normalize(address) } catch (failure: IllegalArgumentException) {
                error = failure.message; return@SettingsDialog
            }
            onSave(normalized); onDismiss()
        },
        positiveEnabled = !state.busy && address.isNotBlank(),
        negativeText = "取消",
        onNegative = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(address, { address = it; error = null }, singleLine = true, label = "服务器地址",
                modifier = Modifier.fillMaxWidth().testTag("server_input"))
            Text("填写完整的 API 地址，包含 /cm/ 等路径。切换到其他服务器后需重新登录。",
                style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondaryVariant)
            error?.let { Text(it, style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.error) }
        }
    }
}
