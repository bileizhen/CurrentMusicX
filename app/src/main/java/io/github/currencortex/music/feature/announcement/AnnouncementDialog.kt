package io.github.currencortex.music.feature.announcement

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.announcement.Announcement
import io.github.currencortex.music.feature.update.MarkdownText
import io.github.currencortex.music.ui.component.MusicDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AnnouncementDialog(items: List<Announcement>, suppressByDefault: Boolean = false,
    onOpenLink: ((String) -> Unit)? = null, onDismiss: (Boolean) -> Unit) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val pager = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val heights = remember(items) { mutableStateMapOf<Int, Int>() }
    val maxHeight = (LocalConfiguration.current.screenHeightDp - 260).coerceIn(64, 460).dp
    val currentHeight = with(LocalDensity.current) { heights[pager.currentPage]?.toDp() } ?: maxHeight
    val height by animateDpAsState(currentHeight.coerceAtMost(maxHeight), tween(240), label = "announcement_height")
    val dismiss = { onDismiss(suppressByDefault) }
    val detailUrl = items[pager.currentPage.coerceIn(items.indices)].detailUrl
    val openDetails: (String) -> Unit = onOpenLink ?: { url ->
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未找到可以打开链接的应用", Toast.LENGTH_SHORT).show()
        }
    }
    MusicDialog(if (items.size == 1) "公告" else "公告（${items.size}）", dismiss,
        footer = {
            if (items.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                if (items.size <= 7) items.indices.forEach { index ->
                    Box(Modifier.size(24.dp).semantics { contentDescription = "第${index + 1}条公告" }
                        .clickable { scope.launch { pager.animateScrollToPage(index) } }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(6.dp).background(MiuixTheme.colorScheme.primary.copy(
                            alpha = if (pager.currentPage == index) 1f else .2f), RoundedCornerShape(3.dp)))
                    }
                }
                Text("${pager.currentPage + 1} / ${items.size}", fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 8.dp).testTag("announcement_page"))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val style = TextStyle(fontSize = 15.sp)
                val margin = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                TextButton("不再显示", onClick = { onDismiss(true) }, textStyle = style, insideMargin = margin,
                    minHeight = 48.dp, modifier = Modifier.weight(1f).testTag("announcement_suppress"))
                TextButton("知道了", onClick = dismiss, textStyle = style, insideMargin = margin,
                    minHeight = 48.dp, modifier = Modifier.weight(1f).testTag("announcement_dismiss"))
                if (detailUrl != null) TextButton("查看详情", onClick = { openDetails(detailUrl) },
                    textStyle = style, insideMargin = margin, minHeight = 48.dp,
                    modifier = Modifier.weight(1f).testTag("announcement_details"))
            }
        }) {
        HorizontalPager(state = pager, pageSpacing = 16.dp, modifier = Modifier.fillMaxWidth()
            .height(height)
            .testTag("announcements")) { index ->
            val item = items[index]
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).onSizeChanged {
                if (heights[index] != it.height) heights[index] = it.height
            },
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.pinned) Text("置顶", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.background(MiuixTheme.colorScheme.primary.copy(alpha = .1f),
                        RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                Text(item.title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                val date = remember(item.createdAt) {
                    if (item.createdAt <= 0) "" else runCatching {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochSecond(item.createdAt))
                    }.getOrDefault("")
                }
                Text(listOf(item.author.ifBlank { "CurrentStation" }, date).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                if (item.body.isNotBlank()) MarkdownText(item.body, compact = true)
            }
        }
    }
}
