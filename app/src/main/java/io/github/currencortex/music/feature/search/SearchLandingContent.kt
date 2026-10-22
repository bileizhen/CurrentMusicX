package io.github.currencortex.music.feature.search

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.local.SearchHistoryEntity
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SearchLandingContent(history: List<SearchHistoryEntity>, landing: SearchLandingState,
    charts: SearchChartsState, recommendations: List<Song>, recent: List<Song>, onSearch: (String) -> Unit,
    onClearHistory: () -> Unit, onDeleteHistory: (String) -> Unit, onRetryHot: () -> Unit, onRetryCharts: () -> Unit,
    onLoadChart: (String, Boolean) -> Unit, roomSearch: Boolean) {
    val colors = MiuixTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    var recommendationOffset by rememberSaveable { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("搜索历史", Modifier.weight(1f))
                if (history.isNotEmpty()) Box(Modifier.size(36.dp).clickable(role = Role.Button, onClick = onClearHistory)
                    .testTag("search_clear_history"), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Delete, "清空搜索历史", Modifier.size(18.dp), tint = colors.onSurface.copy(alpha = .38f))
                }
            }
            if (history.isEmpty()) Text("搜索过的音乐会留在这里", Modifier.padding(top = 10.dp), fontSize = 13.sp,
                color = colors.onSurface.copy(alpha = .4f))
            else FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (expanded) history else history.take(3)).forEach { h ->
                    Box(Modifier.widthIn(max = 180.dp).clip(RoundedCornerShape(18.dp))
                        .background(colors.surfaceContainer).combinedClickable(role = Role.Button,
                            onClick = { onSearch(h.keyword) }, onLongClick = { onDeleteHistory(h.keyword) }, onLongClickLabel = "删除这条搜索记录")
                        .padding(horizontal = 13.dp, vertical = 8.dp)) {
                        Text(h.keyword, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (history.size > 3) Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainer)
                    .clickable(role = Role.Button) { expanded = !expanded }.testTag("search_history_expand"), contentAlignment = Alignment.Center) {
                    SearchChevron(if (expanded) "收起搜索历史" else "展开搜索历史", Modifier.size(18.dp).graphicsLayer { rotationZ = if (expanded) -90f else 90f })
                }
            }
        }
        val unique = remember(recommendations) { recommendations.distinctBy { it.name } }
        if (!roomSearch && unique.isNotEmpty()) Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("猜你喜欢", Modifier.weight(1f))
                Box(Modifier.size(36.dp).clickable(role = Role.Button) { recommendationOffset = (recommendationOffset + 6) % unique.size },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Refresh, "换一批", Modifier.size(18.dp), tint = colors.onSurface.copy(alpha = .38f))
                }
            }
            val offset = recommendationOffset % unique.size
            val words = (unique.drop(offset) + unique.take(offset)).take(6)
            words.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { song -> Text(song.name, Modifier.weight(1f).clickable(role = Role.Button) { onSearch(song.name) }
                    .padding(vertical = 9.dp), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (song == words.first()) colors.primary else colors.onSurface.copy(alpha = .78f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            } }
        }
        if (!roomSearch) BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = (maxWidth * .82f).coerceAtMost(340.dp)
            val carousel = rememberLazyListState()
            LazyRow(Modifier.fillMaxWidth().testTag("search_rankings"), state = carousel,
                flingBehavior = rememberSnapFlingBehavior(carousel), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (charts.charts.isEmpty()) item(key = "hot") {
                    SearchRankingCard("热搜榜", "大家正在搜", Modifier.width(cardWidth)) {
                        if (landing.loading) LoadingSongList(3)
                        else if (landing.error != null) { Text("热搜暂时不可用", fontSize = 13.sp); MusicTextAction("重新加载", onRetryHot, Modifier.testTag("search_hot_retry")) }
                        else if (landing.words.isEmpty()) Text("暂无热搜", fontSize = 13.sp)
                        landing.words.take(8).forEachIndexed { index, word ->
                            RankingRow(index, word.displayText, word.description, Modifier.testTag("search_hot_$index")) { onSearch(word.text) }
                        }
                    }
                }
                items(charts.charts, key = { it.id }) { chart ->
                    LaunchedEffect(chart.id) { onLoadChart(chart.id, false) }
                    val page = charts.pages[chart.id] ?: SearchChartPage(loading = true)
                    SearchRankingCard(chart.name, "", Modifier.width(cardWidth).testTag("search_chart_${chart.id}")) {
                        if (page.loading) LoadingSongList(3)
                        else if (page.error != null) {
                            Text("榜单暂时不可用", fontSize = 13.sp)
                            MusicTextAction("重新加载", { onLoadChart(chart.id, true) }, Modifier.testTag("search_chart_retry_${chart.id}"))
                        } else if (page.words.isEmpty()) Text("暂无热搜词", fontSize = 13.sp)
                        page.words.take(10).forEachIndexed { index, word ->
                            RankingRow(index, word.displayText, word.description, Modifier.testTag("search_chart_word_${chart.id}_$index")) { onSearch(word.text) }
                        }
                    }
                }
                if (charts.error != null) item(key = "charts_status") {
                    SearchRankingCard("音乐榜单", "", Modifier.width(cardWidth)) {
                        if (charts.loading) LoadingSongList(3)
                        else { Text("榜单暂时不可用", fontSize = 13.sp); MusicTextAction("重新加载", onRetryCharts) }
                    }
                }
                if (recent.isNotEmpty()) item(key = "recent") {
                    SearchRankingCard("最近播放", "接着发现喜欢的音乐", Modifier.width(cardWidth)) {
                        recent.take(8).forEachIndexed { index, song -> RankingRow(index, song.name, song.artists) { onSearch(song.name) } }
                    }
                }
            }
        }
    }
}

