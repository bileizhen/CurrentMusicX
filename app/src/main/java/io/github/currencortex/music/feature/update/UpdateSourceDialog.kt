package io.github.currencortex.music.feature.update

import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.core.config.AppMetadata
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.ui.component.MusicDialogChoiceRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun UpdateSourceDialog(
    release: AppRelease?, selected: UpdateSource, onSelect: (UpdateSource) -> Unit, onDismiss: () -> Unit,
    downloading: Boolean = false, channel: UpdateChannel = UpdateChannel.STABLE, backdrop: Backdrop? = null,
    sourceCheck: UpdateSourceCheck = remember { UpdateSourceProbe() },
) {
    val sources = UpdateSource.available
    val health = remember(release, channel, sourceCheck) { mutableStateMapOf<UpdateSource, UpdateSourceHealth>() }
    val orderedSources = sources.sortedByHealth(health)
    LaunchedEffect(release, channel, sourceCheck) {
        sources.forEach { health[it] = UpdateSourceHealth(UpdateSourceStatus.CHECKING) }
        val target = try {
            release?.takeIf { it.apkUrl != null } ?: withContext(Dispatchers.IO) {
                GitHubUpdateChecker(AppMetadata.RELEASES_OWNER, AppMetadata.RELEASES_REPO,
                    "0.0.0", "CurrentMusic updater", nativeAssetsOnly = true).check(channel)
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { null }
        if (target == null) {
            sources.forEach { health[it] = UpdateSourceHealth(UpdateSourceStatus.UNKNOWN, detail = "无法取得安装包信息，请稍后重新打开检测") }
        } else coroutineScope {
            sources.forEach { candidate -> launch { health[candidate] = sourceCheck.check(target, candidate) } }
        }
    }
    UpdateOptionsDialog("下载来源", summary =
        (if (downloading) "切换来源后，将从头重新下载。\n" else "") + "延迟为读取安装包文件头的耗时，重新打开可刷新。",
        onDismiss = onDismiss, backdrop = backdrop, choiceLayout = true, actions = {
            UpdateDialogAction("取消", onDismiss, Modifier.weight(1f).testTag("update_source_cancel"), compact = true)
        }) {
        // Scope follows the scrolling content, so only reordering animates, never scrolling.
        LookaheadScope { UpdateOptionGroup {
            orderedSources.forEachIndexed { index, candidate -> key(candidate) {
                MusicDialogChoiceRow(candidate.label, candidate == selected, { onSelect(candidate) },
                    modifier = Modifier.animateBounds(this@LookaheadScope, boundsTransform = { _, _ ->
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 450f)
                    }).testTag("update_source_${candidate.name}"), showDivider = index < orderedSources.lastIndex,
                    leadingContent = {
                        UpdateSourceBadge(candidate, health[candidate] ?: UpdateSourceHealth(UpdateSourceStatus.CHECKING))
                    })
            } }
        } }
    }
}

@Composable private fun UpdateSourceBadge(source: UpdateSource, health: UpdateSourceHealth) {
    val label = when (health.status) {
        UpdateSourceStatus.CHECKING -> "检测中"
        UpdateSourceStatus.AVAILABLE -> "可用"
        UpdateSourceStatus.LIMITED -> "限流"
        UpdateSourceStatus.TIMEOUT -> "超时"
        UpdateSourceStatus.UNAVAILABLE -> "不可用"
        UpdateSourceStatus.UNKNOWN -> "未检测"
    }
    val dark = MiuixTheme.colorScheme.onBackground.luminance() > .5f
    val color = when (health.status) {
        UpdateSourceStatus.AVAILABLE -> if (dark) Color(0xFF73D4A4) else Color(0xFF24865B)
        UpdateSourceStatus.LIMITED, UpdateSourceStatus.TIMEOUT -> if (dark) Color(0xFFE8BA69) else Color(0xFFAD731B)
        UpdateSourceStatus.UNAVAILABLE -> if (dark) Color(0xFFF28F99) else Color(0xFFCD5058)
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Column(Modifier.width(72.dp).background(color.copy(alpha = .12f), RoundedCornerShape(10.dp))
        .padding(horizontal = 4.dp, vertical = 5.dp).testTag("update_source_badge_${source.name}")
        .semantics { contentDescription = "${source.label}：$label，${health.latencyMs?.let { "${it}毫秒，" }.orEmpty()}${health.detail}" },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = color, fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
        Text(health.latencyMs?.let { "$it ms" } ?: "— ms", color = color, fontSize = 10.sp,
            lineHeight = 13.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}
