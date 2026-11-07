package io.github.currencortex.music.feature.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@Composable internal fun AdminCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    SettingsGroup { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        }
        content()
    } }
}
@Composable internal fun AdminMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value.ifBlank { "—" }, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.widthIn(max = 180.dp))
    }
}
internal data class AdminStat(val label: String, val value: String, val hint: String = "")
@Composable internal fun AdminStats(stats: List<AdminStat>) {
    BoxWithConstraints {
        val columns = if (maxWidth >= 520.dp) 4 else 2
        val stacked = LocalDensity.current.fontScale > 1.15f || maxWidth / columns < 140.dp
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            stats.chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                row.forEach { stat -> Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer).padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (stacked) {
                        Text(stat.label, fontSize = 11.sp, lineHeight = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Text(stat.value, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.primary)
                    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stat.label, Modifier.weight(1f), fontSize = 11.sp, lineHeight = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Text(stat.value, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.primary)
                    }
                    if (stat.hint.isNotBlank()) Text(stat.hint, fontSize = 10.sp, lineHeight = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            } }
        }
    }
}
@Composable internal fun AdminBadge(label: String, warning: Boolean = false) {
    val color = if (warning) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
    Text(label, Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = .1f)).padding(horizontal = 7.dp, vertical = 3.dp),
        color = color, fontSize = 11.sp, lineHeight = 16.sp)
}
@Composable internal fun AdminSectionHeading(title: String, subtitle: String, action: String? = null, enabled: Boolean = true, click: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        action?.let { MusicTextAction(it, click, enabled = enabled) }
    }
}
@Composable internal fun AdminNotice(message: String, error: Boolean = false, retry: (() -> Unit)? = null, onDismiss: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
        .background((if (error) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary).copy(alpha = .09f))
        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f).padding(vertical = 8.dp), fontSize = 13.sp,
            color = if (error) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface)
        retry?.let { MusicTextAction("重试", it) }
        onDismiss?.let { GlassIconButton(it, "关闭提示") { Icon(MusicIcons.Close, null, Modifier.size(16.dp)) } }
    }
}
@Composable internal fun AdminEmpty(title: String, summary: String, action: String? = null, click: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(MusicIcons.Info, null, Modifier.size(32.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(summary, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        action?.let { MusicTextAction(it, click) }
    }
}
@Composable internal fun AdminLoading() {
    Column(Modifier.fillMaxWidth().padding(12.dp).semantics { contentDescription = "正在加载管理数据" }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(3) { SettingsGroup { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LoadingTextLine(.4f, 24.sp); LoadingTextLine(.8f); LoadingTextLine(.6f)
        } } }
    }
}
@Composable internal fun AdminExpandable(title: String, subtitle: String, tag: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(tag) { mutableStateOf(false) }
    SettingsGroup {
        MusicDestinationRow(title, { expanded = !expanded }, Modifier.testTag(tag).semantics {
            stateDescription = if (expanded) "已展开" else "已收起"
        }, summary = subtitle, chevron = true)
        AnimatedVisibility(expanded) { Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
    }
}
@Composable internal fun AdminActionRow(title: String, summary: String, icon: ImageVector, tag: String, enabled: Boolean = true,
    dangerous: Boolean = false, onClick: () -> Unit) {
    SettingsPreference(title, summary, Modifier.testTag(tag), enabled = enabled, onClick = onClick,
        leading = { Icon(icon, null, Modifier.size(22.dp), tint = if (dangerous) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary) },
        trailing = { Icon(MusicIcons.Forward, null, Modifier.size(16.dp)) })
}
internal fun adminCount(value: Long?) = value?.let { NumberFormat.getIntegerInstance().format(it) } ?: "—"
internal fun adminTime(value: Long): String = if (value <= 0) "—" else
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(if (value < 100_000_000_000L) value * 1000 else value))
internal fun adminBytes(value: Long?): String {
    if (value == null) return "—"
    var size = value.toDouble(); var unit = 0
    while (size >= 1024 && unit < 4) { size /= 1024; unit++ }
    return String.format(Locale.getDefault(), "%.1f %s", size, listOf("B", "KB", "MB", "GB", "TB")[unit])
}