@Composable private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable private fun SearchRankingCard(title: String, subtitle: String, modifier: Modifier, onOpen: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(modifier.heightIn(min = 476.dp).clip(RoundedCornerShape(22.dp)).background(colors.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onOpen != null) SearchChevron("查看完整$title", Modifier.size(18.dp))
        }
        if (subtitle.isNotBlank()) Text(subtitle, Modifier.padding(bottom = 10.dp), fontSize = 11.sp, color = colors.onSurface.copy(alpha = .4f))
        content()
    }
}

@Composable private fun RankingRow(index: Int, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("${index + 1}", Modifier.width(26.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = if (index < 3) colors.primary.copy(alpha = .8f) else colors.onSurface.copy(alpha = .35f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = colors.onSurface.copy(alpha = .4f))
        }
    }
}

@Composable internal fun SearchSuggestionsContent(query: String, suggestions: SearchSuggestions, onSearch: (String) -> Unit, modifier: Modifier) {
    val colors = MiuixTheme.colorScheme
    LazyColumn(modifier.testTag("search_suggestions"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item { Text("搜索建议", Modifier.padding(bottom = 8.dp), fontSize = 13.sp, color = colors.onSurface.copy(alpha = .45f)) }
        itemsIndexed(suggestions.words, key = { _, word -> word }) { index, word ->
            Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(role = Role.Button) { onSearch(word) }
                .testTag("search_suggestion_$index").padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = colors.onSurface.copy(alpha = .35f))
                Text(word, Modifier.padding(start = 12.dp), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (suggestions.words.isEmpty()) item {
            if (suggestions.loading) LoadingSongList(3)
            else MusicTextAction("搜索“$query”", { onSearch(query) }, Modifier.fillMaxWidth().testTag("search_suggestion_direct"))
        }
    }
}

@Composable private fun SearchChevron(description: String, modifier: Modifier) {
    val ink = MiuixTheme.colorScheme.onSurface.copy(alpha = .38f)
    Canvas(modifier.semantics { contentDescription = description }) {
        drawLine(ink, Offset(size.width * .35f, size.height * .2f), Offset(size.width * .65f, size.height * .5f), 1.7.dp.toPx(), StrokeCap.Round)
        drawLine(ink, Offset(size.width * .65f, size.height * .5f), Offset(size.width * .35f, size.height * .8f), 1.7.dp.toPx(), StrokeCap.Round)
    }
}
