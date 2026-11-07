package io.github.currencortex.music.feature.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.admin.*
import io.github.currencortex.music.feature.update.MarkdownText
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun LazyListScope.adminOverview(state: AdminState) {
    val stats = state.overview ?: return
    item { AdminStats(listOf(
        AdminStat("注册用户", adminCount(stats.number("users")), "今日 +${adminCount(stats.number("usersToday"))} · 7 日 +${adminCount(stats.number("usersWeek"))}"),
        AdminStat("在线设备", adminCount(stats.number("sessions")), "近 1 小时 ${adminCount(stats.number("onlineUsers"))} 人活跃"),
        AdminStat("累计播放", adminCount(stats.number("plays")), "${adminCount(stats.number("playlists"))} 个歌单"),
        AdminStat("总点赞数", adminCount((stats.number("likes") ?: 0) + (stats.number("ncmLikes") ?: 0)), "站内与网易云红心"),
        AdminStat("禁用账号", adminCount(stats.number("disabled"))),
        AdminStat("网易云绑定", adminCount(stats.number("bindings"))),
        AdminStat("缓存条目", adminCount(stats.number("cache")), "歌词、评论与详情"),
        AdminStat("当前版本", stats.obj("release").text("version").ifBlank { "—" }))) }
    item { AdminTraffic(state) }
    state.traffic?.rows("top")?.takeIf { it.isNotEmpty() }?.let { paths ->
        item { AdminCard("今日流量排行", "按出站字节排序 · Top ${paths.size}") {
            AdminRanking(paths.map { it.text("path") to (it.number("bytes") ?: 0) }, bytes = true)
        } }
    }
    item { AdminCard("用户增长", "近 30 天 · 共新增 ${stats.rows("userDaily").sumOf { it.number("count") ?: 0 }} 人") {
        AdminChart(stats.rows("userDaily").map { it.number("count") ?: 0 }, "每日新增用户", stats.rows("userDaily").map { it.text("day").takeLast(5) }, tag = "admin_chart_growth")
    } }
    item { AdminCard("播放活跃", "近 14 天 · ${adminCount(stats.rows("playDaily").sumOf { it.number("plays") ?: 0 })} 次 · ${String.format(java.util.Locale.ROOT, "%.1f", stats.rows("playDaily").sumOf { it.number("ms") ?: 0 } / 3600000.0)} 小时") {
        AdminChart(stats.rows("playDaily").map { it.number("plays") ?: 0 }, "每日播放次数", stats.rows("playDaily").map { it.text("day").takeLast(5) }, secondary = stats.rows("playDaily").map { it.number("users") ?: 0 }, tag = "admin_chart_plays")
    } }
    item { AdminCard("角色构成", "站长、超级管理员、管理员与普通用户") {
        AdminDistribution(listOf("站长", "超级管理员", "管理员", "普通用户"),
            listOf("owner", "super", "admin", "user").map { stats.obj("roleCounts").number(it) ?: 0 })
    } }
    if (stats.obj("sourceCounts").isNotEmpty()) item { AdminCard("用户来源", "按最近一次成功登录或注册归类") {
        AdminDistribution(listOf("Web 端", "X 端", "其他"), listOf("web", "x", "other").map { stats.obj("sourceCounts").number(it) ?: 0 }, donut = false)
    } }
    item { AdminCard("内容与互动", "站内数据量对比") {
        AdminRanking(listOf("站内点赞" to "likes", "网易云红心" to "ncmLikes", "自建歌单" to "playlists", "歌单曲目" to "tracks",
            "播放记录" to "plays", "网易云绑定" to "bindings", "歌曲缓存" to "songs", "缓存条目" to "cache")
            .map { (label, key) -> label to (stats.number(key) ?: 0) })
    } }
}

