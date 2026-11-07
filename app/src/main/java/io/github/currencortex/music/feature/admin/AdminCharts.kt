package io.github.currencortex.music.feature.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.MusicTextAction
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.*

/** Touch and accessibility selection share the same index; dragging stays inside this chart. */
@Composable internal fun AdminChart(values: List<Long>, label: String, labels: List<String>, bytes: Boolean = false,
    line: Boolean = false, secondary: List<Long> = emptyList(), tag: String = "admin_chart") {
    if (values.isEmpty()) { Text("暂无记录", fontSize = 13.sp); return }
    val data = values.map { it.coerceAtLeast(0) }
    val ceiling = niceMaximum(data.maxOrNull() ?: 0)
    val secondCeiling = niceMaximum(secondary.maxOrNull() ?: 0)
    val primary = MiuixTheme.colorScheme.primary
    val secondaryColor = Color(0xFFFF982E)
    val grid = MiuixTheme.colorScheme.onSurface.copy(alpha = .10f)
    val ink = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val format: (Long) -> String = { if (bytes) adminBytes(it) else adminCount(it) }
    var selected by rememberSaveable(labels, label) { mutableIntStateOf(-1) }
    val index = selected.takeIf { it in data.indices }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("● $label", fontSize = 11.sp, color = primary)
        if (secondary.isNotEmpty()) Text("● 活跃用户", fontSize = 11.sp, color = secondaryColor)
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp))
        .background(primary.copy(alpha = if (index != null) .08f else 0f)).padding(horizontal = 10.dp, vertical = 7.dp)) {
        if (index == null) Text("点按或横向拖动图表查看数据", fontSize = 12.sp, color = ink)
        else Column {
            Text(labels.getOrElse(index) { "${index + 1}" }, fontSize = 11.sp, color = ink)
            Text("$label ${format(data[index])}" + if (secondary.isNotEmpty()) " · 活跃 ${adminCount(secondary.getOrElse(index) { 0 })} 人" else "",
                fontSize = 12.sp, color = primary, modifier = Modifier.testTag("${tag}_selection"))
        }
    }
    Row(Modifier.fillMaxWidth().height(156.dp)) {
        Column(Modifier.width(if (bytes) 52.dp else 42.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            (4 downTo 0).forEach { Text(if (bytes) adminBytes(ceiling * it / 4) else compactAxis(ceiling * it / 4), fontSize = 10.sp, color = ink) }
        }
        Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 7.dp).testTag(tag)
            .semantics {
                contentDescription = "$label，${data.size} 个时间点，最大值 ${format(data.maxOrNull() ?: 0)}"
                stateDescription = index?.let { "${labels.getOrElse(it) { "${it + 1}" }} ${format(data[it])}" } ?: "未选择"
                customActions = listOf(CustomAccessibilityAction("下一个时间点") { selected = (selected + 1).coerceAtMost(data.lastIndex); true },
                    CustomAccessibilityAction("上一个时间点") { selected = (selected - 1).coerceAtLeast(0); true })
            }
            .pointerInput(data, labels) { detectTapGestures { p -> selected = (p.x / size.width * data.size).toInt().coerceIn(data.indices) } }
            .pointerInput(data, labels) { detectHorizontalDragGestures(onDragStart = { p -> selected = (p.x / size.width * data.size).toInt().coerceIn(data.indices) }) { change, _ ->
                change.consume(); selected = (change.position.x / size.width * data.size).toInt().coerceIn(data.indices)
            } }) {
            repeat(5) { val y = size.height * it / 4; drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
            val step = size.width / data.size
            fun point(i: Int, value: Long, max: Long) = Offset((i + .5f) * step, size.height * (1 - value.toDouble() / max).toFloat())
            fun path(series: List<Long>, max: Long) = Path().apply {
                series.take(data.size).forEachIndexed { i, v -> val p = point(i, v.coerceAtLeast(0), max); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            if (line) {
                val area = path(data, ceiling).apply { lineTo((data.lastIndex + .5f) * step, size.height); lineTo(step / 2, size.height); close() }
                drawPath(area, primary.copy(alpha = .10f)); drawPath(path(data, ceiling), primary, style = Stroke(2.dp.toPx()))
            } else data.forEachIndexed { i, value ->
                val p = point(i, value, ceiling)
                if (value > 0) drawRoundRect(primary.copy(alpha = if (i == selected) 1f else .55f),
                    Offset(i * step + step * .16f, p.y), Size(step * .68f, size.height - p.y), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            }
            if (secondary.isNotEmpty()) drawPath(path(secondary, secondCeiling), secondaryColor, style = Stroke(2.dp.toPx()))
            index?.let { i ->
                val p = point(i, data[i], ceiling)
                drawLine(ink.copy(alpha = .5f), Offset(p.x, 0f), Offset(p.x, size.height), 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                drawCircle(primary, 4.dp.toPx(), p)
                if (i in secondary.indices) drawCircle(secondaryColor, 4.dp.toPx(), point(i, secondary[i], secondCeiling))
            }
        }
        if (secondary.isNotEmpty()) Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
            (4 downTo 0).forEach { Text(compactAxis(secondCeiling * it / 4), fontSize = 10.sp, color = secondaryColor) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = if (bytes) 52.dp else 42.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(labels.firstOrNull().orEmpty(), fontSize = 10.sp, color = ink)
        Text(labels.lastOrNull().orEmpty(), fontSize = 10.sp, color = ink)
    }
    var details by rememberSaveable(label) { mutableStateOf(false) }
    MusicTextAction(if (details) "收起明细" else "查看明细", { details = !details })
    if (details) data.indices.reversed().forEach { i -> AdminMetric(labels.getOrElse(i) { "${i + 1}" }, format(data[i]) +
        if (secondary.isNotEmpty()) " · ${secondary.getOrElse(i) { 0 }} 人" else "") }
}

private fun niceMaximum(value: Long): Long {
    if (value <= 4) return 4
    val rough = value / 4.0
    val magnitude = 10.0.pow(floor(log10(rough)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * magnitude >= rough } * magnitude
    return (step * 4).toLong().coerceAtLeast(value)
}
private fun compactAxis(value: Long) = when {
    value >= 10000 -> String.format(java.util.Locale.ROOT, "%.1f万", value / 10000.0)
    value >= 1000 -> String.format(java.util.Locale.ROOT, "%.1fk", value / 1000.0)
    else -> value.toString()
}

@Composable internal fun AdminDistribution(labels: List<String>, values: List<Long>, donut: Boolean = true) {
    val colors = listOf(MiuixTheme.colorScheme.primary, Color(0xFF0088CC), Color(0xFFFF982E), Color(0xFF8E8E93))
    val total = values.sum()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = labels.indices.joinToString { "${labels[it]} ${values.getOrElse(it) { 0 }}" } }) {
                var start = -90f
                values.forEachIndexed { i, v ->
                    val angle = if (total > 0) v.toFloat() / total * 360 else 0f
                    if (donut) drawArc(colors[i % colors.size], start, angle, false, topLeft = Offset(10.dp.toPx(), 10.dp.toPx()),
                        size = Size(size.width - 20.dp.toPx(), size.height - 20.dp.toPx()), style = Stroke(17.dp.toPx()))
                    else drawArc(colors[i % colors.size], start, angle, true)
                    start += angle
                }
            }
            if (donut) Text(adminCount(total), fontSize = 20.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("●", color = colors[i % colors.size], fontSize = 10.sp)
                Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(adminCount(values.getOrElse(i) { 0 }), fontSize = 12.sp)
            } }
        }
    }
}

@Composable internal fun AdminRanking(rows: List<Pair<String, Long>>, bytes: Boolean = false) {
    val max = (rows.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    rows.forEach { (name, value) ->
        AdminMetric(name, if (bytes) adminBytes(value) else adminCount(value))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MiuixTheme.colorScheme.primary.copy(alpha = .08f))) {
            Box(Modifier.fillMaxWidth((value.toFloat() / max).coerceIn(0f, 1f)).fillMaxHeight().background(MiuixTheme.colorScheme.primary))
        }
    }
}