@Composable private fun AdminTraffic(state: AdminState) {
    val traffic = state.traffic
    var hourly by rememberSaveable { mutableStateOf(false) }
    AdminCard("出站流量", "服务器流量统计") {
        if (traffic?.flag("available") == true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) { Text("今日", fontSize = 12.sp); Text(adminBytes(traffic.number("today")), fontSize = 24.sp) }
                Column(Modifier.weight(1f)) { Text("本月", fontSize = 12.sp); Text(adminBytes(traffic.number("month")), fontSize = 24.sp) }
            }
            AdminMetric("实时速率 · 近 5 分钟平均", "${adminBytes(traffic.text("rate5m").toDoubleOrNull()?.toLong())}/s")
            traffic.rows("top").firstOrNull()?.let { AdminMetric("今日最大路径", adminBytes(it.number("bytes"))); Text(it.text("path"), fontSize = 11.sp) }
            SettingsSegmentedControl(listOf("近 30 天", "近 48 小时"), if (hourly) 1 else 0, { hourly = it == 1 }, tag = "admin_traffic_range")
            val rows = traffic.rows(if (hourly) "hourly" else "daily")
            AdminChart(rows.map { it.number("bytes") ?: 0 }, "出站流量", rows.map { row -> row.number("t")?.let { java.text.SimpleDateFormat(if (hourly) "MM/dd HH:mm" else "MM/dd", java.util.Locale.getDefault()).format(java.util.Date(it * 1000)) } ?: row.text("t").takeLast(5) }, bytes = true, line = true, tag = "admin_chart_traffic")
            Text("更新于 ${adminTime(traffic.number("generatedAt") ?: 0)}", fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } else Text(if (state.loading && traffic == null) "正在加载流量统计…" else state.trafficError ?: traffic?.text("error")?.takeIf { it.isNotBlank() } ?: "当前服务器暂未提供流量统计", fontSize = 13.sp)
    }
}

internal fun LazyListScope.adminClients(state: AdminState) {
    val clients = state.clients ?: return
    val current = clients.obj("current"); val cmx = clients.obj("cmx"); val apk = cmx.obj("apk")
    item { AdminCard("CurrentMusic", "网页与原版客户端") {
        AdminBadge("v${current.text("version")}"); AdminMetric("安装包", adminBytes(current.number("size")))
        AdminMetric("发布于", adminTime(current.number("publishedAt") ?: 0)); AdminMetric("镜像", if (current.flag("mirrored")) "已同步" else "未同步")
    } }
    item { AdminCard("CurrentMusicX", "原生 Android 客户端") {
        AdminBadge("v${cmx.text("version")}"); AdminMetric("安装包", adminBytes(apk.number("size")))
        AdminMetric("下载次数", adminCount(apk.number("downloads"))); AdminMetric("镜像", if (apk.flag("mirrored")) "已同步" else "未同步")
        AdminMetric("发布于", adminTime(cmx.number("publishedAt") ?: 0))
    } }
    if (cmx.text("body").isNotBlank()) item { AdminExpandable("CurrentMusicX 更新说明", cmx.text("name"), "admin_cmx_notes") { MarkdownText(cmx.text("body"), compact = true) } }
    if (current.text("changelog").isNotBlank()) item { AdminExpandable("CurrentMusic 更新说明", current.text("version"), "admin_current_notes") { MarkdownText(current.text("changelog"), compact = true) } }
    if (cmx.rows("history").isNotEmpty()) item { AdminCard("各版本下载量", "GitHub 累计 · 最近 ${cmx.rows("history").size} 个版本") {
        val releases = cmx.rows("history").reversed()
        AdminChart(releases.map { it.number("apkDownloads") ?: 0 }, "下载次数", releases.map { it.text("version") }, tag = "admin_chart_downloads")
    } }
    item { SettingsListHeader("历史版本") }
    items(cmx.rows("history")) { release -> SettingsGroup {
        SettingsPreference("v${release.text("version")}", "${adminCount(release.number("apkDownloads"))} 次下载 · ${adminBytes(release.number("apkSize"))} · ${if (release.flag("mirrored")) "已镜像" else "未镜像"}")
    } }
}

internal fun LazyListScope.adminSystem(state: AdminState, confirm: (AdminConfirmation) -> Unit) {
    val stats = state.overview ?: return
    item { AdminStats(listOf(AdminStat("歌单", adminCount(stats.number("playlists"))), AdminStat("曲目", adminCount(stats.number("tracks"))),
        AdminStat("网易云绑定", adminCount(stats.number("bindings"))), AdminStat("歌曲元数据", adminCount(stats.number("songs"))))) }
    item { SettingsListHeader("维护") }
    item { SettingsGroup {
        AdminActionRow("清理辅助缓存", "当前 ${adminCount(stats.number("cache"))} 条 · 歌词、评论与详情", MusicIcons.Storage,
            "admin_clear_cache", enabled = !state.loading && !state.busy, dangerous = true) { confirm(AdminConfirmation.Cache) }
    } }
    item { SettingsHint("清理后首次加载可能稍慢，音质档位探测缓存会保留。") }
}
